package com.ai.assistance.operit.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.net.toFile
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.design.theme.KiyoriTypography
import com.kiyori.design.theme.applyFontFamilyToTypography
import java.io.File

/**
 * 根据系统字体名称获取 FontFamily
 */
fun getSystemFontFamily(systemFontName: String): FontFamily {
    return when (systemFontName) {
        UserPreferencesManager.SYSTEM_FONT_SERIF -> FontFamily.Serif
        UserPreferencesManager.SYSTEM_FONT_SANS_SERIF -> FontFamily.SansSerif
        UserPreferencesManager.SYSTEM_FONT_MONOSPACE -> FontFamily.Monospace
        UserPreferencesManager.SYSTEM_FONT_CURSIVE -> FontFamily.Cursive
        else -> FontFamily.Default
    }
}

/**
 * 从文件路径加载自定义字体
 */
fun loadCustomFontFamily(context: Context, fontPath: String): FontFamily? {
    return try {
        // - 修复了 file:// URI 路径无法被 File 正确解析的问题
        val file = if (fontPath.startsWith("file://")) {
            Uri.parse(fontPath).toFile()
        } else {
            File(fontPath)
        }
        
        if (!file.exists()) {
            AppLogger.e("TypeKt", "Font file does not exist: $fontPath")
            return null
        }
        
        FontFamily(
            Font(file)
        )
    } catch (e: Exception) {
        AppLogger.e("TypeKt", "Error loading custom font from $fontPath", e)
        null
    }
}

/**
 * 根据配置解析可选 FontFamily
 */
fun resolveConfiguredFontFamily(
    context: Context,
    useCustomFont: Boolean,
    fontType: String,
    systemFontName: String,
    customFontPath: String?,
): FontFamily? {
    if (!useCustomFont) {
        return null
    }

    return when (fontType) {
        UserPreferencesManager.FONT_TYPE_SYSTEM -> getSystemFontFamily(systemFontName)
        UserPreferencesManager.FONT_TYPE_FILE ->
            customFontPath
                ?.takeIf { it.isNotBlank() }
                ?.let { loadCustomFontFamily(context, it) }
        else -> null
    }
}

/**
 * 根据用户设置创建自定义 Typography
 */
fun createCustomTypography(
    context: Context,
    useCustomFont: Boolean,
    fontType: String,
    systemFontName: String,
    customFontPath: String?,
    fontScale: Float
): Typography {
    // 如果不使用自定义字体且字体大小为默认值，则直接返回默认Typography
    if (!useCustomFont && fontScale == 1.0f) {
        return KiyoriTypography
    }

    val fontFamily =
        resolveConfiguredFontFamily(
            context = context,
            useCustomFont = useCustomFont,
            fontType = fontType,
            systemFontName = systemFontName,
            customFontPath = customFontPath,
        ) ?: FontFamily.Default
    val baseTypography = applyFontFamilyToTypography(KiyoriTypography, fontFamily)

    // Helper to apply scale. It will be applied to every style.
    fun TextStyle.withScale(): TextStyle = if (fontScale != 1.0f) {
        copy(fontSize = fontSize * fontScale, lineHeight = lineHeight * fontScale)
    } else {
        this
    }

    // 创建带有自定义字体的 Typography
    return Typography(
        displayLarge = baseTypography.displayLarge.withScale(),
        displayMedium = baseTypography.displayMedium.withScale(),
        displaySmall = baseTypography.displaySmall.withScale(),
        headlineLarge = baseTypography.headlineLarge.withScale(),
        headlineMedium = baseTypography.headlineMedium.withScale(),
        headlineSmall = baseTypography.headlineSmall.withScale(),
        titleLarge = baseTypography.titleLarge.withScale(),
        titleMedium = baseTypography.titleMedium.withScale(),
        titleSmall = baseTypography.titleSmall.withScale(),
        bodyLarge = baseTypography.bodyLarge.withScale(),
        bodyMedium = baseTypography.bodyMedium.withScale(),
        bodySmall = baseTypography.bodySmall.withScale(),
        labelLarge = baseTypography.labelLarge.withScale(),
        labelMedium = baseTypography.labelMedium.withScale(),
        labelSmall = baseTypography.labelSmall.withScale()
    )
}
