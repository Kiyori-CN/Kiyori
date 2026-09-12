package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.ai.assistance.operit.data.preferences.FileManagerSettings
import com.ai.assistance.operit.data.preferences.validFileManagerStartPath
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors

/** 与 AI 侧栏一样显式处理遮罩；Material3 的 gesturesEnabled 会同时禁用遮罩关闭。 */
@Composable
internal fun FileManagerModalStorageDrawer(
    isOpen: Boolean,
    width: Dp,
    onDismiss: () -> Unit,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val fraction by animateFloatAsState(if (isOpen) 1f else 0f, tween(240), label = "fileStorageDrawer")
    val visible = isOpen || fraction > 0f
    Box(modifier.fillMaxSize()) {
        // 模态期间从无障碍树隐藏背景，避免读屏直接触发列表与工具栏。
        Box(if (visible) Modifier.clearAndSetSemantics {} else Modifier) { content() }
        if (visible) {
            Box(Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f * fraction))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClickLabel = "关闭存储侧栏", onClick = { if (isOpen) onDismiss() }))
            Box(Modifier.width(width).fillMaxHeight()
                .graphicsLayer { translationX = -width.toPx() * (1f - fraction) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { /* 拦住抽屉空白区域，不能穿透遮罩。 */ })) {
                drawerContent()
            }
        }
    }
    // 在背景内容之后注册，保证系统返回先关闭模态侧栏。
    BackHandler(enabled = visible) { if (isOpen) onDismiss() }
}

/** SAF 的 repo 名称是后端身份，侧栏别名只改变显示，不能借重命名撤销授权或改写路径。 */
val FileManagerStorageEntry.storageId: String get() = when {
    network != null -> "network:${network.id}"
    bookmarkUri != null -> "saf:$bookmarkUri"
    category == "工作区" && fileBookmark == null -> "default-workspace"
    fileBookmark != null -> com.ai.assistance.operit.data.preferences.fileBookmarkIdentity(fileBookmark, category == "工作区")
    else -> "$category:${environment.orEmpty()}:$path"
}

val FileManagerStorageEntry.isFixedLocal: Boolean get() = category == "本地" &&
    bookmarkUri == null && fileBookmark == null && environment.isNullOrBlank()

fun projectFileManagerStorageEntries(entries: List<FileManagerStorageEntry>, settings: FileManagerSettings): List<FileManagerStorageEntry> {
    val order = settings.drawerOrder.withIndex().associate { it.value to it.index }
    return entries.filter { it.isFixedLocal || it.storageId !in settings.drawerHidden && it.storageId !in settings.drawerRemoved }
        .sortedBy { if (it.isFixedLocal) -1 else order[it.storageId] ?: Int.MAX_VALUE }
        .map { entry -> entry.copy(title = settings.drawerNames[entry.storageId] ?: entry.title,
            path = if (entry.storageId == "default-workspace") settings.defaultWorkspacePath.ifBlank { entry.path } else entry.path) }
}

@Composable
fun FileManagerStorageMenu(entry: FileManagerStorageEntry, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    val actions = if (entry.category == "本地") listOf("重命名", "删除", "隐藏", "排序")
        else listOf("编辑", "删除", "排序", "创建快捷方式")
    AlertDialog(onDismissRequest = onDismiss, title = {
        Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            actions.forEach { action ->
                val tone = when (action) { "删除" -> KiyoriSemanticTone.RED; "排序" -> KiyoriSemanticTone.ORANGE
                    "隐藏" -> KiyoriSemanticTone.PURPLE; "创建快捷方式" -> KiyoriSemanticTone.GREEN; else -> KiyoriSemanticTone.BLUE }
                Row(Modifier.fillMaxWidth().clickable { onAction(action) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when(action) { "删除" -> Icons.Outlined.Delete; "排序" -> Icons.Outlined.SwapVert
                        "隐藏" -> Icons.Outlined.VisibilityOff; "创建快捷方式" -> Icons.AutoMirrored.Outlined.ArrowForward
                        else -> Icons.Outlined.Edit }, null, tint = tone.resolveColors().icon)
                    Spacer(Modifier.width(16.dp)); Text(action)
                }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun FileManagerStorageEditDialog(entry: FileManagerStorageEntry, busy: Boolean, error: String?, onDismiss: () -> Unit,
    onSave: (String, String) -> Unit) {
    var name by remember(entry.storageId) { mutableStateOf(entry.title) }
    var path by remember(entry.storageId) { mutableStateOf(entry.path) }
    val canEditPath = entry.category != "本地"
    val valid = name.trim().isNotEmpty() && name.length <= 80 && name.none { it.isISOControl() } &&
        (!canEditPath || validFileManagerStartPath(path.trim()))
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (canEditPath) "编辑${entry.category}" else "重命名入口") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true, enabled = !busy,
                supportingText = { Text("最多 80 个字符") })
            if (canEditPath) OutlinedTextField(path, { path = it }, Modifier.fillMaxWidth(), label = { Text("绝对路径") }, enabled = !busy,
                isError = !validFileManagerStartPath(path.trim()), supportingText = { Text("沿用此入口的存储环境；不移动文件") })
            else Text("只修改侧栏中的名称，不重命名文件夹或改变存储授权。", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(enabled = valid && !busy, onClick = { onSave(name.trim(), path.trim()) }) { Text(if (busy) "保存中…" else "保存") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } })
}

@Composable
fun FileManagerStorageSortDialog(entries: List<FileManagerStorageEntry>, category: String, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var ordered by remember { mutableStateOf(entries.filter { it.category == category && !it.isFixedLocal }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("排序 · $category") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text("使用上下箭头调整顺序，保存后应用。根目录与内部存储保持固定。", style = MaterialTheme.typography.bodySmall)
            ordered.forEachIndexed { index, entry ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(enabled = index > 0, onClick = { ordered = ordered.toMutableList().apply { add(index - 1, removeAt(index)) } }) {
                        Icon(Icons.Outlined.KeyboardArrowUp, "上移${entry.title}")
                    }
                    IconButton(enabled = index < ordered.lastIndex, onClick = { ordered = ordered.toMutableList().apply { add(index + 1, removeAt(index)) } }) {
                        Icon(Icons.Outlined.KeyboardArrowDown, "下移${entry.title}")
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(ordered.map { it.storageId }) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
