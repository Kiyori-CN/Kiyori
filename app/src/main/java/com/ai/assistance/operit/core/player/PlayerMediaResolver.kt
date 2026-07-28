package com.ai.assistance.operit.core.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.util.Locale

internal class PlayerMediaResolver(context: Context) {
    private val appContext = context.applicationContext
    private var contentFileDescriptor: ParcelFileDescriptor? = null

    fun resolve(request: PlayerMediaRequest): String {
        close()
        val uri = Uri.parse(request.uri)
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "content" -> {
                val descriptor =
                    requireNotNull(appContext.contentResolver.openFileDescriptor(uri, "r")) {
                        "Cannot open content URI"
                    }
                contentFileDescriptor = descriptor
                "fd://${descriptor.fd}"
            }
            "file" -> requireNotNull(uri.path) { "File URI has no path" }
            "http", "https", "rtsp", "rtmp", "rtmps" -> request.uri
            null -> File(request.uri).absolutePath
            else -> error("Unsupported player URI scheme: ${uri.scheme}")
        }
    }

    fun close() {
        runCatching { contentFileDescriptor?.close() }
            .onFailure { AppLogger.w(TAG, "Failed to close player content descriptor", it) }
        contentFileDescriptor = null
    }

    private companion object {
        const val TAG = "PlayerMediaResolver"
    }
}
