package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 浏览器菜单使用比全应用状态语义更细的入口身份色。
 *
 * 每个枚举值与菜单中的一个固定动作一一对应；子抽屉、弹窗和入口外的关联 UI 必须复用同一个值，
 * 否则菜单入口与内容页会产生视觉身份漂移。
 */
internal enum class WebSessionBrowserMenuTone(
    val rowIndex: Int,
    val columnIndex: Int,
    val lightIcon: Color,
    val lightContainer: Color,
    val darkIcon: Color,
    val darkContainer: Color,
) {
    ADD_BOOKMARK(
        0,
        0,
        Color(0xFF1976D2),
        Color(0xFFE3F2FD),
        Color(0xFF90CAF9),
        Color(0xFF0B2A42),
    ),
    BOOKMARKS(
        0,
        1,
        Color(0xFF7E57C2),
        Color(0xFFEDE9FE),
        Color(0xFFC4B5FD),
        Color(0xFF2E1065),
    ),
    HISTORY(
        0,
        2,
        Color(0xFFB45309),
        Color(0xFFFEF3C7),
        Color(0xFFFCD34D),
        Color(0xFF451A03),
    ),
    DOWNLOADS(
        0,
        3,
        Color(0xFF059669),
        Color(0xFFD1FAE5),
        Color(0xFF6EE7B7),
        Color(0xFF022C22),
    ),
    PLUGINS(
        0,
        4,
        Color(0xFFC026D3),
        Color(0xFFFAE8FF),
        Color(0xFFF0ABFC),
        Color(0xFF4A044E),
    ),
    FLOATING_SNIFFER(
        1,
        0,
        Color(0xFF0891B2),
        Color(0xFFCFFAFE),
        Color(0xFF67E8F9),
        Color(0xFF083344),
    ),
    USER_AGENT(
        1,
        1,
        Color(0xFF4F46E5),
        Color(0xFFE0E7FF),
        Color(0xFFA5B4FC),
        Color(0xFF1E1B4B),
    ),
    NETWORK_LOG(
        1,
        2,
        Color(0xFF0F766E),
        Color(0xFFCCFBF1),
        Color(0xFF5EEAD4),
        Color(0xFF042F2E),
    ),
    AI_DIALOGUE(
        1,
        3,
        Color(0xFF0369A1),
        Color(0xFFE0F2FE),
        Color(0xFF7DD3FC),
        Color(0xFF082F49),
    ),
    TOOLBOX(
        1,
        4,
        Color(0xFFEA580C),
        Color(0xFFFFEDD5),
        Color(0xFFFDBA74),
        Color(0xFF431407),
    ),
    INCOGNITO(
        2,
        0,
        Color(0xFF6B21A8),
        Color(0xFFF3E8FF),
        Color(0xFFD8B4FE),
        Color(0xFF3B0764),
    ),
    READER_MODE(
        2,
        1,
        Color(0xFF4D7C0F),
        Color(0xFFECFCCB),
        Color(0xFFBEF264),
        Color(0xFF1A2E05),
    ),
    PAGE_SOURCE(
        2,
        2,
        Color(0xFF1D4ED8),
        Color(0xFFDBEAFE),
        Color(0xFF93C5FD),
        Color(0xFF172554),
    ),
    AD_MARKING(
        2,
        3,
        Color(0xFFDC2626),
        Color(0xFFFEE2E2),
        Color(0xFFFCA5A5),
        Color(0xFF450A0A),
    ),
    SITE_CONFIG(
        2,
        4,
        Color(0xFFA16207),
        Color(0xFFFEF9C3),
        Color(0xFFFDE047),
        Color(0xFF422006),
    ),
    EXIT_BROWSER(
        3,
        0,
        Color(0xFFE11D48),
        Color(0xFFFFE4E6),
        Color(0xFFFDA4AF),
        Color(0xFF4C0519),
    ),
    COLLAPSE(
        3,
        1,
        Color(0xFF0F8F77),
        Color(0xFFD7F9F1),
        Color(0xFF83E6CF),
        Color(0xFF073B36),
    ),
    BROWSER_SETTINGS(
        3,
        2,
        Color(0xFF475569),
        Color(0xFFE2E8F0),
        Color(0xFFCBD5E1),
        Color(0xFF1E293B),
    ),
}

@Immutable
internal data class WebSessionBrowserMenuColors(
    val icon: Color,
    val container: Color,
)

internal fun resolveWebSessionBrowserMenuColors(
    tone: WebSessionBrowserMenuTone,
    isDark: Boolean,
): WebSessionBrowserMenuColors =
    if (isDark) {
        WebSessionBrowserMenuColors(
            icon = tone.darkIcon,
            container = tone.darkContainer,
        )
    } else {
        WebSessionBrowserMenuColors(
            icon = tone.lightIcon,
            container = tone.lightContainer,
        )
    }

@Composable
internal fun WebSessionBrowserMenuTone.resolveColors(): WebSessionBrowserMenuColors {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return resolveWebSessionBrowserMenuColors(this, isDark)
}

internal val WebSessionDetectedMediaBadgeTone = WebSessionBrowserMenuTone.FLOATING_SNIFFER

@Composable
internal fun WebSessionBrowserMenuIconBadge(
    imageVector: ImageVector,
    tone: WebSessionBrowserMenuTone,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerSize: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
) {
    val colors = tone.resolveColors()
    val alpha = if (enabled) 1f else 0.38f
    Surface(
        modifier = modifier.size(containerSize),
        shape = shape,
        color = colors.container.copy(alpha = alpha),
        contentColor = colors.icon.copy(alpha = alpha),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = colors.icon.copy(alpha = alpha),
            )
        }
    }
}

@Composable
internal fun WebSessionBrowserMenuIconBadge(
    painter: Painter,
    tone: WebSessionBrowserMenuTone,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerSize: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
) {
    val colors = tone.resolveColors()
    val alpha = if (enabled) 1f else 0.38f
    Surface(
        modifier = modifier.size(containerSize),
        shape = shape,
        color = colors.container.copy(alpha = alpha),
        contentColor = colors.icon.copy(alpha = alpha),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = colors.icon.copy(alpha = alpha),
            )
        }
    }
}
