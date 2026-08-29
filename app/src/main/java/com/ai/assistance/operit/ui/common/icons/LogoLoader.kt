package com.ai.assistance.operit.ui.common.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.core.graphics.createBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.util.AppLogger
import com.caverock.androidsvg.SVG
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.max

private data class CachedRemoteLogo(val bytes: ByteArray, val mimeType: String?)

object LogoBitmapLoader {
    private const val TAG = "LogoBitmapLoader"
    private val rasterExtensions = setOf("png", "jpg", "jpeg", "webp")
    private val rasterMimeTypes = setOf("image/png", "image/jpeg", "image/webp")

    fun load(bytes: ByteArray, mimeType: String?, fileName: String?, sizePx: Int): Bitmap? {
        require(sizePx > 0) { "Logo size must be positive" }
        return try {
            when {
                isSvg(mimeType, fileName) -> bytes.inputStream().use { renderSvg(it, sizePx) }
                isRaster(mimeType, fileName) -> renderRaster(bytes, sizePx)
                else -> null
            }
        } catch (error: OutOfMemoryError) {
            AppLogger.e(
                TAG,
                "Logo bitmap allocation failed file=${fileName.orEmpty()} mime=${mimeType.orEmpty()}",
                error,
            )
            null
        } catch (error: Exception) {
            AppLogger.e(
                TAG,
                "Failed to decode logo file=${fileName.orEmpty()} mime=${mimeType.orEmpty()}",
                error,
            )
            null
        }
    }

    private fun isSvg(mimeType: String?, fileName: String?): Boolean {
        return mimeType.equals("image/svg+xml", ignoreCase = true) ||
            fileName.extension().equals("svg", ignoreCase = true)
    }

    private fun isRaster(mimeType: String?, fileName: String?): Boolean {
        return mimeType?.trim()?.lowercase()?.let { it in rasterMimeTypes } == true ||
            fileName.extension().lowercase() in rasterExtensions
    }

    private fun renderSvg(input: InputStream, sizePx: Int): Bitmap? {
        val svg = SVG.getFromInputStream(input)
        val width = svg.documentWidth.takeIf { it.isFinite() && it > 0f } ?: 24f
        val height = svg.documentHeight.takeIf { it.isFinite() && it > 0f } ?: 24f
        val scale = sizePx / max(width, height)
        val scaledWidth = width * scale
        val scaledHeight = height * scale
        svg.setDocumentWidth(scaledWidth)
        svg.setDocumentHeight(scaledHeight)
        val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate((sizePx - scaledWidth) / 2f, (sizePx - scaledHeight) / 2f)
        svg.renderToPicture().draw(canvas)
        return bitmap
    }

    private fun renderRaster(bytes: ByteArray, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        val decodeEdgeLimit = sizePx.toLong() * 2L
        while (
            bounds.outWidth.toLong() / sampleSize > decodeEdgeLimit ||
                bounds.outHeight.toLong() / sampleSize > decodeEdgeLimit
        ) {
            sampleSize = sampleSize shl 1
        }
        val source =
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: return null
        if (source.width == sizePx && source.height == sizePx) return source
        val scale = sizePx / max(source.width, source.height).toFloat()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawBitmap(
            source,
            null,
            android.graphics.RectF(
                (sizePx - width) / 2f,
                (sizePx - height) / 2f,
                (sizePx + width) / 2f,
                (sizePx + height) / 2f
            ),
            null
        )
        source.recycle()
        return bitmap
    }

    private fun String?.extension(): String = this?.substringAfterLast('.', "").orEmpty()
}

object RemoteLogoLoader {
    private const val TAG = "RemoteLogoLoader"
    private const val MAX_LOGO_BYTES = 512 * 1024L
    private const val MAX_CACHE_BYTES = 4 * 1024 * 1024
    private val cache = object : LruCache<String, CachedRemoteLogo>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: CachedRemoteLogo): Int = value.bytes.size
    }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .build()
    }

    fun load(url: String, sizePx: Int): Bitmap? {
        val parsed = url.trim().toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "https") return null
        return try {
            val cached = synchronized(cache) { cache.get(parsed.toString()) }
            val logo = cached ?: fetch(parsed.toString())?.also { synchronized(cache) { cache.put(parsed.toString(), it) } }
            logo?.let { LogoBitmapLoader.load(it.bytes, it.mimeType, parsed.encodedPath, sizePx) }
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to load remote logo host=${parsed.host}", error)
            null
        }
    }

    private fun fetch(url: String): CachedRemoteLogo? {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_LOGO_BYTES) return null
            val bytes = body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_LOGO_BYTES) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return CachedRemoteLogo(bytes, response.header("Content-Type")?.substringBefore(';')?.trim())
        }
    }
}

@Composable
fun rememberLogoPainter(
    logoKey: Any?,
    bytes: ByteArray?,
    mimeType: String?,
    fileName: String?,
    size: Dp = 24.dp
): Painter? {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, logoKey, bytes?.contentHashCode(), mimeType, fileName, sizePx) {
        value = bytes?.let { withContext(Dispatchers.IO) { LogoBitmapLoader.load(it, mimeType, fileName, sizePx)?.asImageBitmap() } }
    }
    return bitmap?.let(::BitmapPainter)
}

@Composable
fun rememberRemoteLogoPainter(logoUrl: String?, size: Dp = 24.dp): Painter? {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, logoUrl, sizePx) {
        value = logoUrl?.trim()?.takeIf(String::isNotBlank)?.let { url ->
            withContext(Dispatchers.IO) { RemoteLogoLoader.load(url, sizePx)?.asImageBitmap() }
        }
    }
    return bitmap?.let(::BitmapPainter)
}
