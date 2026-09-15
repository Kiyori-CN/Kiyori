package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriUiShapes
import com.kiyori.design.theme.resolveColors

/** 功能语义统一，颜色使用主题解析后的图标色，动态色和深浅主题沿用唯一主题所有者。 */
internal enum class KiyoriActionRole(val tone: KiyoriSemanticTone) {
    NAVIGATE(KiyoriSemanticTone.BLUE),
    ORGANIZE(KiyoriSemanticTone.ORANGE),
    CONFIGURE(KiyoriSemanticTone.PURPLE),
    EXECUTE(KiyoriSemanticTone.GREEN),
    ERROR(KiyoriSemanticTone.PURPLE),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KiyoriToolbarAction(
    icon: ImageVector,
    label: String,
    role: KiyoriActionRole,
    enabled: Boolean = true,
    selected: Boolean = false,
    badge: Boolean = false,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val ink = if (role == KiyoriActionRole.ERROR) MaterialTheme.colorScheme.error else role.tone.resolveColors().icon
    val busyDescription = stringResource(R.string.processing)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = Modifier.size(48.dp).clip(KiyoriUiShapes.control).semantics {
                this.selected = selected
                if (loading) stateDescription = busyDescription
            },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                contentColor = ink,
                disabledContentColor = ink.copy(alpha = 0.38f),
            ),
        ) {
            BadgedBox(badge = { if (badge) Badge() }) {
                // 进行中的动作必须给出可见反馈：只禁用按钮会让人以为点击没有生效。
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = ink)
                else Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            }
        }
    }
}
