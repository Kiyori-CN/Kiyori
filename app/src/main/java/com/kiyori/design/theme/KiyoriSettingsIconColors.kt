package com.kiyori.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 设置详情页图标使用的低饱和功能色。
 *
 * 设置页会在同一张卡片中并列多个业务入口；这里保留七类既有语义，同时降低综合色彩的冲突，
 * 避免直接修改全应用语义色后连带影响文件管理、工具箱等非设置界面。
 */
internal fun resolveKiyoriSettingsIconColors(
    tone: KiyoriSemanticTone,
    isDark: Boolean,
): KiyoriSemanticColors =
    if (isDark) {
        when (tone) {
            KiyoriSemanticTone.BLUE ->
                KiyoriSemanticColors(Color(0xFFA8C9EE), Color(0xFF243548))
            KiyoriSemanticTone.GREEN ->
                KiyoriSemanticColors(Color(0xFF8CCDB3), Color(0xFF213A32))
            KiyoriSemanticTone.PURPLE ->
                KiyoriSemanticColors(Color(0xFFC3B5EA), Color(0xFF342F48))
            KiyoriSemanticTone.ORANGE ->
                KiyoriSemanticColors(Color(0xFFECB681), Color(0xFF433425))
            KiyoriSemanticTone.RED ->
                KiyoriSemanticColors(Color(0xFFEEA0A5), Color(0xFF462A2D))
            KiyoriSemanticTone.CYAN ->
                KiyoriSemanticColors(Color(0xFF8CCFD6), Color(0xFF213B3F))
            KiyoriSemanticTone.PINK ->
                KiyoriSemanticColors(Color(0xFFE2A6C0), Color(0xFF452D39))
        }
    } else {
        when (tone) {
            KiyoriSemanticTone.BLUE ->
                KiyoriSemanticColors(Color(0xFF3F6FA8), Color(0xFFE8F0FA))
            KiyoriSemanticTone.GREEN ->
                KiyoriSemanticColors(Color(0xFF2C7A63), Color(0xFFE6F2EE))
            KiyoriSemanticTone.PURPLE ->
                KiyoriSemanticColors(Color(0xFF6C5AA7), Color(0xFFEEEBF8))
            KiyoriSemanticTone.ORANGE ->
                KiyoriSemanticColors(Color(0xFFAA642A), Color(0xFFF8EEE4))
            KiyoriSemanticTone.RED ->
                KiyoriSemanticColors(Color(0xFFB84F56), Color(0xFFF9E9EA))
            KiyoriSemanticTone.CYAN ->
                KiyoriSemanticColors(Color(0xFF297B86), Color(0xFFE5F2F4))
            KiyoriSemanticTone.PINK ->
                KiyoriSemanticColors(Color(0xFFA75078), Color(0xFFF7E8EF))
        }
    }

@Composable
internal fun KiyoriSemanticTone.resolveSettingsIconColors(): KiyoriSemanticColors {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return resolveKiyoriSettingsIconColors(this, isDark)
}

internal fun resolveKiyoriSettingsThemeShortcutIconColor(isDark: Boolean): Color =
    if (isDark) {
        Color(0xFFC3B5EA)
    } else {
        Color(0xFFB86A18)
    }
