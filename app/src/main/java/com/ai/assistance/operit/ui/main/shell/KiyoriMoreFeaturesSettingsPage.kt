package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.kiyori.design.theme.KiyoriSemanticTone

internal const val KIYORI_MORE_FEATURES_SETTINGS_PAGE_TITLE = "更多功能"

internal enum class KiyoriMoreFeaturesSettingsAction {
    OPEN_PERMISSIONS,
    OPEN_NETWORK_PROXY,
    OPEN_OPEN_SOURCE,
    OPEN_USER_AGREEMENT,
    OPEN_PRIVACY_POLICY,
}

internal data class KiyoriMoreFeaturesSettingsEntrySpec(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTone: KiyoriSemanticTone,
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
            title = "系统能力",
            description = "管理 Kiyori 在设备上运行所需的授权和系统访问能力",
            entries =
                listOf(
                    KiyoriMoreFeaturesSettingsEntrySpec(
                        title = "权限管理",
                        description = "查看应用权限、系统访问和高级设备能力的当前状态",
                        icon = Icons.Default.Security,
                        iconTone = KiyoriSemanticTone.GREEN,
                        action = KiyoriMoreFeaturesSettingsAction.OPEN_PERMISSIONS,
                    ),
                ),
        ),
        KiyoriMoreFeaturesSettingsGroupSpec(
            title = "网络能力",
            description = "为 Kiyori 内部模块管理代理订阅、策略组和路由规则",
            entries =
                listOf(
                    KiyoriMoreFeaturesSettingsEntrySpec(
                        title = "网络代理",
                        description = "配置直连或内嵌 Mihomo 代理，并查看当前连接策略",
                        icon = Icons.Default.VpnKey,
                        iconTone = KiyoriSemanticTone.CYAN,
                        action = KiyoriMoreFeaturesSettingsAction.OPEN_NETWORK_PROXY,
                    ),
                ),
        ),
        KiyoriMoreFeaturesSettingsGroupSpec(
            title = "开源与法律",
            description = "了解 Kiyori 的开源组成、使用规则和数据处理方式",
            entries =
                listOf(
                    KiyoriMoreFeaturesSettingsEntrySpec(
                        title = "开源协议",
                        description = "查看随 Kiyori 分发的开源组件、许可证和项目地址",
                        icon = Icons.Default.Code,
                        iconTone = KiyoriSemanticTone.ORANGE,
                        action = KiyoriMoreFeaturesSettingsAction.OPEN_OPEN_SOURCE,
                    ),
                    KiyoriMoreFeaturesSettingsEntrySpec(
                        title = "用户协议",
                        description = "查看 Kiyori 的服务边界、使用规则和责任说明",
                        icon = Icons.Default.Description,
                        iconTone = KiyoriSemanticTone.BLUE,
                        action = KiyoriMoreFeaturesSettingsAction.OPEN_USER_AGREEMENT,
                    ),
                    KiyoriMoreFeaturesSettingsEntrySpec(
                        title = "隐私政策",
                        description = "查看设备端数据、联网场景、权限用途和删除方式",
                        icon = Icons.Default.PrivacyTip,
                        iconTone = KiyoriSemanticTone.PURPLE,
                        action = KiyoriMoreFeaturesSettingsAction.OPEN_PRIVACY_POLICY,
                    ),
                ),
        ),
    )

@Composable
internal fun KiyoriMoreFeaturesSettingsPage(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenNetworkProxy: () -> Unit,
    onOpenOpenSource: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
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
                        iconTone = entry.iconTone,
                        onClick = {
                            when (entry.action) {
                                KiyoriMoreFeaturesSettingsAction.OPEN_PERMISSIONS ->
                                    onOpenPermissions()
                                KiyoriMoreFeaturesSettingsAction.OPEN_NETWORK_PROXY ->
                                    onOpenNetworkProxy()
                                KiyoriMoreFeaturesSettingsAction.OPEN_OPEN_SOURCE ->
                                    onOpenOpenSource()
                                KiyoriMoreFeaturesSettingsAction.OPEN_USER_AGREEMENT ->
                                    onOpenUserAgreement()
                                KiyoriMoreFeaturesSettingsAction.OPEN_PRIVACY_POLICY ->
                                    onOpenPrivacyPolicy()
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
