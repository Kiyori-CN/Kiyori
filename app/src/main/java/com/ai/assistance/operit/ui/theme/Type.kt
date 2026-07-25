package com.ai.assistance.operit.ui.theme

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.core.net.toFile
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import java.io.File

private val Material3Typography = Typography()

val Typography =
    Typography(
        displayLarge = Material3Typography.displayLarge.copy(letterSpacing = 0.sp),
        displayMedium = Material3Typography.displayMedium.copy(letterSpacing = 0.sp),
        displaySmall = Material3Typography.displaySmall.copy(letterSpacing = 0.sp),
        headlineLarge = Material3Typography.headlineLarge.copy(letterSpacing = 0.sp),
        headlineMedium = Material3Typography.headlineMedium.copy(letterSpacing = 0.sp),
        headlineSmall = Material3Typography.headlineSmall.copy(letterSpacing = 0.sp),
        titleLarge = Material3Typography.titleLarge.copy(letterSpacing = 0.sp),
        titleMedium = Material3Typography.titleMedium.copy(letterSpacing = 0.sp),
        titleSmall = Material3Typography.titleSmall.copy(letterSpacing = 0.sp),
        bodyLarge = Material3Typography.bodyLarge.copy(letterSpacing = 0.sp),
        bodyMedium = Material3Typography.bodyMedium.copy(letterSpacing = 0.sp),
        bodySmall = Material3Typography.bodySmall.copy(letterSpacing = 0.sp),
        labelLarge = Material3Typography.labelLarge.copy(letterSpacing = 0.sp),
        labelMedium = Material3Typography.labelMedium.copy(letterSpacing = 0.sp),
        labelSmall = Material3Typography.labelSmall.copy(letterSpacing = 0.sp),
    )

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
 * 将指定 FontFamily 应用于整套 Typography
 */
fun applyFontFamilyToTypography(
    baseTypography: Typography,
    fontFamily: FontFamily?,
): Typography {
    if (fontFamily == null) {
        return baseTypography
    }

    return Typography(
        displayLarge = baseTypography.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = baseTypography.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = fontFamily),
    )
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
        return Typography
    }

    val fontFamily =
        resolveConfiguredFontFamily(
            context = context,
            useCustomFont = useCustomFont,
            fontType = fontType,
            systemFontName = systemFontName,
            customFontPath = customFontPath,
        ) ?: FontFamily.Default
    val baseTypography = applyFontFamilyToTypography(Typography, fontFamily)

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
