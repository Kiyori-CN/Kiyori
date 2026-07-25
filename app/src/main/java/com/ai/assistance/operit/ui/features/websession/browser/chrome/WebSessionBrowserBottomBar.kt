package com.ai.assistance.operit.ui.features.websession.browser.chrome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
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
    canGoBack: Boolean,
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
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrowserBottomBarAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.web_session_back),
                enabled = canGoBack,
                onClick = onBack,
            )
            BrowserBottomBarAction(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.web_session_forward),
                enabled = canGoForward,
                onClick = onForward,
            )
            BrowserBottomBarAction(
                icon = Icons.Filled.Home,
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
                icon = Icons.Filled.MoreHoriz,
                contentDescription = stringResource(R.string.web_session_browser_menu_button),
                onClick = onToolbox,
            )
        }
    }
}

@Composable
private fun RowScope.BrowserBottomBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(46.dp)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                        role = Role.Button
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(25.dp),
            )
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
                    .size(46.dp)
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
    Surface(
        modifier = modifier.size(width = 23.dp, height = 21.dp),
        shape = RoundedCornerShape(3.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = contentColor,
        border = BorderStroke(1.7.dp, contentColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
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
}
