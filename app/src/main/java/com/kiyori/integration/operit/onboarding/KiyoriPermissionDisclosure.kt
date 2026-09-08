package com.kiyori.integration.operit.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiyori.design.theme.KiyoriUiShapes

/** 首启与设置仅共享展示状态；授权事实和动作继续由原有 owner 提供。 */
@Composable
internal fun KiyoriPermissionDisclosure(
    group: KiyoriPermissionGroupSpec,
    modifier: Modifier = Modifier,
    selectedCount: Int = 0,
    autoExpandWhenSelected: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(group.id.name) {
        mutableStateOf(group.initiallyExpanded || (autoExpandWhenSelected && selectedCount > 0))
    }
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "permissionDisclosure")
    Column(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = { expanded = !expanded },
            shape = KiyoriUiShapes.card,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().semantics {
                heading()
                stateDescription = if (expanded) "已展开" else "已折叠"
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(group.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(group.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp)
                    Text(
                        if (selectedCount > 0) "${group.permissionIds.size} 项 · 已选 $selectedCount 项" else "${group.permissionIds.size} 项 · 按需开启",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(Icons.Default.ExpandMore, null, Modifier.size(24.dp).rotate(angle))
            }
        }
        if (expanded) content()
    }
}

@Composable
internal fun KiyoriPermissionScopeNote(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("还有哪些访问方式？", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "联网、网络状态、前台服务、唤醒锁等由 Android 按声明管理，不需要逐项弹窗。照片和文档可通过系统选择器按次提供；屏幕捕获在使用时确认。网站授权与 AI 工具执行授权另行管理，不因同意协议而自动开启。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
        Text(
            "拒绝可选权限不影响进入应用，对应功能可能不可用。已授予的访问可到系统设置或对应的 Shizuku、Root 管理器撤销。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
    }
}
