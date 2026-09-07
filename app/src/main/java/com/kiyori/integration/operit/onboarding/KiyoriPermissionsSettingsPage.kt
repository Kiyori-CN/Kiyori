package com.kiyori.integration.operit.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.core.tools.system.RootAuthorizer
import com.ai.assistance.operit.ui.main.shell.KiyoriCollapsingSettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsDivider
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsGroupCard
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsGroupSection
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import com.kiyori.design.theme.resolveSettingsIconColors
import com.kiyori.design.theme.KiyoriUiShapes
import com.kiyori.platform.logging.KiyoriLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val KIYORI_PERMISSION_SETTINGS_PAGE_TITLE = "权限管理"

internal fun kiyoriPermissionActionLabel(
    action: KiyoriPermissionActionKind,
    status: KiyoriPermissionStatus,
): String? =
    when (action) {
        KiyoriPermissionActionKind.REQUEST_RUNTIME -> "授权"
        KiyoriPermissionActionKind.OPEN_APPLICATION_SETTINGS -> "管理"
        KiyoriPermissionActionKind.OPEN_RESTRICTED_SETTINGS -> "前往设置"
        KiyoriPermissionActionKind.OPEN_SYSTEM_SETTINGS ->
            if (status == KiyoriPermissionStatus.GRANTED) "管理" else "前往设置"
        KiyoriPermissionActionKind.CONFIGURE_ACCESSIBILITY ->
            if (status == KiyoriPermissionStatus.REQUIRES_SETUP) "安装并设置" else "启用"
        KiyoriPermissionActionKind.CONFIGURE_SHIZUKU ->
            if (status == KiyoriPermissionStatus.REQUIRES_SETUP) "配置" else "授权"
        KiyoriPermissionActionKind.REQUEST_ROOT -> "请求授权"
        KiyoriPermissionActionKind.NONE -> null
    }

@Composable
internal fun KiyoriPermissionsSettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var snapshot by
        remember(context) {
            mutableStateOf(readKiyoriPermissionSnapshot(context))
        }
    var refreshRequestId by remember { mutableLongStateOf(0L) }
    var refreshing by remember { mutableStateOf(false) }
    var permissionQueueNames by remember { mutableStateOf(emptyList<String>()) }
    var runtimeRequestInFlight by remember { mutableStateOf(false) }
    var waitingForExternalSettings by remember { mutableStateOf(false) }
    var activePermissionId by remember { mutableStateOf<KiyoriPermissionId?>(null) }

    fun requestRefresh() {
        refreshRequestId += 1L
    }

    val runtimePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            runtimeRequestInFlight = false
            activePermissionId = null
            requestRefresh()
        }

    LaunchedEffect(refreshRequestId) {
        if (refreshRequestId == 0L) {
            return@LaunchedEffect
        }
        refreshing = true
        try {
            snapshot =
                withContext(Dispatchers.IO) {
                    readKiyoriPermissionSnapshot(context.applicationContext)
                }
        } catch (error: Exception) {
            KiyoriLogger.e(
                "KiyoriPermissionsSettings",
                "刷新设备权限状态失败",
                error,
            )
        } finally {
            refreshing = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (waitingForExternalSettings) {
                        waitingForExternalSettings = false
                        activePermissionId = null
                    }
                    requestRefresh()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val operationActive =
        runtimeRequestInFlight ||
            waitingForExternalSettings ||
            permissionQueueNames.isNotEmpty()

    fun enqueuePermissions(permissionIds: List<KiyoriPermissionId>) {
        if (operationActive || permissionIds.isEmpty()) {
            return
        }
        permissionQueueNames = permissionIds.distinct().map(KiyoriPermissionId::name)
    }

    LaunchedEffect(
        permissionQueueNames,
        runtimeRequestInFlight,
        waitingForExternalSettings,
        snapshot,
    ) {
        if (
            permissionQueueNames.isEmpty() ||
                runtimeRequestInFlight ||
                waitingForExternalSettings
        ) {
            return@LaunchedEffect
        }
        val queue = permissionQueueNames.map(KiyoriPermissionId::valueOf)
        val runtimeIds =
            queue.filter { permissionId ->
                resolveKiyoriPermissionAction(
                    permissionId = permissionId,
                    status = snapshot.status(permissionId),
                ) == KiyoriPermissionActionKind.REQUEST_RUNTIME
            }
        if (runtimeIds.isNotEmpty()) {
            permissionQueueNames =
                queue
                    .filterNot(runtimeIds::contains)
                    .map(KiyoriPermissionId::name)
            activePermissionId = runtimeIds.first()
            runtimeRequestInFlight = true
            runtimePermissionLauncher.launch(
                kiyoriRuntimePermissionsForSdk(
                    sdkInt = Build.VERSION.SDK_INT,
                    selectedPermissionIds = runtimeIds.toSet(),
                ).toTypedArray(),
            )
            return@LaunchedEffect
        }

        val permissionId = queue.first()
        permissionQueueNames = queue.drop(1).map(KiyoriPermissionId::name)
        val status = snapshot.status(permissionId)
        val action =
            resolveKiyoriPermissionAction(
                permissionId = permissionId,
                status = status,
            )
        activePermissionId = permissionId
        try {
            when (action) {
                KiyoriPermissionActionKind.OPEN_APPLICATION_SETTINGS -> {
                    waitingForExternalSettings = true
                    launchKiyoriApplicationPermissionSettings(context)
                }

                KiyoriPermissionActionKind.OPEN_RESTRICTED_SETTINGS -> {
                    waitingForExternalSettings = true
                    launchKiyoriApplicationPermissionSettings(context)
                }

                KiyoriPermissionActionKind.OPEN_SYSTEM_SETTINGS -> {
                    waitingForExternalSettings = true
                    launchKiyoriPermissionSettings(
                        context = context,
                        permissionId = permissionId,
                    )
                }

                KiyoriPermissionActionKind.CONFIGURE_ACCESSIBILITY -> {
                    waitingForExternalSettings = true
                    performKiyoriAccessibilityAction(context)
                }

                KiyoriPermissionActionKind.CONFIGURE_SHIZUKU -> {
                    waitingForExternalSettings = true
                    performKiyoriShizukuAction(context) {
                        scope.launch {
                            if (it) {
                                try {
                                    activateKiyoriShizukuExecution()
                                } catch (error: Exception) {
                                    KiyoriLogger.e(
                                        "KiyoriPermissionsSettings",
                                        "Shizuku granted but execution state activation failed",
                                        error,
                                    )
                                }
                            }
                            waitingForExternalSettings = false
                            activePermissionId = null
                            requestRefresh()
                        }
                    }
                }

                KiyoriPermissionActionKind.REQUEST_ROOT -> {
                    waitingForExternalSettings = true
                    RootAuthorizer.requestRootPermission {
                        scope.launch {
                            waitingForExternalSettings = false
                            activePermissionId = null
                            requestRefresh()
                        }
                    }
                }

                KiyoriPermissionActionKind.REQUEST_RUNTIME ->
                    error("Runtime permissions must be handled by the shared launcher")

                KiyoriPermissionActionKind.NONE -> {
                    activePermissionId = null
                }
            }
        } catch (error: Exception) {
            KiyoriLogger.e(
                "KiyoriPermissionsSettings",
                "无法打开或请求设置权限: $permissionId",
                error,
            )
            waitingForExternalSettings = false
            activePermissionId = null
            showKiyoriPermissionActionFailure(context, permissionId)
        }
    }

    KiyoriCollapsingSettingsPage(
        title = KIYORI_PERMISSION_SETTINGS_PAGE_TITLE,
        onBack = onBack,
        modifier = modifier,
        headerAction = {
            IconButton(
                onClick = ::requestRefresh,
                enabled = !refreshing && !operationActive,
                modifier = Modifier.size(40.dp),
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = LocalKiyoriSettingsColors.current.accent,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重新检查权限状态",
                        tint =
                            if (operationActive) {
                                LocalKiyoriSettingsColors.current.mutedIcon
                            } else {
                                LocalKiyoriSettingsColors.current.primaryText
                            },
                    )
                }
            }
        },
    ) {
        item(key = "kiyori_permissions_summary") {
            KiyoriPermissionSummaryCard(
                summary = summarizeKiyoriPermissions(snapshot),
                refreshing = refreshing,
                operationActive = operationActive,
            )
        }
        kiyoriPermissionGroups.forEach { group ->
            item(key = "kiyori_permissions_group_${group.id.name}") {
                KiyoriPermissionGroup(
                    group = group,
                    snapshot = snapshot,
                    operationActive = operationActive,
                    activePermissionId = activePermissionId,
                    onPermissionClick = { permissionId ->
                        enqueuePermissions(listOf(permissionId))
                    },
                )
            }
        }
        item(key = "permission_scope") { KiyoriPermissionScopeNote(Modifier.padding(horizontal = 15.dp)) }
    }
}

@Composable
private fun KiyoriPermissionSummaryCard(
    summary: KiyoriPermissionSummary,
    refreshing: Boolean,
    operationActive: Boolean,
) {
    val colors = LocalKiyoriSettingsColors.current
    KiyoriSettingsGroupCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "权限，由你决定",
                        color = colors.primaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            if (refreshing) {
                                "正在重新检查真实系统状态"
                            } else {
                                "按功能开启，随时可以在系统中调整。无需全部授权。"
                            },
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = "${summary.readyCount} 项已授权",
                    color = colors.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KiyoriPermissionMetric(
                    label = "已授权",
                    value = summary.readyCount,
                    modifier = Modifier.weight(1f),
                )
                KiyoriPermissionMetric(
                    label = "可选开启",
                    value = summary.actionRequiredCount,
                    modifier = Modifier.weight(1f),
                )
                KiyoriPermissionMetric(
                    label = "使用时确认",
                    value = summary.onDemandCount,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = if (operationActive) "正在处理你选择的权限，请完成系统确认。" else
                    "${summary.notApplicableCount} 项在当前系统无需单独授权。下方分组可展开查看完整用途。",
                color = colors.secondaryText, fontSize = 12.sp, lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun KiyoriPermissionMetric(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKiyoriSettingsColors.current
    Column(
        modifier =
            modifier
                .background(
                    color = colors.pageBackground,
                    shape = KiyoriUiShapes.control,
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value.toString(),
            color = colors.primaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            color = colors.secondaryText,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun KiyoriPermissionGroup(
    group: KiyoriPermissionGroupSpec,
    snapshot: KiyoriPermissionSnapshot,
    operationActive: Boolean,
    activePermissionId: KiyoriPermissionId?,
    onPermissionClick: (KiyoriPermissionId) -> Unit,
) {
    KiyoriPermissionDisclosure(
        group = group,
        modifier = Modifier.padding(horizontal = 15.dp),
    ) {
        group.permissionIds.forEachIndexed { index, permissionId ->
            val status = snapshot.status(permissionId)
            KiyoriPermissionRow(
                permissionId = permissionId,
                status = status,
                operationActive = operationActive,
                active = activePermissionId == permissionId,
                onClick = { onPermissionClick(permissionId) },
            )
            if (index != group.permissionIds.lastIndex) {
                KiyoriSettingsDivider()
            }
        }
    }
}

@Composable
private fun KiyoriPermissionRow(
    permissionId: KiyoriPermissionId,
    status: KiyoriPermissionStatus,
    operationActive: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalKiyoriSettingsColors.current
    val metadata = kiyoriPermissionMetadata(permissionId)
    val iconColors = metadata.tone.resolveSettingsIconColors()
    val statusColors = kiyoriPermissionStatusTone(status).resolveSettingsIconColors()
    val action =
        resolveKiyoriPermissionAction(
            permissionId = permissionId,
            status = status,
        )
    val actionLabel = kiyoriPermissionActionLabel(action, status)
    val enabled = actionLabel != null && !operationActive

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .background(
                        color = iconColors.container,
                        shape = KiyoriUiShapes.control,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = metadata.icon,
                contentDescription = null,
                tint = iconColors.icon,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metadata.title(context),
                color = colors.primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = metadata.description(context),
                color = colors.secondaryText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = statusColors.container,
                contentColor = statusColors.icon,
            ) {
                Text(
                    text = kiyoriPermissionStatusLabel(status),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            when {
                active ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )

                actionLabel != null ->
                    Text(
                        text = actionLabel,
                        color = if (enabled) colors.accent else colors.mutedIcon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                else -> Spacer(modifier = Modifier.height(17.dp))
            }
        }
    }
}
