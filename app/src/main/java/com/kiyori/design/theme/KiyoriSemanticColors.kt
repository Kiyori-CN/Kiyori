package com.kiyori.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class KiyoriSemanticTone {
    BLUE,
    GREEN,
    PURPLE,
    ORANGE,
    RED,
    CYAN,
    PINK,
}

internal fun kiyoriSemanticToneForStableId(stableId: String): KiyoriSemanticTone =
    KiyoriSemanticTone.entries[
        Math.floorMod(stableId.hashCode(), KiyoriSemanticTone.entries.size)
    ]

@Immutable
data class KiyoriSemanticColors(
    val icon: Color,
    val container: Color,
)

internal fun resolveKiyoriSemanticColors(
    tone: KiyoriSemanticTone,
    isDark: Boolean,
): KiyoriSemanticColors =
    if (isDark) {
        when (tone) {
            KiyoriSemanticTone.BLUE ->
                KiyoriSemanticColors(Color(0xFF90CAF9), Color(0xFF163044))
            KiyoriSemanticTone.GREEN ->
                KiyoriSemanticColors(Color(0xFF67D7A5), Color(0xFF17382D))
            KiyoriSemanticTone.PURPLE ->
                KiyoriSemanticColors(Color(0xFFB7A7FF), Color(0xFF2D254A))
            KiyoriSemanticTone.ORANGE ->
                KiyoriSemanticColors(Color(0xFFFFB76A), Color(0xFF432B16))
            KiyoriSemanticTone.RED ->
                KiyoriSemanticColors(Color(0xFFFF8A8A), Color(0xFF472323))
            KiyoriSemanticTone.CYAN ->
                KiyoriSemanticColors(Color(0xFF65D3E8), Color(0xFF173942))
            KiyoriSemanticTone.PINK ->
                KiyoriSemanticColors(Color(0xFFF49AC0), Color(0xFF452336))
        }
    } else {
        when (tone) {
            KiyoriSemanticTone.BLUE ->
                KiyoriSemanticColors(Color(0xFF1E88E5), Color(0xFFE8F3FE))
            KiyoriSemanticTone.GREEN ->
                KiyoriSemanticColors(Color(0xFF1B8D5F), Color(0xFFE6F5EE))
            KiyoriSemanticTone.PURPLE ->
                KiyoriSemanticColors(Color(0xFF7056D9), Color(0xFFF0ECFF))
            KiyoriSemanticTone.ORANGE ->
                KiyoriSemanticColors(Color(0xFFC66A13), Color(0xFFFFF0DF))
            KiyoriSemanticTone.RED ->
                KiyoriSemanticColors(Color(0xFFD64545), Color(0xFFFDEAEA))
            KiyoriSemanticTone.CYAN ->
                KiyoriSemanticColors(Color(0xFF168CA7), Color(0xFFE4F5F8))
            KiyoriSemanticTone.PINK ->
                KiyoriSemanticColors(Color(0xFFC8467D), Color(0xFFFCEAF2))
        }
    }

internal val KiyoriBottomNavigationSelectedFillColor = Color(0xFFFFC153)

internal fun resolveKiyoriWeatherSunColor(isDark: Boolean): Color =
    if (isDark) {
        Color(0xFFFFD166)
    } else {
        Color(0xFFC57C00)
    }
