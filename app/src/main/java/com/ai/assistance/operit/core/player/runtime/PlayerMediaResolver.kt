package com.ai.assistance.operit.core.player.runtime

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.net.toUri
import java.io.File
import java.util.Locale

internal class PlayerMediaResolver(context: Context) {
    private val appContext = context.applicationContext
    private var contentFileDescriptor: ParcelFileDescriptor? = null

    fun resolve(uriText: String): String {
        close()
        val uri = uriText.toUri()
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
            "http", "https", "rtsp", "rtmp", "rtmps" -> uriText
            null -> File(uriText).absolutePath
            else -> error("Unsupported player URI scheme: ${uri.scheme}")
        }
    }

    fun close() {
        runCatching { contentFileDescriptor?.close() }
            .onFailure { error ->
                Log.w(TAG, "Failed to close player content descriptor", error)
            }
        contentFileDescriptor = null
    }

    private companion object {
        const val TAG = "PlayerMediaResolver"
    }
}
