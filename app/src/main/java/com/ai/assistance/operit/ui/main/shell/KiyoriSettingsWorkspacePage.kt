package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiyori.design.theme.KiyoriSettingsTheme
import com.kiyori.design.theme.LocalKiyoriSettingsColors

internal const val KIYORI_SETTINGS_WORKSPACE_TOP_BAR_HEIGHT_DP = 56
internal const val KIYORI_SETTINGS_WORKSPACE_HORIZONTAL_PADDING_DP = 16
internal const val KIYORI_SETTINGS_WORKSPACE_VERTICAL_PADDING_DP = 12

/**
 * 长表单、编辑器、统计和多标签页面使用的设置工作台。
 *
 * 这些页面需要保留自己的滚动与输入状态，不能再嵌套一层 LazyColumn；工作台只统一安全区顶栏、
 * Settings 主题、页面背景、Snackbar/FAB 宿主和底部系统栏边界。若继续依赖外层 Operit TopAppBar，
 * 同一设置会同时存在两套标题与背景所有者，来源切换时也更容易出现透明首帧和视觉跳变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KiyoriSettingsWorkspacePage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: KiyoriSettingsNavigationIcon = KiyoriSettingsNavigationIcon.BACK,
    snackbarHostState: SnackbarHostState? = null,
    headerAction: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    KiyoriSettingsTheme {
        val colors = LocalKiyoriSettingsColors.current
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                KiyoriSettingsWorkspaceTopBar(
                    title = title,
                    onBack = onBack,
                    navigationIcon = navigationIcon,
                    headerAction = headerAction,
                )
            },
            snackbarHost = {
                snackbarHostState?.let { state ->
                    SnackbarHost(hostState = state)
                }
            },
            floatingActionButton = {
                floatingActionButton?.invoke()
            },
            containerColor = colors.pageBackground,
            contentColor = colors.primaryText,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
            content = content,
        )
    }
}

@Composable
private fun KiyoriSettingsWorkspaceTopBar(
    title: String,
    onBack: () -> Unit,
    navigationIcon: KiyoriSettingsNavigationIcon,
    headerAction: (@Composable () -> Unit)?,
) {
    val colors = LocalKiyoriSettingsColors.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.pageBackground)
                .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(KIYORI_SETTINGS_WORKSPACE_TOP_BAR_HEIGHT_DP.dp)
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector =
                        when (navigationIcon) {
                            KiyoriSettingsNavigationIcon.BACK ->
                                Icons.AutoMirrored.Filled.ArrowBack
                            KiyoriSettingsNavigationIcon.MENU -> Icons.Default.Menu
                        },
                    contentDescription =
                        when (navigationIcon) {
                            KiyoriSettingsNavigationIcon.BACK -> "返回"
                            KiyoriSettingsNavigationIcon.MENU -> "菜单"
                        },
                    tint = colors.primaryText,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .semantics { heading() },
            )
            if (headerAction == null) {
                Spacer(modifier = Modifier.size(48.dp))
            } else {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    headerAction()
                }
            }
        }
        HorizontalDivider(
            color = colors.divider,
            thickness = 0.6.dp,
        )
    }
}
