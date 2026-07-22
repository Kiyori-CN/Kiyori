package com.ai.assistance.operit.ui.main.shell

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.action.ActionListenerFactory
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.data.repository.WorkflowRepository
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.NavigationSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

private const val AI_DRAWER_ANIMATION_MILLIS = 280

private data class AiDrawerPermissionStatus(
    val badgeTextResId: Int,
)

@Composable
internal fun KiyoriModalAiDrawer(
    isOpen: Boolean,
    selectedEntryId: String?,
    navigationEntries: List<NavigationEntrySpec>,
    isNetworkAvailable: Boolean,
    networkType: String,
    onDismiss: () -> Unit,
    onEntrySelected: (NavigationEntrySpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val drawerTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val separatingFoldLeftPx = rememberSeparatingVerticalFoldLeftPx()
    val visibilityFraction by
        animateFloatAsState(
            targetValue = if (isOpen) 1f else 0f,
            animationSpec = tween(durationMillis = AI_DRAWER_ANIMATION_MILLIS),
            label = "kiyoriAiDrawerVisibility",
        )
    val isVisible = isOpen || visibilityFraction > 0.001f

    BackHandler(enabled = isVisible) {
        if (isOpen) {
            onDismiss()
        }
    }

    if (!isVisible) {
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val foldLeftDp =
            separatingFoldLeftPx?.let { foldLeftPx ->
                foldLeftPx / density.density
            }
        val drawerWidth =
            calculateKiyoriAiDrawerWidthDp(
                windowWidthDp = maxWidth.value,
                separatingFoldLeftDp = foldLeftDp,
            ).dp
        val backgroundInteractionSource = remember { MutableInteractionSource() }
        val drawerInteractionSource = remember { MutableInteractionSource() }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f * visibilityFraction),
                    )
                    .clickable(
                        interactionSource = backgroundInteractionSource,
                        indication = null,
                        onClick = { if (isOpen) onDismiss() },
                    ),
        )

        Box(
            modifier =
                Modifier
                    .width(drawerWidth)
                    .padding(top = drawerTopInset)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = -drawerWidth.toPx() * (1f - visibilityFraction)
                    }
                    .clickable(
                        interactionSource = drawerInteractionSource,
                        indication = null,
                        onClick = {},
                    )
                    .zIndex(1f),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape =
                    RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 16.dp,
                        bottomEnd = 16.dp,
                        bottomStart = 0.dp,
                    ),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
            ) {
                KiyoriAiDrawerContent(
                    isOpen = isOpen,
                    selectedEntryId = selectedEntryId,
                    navigationEntries = navigationEntries,
                    isNetworkAvailable = isNetworkAvailable,
                    networkType = networkType,
                    onEntrySelected = onEntrySelected,
                )
            }
        }
    }
}

@Composable
private fun KiyoriAiDrawerContent(
    isOpen: Boolean,
    selectedEntryId: String?,
    navigationEntries: List<NavigationEntrySpec>,
    isNetworkAvailable: Boolean,
    networkType: String,
    onEntrySelected: (NavigationEntrySpec) -> Unit,
) {
    val context = LocalContext.current
    val preferredPermissionLevel by
        androidPermissionPreferences.preferredPermissionLevelFlow.collectAsState(initial = null)
    val quickActionEntries =
        navigationEntries.filter { entry ->
            entry.surface == NavigationSurface.MAIN_SIDEBAR_TOOLS
        }
    val aiEntries =
        navigationEntries.filter { entry ->
            entry.surface == NavigationSurface.MAIN_SIDEBAR_AI
        }
    val pluginEntries =
        navigationEntries.filter { entry ->
            entry.surface == NavigationSurface.MAIN_SIDEBAR_PLUGINS
        }
    val settingsEntry =
        navigationEntries.single { entry -> entry.entryId == "main.settings" }
    val packageManager =
        remember(context) {
            PackageManager.getInstance(context, AIToolHandler.getInstance(context))
        }
    val workflowRepository = remember(context) { WorkflowRepository(context) }
    val activePackageCount by
        produceState<Int?>(initialValue = null, isOpen) {
            if (isOpen) {
                value = withContext(Dispatchers.IO) { packageManager.getEnabledPackageNames().size }
            }
        }
    val workflowCount by
        produceState<Int?>(initialValue = null, isOpen) {
            if (isOpen) {
                value =
                    withContext(Dispatchers.IO) {
                        workflowRepository.getAllWorkflows().getOrThrow().size
                    }
            }
        }
    val permissionStatus by
        produceState<AiDrawerPermissionStatus?>(
            initialValue = null,
            isOpen,
            preferredPermissionLevel,
        ) {
            if (isOpen) {
                value =
                    withContext(Dispatchers.IO) {
                        resolveAiDrawerPermissionStatus(
                            context = context,
                            preferredPermissionLevel = preferredPermissionLevel,
                        )
                    }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(end = 8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            KiyoriAiDrawerStatusHeader(
                isNetworkAvailable = isNetworkAvailable,
                networkType = networkType,
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                quickActionEntries.forEach { entry ->
                    val badgeText =
                        when (entry.entryId) {
                            "main.packages" -> activePackageCount?.toString() ?: "..."
                            "main.shizuku_commands" ->
                                permissionStatus?.let { status ->
                                    stringResource(status.badgeTextResId)
                                } ?: "..."
                            "main.workflow" -> workflowCount?.toString() ?: "..."
                            else -> error("Unexpected AI drawer quick action ${entry.entryId}")
                        }
                    val label =
                        if (entry.entryId == "main.shizuku_commands") {
                            stringResource(R.string.sidebar_permission_short)
                        } else {
                            entry.title
                        }
                    KiyoriAiDrawerQuickAction(
                        modifier = Modifier.weight(1f),
                        entry = entry,
                        label = label,
                        badgeText = badgeText,
                        selected = selectedEntryId == entry.entryId,
                        enabled = isOpen,
                        onClick = { onEntrySelected(entry) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            KiyoriAiDrawerGroupTitle(stringResource(R.string.nav_group_ai_features))
            aiEntries.forEach { entry ->
                KiyoriAiDrawerNavigationRow(
                    entry = entry,
                    selected = selectedEntryId == entry.entryId,
                    enabled = isOpen,
                    onClick = { onEntrySelected(entry) },
                )
            }

            if (pluginEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                KiyoriAiDrawerGroupTitle(stringResource(R.string.nav_group_plugins))
                pluginEntries.forEach { entry ->
                    KiyoriAiDrawerNavigationRow(
                        entry = entry,
                        selected = selectedEntryId == entry.entryId,
                        enabled = isOpen,
                        onClick = { onEntrySelected(entry) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        KiyoriAiDrawerNavigationRow(
            entry = settingsEntry,
            label = stringResource(R.string.kiyori_shell_ai_settings),
            selected = selectedEntryId == settingsEntry.entryId,
            enabled = isOpen,
            onClick = { onEntrySelected(settingsEntry) },
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun KiyoriAiDrawerStatusHeader(
    isNetworkAvailable: Boolean,
    networkType: String,
) {
    val statusColor =
        if (isNetworkAvailable) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.error
        }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.kiyori_ai_drawer_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = CircleShape,
            color = statusColor.copy(alpha = 0.12f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector =
                        if (isNetworkAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = stringResource(R.string.network_status_label),
                    tint = statusColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = networkType,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun KiyoriAiDrawerQuickAction(
    entry: NavigationEntrySpec,
    label: String,
    badgeText: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun KiyoriAiDrawerGroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 28.dp, end = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun KiyoriAiDrawerNavigationRow(
    entry: NavigationEntrySpec,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    label: String = entry.title,
) {
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .height(40.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private suspend fun resolveAiDrawerPermissionStatus(
    context: Context,
    preferredPermissionLevel: AndroidPermissionLevel?,
): AiDrawerPermissionStatus =
    when (preferredPermissionLevel) {
        null,
        AndroidPermissionLevel.STANDARD ->
            AiDrawerPermissionStatus(R.string.sidebar_status_normal)
        AndroidPermissionLevel.DEBUGGER ->
            when {
                !ShizukuAuthorizer.isShizukuInstalled(context) ->
                    AiDrawerPermissionStatus(R.string.status_not_installed)
                !ShizukuAuthorizer.isShizukuServiceRunning() ->
                    AiDrawerPermissionStatus(R.string.status_not_running)
                ShizukuAuthorizer.hasShizukuPermission() ->
                    AiDrawerPermissionStatus(R.string.sidebar_status_normal)
                else -> AiDrawerPermissionStatus(R.string.unauthorized)
            }
        AndroidPermissionLevel.ACCESSIBILITY,
        AndroidPermissionLevel.ADMIN,
        AndroidPermissionLevel.ROOT -> {
            val permissionStatus =
                ActionListenerFactory.getListener(context, preferredPermissionLevel).hasPermission()
            AiDrawerPermissionStatus(
                if (permissionStatus.granted) {
                    R.string.sidebar_status_normal
                } else {
                    R.string.unauthorized
                },
            )
        }
    }

@Composable
private fun rememberSeparatingVerticalFoldLeftPx(): Int? {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val foldLeftPx by
        produceState<Int?>(initialValue = null, activity) {
            WindowInfoTracker.getOrCreate(context)
                .windowLayoutInfo(activity)
                .collect { layoutInfo ->
                    value =
                        layoutInfo.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .filter { feature ->
                                feature.isSeparating &&
                                    feature.orientation == FoldingFeature.Orientation.VERTICAL &&
                                    feature.bounds.left > 0
                            }
                            .minOfOrNull { feature -> feature.bounds.left }
                }
        }
    return foldLeftPx
}

private tailrec fun Context.findActivity(): Activity =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> error("KiyoriModalAiDrawer must be hosted by an Activity")
    }
