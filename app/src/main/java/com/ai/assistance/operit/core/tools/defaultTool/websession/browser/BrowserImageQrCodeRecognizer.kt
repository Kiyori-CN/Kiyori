package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import okhttp3.Headers

internal sealed interface BrowserQrCodeRecognitionResult {
    data class Success(val content: String) : BrowserQrCodeRecognitionResult

    data object ImageLoadFailed : BrowserQrCodeRecognitionResult

    data object NotRecognized : BrowserQrCodeRecognitionResult
}

internal class BrowserImageQrCodeRecognizer(
    private val appContext: Context,
) {
    suspend fun recognize(
        url: String,
        requestHeaders: Map<String, String>,
    ): BrowserQrCodeRecognitionResult {
        val headers = Headers.Builder()
        sanitizeBrowserImageRequestHeaders(requestHeaders).forEach { (name, value) ->
            headers.set(name, value)
        }
        val request =
            ImageRequest.Builder(appContext)
                .data(url)
                .headers(headers.build())
                .size(MAX_BROWSER_QR_BITMAP_SIZE_PX, MAX_BROWSER_QR_BITMAP_SIZE_PX)
                .allowHardware(false)
                .crossfade(false)
                .build()
        val result = Coil.imageLoader(appContext).execute(request)
        if (result !is SuccessResult) {
            return BrowserQrCodeRecognitionResult.ImageLoadFailed
        }
        val bitmap = result.drawable.toBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(
            pixels,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height,
        )
        val content = decodeBrowserQrCodePixels(bitmap.width, bitmap.height, pixels)
        return if (content == null) {
            BrowserQrCodeRecognitionResult.NotRecognized
        } else {
            BrowserQrCodeRecognitionResult.Success(content)
        }
    }
}

internal fun decodeBrowserQrCodePixels(
    width: Int,
    height: Int,
    pixels: IntArray,
): String? {
    require(width > 0 && height > 0) { "QR bitmap dimensions must be positive" }
    require(pixels.size == width * height) {
        "QR pixel count does not match bitmap dimensions"
    }
    val bitmap =
        BinaryBitmap(
            HybridBinarizer(
                RGBLuminanceSource(width, height, pixels),
            ),
        )
    return try {
        QRCodeReader().decode(bitmap).text
    } catch (_: ReaderException) {
        null
    }
}

private const val MAX_BROWSER_QR_BITMAP_SIZE_PX = 2048
