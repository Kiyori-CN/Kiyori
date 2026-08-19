package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

internal const val KIYORI_MORE_FEATURES_SETTINGS_PAGE_TITLE = "更多功能"

internal enum class KiyoriMoreFeaturesSettingsAction {
    OPEN_PERMISSIONS,
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
            title = "系统能力",
            description = "集中管理需要系统授权的设备能力与执行入口",
            entries =
                listOf(
                    KiyoriMoreFeaturesSettingsEntrySpec(
                        title = "权限",
                        description = "配置 Shizuku、无障碍、Root 与设备执行权限",
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
