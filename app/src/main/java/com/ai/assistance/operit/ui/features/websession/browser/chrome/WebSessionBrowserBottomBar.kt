package com.ai.assistance.operit.ui.features.websession.browser.chrome

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

@Composable
internal fun WebSessionBrowserBottomBar(
    canNavigateBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onToolbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP.dp,
                        top = WEB_SESSION_BROWSER_BOTTOM_TOP_PADDING_DP.dp,
                        end = WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP.dp,
                        bottom = WEB_SESSION_BROWSER_BOTTOM_BOTTOM_PADDING_DP.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrowserBottomBarAction(
                iconResId = R.drawable.ic_kiyori_browser_bottom_back,
                contentDescription = stringResource(R.string.web_session_back),
                enabled = canNavigateBack,
                onClick = onBack,
            )
            BrowserBottomBarAction(
                iconResId = R.drawable.ic_kiyori_browser_bottom_forward,
                contentDescription = stringResource(R.string.web_session_forward),
                enabled = canGoForward,
                onClick = onForward,
            )
            BrowserBottomBarAction(
                iconResId = R.drawable.ic_kiyori_browser_bottom_home,
                contentDescription = stringResource(R.string.kiyori_shell_browser_home),
                onClick = onHome,
            )
            BrowserBottomBarTabAction(
                count = tabCount,
                contentDescription =
                    "${stringResource(R.string.web_session_windows)}，${tabCount.coerceAtLeast(0)}",
                onClick = onTabs,
            )
            BrowserBottomBarAction(
            iconResId = R.drawable.ic_kiyori_tool_toolbox,
                contentDescription = stringResource(R.string.web_session_browser_menu_button),
                onClick = onToolbox,
            )
        }
    }
}

@Composable
internal fun RowScope.BrowserBottomBarAction(
    iconResId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    BrowserBottomBarSlot(contentDescription, onClick, enabled) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            modifier = Modifier.size(WEB_SESSION_BROWSER_BOTTOM_ICON_SIZE_DP.dp),
        )
    }
}

/** 浏览器与文件管理器共用触摸区域、间距和禁用色，图标由各自领域提供。 */
@Composable
internal fun RowScope.BrowserBottomBarSlot(
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                        role = Role.Button
                    },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun RowScope.BrowserBottomBarTabAction(
    count: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP.dp)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                        role = Role.Button
                    },
            contentAlignment = Alignment.Center,
        ) {
            WebSessionBrowserWindowCountIcon(count = count)
        }
    }
}

@Composable
internal fun WebSessionBrowserWindowCountIcon(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val displayCount = if (count > 99) "99+" else count.coerceAtLeast(0).toString()
    val contentColor = LocalContentColor.current
    val countShape = RoundedCornerShape(1.75.dp)
    Box(
        modifier =
            modifier
                .size(20.dp)
                .clip(countShape)
                .border(1.75.dp, contentColor, countShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayCount,
            style = MaterialTheme.typography.labelSmall,
            fontSize = if (displayCount.length > 2) 7.sp else 9.sp,
            lineHeight = if (displayCount.length > 2) 7.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
