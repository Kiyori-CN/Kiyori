package com.ai.assistance.operit.ui.floating

import androidx.compose.runtime.Composable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.theme.KiyoriLightColorScheme
import com.ai.assistance.operit.ui.theme.Typography as KiyoriTypography

/**
 * 为悬浮窗提供的独立主题
 * 使用静态颜色，避免对Activity上下文的依赖
 */
@Composable
fun FloatingWindowTheme(
    colorScheme: ColorScheme? = null,
    typography: Typography? = null,
    content: @Composable () -> Unit
) {
    val finalColorScheme = colorScheme ?: KiyoriLightColorScheme
    val defaultSmallTypography =
        KiyoriTypography.copy(
            bodyLarge = KiyoriTypography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 18.sp),
            bodyMedium = KiyoriTypography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
            bodySmall = KiyoriTypography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
            labelSmall = KiyoriTypography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
            titleSmall = KiyoriTypography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
            labelMedium = KiyoriTypography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
            labelLarge = KiyoriTypography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp),
        )

    // 优先使用传入的typography，如果没有则使用默认的小型typography
    val finalTypography = typography ?: defaultSmallTypography
    
    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = finalTypography,
        content = content
    )
}
