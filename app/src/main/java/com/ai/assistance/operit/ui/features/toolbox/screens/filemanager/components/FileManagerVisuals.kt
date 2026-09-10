package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.getFileIcon
import com.kiyori.design.theme.*

internal fun FileManagerFileKind.iconTone(): KiyoriSemanticTone = when (this) {
    FileManagerFileKind.PARENT, FileManagerFileKind.DOCUMENT, FileManagerFileKind.TEXT, FileManagerFileKind.FILE -> KiyoriSemanticTone.BLUE
    FileManagerFileKind.FOLDER, FileManagerFileKind.ARCHIVE, FileManagerFileKind.PRESENTATION -> KiyoriSemanticTone.ORANGE
    FileManagerFileKind.IMAGE -> KiyoriSemanticTone.PURPLE
    FileManagerFileKind.AUDIO -> KiyoriSemanticTone.PINK
    FileManagerFileKind.VIDEO, FileManagerFileKind.PDF -> KiyoriSemanticTone.RED
    FileManagerFileKind.SHEET, FileManagerFileKind.PACKAGE -> KiyoriSemanticTone.GREEN
    FileManagerFileKind.CODE -> KiyoriSemanticTone.CYAN
}

/** 与设置详情共用已有浅深色板，图标颜色和容器颜色始终成对消费。 */
@Composable
fun FileManagerIconBadge(icon: ImageVector, tone: KiyoriSemanticTone, size: Dp = 40.dp, selected: Boolean = false) {
    val colors = tone.resolveSettingsIconColors()
    Surface(
        modifier = Modifier.size(size), shape = KiyoriUiShapes.control,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else colors.container,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else colors.icon,
    ) {
        Icon(icon, null, Modifier.padding(size * if (size <= 30.dp) 0.15f else 0.24f))
    }
}

@Composable
fun FileManagerFileBadge(file: FileItem, size: Dp = 40.dp, selected: Boolean = false) =
    FileManagerIconBadge(getFileIcon(file), fileManagerFileKind(file).iconTone(), size, selected)

@Composable
internal fun FileManagerActionRow(
    title: String, subtitle: String? = null, icon: ImageVector, tone: KiyoriSemanticTone,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface) {
        ListItem(
            headlineContent = { Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) },
            supportingContent = subtitle?.let { { Text(it) } },
            leadingContent = { FileManagerIconBadge(icon, tone, 36.dp) },
        )
    }
}
