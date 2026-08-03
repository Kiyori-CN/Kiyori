package com.ai.assistance.operit.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 使用 Android 平台剪贴板保持同步点击处理，避免 Compose 已弃用的
 * LocalClipboardManager，同时让所有调用点共享同一份文本读写契约。
 */
internal fun Context.copyPlainTextToClipboard(label: String, text: CharSequence) {
    val clipboard =
        requireNotNull(getSystemService(ClipboardManager::class.java)) {
            "ClipboardManager system service is unavailable"
        }
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun Context.readPlainTextFromClipboard(): CharSequence? {
    val clipboard =
        requireNotNull(getSystemService(ClipboardManager::class.java)) {
            "ClipboardManager system service is unavailable"
        }
    val clip = clipboard.primaryClip
    if (clip == null || clip.itemCount == 0) {
        return null
    }
    return clip.getItemAt(0).coerceToText(this)
}
