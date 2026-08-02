package com.kiyori.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

private val Material3Typography = Typography()

val KiyoriTypography =
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

/** 将指定 FontFamily 一致地应用到完整 Material Typography。 */
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
