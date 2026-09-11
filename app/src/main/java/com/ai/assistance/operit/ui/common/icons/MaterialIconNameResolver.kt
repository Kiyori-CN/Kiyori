package com.ai.assistance.operit.ui.common.icons

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.concurrent.ConcurrentHashMap

object MaterialIconNameResolver {
    private val iconCache = ConcurrentHashMap<String, ImageVector>()

    fun resolveOrNull(iconName: String?): ImageVector? = resolveOrNull(iconName, outlined = false)

    private fun resolveOrNull(iconName: String?, outlined: Boolean): ImageVector? {
        val normalizedName = iconName?.trim().orEmpty()
        if (normalizedName.isEmpty()) {
            return null
        }
        // 同一插件图标可在不同表面使用不同字形，缓存必须包含样式，避免首次解析污染后续入口。
        val family = if (outlined) "outlined" else "filled"
        val receiver = if (outlined) Icons.Outlined else Icons.Default
        val cacheKey = "$family:$normalizedName"
        iconCache[cacheKey]?.let { return it }
        return runCatching {
            val pascalCaseName =
                normalizedName
                    .split(Regex("[^A-Za-z0-9]+"))
                    .filter { it.isNotBlank() }
                    .joinToString(separator = "") { segment ->
                        segment.replaceFirstChar { char -> char.uppercaseChar() }
                    }
                    .ifBlank {
                        normalizedName.replaceFirstChar { char -> char.uppercaseChar() }
                    }
            require(pascalCaseName.isNotEmpty()) {
                "icon name is invalid: $normalizedName"
            }
            val iconKtClass =
                Class.forName("androidx.compose.material.icons.$family.${pascalCaseName}Kt")
            val getterMethod =
                iconKtClass.getMethod("get$pascalCaseName", receiver::class.java)
            (getterMethod.invoke(null, receiver) as ImageVector)
                .also { iconCache[cacheKey] = it }
        }.getOrNull()
    }

    fun resolveOrDefault(iconName: String?, fallback: ImageVector): ImageVector {
        return runCatching { resolveOrNull(iconName) }.getOrNull() ?: fallback
    }

    /** 输入菜单只请求轮廓版；未知名称继续使用调用方既有的轮廓占位图标。 */
    fun resolveOutlinedOrDefault(iconName: String?, fallback: ImageVector): ImageVector =
        resolveOrNull(iconName, outlined = true) ?: fallback
}
