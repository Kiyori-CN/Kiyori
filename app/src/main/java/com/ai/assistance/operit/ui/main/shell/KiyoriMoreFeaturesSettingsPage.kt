package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

internal const val KIYORI_MORE_FEATURES_SETTINGS_PAGE_TITLE = "更多功能"

internal enum class KiyoriMoreFeaturesSettingsAction {
    OPEN_PERMISSIONS,
    OPEN_AGREEMENT,
    OPEN_NETWORK_PROXY,
}

internal data class KiyoriMoreFeaturesSettingsEntrySpec(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val action: KiyoriMoreFeaturesSettingsAction,
)

internal data class KiyoriMoreFeaturesSettingsGroupSpec(
    val title: String,
    val description: String,
    val entries: List<KiyoriMoreFeaturesSettingsEntrySpec>,
)

internal val kiyoriMoreFeaturesSettingsGroups =
    listOf(
        KiyoriMoreFeaturesSettingsGroupSpec(
            title = "网络能力",
            description = "为 Kiyori 内部的不同模块选择直连或内嵌 Mihomo 代理",
            entries =
                listOf(
                    KiyoriMoreFeaturesSettingsEntrySpec(
                        title = "网络代理",
                        description = "管理订阅、策略组、模块路由和脚本规则",
                        icon = Icons.Default.VpnKey,
                        action = KiyoriMoreFeaturesSettingsAction.OPEN_NETWORK_PROXY,
                    ),
                ),
        ),
        KiyoriMoreFeaturesSettingsGroupSpec(
            title = "应用与隐私",
            description = "查看 Kiyori 的用户协议、隐私政策与应用使用边界",
            entries =
                listOf(
                    KiyoriMoreFeaturesSettingsEntrySpec(
                        title = "用户协议与隐私政策",
                        description = "查看用户协议、隐私政策和当前协议版本",
                        icon = Icons.Default.Policy,
                        action = KiyoriMoreFeaturesSettingsAction.OPEN_AGREEMENT,
                    ),
                ),
        ),
        KiyoriMoreFeaturesSettingsGroupSpec(
            title = "系统能力",
            description = "集中管理需要系统授权的设备能力与执行入口",
            entries =
                listOf(
                    KiyoriMoreFeaturesSettingsEntrySpec(
                        title = "权限",
                        description = "管理应用权限、系统访问与高级设备能力",
                        icon = Icons.Default.Security,
                        action = KiyoriMoreFeaturesSettingsAction.OPEN_PERMISSIONS,
                    ),
                ),
        ),
    )

@Composable
internal fun KiyoriMoreFeaturesSettingsPage(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAgreement: () -> Unit,
    onOpenNetworkProxy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KiyoriCollapsingSettingsPage(
        title = KIYORI_MORE_FEATURES_SETTINGS_PAGE_TITLE,
        onBack = onBack,
        modifier = modifier,
    ) {
        items(
            items = kiyoriMoreFeaturesSettingsGroups,
            key = KiyoriMoreFeaturesSettingsGroupSpec::title,
        ) { group ->
            KiyoriSettingsGroupSection(
                title = group.title,
                description = group.description,
            ) {
                group.entries.forEachIndexed { index, entry ->
                    KiyoriSettingsRow(
                        title = entry.title,
                        description = entry.description,
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = entry.icon,
                        onClick = {
                            when (entry.action) {
                                KiyoriMoreFeaturesSettingsAction.OPEN_PERMISSIONS ->
                                    onOpenPermissions()
                                KiyoriMoreFeaturesSettingsAction.OPEN_AGREEMENT ->
                                    onOpenAgreement()
                                KiyoriMoreFeaturesSettingsAction.OPEN_NETWORK_PROXY ->
                                    onOpenNetworkProxy()
                            }
                        },
                    )
                    if (index != group.entries.lastIndex) {
                        KiyoriSettingsDivider()
                    }
                }
            }
        }
    }
}
