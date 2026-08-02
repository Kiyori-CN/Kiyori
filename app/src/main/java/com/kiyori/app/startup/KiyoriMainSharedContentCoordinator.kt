package com.kiyori.app.startup

import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.main.SharedFileHandler
import com.kiyori.platform.logging.KiyoriLogger
import kotlinx.coroutines.launch

internal data class KiyoriPendingSharedFiles(
    val uris: List<Uri>,
    val text: String?,
)

internal fun resolvePendingSharedText(
    sharedFileUris: List<Uri>?,
    sharedText: String?,
): String? {
    if (sharedFileUris != null) {
        return null
    }
    val normalizedText = sharedText?.trim()
    if (normalizedText.isNullOrBlank()) {
        return null
    }
    return normalizedText
}

internal fun resolvePendingSharedFiles(
    sharedFileUris: List<Uri>?,
    sharedText: String?,
): KiyoriPendingSharedFiles? {
    if (sharedFileUris == null) {
        return null
    }
    return KiyoriPendingSharedFiles(
        uris = sharedFileUris,
        text = sharedText?.trim(),
    )
}

/**
 * 把 MainActivity 尚未转交的外部分享内容发送到既有 SharedFileHandler。
 *
 * pendingRequests 与 SharedFileHandler 保持各自唯一状态所有权；该 coordinator 只执行
 * 生命周期绑定的转交、日志和错误提示。
 */
internal class KiyoriMainSharedContentCoordinator(
    private val activity: ComponentActivity,
    private val pendingRequests: KiyoriMainPendingRequests,
) {
    private companion object {
        const val TAG = "MainActivity"
    }

    fun processPendingSharedText() {
        if (pendingRequests.sharedFileUris != null) {
            KiyoriLogger.d(TAG, "Pending shared text will be processed with shared files")
            return
        }

        val text =
            resolvePendingSharedText(
                sharedFileUris = pendingRequests.sharedFileUris,
                sharedText = pendingRequests.sharedText,
            )
        if (text == null) {
            KiyoriLogger.d(TAG, "No pending shared text to process")
            return
        }

        SharedFileHandler.setSharedText(text)
        KiyoriLogger.d(TAG, "Successfully passed shared text to SharedFileHandler")
        pendingRequests.clearSharedText()
    }

    fun processPendingSharedFiles() {
        val content =
            resolvePendingSharedFiles(
                sharedFileUris = pendingRequests.sharedFileUris,
                sharedText = pendingRequests.sharedText,
            )
        if (content == null) {
            KiyoriLogger.d(TAG, "No pending shared files to process")
            return
        }

        KiyoriLogger.d(TAG, "Processing ${content.uris.size} pending shared file(s)")
        content.uris.forEachIndexed { index, uri ->
            KiyoriLogger.d(TAG, "  [$index] URI: $uri")
        }

        activity.lifecycleScope.launch {
            try {
                SharedFileHandler.setSharedFiles(content.uris, content.text)
                KiyoriLogger.d(TAG, "Successfully passed shared files to SharedFileHandler")
                pendingRequests.clearSharedFilesAndText()
            } catch (error: Exception) {
                KiyoriLogger.e(TAG, "Failed to process shared files", error)
                Toast.makeText(
                    activity,
                    activity.getString(
                        R.string.chat_process_shared_files_failed,
                        error.message ?: "",
                    ),
                    Toast.LENGTH_LONG,
                ).show()
                pendingRequests.clearSharedFiles()
            }
        }
    }
}
