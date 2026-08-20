package com.kiyori.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 设置首页固定十二入口的产品语义色。
 *
 * 这里不复用七色通用语义枚举，因为设置首页需要让每个入口在同一屏内保持可区分性；
 * 浅色和深色分别定义，避免仅靠透明度或同色深浅制造难以识别的重复入口。
 */
internal enum class KiyoriSettingsHomeIconPalette {
    ACCOUNT_CONNECTION,
    AI_ASSISTANT,
    MINI_APP,
    BROWSER,
    DOWNLOADS,
    FILE_MANAGER,
    VIDEO_PLAYER,
    MUSIC_PLAYER,
    DOCUMENT_READER,
    APPEARANCE,
    DATA_BACKUP,
    MORE_FEATURES,
}

internal fun resolveKiyoriSettingsHomeIconColors(
    palette: KiyoriSettingsHomeIconPalette,
    isDark: Boolean,
): KiyoriSemanticColors =
    if (isDark) {
        when (palette) {
            KiyoriSettingsHomeIconPalette.ACCOUNT_CONNECTION ->
                KiyoriSemanticColors(Color(0xFF67D7A5), Color(0xFF17382D))
            KiyoriSettingsHomeIconPalette.AI_ASSISTANT ->
                KiyoriSemanticColors(Color(0xFFB7A7FF), Color(0xFF2D254A))
            KiyoriSettingsHomeIconPalette.MINI_APP ->
                KiyoriSemanticColors(Color(0xFF66D9C8), Color(0xFF153B36))
            KiyoriSettingsHomeIconPalette.BROWSER ->
                KiyoriSemanticColors(Color(0xFF90CAF9), Color(0xFF163044))
            KiyoriSettingsHomeIconPalette.VIDEO_PLAYER ->
                KiyoriSemanticColors(Color(0xFFFF8A8A), Color(0xFF472323))
            KiyoriSettingsHomeIconPalette.MUSIC_PLAYER ->
                KiyoriSemanticColors(Color(0xFFFFD166), Color(0xFF493B16))
            KiyoriSettingsHomeIconPalette.DOCUMENT_READER ->
                KiyoriSemanticColors(Color(0xFFD7A86E), Color(0xFF44311F))
            KiyoriSettingsHomeIconPalette.DOWNLOADS ->
                KiyoriSemanticColors(Color(0xFFFFB76A), Color(0xFF432B16))
            KiyoriSettingsHomeIconPalette.FILE_MANAGER ->
                KiyoriSemanticColors(Color(0xFFB5CC75), Color(0xFF323A1C))
            KiyoriSettingsHomeIconPalette.APPEARANCE ->
                KiyoriSemanticColors(Color(0xFFF49AC0), Color(0xFF452336))
            KiyoriSettingsHomeIconPalette.DATA_BACKUP ->
                KiyoriSemanticColors(Color(0xFF9FA8FF), Color(0xFF252D4A))
            KiyoriSettingsHomeIconPalette.MORE_FEATURES ->
                KiyoriSemanticColors(Color(0xFF62DDE4), Color(0xFF17373B))
        }
    } else {
        when (palette) {
            KiyoriSettingsHomeIconPalette.ACCOUNT_CONNECTION ->
                KiyoriSemanticColors(Color(0xFF1B8D5F), Color(0xFFE6F5EE))
            KiyoriSettingsHomeIconPalette.AI_ASSISTANT ->
                KiyoriSemanticColors(Color(0xFF7056D9), Color(0xFFF0ECFF))
            KiyoriSettingsHomeIconPalette.MINI_APP ->
                KiyoriSemanticColors(Color(0xFF00897B), Color(0xFFE2F5F2))
            KiyoriSettingsHomeIconPalette.BROWSER ->
                KiyoriSemanticColors(Color(0xFF1E88E5), Color(0xFFE8F3FE))
            KiyoriSettingsHomeIconPalette.VIDEO_PLAYER ->
                KiyoriSemanticColors(Color(0xFFD64545), Color(0xFFFDEAEA))
            KiyoriSettingsHomeIconPalette.MUSIC_PLAYER ->
                KiyoriSemanticColors(Color(0xFFB77900), Color(0xFFFFF5D6))
            KiyoriSettingsHomeIconPalette.DOCUMENT_READER ->
                KiyoriSemanticColors(Color(0xFF8D5A2B), Color(0xFFF8EEE5))
            KiyoriSettingsHomeIconPalette.DOWNLOADS ->
                KiyoriSemanticColors(Color(0xFFC66A13), Color(0xFFFFF0DF))
            KiyoriSettingsHomeIconPalette.FILE_MANAGER ->
                KiyoriSemanticColors(Color(0xFF708238), Color(0xFFF0F4DF))
            KiyoriSettingsHomeIconPalette.APPEARANCE ->
                KiyoriSemanticColors(Color(0xFFC8467D), Color(0xFFFCEAF2))
            KiyoriSettingsHomeIconPalette.DATA_BACKUP ->
                KiyoriSemanticColors(Color(0xFF4055B5), Color(0xFFE9ECFF))
            KiyoriSettingsHomeIconPalette.MORE_FEATURES ->
                KiyoriSemanticColors(Color(0xFF00838F), Color(0xFFE2F5F6))
        }
    }

@Composable
internal fun KiyoriSettingsHomeIconPalette.resolveSettingsHomeIconColors(): KiyoriSemanticColors {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return resolveKiyoriSettingsHomeIconColors(this, isDark)
}
