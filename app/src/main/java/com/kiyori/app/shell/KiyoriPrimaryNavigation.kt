package com.kiyori.app.shell

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_BOTTOM_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_CENTER_ICON_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_ICON_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_TOP_PADDING_DP
import com.ai.assistance.operit.ui.main.shell.KiyoriFileManagementPage
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsHomePage
import com.kiyori.design.theme.KiyoriBottomNavigationSelectedFillColor

private data class PrimaryDestinationVisual(
    val destination: PrimaryDestination,
    val labelResId: Int,
    val iconResId: Int,
    val selectedFillResId: Int,
    val selectedDetailResId: Int,
    val iconSizeDp: Int = WEB_SESSION_BROWSER_BOTTOM_ICON_SIZE_DP,
)

private val primaryDestinationVisuals =
    listOf(
        PrimaryDestinationVisual(
            PrimaryDestination.SOFTWARE_HOME,
            R.string.kiyori_shell_software_home,
            R.drawable.ic_kiyori_nav_home,
            R.drawable.ic_kiyori_nav_home_selected_fill,
            R.drawable.ic_kiyori_nav_home,
        ),
        PrimaryDestinationVisual(
            PrimaryDestination.BROWSER_HOME,
            R.string.kiyori_shell_browser_home,
            R.drawable.ic_kiyori_nav_globe,
            R.drawable.ic_kiyori_nav_globe_selected_fill,
            R.drawable.ic_kiyori_nav_globe,
        ),
        PrimaryDestinationVisual(
            PrimaryDestination.MINI_APP_HOME,
            R.string.kiyori_shell_mini_app_home,
            R.drawable.ic_kiyori_nav_apps,
            R.drawable.ic_kiyori_nav_apps_selected_fill,
            R.drawable.ic_kiyori_nav_apps,
            WEB_SESSION_BROWSER_BOTTOM_CENTER_ICON_SIZE_DP,
        ),
        PrimaryDestinationVisual(
            PrimaryDestination.FILE_MANAGEMENT_HOME,
            R.string.kiyori_shell_file_management_home,
            R.drawable.ic_kiyori_nav_folder,
            R.drawable.ic_kiyori_nav_folder_selected_fill,
            R.drawable.ic_kiyori_nav_folder,
        ),
        PrimaryDestinationVisual(
            PrimaryDestination.SETTINGS_HOME,
            R.string.kiyori_shell_settings_home,
            R.drawable.ic_kiyori_tool_settings,
            R.drawable.ic_kiyori_tool_settings_selected_fill,
            R.drawable.ic_kiyori_tool_settings_selected_detail,
        ),
    )

internal fun resolveKiyoriBottomNavigationSelectedStartScale(
    destination: PrimaryDestination,
): Float =
    when (destination) {
        PrimaryDestination.SOFTWARE_HOME,
        PrimaryDestination.BROWSER_HOME,
        PrimaryDestination.MINI_APP_HOME,
        PrimaryDestination.FILE_MANAGEMENT_HOME -> 1f
        PrimaryDestination.SETTINGS_HOME -> 0.9f
    }

internal fun resolveKiyoriBottomNavigationSelectedFinalScale(
    destination: PrimaryDestination,
): Float =
    when (destination) {
        PrimaryDestination.SOFTWARE_HOME,
        PrimaryDestination.BROWSER_HOME,
        PrimaryDestination.FILE_MANAGEMENT_HOME -> 1.1f
        PrimaryDestination.MINI_APP_HOME -> 1.12f
        PrimaryDestination.SETTINGS_HOME -> 1f
    }

internal fun resolveKiyoriBottomNavigationSelectedSpringDampingRatio(
    destination: PrimaryDestination,
): Float =
    when (destination) {
        PrimaryDestination.SOFTWARE_HOME,
        PrimaryDestination.BROWSER_HOME,
        PrimaryDestination.MINI_APP_HOME,
        PrimaryDestination.FILE_MANAGEMENT_HOME -> 0.42f
        PrimaryDestination.SETTINGS_HOME -> 0.55f
    }

@Composable
internal fun KiyoriPrimaryRootPage(
    destination: PrimaryDestination,
    onOpenAccountConnections: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenSpeechServices: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenDataSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        PrimaryDestination.FILE_MANAGEMENT_HOME -> {
            KiyoriFileManagementPage(modifier = modifier)
            return
        }
        PrimaryDestination.SETTINGS_HOME -> {
            KiyoriSettingsHomePage(
                onOpenAccountConnections = onOpenAccountConnections,
                onOpenAiAssistant = onOpenAiAssistant,
                onOpenSpeechServices = onOpenSpeechServices,
                onOpenBrowserSettings = onOpenBrowserSettings,
                onOpenDownloadSettings = onOpenDownloadSettings,
                onOpenPlayerSettings = onOpenPlayerSettings,
                onOpenAppearanceSettings = onOpenAppearanceSettings,
                onOpenDataSettings = onOpenDataSettings,
                modifier = modifier,
            )
            return
        }
        else -> Unit
    }
    val visual = primaryDestinationVisuals.single { item -> item.destination == destination }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background).padding(bottom = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(visual.iconResId),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(visual.labelResId),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun KiyoriBottomNavigation(
    selectedDestination: PrimaryDestination,
    alpha: Float,
    onDestinationSelected: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastActivatedDestination by remember {
        mutableStateOf<PrimaryDestination?>(null)
    }
    var activationSequence by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
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
                primaryDestinationVisuals.forEach { item ->
                    val label = stringResource(item.labelResId)
                    val selected = selectedDestination == item.destination
                    val interactionSource = remember(item.destination) { MutableInteractionSource() }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP.dp)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        role = Role.Button,
                                        onClick = {
                                            lastActivatedDestination = item.destination
                                            activationSequence += 1
                                            onDestinationSelected(item.destination)
                                        },
                                    )
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = label
                                        role = Role.Button
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            KiyoriBottomNavigationIcon(
                                visual = item,
                                selected = selected,
                                activationSequence =
                                    if (lastActivatedDestination == item.destination) {
                                        activationSequence
                                    } else {
                                        0
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KiyoriBottomNavigationIcon(
    visual: PrimaryDestinationVisual,
    selected: Boolean,
    activationSequence: Int,
) {
    val selectedDetailColor = MaterialTheme.colorScheme.background
    val selectedStartScale =
        resolveKiyoriBottomNavigationSelectedStartScale(visual.destination)
    val selectedFinalScale =
        resolveKiyoriBottomNavigationSelectedFinalScale(visual.destination)
    val selectedSpringDampingRatio =
        resolveKiyoriBottomNavigationSelectedSpringDampingRatio(visual.destination)
    val selectedScale =
        remember(visual.destination) {
            Animatable(if (selected) selectedFinalScale else selectedStartScale)
        }

    LaunchedEffect(
        selected,
        activationSequence,
        selectedStartScale,
        selectedFinalScale,
        selectedSpringDampingRatio,
    ) {
        when {
            !selected -> selectedScale.snapTo(selectedStartScale)
            activationSequence == 0 -> selectedScale.snapTo(selectedFinalScale)
            else -> {
                // 前四项使用更低阻尼扩大黄色填充的弹性峰值；设置项保持原峰值，避免齿轮显得过重。
                selectedScale.animateTo(
                    targetValue = selectedStartScale,
                    animationSpec =
                        tween(
                            durationMillis = 70,
                            easing = FastOutLinearInEasing,
                        ),
                )
                selectedScale.animateTo(
                    targetValue = selectedFinalScale,
                    animationSpec =
                        spring(
                            dampingRatio = selectedSpringDampingRatio,
                            stiffness = 420f,
                        ),
                )
            }
        }
    }

    Crossfade(
        targetState = selected,
        modifier = Modifier.size(visual.iconSizeDp.dp),
        animationSpec = tween(durationMillis = 160),
        label = "kiyoriBottomNavigationIcon",
    ) { isSelected ->
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = selectedScale.value
                            scaleY = selectedScale.value
                        },
            ) {
                Icon(
                    painter = painterResource(visual.selectedFillResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = KiyoriBottomNavigationSelectedFillColor,
                )
                Icon(
                    painter = painterResource(visual.selectedDetailResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = selectedDetailColor,
                )
            }
        } else {
            Icon(
                painter = painterResource(visual.iconResId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
