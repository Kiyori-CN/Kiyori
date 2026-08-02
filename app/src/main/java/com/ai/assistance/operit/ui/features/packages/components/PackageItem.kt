package com.ai.assistance.operit.ui.features.packages.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors

@Composable
fun PackageItem(
        name: String,
        description: String,
        isImported: Boolean,
        onClick: () -> Unit,
        onToggleImport: (Boolean) -> Unit
) {
    val colors = KiyoriSemanticTone.PURPLE.resolveColors()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
        ) {
                Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
                ) {
            // 图标
            KiyoriSemanticIconBadge(
                imageVector = Icons.Default.Extension,
                tone = KiyoriSemanticTone.PURPLE,
                contentDescription = null,
                containerSize = 36.dp,
                iconSize = 20.dp,
                shape = RoundedCornerShape(11.dp),
            )
            
            Spacer(modifier = Modifier.width(8.dp))

            // 文本内容 - 添加右侧边距防止撞到开关
                        Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                        ) {
                                Text(
                                        text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                                )
                        }

            // 开关
                        Switch(
                                checked = isImported,
                                onCheckedChange = onToggleImport,
                modifier = Modifier.size(width = 32.dp, height = 20.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.icon,
                    checkedTrackColor = colors.container,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                        )
                }
        }
}
