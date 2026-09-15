package com.ai.assistance.operit.ui.features.chat.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.AccountTree



import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock

import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin

import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatHistoryDisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterGroupCard
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.ChatGroupScope
import com.ai.assistance.operit.data.repository.ChatGroupTarget
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.ui.common.rememberLocal
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import com.ai.assistance.operit.data.repository.ChatOrderMove
import com.ai.assistance.operit.data.repository.ChatPositionSnapshot
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.ai.assistance.operit.ui.components.KiyoriDrawerScaffold
import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer
import com.kiyori.design.theme.KiyoriUiShapes
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun resolveBindingForCreate(
    historyDisplayMode: ChatHistoryDisplayMode,
    activePrompt: ActivePrompt,
    activeCharacterCardName: String?
): Pair<String?, String?> {
    if (historyDisplayMode == ChatHistoryDisplayMode.BY_FOLDER) {
        return Pair(null, null)
    }
    return when (val prompt = activePrompt) {
        is ActivePrompt.CharacterGroup -> Pair(null, prompt.id)
        is ActivePrompt.CharacterCard -> Pair(activeCharacterCardName, null)
    }
}

private sealed interface HistoryListItem {
    data class CharacterHeader(
        val key: String,
        val name: String,
        val characterCardName: String? = null,
        val characterGroupId: String? = null
    ) : HistoryListItem
    data class Header(
        val key: String, 
        val name: String, 
        val groupValue: String?,
        val scope: ChatGroupScope? = ChatGroupScope.All
    ) : HistoryListItem
    data class Item(val history: ChatHistory) : HistoryListItem
}

@Composable
private fun HistoryQuickScrollButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color =
            if (enabled) {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f)
            } else {
                Color.Transparent
            },
        shadowElevation = if (enabled) 1.dp else 0.dp
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(14.dp),
                tint =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
                    }
            )
        }
    }
}

@Composable
private fun HistoryQuickScroller(
    listState: LazyListState,
    itemCount: Int,
    onInteractionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val visibleItemCount = visibleItems.size
    val totalItemCount = layoutInfo.totalItemsCount.takeIf { it > 0 } ?: itemCount
    val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: 0
    val viewportHeightPx =
        (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat().coerceAtLeast(1f)
    val averageVisibleItemHeightPx =
        visibleItems
            .map { it.size }
            .average()
            .toFloat()
            .takeIf { it > 0f }
            ?: viewportHeightPx
    val estimatedContentHeightPx =
        (averageVisibleItemHeightPx * totalItemCount).coerceAtLeast(viewportHeightPx)
    val estimatedScrollOffsetPx =
        (
            listState.firstVisibleItemIndex * averageVisibleItemHeightPx +
                listState.firstVisibleItemScrollOffset.toFloat()
        ).coerceAtLeast(0f)
    val maxScrollOffsetPx = (estimatedContentHeightPx - viewportHeightPx).coerceAtLeast(1f)
    val canScrollBackward =
        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
    val canScrollForward = totalItemCount > 0 && lastVisibleIndex < totalItemCount - 1
    val shouldShow = totalItemCount > 1 && visibleItemCount > 0 && totalItemCount > visibleItemCount

    if (!shouldShow) {
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minThumbHeight = with(density) { 36.dp.toPx() }
    val trackWidth = 12.dp
    var trackHeightPx by remember { mutableStateOf(0f) }
    var isHandlingTouch by remember { mutableStateOf(false) }
    val scrollProgress =
        if (totalItemCount <= 1) {
            0f
        } else {
            (estimatedScrollOffsetPx / maxScrollOffsetPx)
                .coerceIn(0f, 1f)
        }
    val visibleFraction =
        (viewportHeightPx / estimatedContentHeightPx).coerceIn(0.12f, 1f)
    val thumbHeightPx =
        when {
            trackHeightPx <= 0f -> minThumbHeight
            trackHeightPx <= minThumbHeight -> trackHeightPx
            else -> (trackHeightPx * visibleFraction).coerceIn(minThumbHeight, trackHeightPx)
        }
    val maxThumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx = maxThumbOffsetPx * scrollProgress
    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
    val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }
    val fastScrollDescription = stringResource(R.string.history_fast_scroll)
    val quickScrollerAlpha = if (listState.isScrollInProgress || isHandlingTouch) 0.9f else 0.5f
    val hostView = LocalView.current
    val currentTrackHeightPx by rememberUpdatedState(trackHeightPx)
    val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)
    val currentMaxScrollOffsetPx by rememberUpdatedState(maxScrollOffsetPx)
    val currentEstimatedScrollOffsetPx by rememberUpdatedState(estimatedScrollOffsetPx)
    val currentTotalItemCount by rememberUpdatedState(totalItemCount)
    val currentOnInteractionChange by rememberUpdatedState(onInteractionChange)
    DisposableEffect(Unit) {
        onDispose {
            onInteractionChange(false)
        }
    }

    fun jumpToPointer(pointerY: Float) {
        if (currentTrackHeightPx <= 0f || currentTotalItemCount <= 1) {
            return
        }
        val trackableHeight = (currentTrackHeightPx - currentThumbHeightPx).coerceAtLeast(1f)
        val normalizedOffset = (pointerY - currentThumbHeightPx / 2f).coerceIn(0f, trackableHeight)
        val progress = (normalizedOffset / trackableHeight).coerceIn(0f, 1f)
        val targetScrollOffsetPx = progress * currentMaxScrollOffsetPx
        val scrollDeltaPx = targetScrollOffsetPx - currentEstimatedScrollOffsetPx
        if (scrollDeltaPx != 0f) {
            listState.dispatchRawDelta(scrollDeltaPx)
        }
    }

    fun scrollByPointerDelta(pointerDeltaY: Float) {
        if (currentTrackHeightPx <= 0f || currentTotalItemCount <= 1) {
            return
        }
        val trackableHeight = (currentTrackHeightPx - currentThumbHeightPx).coerceAtLeast(1f)
        val contentDeltaPx = pointerDeltaY * (currentMaxScrollOffsetPx / trackableHeight)
        if (contentDeltaPx != 0f) {
            listState.dispatchRawDelta(contentDeltaPx)
        }
    }

    fun startHandlingTouch(pointerY: Float) {
        isHandlingTouch = true
        currentOnInteractionChange(true)
        hostView.parent?.requestDisallowInterceptTouchEvent(true)
        jumpToPointer(pointerY)
    }

    fun stopHandlingTouch() {
        isHandlingTouch = false
        currentOnInteractionChange(false)
        hostView.parent?.requestDisallowInterceptTouchEvent(false)
    }

    Column(
        modifier = modifier
            .width(20.dp)
            .alpha(quickScrollerAlpha)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HistoryQuickScrollButton(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = stringResource(R.string.history_scroll_to_top),
            enabled = canScrollBackward,
            onClick = {
                coroutineScope.launch {
                    listState.animateScrollToItem(0)
                }
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .width(28.dp)
                .padding(vertical = 4.dp)
                .onGloballyPositioned { coordinates ->
                    trackHeightPx = coordinates.size.height.toFloat()
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        startHandlingTouch(down.position.y)
                        down.consume()
                        try {
                            var previousPointerY = down.position.y
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change =
                                    event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                val pointerDeltaY = change.position.y - previousPointerY
                                previousPointerY = change.position.y
                                if (pointerDeltaY != 0f) {
                                    scrollByPointerDelta(pointerDeltaY)
                                }
                                change.consume()
                            }
                        } finally {
                            stopHandlingTouch()
                        }
                    }
                }
                .semantics {
                    contentDescription = fastScrollDescription
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(trackWidth)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = thumbOffsetDp)
                        .width(8.dp)
                        .height(thumbHeightDp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                )
            }
        }

        HistoryQuickScrollButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.history_scroll_to_bottom),
            enabled = canScrollForward,
            onClick = {
                coroutineScope.launch {
                    listState.animateScrollToItem((totalItemCount - 1).coerceAtLeast(0))
                }
            }
        )
    }
}

/**
 * 角色卡模式下的层级导轨，统一分组头与对话条目的缩进，避免两处各写一份魔法宽度。
 * 高度显式传入：列表项测量高度无上界，导轨不能用 fillMaxHeight 跟随行高。
 */
@Composable
private fun HistoryBranchRail(railHeight: Dp) {
    Box(
        modifier = Modifier
            .width(22.dp)
            .padding(start = 10.dp, end = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(railHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        )
    }
}

/**
 * 失败信息必须可见且能手动关闭。原实现把切换失败渲染成一行永久文案，用户既无法确认
 * 是否已经重试成功，也没有关闭入口。
 */
@Composable
private fun HistoryInlineBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = KiyoriUiShapes.control
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 空列表、无结果与搜索中共用一个占位版式，保证三种状态的视觉重量一致。 */
@Composable
private fun HistoryStatusPlaceholder(
    title: String,
    description: String,
    icon: ImageVector? = null,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showProgress) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 底部抽屉里的操作行：单一命中区、固定高度、禁用时保留说明，不用 Toast 事后解释。 */
@Composable
private fun HistoryActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    supportingText: String? = null
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KiyoriUiShapes.control)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}

private val HISTORY_SAME_YEAR_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd")
private val HISTORY_FULL_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * 对话列表没有任何时间线索时，用户只能靠标题回忆顺序。这里复用既有的相对时间文案，
 * 一周以内给相对值，更早给数字日期，避免引入第二套本地化时间格式。
 */
@Composable
private fun historyRelativeTimeLabel(updatedAt: LocalDateTime, nowMillis: Long): String {
    val timestamp = remember(updatedAt) {
        updatedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val diff = nowMillis - timestamp
    return when {
        diff < 60_000L -> stringResource(R.string.time_just_now)
        diff < 3_600_000L -> stringResource(R.string.time_minutes_ago, diff / 60_000L)
        diff < 86_400_000L -> stringResource(R.string.time_hours_ago, diff / 3_600_000L)
        diff < 604_800_000L -> stringResource(R.string.time_days_ago, diff / 86_400_000L)
        else -> remember(updatedAt) {
            val sameYear = updatedAt.year == LocalDateTime.now().year
            updatedAt.format(
                if (sameYear) HISTORY_SAME_YEAR_DATE_FORMATTER else HISTORY_FULL_DATE_FORMATTER
            )
        }
    }
}

/** 设置抽屉里的开关行：整行可点，读屏读到一个 Switch 而不是标题、说明与开关三个独立节点。 */
@Composable
private fun HistorySettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KiyoriUiShapes.control)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = KiyoriUiShapes.control
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun ChatHistorySelector(
        modifier: Modifier = Modifier,
        onNewChat: (characterCardName: String?, characterGroupId: String?) -> Unit,
        onSelectChat: suspend (String) -> Boolean,
        onDeleteChat: suspend (String) -> Boolean,
        onEditChatMetadata: suspend (original: ChatHistory, newTitle: String, characterCardName: String?, characterGroupId: String?) -> Unit,
        onCreateGroup: suspend (groupName: String, characterCardName: String?, characterGroupId: String?) -> com.ai.assistance.operit.data.repository.ChatGroupCreationResult,
        onMoveChat: suspend (ChatOrderMove) -> Unit,
        onUpdateGroupName: suspend (target: ChatGroupTarget, newName: String) -> Unit,
        onDeleteGroup: suspend (target: ChatGroupTarget, deleteChats: Boolean) -> Unit,
        chatHistories: List<ChatHistory>,
        currentId: String?,
        activeStreamingChatIds: Set<String> = emptySet(),
        lazyListState: LazyListState? = null,
        onBack: (() -> Unit)? = null,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        historyDisplayMode: ChatHistoryDisplayMode,
        onDisplayModeChange: (ChatHistoryDisplayMode) -> Unit,
        autoSwitchCharacterCard: Boolean,
        onAutoSwitchCharacterCardChange: (Boolean) -> Unit,
        autoSwitchChatOnCharacterSelect: Boolean,
        onAutoSwitchChatOnCharacterSelectChange: (Boolean) -> Unit,
        onQuickScrollInteractionChange: (Boolean) -> Unit = {},
        activePrompt: ActivePrompt
) {
    var chatToEdit by remember { mutableStateOf<ChatHistory?>(null) }
    var chatToDelete by remember { mutableStateOf<ChatHistory?>(null) }
    var chatItemActionTarget by remember { mutableStateOf<ChatHistory?>(null) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupBinding by remember { mutableStateOf<Pair<String?, String?>>(null to null) }
    var collapsedGroups by rememberLocal("chat_history_collapsed_groups", emptySet<String>())
    var collapsedCharacters by rememberLocal("chat_history_collapsed_characters", emptySet<String>())

    var groupActionTarget by remember { mutableStateOf<ChatGroupTarget?>(null) }
    var groupToRename by remember { mutableStateOf<ChatGroupTarget?>(null) }
    var groupToDelete by remember { mutableStateOf<ChatGroupTarget?>(null) }

    // 搜索常驻，不再有折叠状态；折叠后保留查询词会让列表被一个不可见条件继续过滤。
    var matchedChatIdsByContent by remember(searchQuery) { mutableStateOf<Set<String>>(emptySet()) }
    var isSearching by remember(searchQuery) { mutableStateOf(searchQuery.trim().length >= 2) }
    var searchFailed by remember(searchQuery) { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    // 相对时间必须随真实时间推进刷新，否则抽屉长时间停留会一直停在“刚刚”。
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val context = LocalContext.current
    val observableResources = LocalResources.current
    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    var selectingChat by remember { mutableStateOf(false) }
    var selectionError by remember { mutableStateOf(false) }
    fun selectChat(id: String) {
        if (selectingChat) return
        selectingChat = true
        selectionError = false
        coroutineScope.launch {
            try {
                selectionError = !onSelectChat(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.e("ChatHistorySelector", "Chat selection failed: ${failure.javaClass.simpleName}")
                selectionError = true
            } finally {
                selectingChat = false
            }
        }
    }
    var moveSaving by remember { mutableStateOf(false) }
    var moveError by remember { mutableStateOf<String?>(null) }

    suspend fun submitMove(original: ChatHistory, target: ChatHistory, ordered: List<ChatHistory>): Boolean {
        if (moveSaving) return false
        val index = ordered.indexOfFirst { it.id == original.id }
        if (index < 0) return false
        // 置顶是独立排序桶；锚点只取同桶可见项，隐藏项由仓储完整列表保留。
        val next = ordered.drop(index + 1).firstOrNull { it.pinned == original.pinned }
        val previous = ordered.take(index).lastOrNull { it.pinned == original.pinned }
        val anchor = (next ?: previous)?.let { item -> chatHistories.firstOrNull { it.id == item.id } }
        val move = ChatOrderMove(
            original = ChatPositionSnapshot.from(original),
            anchor = anchor?.let(ChatPositionSnapshot::from),
            beforeAnchor = next != null,
            targetGroup = target.group,
            targetCardName = target.characterCardName,
            targetCharacterGroupId = target.characterGroupId,
        )
        moveSaving = true
        moveError = null
        return try {
            onMoveChat(move)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("ChatHistorySelector", "Chat move failed: ${failure.javaClass.simpleName}")
            moveError = context.getString(R.string.chat_order_move_failed)
            false
        } finally {
            moveSaving = false
        }
    }

    var deletingChatIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var chatDeleteError by remember { mutableStateOf<String?>(null) }
    var chatDeletedWithCleanupError by remember { mutableStateOf(false) }
    var availableCharacterCards by remember { mutableStateOf<List<CharacterCard>>(emptyList()) }
    var availableCharacterGroups by remember { mutableStateOf<List<CharacterGroupCard>>(emptyList()) }
    var resolvedGroupNameById by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun promptDeleteChat(history: ChatHistory) {
        if (history.locked) {
            android.widget.Toast.makeText(context, R.string.chat_locked_cannot_delete, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (deletingChatIds.contains(history.id)) {
            return
        }
        chatDeleteError = null
        chatDeletedWithCleanupError = false
        chatToDelete = history
    }

    fun requestDeleteChat(history: ChatHistory) {
        if (history.locked) {
            chatDeleteError = context.getString(R.string.chat_locked_cannot_delete)
            return
        }
        if (deletingChatIds.contains(history.id)) {
            return
        }
        deletingChatIds = deletingChatIds + history.id
        chatDeleteError = null
        coroutineScope.launch {
            try {
                val deleted = onDeleteChat(history.id)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (deleted) chatToDelete = null
                else chatDeleteError = context.getString(R.string.chat_delete_not_permitted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (partial: com.ai.assistance.operit.data.repository.ChatDeletionCleanupException) {
                chatDeletedWithCleanupError = true
                chatDeleteError = context.getString(R.string.chat_delete_cleanup_failed)
            } catch (failure: Exception) {
                AppLogger.e("ChatHistorySelector", "Chat deletion failed: ${failure.javaClass.simpleName}")
                chatDeleteError = context.getString(R.string.chat_delete_failed)
            } finally {
                deletingChatIds = deletingChatIds - history.id
            }
        }
    }

    if (chatToDelete != null) {
        val deletingChat = chatToDelete!!
        val deleting = deletingChat.id in deletingChatIds
        AlertDialog(
            onDismissRequest = { if (!deleting) chatToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.confirm_delete_chat)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = stringResource(R.string.delete_chat_confirmation, deletingChat.title))
                    Text(
                        text = stringResource(R.string.delete_operation_irreversible),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    chatDeleteError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    // 删除进度不再插在正文中间挤动文案，固定在底部占一行高度。
                    if (deleting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (chatDeletedWithCleanupError) chatToDelete = null
                        else requestDeleteChat(deletingChat)
                    },
                    enabled = !deleting,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(if (chatDeletedWithCleanupError) R.string.close else R.string.confirm_delete_action))
                }
            },
            dismissButton = {
                if (!chatDeletedWithCleanupError) {
                    TextButton(onClick = { chatToDelete = null }, enabled = !deleting) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        characterCardManager.characterCardListFlow.collectLatest { ids ->
            val cards = ids.mapNotNull { id ->
                runCatching { characterCardManager.getCharacterCard(id) }.getOrNull()
            }
            availableCharacterCards = cards
        }
    }
    LaunchedEffect(Unit) {
        characterGroupCardManager.allCharacterGroupCardsFlow.collectLatest { groups ->
            availableCharacterGroups = groups
        }
    }
    val groupNameById = remember(availableCharacterGroups, resolvedGroupNameById) {
        val fromList = availableCharacterGroups.associate { it.id to it.name }
        fromList + resolvedGroupNameById
    }
    val activeCharacterCardName = remember(activePrompt, availableCharacterCards) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterCard ->
                availableCharacterCards.firstOrNull { it.id == prompt.id }?.name
            is ActivePrompt.CharacterGroup -> null
        }
    }
    val actualLazyListState = lazyListState ?: rememberLazyListState()
    val ungroupedText = stringResource(R.string.ungrouped)

    // 当搜索查询改变时，执行内容搜索（带防抖延迟）
    LaunchedEffect(searchQuery, chatHistories) {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isBlank()) {
            matchedChatIdsByContent = emptySet()
            isSearching = false
            return@LaunchedEffect
        }

        // 标题与正文结果取并集；标题命中不能阻止其他对话的正文匹配。
        if (trimmedQuery.length < 2) {
            matchedChatIdsByContent = emptySet()
            isSearching = false
            return@LaunchedEffect
        }
        matchedChatIdsByContent = emptySet()
        searchFailed = false
        isSearching = true

        // 延迟400ms，如果用户继续输入则取消本次搜索（LaunchedEffect会自动取消）
        delay(400)
        try {
            matchedChatIdsByContent = chatHistoryManager.searchChatIdsByContent(trimmedQuery)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("ChatHistorySelector", "History content search failed: ${e.javaClass.simpleName}")
            searchFailed = true
            matchedChatIdsByContent = emptySet()
        } finally {
            isSearching = false
        }
    }

    val filteredHistories = remember(chatHistories, searchQuery, matchedChatIdsByContent) {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isNotBlank()) {
            chatHistories.filter { history ->
                val matchesTitleOrGroup = history.title.contains(trimmedQuery, ignoreCase = true) ||
                        (history.group?.contains(trimmedQuery, ignoreCase = true) == true)
                val matchesContent = matchedChatIdsByContent.contains(history.id)
                matchesTitleOrGroup || matchesContent
            }
        } else {
            chatHistories
        }
    }
    val groupIdsInFilteredHistories = remember(filteredHistories) {
        filteredHistories
            .mapNotNull { it.characterGroupId?.trim()?.takeIf { id -> id.isNotBlank() } }
            .toSet()
    }
    LaunchedEffect(groupIdsInFilteredHistories, groupNameById) {
        val missingIds = groupIdsInFilteredHistories.filter { groupNameById[it].isNullOrBlank() }
        if (missingIds.isEmpty()) {
            return@LaunchedEffect
        }
        val fetched = mutableMapOf<String, String>()
        missingIds.forEach { groupId ->
            val groupName = runCatching {
                characterGroupCardManager.getCharacterGroupCard(groupId)?.name
            }.getOrNull()?.takeIf { it.isNotBlank() }
            if (!groupName.isNullOrBlank()) {
                fetched[groupId] = groupName
            }
        }
        if (fetched.isNotEmpty()) {
            resolvedGroupNameById = resolvedGroupNameById + fetched
        }
    }

    val unboundCharacterText = stringResource(R.string.unbound_character_card)
    val groupPrefix = stringResource(R.string.character_group_binding_prefix)
    val flatItems =
            remember(
                    filteredHistories,
                    collapsedGroups,
                    collapsedCharacters,
                    ungroupedText,
                    unboundCharacterText,
                    availableCharacterGroups,
                    groupNameById,
                    groupPrefix,
                    historyDisplayMode,
                    activePrompt,
                    availableCharacterCards,
                    activeCharacterCardName
            ) {
                fun characterKey(name: String) = "character::$name"
                fun groupKey(characterName: String?, groupValue: String?): String {
                    val characterPart = characterName ?: "all"
                    val groupPart = groupValue ?: "ungrouped"
                    return "group::$characterPart::$groupPart"
                }

                when (historyDisplayMode) {
                    ChatHistoryDisplayMode.BY_CHARACTER_CARD -> {
                        data class BindingBucket(
                            val key: String,
                            val displayName: String,
                            val characterCardName: String?,
                            val characterGroupId: String?
                        )
                        fun resolveBindingBucket(history: ChatHistory): BindingBucket {
                            val groupId = history.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
                            if (!groupId.isNullOrBlank()) {
                                val groupName = groupNameById[groupId]?.takeIf { it.isNotBlank() }
                                val displayName =
                                    if (!groupName.isNullOrBlank()) {
                                        "$groupPrefix: $groupName"
                                    } else {
                                        context.getString(R.string.missing_character_group_id, groupId)
                                    }
                                return BindingBucket(
                                    key = "binding::group::$groupId",
                                    displayName = displayName,
                                    characterCardName = null,
                                    characterGroupId = groupId
                                )
                            }

                            val cardName = history.characterCardName?.takeIf { it.isNotBlank() }
                            if (!cardName.isNullOrBlank()) {
                                return BindingBucket(
                                    key = "binding::card::$cardName",
                                    displayName = cardName,
                                    characterCardName = cardName,
                                    characterGroupId = null
                                )
                            }

                            return BindingBucket(
                                key = "binding::unbound",
                                displayName = unboundCharacterText,
                                characterCardName = null,
                                characterGroupId = null
                            )
                        }

                        filteredHistories
                            .groupBy { resolveBindingBucket(it) }
                            .flatMap { (bindingBucket, histories) ->
                                val cKey = characterKey(bindingBucket.key)
                                val children =
                                        if (collapsedCharacters.contains(cKey)) {
                                            emptyList()
                                        } else {
                                            histories
                                                .groupBy { it.group }
                                                .flatMap { (groupValue, groupedHistories) ->
                                                    val displayName = groupValue ?: ungroupedText
                                                    val gKey = groupKey(bindingBucket.key, groupValue)
                                                    val header =
                                                            HistoryListItem.Header(
                                                                    key = gKey,
                                                                    name = displayName,
                                                                    groupValue = groupValue,
                                                                    scope = when {
                                                                        bindingBucket.characterGroupId != null -> ChatGroupScope.Group(bindingBucket.characterGroupId)
                                                                        bindingBucket.characterCardName != null -> ChatGroupScope.Card(bindingBucket.characterCardName)
                                                                        else -> ChatGroupScope.Unbound
                                                                    }
                                                            )
                                                    val items =
                                                            if (collapsedGroups.contains(gKey)) {
                                                                emptyList()
                                                            } else {
                                                                groupedHistories.map {
                                                                    HistoryListItem.Item(it)
                                                                }
                                                    }
                                                    listOf(header) + items
                                                }
                                        }
                                listOf(
                                    HistoryListItem.CharacterHeader(
                                        key = cKey,
                                        name = bindingBucket.displayName,
                                        characterCardName = bindingBucket.characterCardName,
                                        characterGroupId = bindingBucket.characterGroupId
                                    )
                                ) + children
                            }
                    }
                    else -> {
                        filteredHistories
                            .groupBy { it.group }
                            .flatMap { (groupValue, histories) ->
                                val displayName = groupValue ?: ungroupedText
                                val gKey = groupKey(null, groupValue)
                                val scope = if (historyDisplayMode == ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY) {
                                    when (val prompt = activePrompt) {
                                        is ActivePrompt.CharacterGroup -> ChatGroupScope.Group(prompt.id)
                                        is ActivePrompt.CharacterCard -> {
                                            val card = availableCharacterCards.firstOrNull { it.id == prompt.id }
                                            card?.let { ChatGroupScope.Card(it.name, it.isDefault) }
                                        }
                                    }
                                } else ChatGroupScope.All
                                val header =
                                        HistoryListItem.Header(
                                                key = gKey,
                                                name = displayName,
                                                groupValue = groupValue,
                                                scope = scope
                                        )
                                val items =
                                        if (collapsedGroups.contains(gKey)) {
                                            emptyList()
                                        } else {
                                            histories.map { HistoryListItem.Item(it) }
                                        }
                                listOf(header) + items
                            }
                    }
                }
            }

    val reorderableState = rememberReorderableLazyListState(actualLazyListState) { from, to ->
        if (moveSaving) return@rememberReorderableLazyListState
        val movedItem = flatItems.getOrNull(from.index) as? HistoryListItem.Item
                ?: return@rememberReorderableLazyListState

        val reorderedFlatList = flatItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }

        var newGroup: String? = null
        var newCharacterCardName: String? = null
        var newCharacterGroupId: String? = null
        val newOrderedHistories =
                reorderedFlatList
                    .mapNotNull {
                        when (it) {
                            is HistoryListItem.CharacterHeader -> {
                                // 在绑定分类模式下，更新当前绑定（角色卡/群组）
                                newCharacterCardName = it.characterCardName
                                newCharacterGroupId = it.characterGroupId
                                newGroup = null
                                null
                            }
                            is HistoryListItem.Header -> {
                                newGroup = it.groupValue
                                null
                            }
                            is HistoryListItem.Item -> {
                                // 根据当前显示模式决定是否更新 characterCardName
                                val updatedHistory = if (historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD) {
                                    it.history.copy(
                                        group = newGroup,
                                        characterCardName = newCharacterCardName,
                                        characterGroupId = newCharacterGroupId
                                    )
                                } else {
                                    it.history.copy(group = newGroup)
                                }
                                updatedHistory
                            }
                        }
                    }
                    .mapIndexed { index, history -> history.copy(displayOrder = index.toLong()) }

        val finalMovedItem =
                newOrderedHistories.find { it.id == movedItem.history.id }
                        ?: return@rememberReorderableLazyListState

        submitMove(movedItem.history, finalMovedItem, newOrderedHistories)
    }

    if (chatItemActionTarget != null) {
        val actionTargetChat = chatItemActionTarget!!
        val resolvedTargetChat = remember(actionTargetChat, chatHistories) {
            chatHistories.firstOrNull { it.id == actionTargetChat.id } ?: actionTargetChat
        }
        val moveMenuIndex = filteredHistories.indexOfFirst { it.id == actionTargetChat.id }
        val moveMenuChat = filteredHistories.getOrNull(moveMenuIndex)
        val canMoveUp = moveMenuChat != null && moveMenuIndex > 0 &&
            filteredHistories[moveMenuIndex - 1].pinned == moveMenuChat.pinned
        val canMoveDown = moveMenuChat != null && moveMenuIndex < filteredHistories.lastIndex &&
            filteredHistories[moveMenuIndex + 1].pinned == moveMenuChat.pinned
        // 关闭动画跑完后才执行后续动作：直接在点击里切换状态会跳过共享抽屉的退出动画，
        // 并让编辑或删除弹窗与正在收起的抽屉同时出现。
        var pendingChatAction by remember(actionTargetChat.id) { mutableStateOf<(() -> Unit)?>(null) }
        KiyoriModalBottomDrawer(
            onDismissRequest = {
                val action = pendingChatAction
                pendingChatAction = null
                chatItemActionTarget = null
                action?.invoke()
            },
            confirmDismiss = { !moveSaving }
        ) { dismissDrawer ->
            fun runAndClose(action: () -> Unit) {
                pendingChatAction = action
                dismissDrawer()
            }
            KiyoriDrawerScaffold(
                title = stringResource(R.string.chat_history_manage_chat),
                onClose = dismissDrawer,
                scrollableContent = false
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = resolvedTargetChat.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${historyRelativeTimeLabel(resolvedTargetChat.updatedAt, nowMillis)} · " +
                                (resolvedTargetChat.group ?: ungroupedText),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (moveSaving) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }
                    moveError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    HistoryActionRow(
                        icon = Icons.Outlined.Edit,
                        label = stringResource(R.string.edit_title),
                        enabled = !moveSaving,
                        onClick = { runAndClose { chatToEdit = resolvedTargetChat } }
                    )

                    HistoryActionRow(
                        icon = if (resolvedTargetChat.pinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                        label = if (resolvedTargetChat.pinned) {
                            stringResource(R.string.unpin_chat)
                        } else {
                            stringResource(R.string.pin_chat)
                        },
                        enabled = !moveSaving,
                        onClick = {
                            val newPinned = !resolvedTargetChat.pinned
                            runAndClose {
                                coroutineScope.launch {
                                    chatHistoryManager.updateChatPinned(resolvedTargetChat.id, newPinned)
                                }
                            }
                        }
                    )

                    HistoryActionRow(
                        icon = if (resolvedTargetChat.locked) Icons.Outlined.LockOpen else Icons.Default.Lock,
                        label = if (resolvedTargetChat.locked) {
                            stringResource(R.string.unlock_chat)
                        } else {
                            stringResource(R.string.lock_chat)
                        },
                        supportingText = stringResource(R.string.chat_history_lock_hint),
                        enabled = !moveSaving,
                        onClick = {
                            val newLocked = !resolvedTargetChat.locked
                            runAndClose {
                                coroutineScope.launch {
                                    chatHistoryManager.updateChatLocked(resolvedTargetChat.id, newLocked)
                                }
                            }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )

                    HistoryActionRow(
                        icon = Icons.Outlined.ArrowUpward,
                        label = stringResource(R.string.move_up),
                        enabled = !moveSaving && canMoveUp,
                        supportingText = if (!canMoveUp) stringResource(R.string.chat_history_move_blocked) else null,
                        onClick = {
                            val targetChat = moveMenuChat ?: return@HistoryActionRow
                            val currentIndex = filteredHistories.indexOfFirst { it.id == targetChat.id }
                            if (currentIndex > 0) {
                                val newHistories = filteredHistories.toMutableList()
                                newHistories.removeAt(currentIndex)
                                newHistories.add(currentIndex - 1, targetChat)
                                coroutineScope.launch {
                                    submitMove(targetChat, targetChat, newHistories)
                                }
                            }
                        }
                    )

                    HistoryActionRow(
                        icon = Icons.Outlined.ArrowDownward,
                        label = stringResource(R.string.move_down),
                        enabled = !moveSaving && canMoveDown,
                        supportingText = if (!canMoveDown) stringResource(R.string.chat_history_move_blocked) else null,
                        onClick = {
                            val targetChat = moveMenuChat ?: return@HistoryActionRow
                            val currentIndex = filteredHistories.indexOfFirst { it.id == targetChat.id }
                            if (currentIndex >= 0 && currentIndex < filteredHistories.size - 1) {
                                val newHistories = filteredHistories.toMutableList()
                                newHistories.removeAt(currentIndex)
                                newHistories.add(currentIndex + 1, targetChat)
                                coroutineScope.launch {
                                    submitMove(targetChat, targetChat, newHistories)
                                }
                            }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )

                    // 锁定的对话此前仍然可以点“删除”，只在事后弹一个 Toast 解释。
                    // 这里直接禁用并说明原因，冲突在点击前就可见。
                    HistoryActionRow(
                        icon = Icons.Outlined.Delete,
                        label = stringResource(R.string.delete),
                        destructive = true,
                        enabled = !moveSaving && !resolvedTargetChat.locked,
                        supportingText = if (resolvedTargetChat.locked) {
                            stringResource(R.string.chat_locked_cannot_delete)
                        } else {
                            null
                        },
                        onClick = { runAndClose { promptDeleteChat(resolvedTargetChat) } }
                    )
                }
            }
        }
    }

    if (groupActionTarget != null) {
        val groupTarget = groupActionTarget!!
        var pendingGroupAction by remember(groupTarget) { mutableStateOf<(() -> Unit)?>(null) }
        KiyoriModalBottomDrawer(
            onDismissRequest = {
                val action = pendingGroupAction
                pendingGroupAction = null
                groupActionTarget = null
                action?.invoke()
            }
        ) { dismissDrawer ->
            KiyoriDrawerScaffold(
                title = stringResource(R.string.manage_group),
                onClose = dismissDrawer,
                scrollableContent = false
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = groupTarget.groupName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    HistoryActionRow(
                        icon = Icons.Outlined.DriveFileRenameOutline,
                        label = stringResource(R.string.rename_group),
                        onClick = {
                            pendingGroupAction = { groupToRename = groupTarget }
                            dismissDrawer()
                        }
                    )

                    HistoryActionRow(
                        icon = Icons.Outlined.Delete,
                        label = stringResource(R.string.delete_group),
                        destructive = true,
                        supportingText = stringResource(R.string.chat_history_delete_group_hint),
                        onClick = {
                            pendingGroupAction = { groupToDelete = groupTarget }
                            dismissDrawer()
                        }
                    )
                }
            }
        }
    }

    if (groupToRename != null) {
        val target = groupToRename!!
        var newGroupNameText by remember(target) { mutableStateOf(target.groupName) }
        var saving by remember(target) { mutableStateOf(false) }
        var error by remember(target) { mutableStateOf<String?>(null) }
        var discard by remember(target) { mutableStateOf(false) }
        fun closeRename() {
            if (saving) return
            if (newGroupNameText != target.groupName) discard = true else groupToRename = null
        }
        AlertDialog(
            onDismissRequest = { closeRename() },
            title = { Text(stringResource(R.string.rename_group)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newGroupNameText,
                        onValueChange = { newGroupNameText = it },
                        enabled = !saving,
                        singleLine = true,
                        shape = KiyoriUiShapes.field,
                        label = { Text(stringResource(R.string.new_group_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = newGroupNameText.isNotBlank() && !saving,
                    onClick = {
                        if (!saving) {
                            saving = true
                            error = null
                            val submitted = newGroupNameText.trim()
                            coroutineScope.launch {
                                try {
                                    if (submitted != target.groupName) onUpdateGroupName(target, submitted)
                                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                    if (groupToRename == target) groupToRename = null
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    AppLogger.e("ChatHistorySelector", "Group rename failed: ${failure.javaClass.simpleName}")
                                    error = observableResources.getString(if (failure is com.ai.assistance.operit.data.repository.ChatGroupChangedException)
                                        R.string.chat_group_changed else R.string.chat_group_operation_failed)
                                } finally { saving = false }
                            }
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { closeRename() }, enabled = !saving) { Text(stringResource(R.string.cancel)) }
            }
        )
        if (discard) {
            AlertDialog(
                onDismissRequest = { discard = false },
                title = { Text(stringResource(R.string.pkg_env_discard_title)) },
                text = { Text(stringResource(R.string.chat_group_discard)) },
                confirmButton = { TextButton(onClick = { groupToRename = null }) { Text(stringResource(R.string.pkg_env_discard)) } },
                dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }

    if (groupToDelete != null) {
        val target = groupToDelete!!
        var deleting by remember(target) { mutableStateOf(false) }
        var error by remember(target) { mutableStateOf<String?>(null) }
        var partial by remember(target) { mutableStateOf(false) }
        fun deleteSelectedGroup(deleteChats: Boolean) {
            if (deleting || partial) return
            deleting = true
            error = null
            coroutineScope.launch {
                try {
                    onDeleteGroup(target, deleteChats)
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (groupToDelete == target) groupToDelete = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: com.ai.assistance.operit.data.repository.ChatGroupCleanupException) {
                    partial = true
                    error = observableResources.getString(R.string.chat_group_cleanup_failed)
                } catch (failure: Exception) {
                    AppLogger.e("ChatHistorySelector", "Group deletion failed: ${failure.javaClass.simpleName}")
                    error = observableResources.getString(if (failure is com.ai.assistance.operit.data.repository.ChatGroupChangedException)
                        R.string.chat_group_changed else R.string.chat_group_operation_failed)
                } finally { deleting = false }
            }
        }
        // 两个删除口径都是破坏性的且各自有不同后果，用“确认/取消”两键对话框无法表达；
        // 共享抽屉把它们并列成两条可读的选项，取消仍然是默认退路。
        KiyoriModalBottomDrawer(
            onDismissRequest = { groupToDelete = null },
            confirmDismiss = { !deleting }
        ) { dismissDrawer ->
            KiyoriDrawerScaffold(
                title = stringResource(R.string.confirm_delete_group),
                onClose = dismissDrawer,
                scrollableContent = false,
                footer = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = dismissDrawer, enabled = !deleting) {
                            Text(stringResource(if (partial) R.string.close else R.string.cancel))
                        }
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = target.groupName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = stringResource(R.string.choose_delete_method),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )

                    if (deleting) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }
                    error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    HistoryActionRow(
                        icon = Icons.Outlined.Folder,
                        label = stringResource(R.string.delete_group_only),
                        supportingText = stringResource(R.string.chats_move_to_ungrouped),
                        enabled = !deleting && !partial,
                        onClick = { deleteSelectedGroup(false) }
                    )

                    HistoryActionRow(
                        icon = Icons.Outlined.Delete,
                        label = stringResource(R.string.delete_group_and_chats),
                        supportingText = stringResource(R.string.delete_operation_irreversible),
                        destructive = true,
                        enabled = !deleting && !partial,
                        onClick = { deleteSelectedGroup(true) }
                    )
                }
            }
        }
    }

    if (chatToEdit != null) {
        val editingChat = chatToEdit!!
        var newTitle by remember(editingChat) { mutableStateOf(editingChat.title) }
        var selectedCharacterCardName by remember(editingChat) {
            mutableStateOf(editingChat.characterCardName)
        }
        var selectedCharacterGroupId by remember(editingChat) {
            mutableStateOf(editingChat.characterGroupId)
        }
        var bindingMenuExpanded by remember(editingChat) { mutableStateOf(false) }
        var savingMetadata by remember(editingChat) { mutableStateOf(false) }
        var metadataError by remember(editingChat) { mutableStateOf<String?>(null) }
        var confirmDiscardMetadata by remember(editingChat) { mutableStateOf(false) }
        fun requestCloseMetadata() {
            if (savingMetadata) return
            if (newTitle != editingChat.title ||
                selectedCharacterCardName != editingChat.characterCardName ||
                selectedCharacterGroupId != editingChat.characterGroupId
            ) confirmDiscardMetadata = true else chatToEdit = null
        }
        data class ChatBindingOption(
            val label: String,
            val characterCardName: String?,
            val characterGroupId: String?
        )
        val unboundLabel = stringResource(R.string.unbound_character_card)
        val bindingLabel = stringResource(R.string.bind_character_card)
        val bindingHint = stringResource(R.string.chat_binding_scope_hint)
        val groupPrefix = stringResource(R.string.character_group_binding_prefix)
        val bindingOptions = remember(availableCharacterCards, availableCharacterGroups, unboundLabel, groupPrefix) {
            buildList {
                add(ChatBindingOption(unboundLabel, null, null))
                availableCharacterCards.forEach { card ->
                    add(ChatBindingOption(card.name, card.name, null))
                }
                availableCharacterGroups.forEach { group ->
                    add(ChatBindingOption("$groupPrefix: ${group.name}", null, group.id))
                }
            }
        }
        val selectedBindingLabel = remember(
            selectedCharacterCardName,
            selectedCharacterGroupId,
            groupNameById,
            unboundLabel,
            groupPrefix
        ) {
            when {
                !selectedCharacterGroupId.isNullOrBlank() -> {
                    val normalizedGroupId = selectedCharacterGroupId?.trim()?.takeIf { it.isNotBlank() }
                    val groupName = normalizedGroupId?.let { groupNameById[it] }?.takeIf { it.isNotBlank() }
                    if (!groupName.isNullOrBlank()) {
                        "$groupPrefix: $groupName"
                    } else {
                        observableResources.getString(R.string.missing_character_group_id, normalizedGroupId ?: "")
                    }
                }
                !selectedCharacterCardName.isNullOrBlank() -> selectedCharacterCardName!!
                else -> unboundLabel
            }
        }
        val density = LocalDensity.current
        var bindingMenuWidth by remember { mutableStateOf(0.dp) }
        val dropdownClickSource = remember { MutableInteractionSource() }

        AlertDialog(
                onDismissRequest = { requestCloseMetadata() },
                title = { Text(stringResource(R.string.edit_title)) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                enabled = !savingMetadata,
                                singleLine = true,
                                shape = KiyoriUiShapes.field,
                                label = { Text(stringResource(R.string.new_title)) },
                                modifier = Modifier.fillMaxWidth()
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                    value = selectedBindingLabel,
                                    onValueChange = {},
                                    enabled = !savingMetadata,
                                    readOnly = true,
                                    shape = KiyoriUiShapes.field,
                                    label = { Text(bindingLabel) },
                                    trailingIcon = {
                                        Icon(
                                                imageVector = if (bindingMenuExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null
                                        )
                                    },
                                    modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { coordinates ->
                                                bindingMenuWidth = with(density) { coordinates.size.width.toDp() }
                                            }
                            )
                            Box(
                                    modifier = Modifier
                                            .matchParentSize()
                                            .clickable(
                                                    enabled = !savingMetadata,
                                                    interactionSource = dropdownClickSource,
                                                    indication = null
                                            ) { bindingMenuExpanded = !bindingMenuExpanded }
                            )
                            DropdownMenu(
                                    expanded = bindingMenuExpanded,
                                    onDismissRequest = { bindingMenuExpanded = false },
                                    modifier = if (bindingMenuWidth > 0.dp) Modifier.width(bindingMenuWidth) else Modifier
                            ) {
                                bindingOptions.forEach { option ->
                                    DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                selectedCharacterCardName = option.characterCardName
                                                selectedCharacterGroupId = option.characterGroupId
                                                bindingMenuExpanded = false
                                            }
                                    )
                                }
                            }
                        }
                        Text(
                                text = bindingHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        metadataError?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (savingMetadata) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(
                            enabled = !savingMetadata && newTitle.isNotBlank(),
                            onClick = {
                                if (!savingMetadata) {
                                    savingMetadata = true
                                    metadataError = null
                                    bindingMenuExpanded = false
                                    val titleSnapshot = newTitle
                                    val cardSnapshot = selectedCharacterCardName
                                    val groupSnapshot = selectedCharacterGroupId
                                    coroutineScope.launch {
                                        try {
                                            onEditChatMetadata(editingChat, titleSnapshot, cardSnapshot, groupSnapshot)
                                            // 不允许旧弹窗的完成回调关闭后来打开的目标。
                                            if (chatToEdit === editingChat) chatToEdit = null
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            AppLogger.e("ChatHistorySelector", "Metadata save failed: ${failure.javaClass.simpleName}")
                                            metadataError = observableResources.getString(
                                                if (failure is com.ai.assistance.operit.data.repository.ChatMetadataConflictException)
                                                    R.string.chat_metadata_edit_conflict
                                                else R.string.chat_metadata_edit_failed
                                            )
                                        } finally {
                                            savingMetadata = false
                                        }
                                    }
                                }
                            }
                    ) {
                        Text(stringResource(if (savingMetadata) R.string.chat_metadata_saving else R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(enabled = !savingMetadata, onClick = { requestCloseMetadata() }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
        )
        if (confirmDiscardMetadata) {
            AlertDialog(
                onDismissRequest = { confirmDiscardMetadata = false },
                title = { Text(stringResource(R.string.pkg_env_discard_title)) },
                text = { Text(stringResource(R.string.chat_metadata_discard_message)) },
                confirmButton = {
                    TextButton(onClick = { chatToEdit = null }) {
                        Text(stringResource(R.string.pkg_env_discard))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscardMetadata = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    if (showSettingsDialog) {
        // 这里的每个选项都即时生效，原来的“取消”按钮与实际行为相反；改为“完成”并统一到
        // 共享底部抽屉，同时给显示模式补上单选语义，读屏不再把三项读成三个独立开关。
        KiyoriModalBottomDrawer(onDismissRequest = { showSettingsDialog = false }) { dismissDrawer ->
            KiyoriDrawerScaffold(
                title = stringResource(R.string.chat_history_settings),
                onClose = dismissDrawer,
                scrollableContent = false,
                footer = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = dismissDrawer, shape = KiyoriUiShapes.control) {
                            Text(stringResource(R.string.done))
                        }
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.chat_display_mode),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    listOf(
                        Triple(
                            ChatHistoryDisplayMode.BY_CHARACTER_CARD,
                            stringResource(R.string.history_filter_role_card),
                            stringResource(R.string.history_filter_role_card_desc)
                        ),
                        Triple(
                            ChatHistoryDisplayMode.BY_FOLDER,
                            stringResource(R.string.history_filter_folder),
                            stringResource(R.string.history_filter_folder_desc)
                        ),
                        Triple(
                            ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY,
                            stringResource(R.string.history_filter_current_card),
                            stringResource(R.string.history_filter_current_card_desc)
                        )
                    ).forEach { (mode, title, description) ->
                        val selected = historyDisplayMode == mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(KiyoriUiShapes.control)
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onDisplayModeChange(mode) }
                                ),
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            shape = KiyoriUiShapes.control
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )

                    Text(
                        text = stringResource(R.string.chat_history_switch_behavior),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HistorySettingsToggle(
                        title = stringResource(R.string.history_auto_switch_character),
                        description = stringResource(R.string.history_auto_switch_character_desc),
                        checked = autoSwitchCharacterCard,
                        onCheckedChange = onAutoSwitchCharacterCardChange
                    )

                    HistorySettingsToggle(
                        title = stringResource(R.string.history_auto_switch_chat),
                        description = stringResource(R.string.history_auto_switch_chat_desc),
                        checked = autoSwitchChatOnCharacterSelect,
                        onCheckedChange = onAutoSwitchChatOnCharacterSelectChange
                    )
                }
            }
        }
    }

    if (showNewGroupDialog) {
        var creating by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var createdButNotSelected by remember { mutableStateOf(false) }
        var discard by remember { mutableStateOf(false) }
        fun closeCreate() {
            if (creating) return
            if (!createdButNotSelected && newGroupName.isNotBlank()) discard = true
            else { newGroupName = ""; showNewGroupDialog = false }
        }
        AlertDialog(
            onDismissRequest = { closeCreate() },
            title = { Text(stringResource(R.string.new_group)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        enabled = !creating && !createdButNotSelected,
                        singleLine = true,
                        shape = KiyoriUiShapes.field,
                        label = { Text(stringResource(R.string.group_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (creating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = !creating && (createdButNotSelected || newGroupName.isNotBlank()),
                    onClick = {
                        if (createdButNotSelected) closeCreate()
                        else if (!creating) {
                            creating = true
                            error = null
                            val submitted = newGroupName.trim()
                            val binding = newGroupBinding
                            coroutineScope.launch {
                                try {
                                    val result = onCreateGroup(submitted, binding.first, binding.second)
                                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                    if (!result.selected) {
                                        android.widget.Toast.makeText(context, R.string.chat_group_created_selection_changed, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                    newGroupName = ""
                                    showNewGroupDialog = false
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (partial: com.ai.assistance.operit.data.repository.ChatCreationSelectionException) {
                                    createdButNotSelected = true
                                    error = observableResources.getString(R.string.chat_group_created_selection_failed)
                                } catch (failure: Exception) {
                                    AppLogger.e("ChatHistorySelector", "Group creation failed: ${failure.javaClass.simpleName}")
                                    error = observableResources.getString(R.string.chat_group_operation_failed)
                                } finally { creating = false }
                            }
                        }
                    }
                ) { Text(stringResource(if (createdButNotSelected) R.string.close else R.string.create)) }
            },
            dismissButton = {
                if (!createdButNotSelected) {
                    TextButton(onClick = { closeCreate() }, enabled = !creating) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
        if (discard) {
            AlertDialog(
                onDismissRequest = { discard = false },
                title = { Text(stringResource(R.string.pkg_env_discard_title)) },
                text = { Text(stringResource(R.string.chat_group_discard)) },
                confirmButton = { TextButton(onClick = { newGroupName = ""; showNewGroupDialog = false }) { Text(stringResource(R.string.pkg_env_discard)) } },
                dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }

    val totalChatCount = chatHistories.size
    val visibleChatCount = filteredHistories.size
    val isFilteringHistories = searchQuery.trim().isNotEmpty()
    val focusManager = LocalFocusManager.current
    var showGestureHint by rememberLocal(key = "show_swipe_hint", defaultValue = true)

    Column(modifier = modifier) {
        // 抽屉自身是左侧面板，右上角是“收起”，不是页面级返回；标题下给出当前可见口径，
        // 让搜索或显示模式造成的过滤结果始终可解释，而不是让用户面对一个来历不明的短列表。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isFilteringHistories) {
                        stringResource(
                            R.string.chat_history_filtered_count,
                            visibleChatCount,
                            totalChatCount
                        )
                    } else {
                        stringResource(R.string.chat_history_total_count, totalChatCount)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.chat_history_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.chat_history_collapse),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 搜索常驻。折叠式搜索会在收起时保留查询词，列表继续被一个看不见的条件过滤；
        // 常驻输入框让过滤条件始终可见，清空也只需要一次点击。
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            isError = searchFailed,
            supportingText = if (searchFailed) {
                {
                    Text(
                        text = stringResource(R.string.chat_history_search_failed),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                null
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.chat_history_search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onSearchQueryChange("")
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.clear_search),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = KiyoriUiShapes.field,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = context.getString(R.string.search_chat_history_hint) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val (characterCardName, characterGroupId) = resolveBindingForCreate(
                        historyDisplayMode = historyDisplayMode,
                        activePrompt = activePrompt,
                        activeCharacterCardName = activeCharacterCardName
                    )
                    onNewChat(characterCardName, characterGroupId)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = KiyoriUiShapes.control,
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.new_chat),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalIconButton(
                onClick = {
                    if (historyDisplayMode != ChatHistoryDisplayMode.BY_FOLDER &&
                        activePrompt is ActivePrompt.CharacterCard && activeCharacterCardName == null
                    ) {
                        android.widget.Toast.makeText(
                            context,
                            R.string.chat_group_binding_loading,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        newGroupBinding = resolveBindingForCreate(
                            historyDisplayMode,
                            activePrompt,
                            activeCharacterCardName
                        )
                        showNewGroupDialog = true
                    }
                },
                modifier = Modifier.size(40.dp),
                shape = KiyoriUiShapes.control
            ) {
                Icon(
                    imageVector = Icons.Outlined.CreateNewFolder,
                    contentDescription = stringResource(R.string.new_group),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (moveSaving || selectingChat) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (selectionError) {
            HistoryInlineBanner(
                message = stringResource(R.string.chat_selection_failed),
                onDismiss = { selectionError = false }
            )
        }

        moveError?.let { error ->
            HistoryInlineBanner(
                message = error,
                onDismiss = { moveError = null }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (showGestureHint && chatHistories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.chat_history_gesture_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showGestureHint = false },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.chat_history_hint_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(end = 2.dp)
        ) {
            LazyColumn(
                state = actualLazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 22.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (filteredHistories.isEmpty()) {
                    item(key = "history-placeholder-state") {
                        when {
                            isSearching ->
                                HistoryStatusPlaceholder(
                                    title = stringResource(R.string.chat_history_searching),
                                    description = stringResource(R.string.chat_history_searching_desc),
                                    showProgress = true
                                )
                            isFilteringHistories ->
                                HistoryStatusPlaceholder(
                                    icon = Icons.Outlined.SearchOff,
                                    title = stringResource(R.string.chat_history_no_result),
                                    description = stringResource(R.string.no_matching_chats_adjust_filter),
                                    actionLabel = stringResource(R.string.clear_search),
                                    onAction = {
                                        onSearchQueryChange("")
                                        focusManager.clearFocus()
                                    }
                                )
                            else ->
                                HistoryStatusPlaceholder(
                                    icon = Icons.Outlined.Forum,
                                    title = stringResource(R.string.chat_history_empty_title),
                                    description = stringResource(R.string.chat_history_empty_hint),
                                    actionLabel = stringResource(R.string.new_chat),
                                    onAction = {
                                        val (cardName, groupId) = resolveBindingForCreate(
                                            historyDisplayMode = historyDisplayMode,
                                            activePrompt = activePrompt,
                                            activeCharacterCardName = activeCharacterCardName
                                        )
                                        onNewChat(cardName, groupId)
                                    }
                                )
                        }
                    }
                }
                items(
                    items = if (filteredHistories.isEmpty()) emptyList() else flatItems,
                    key = {
                        when (it) {
                            is HistoryListItem.CharacterHeader -> it.key
                            is HistoryListItem.Header -> it.key
                            is HistoryListItem.Item -> it.history.id
                        }
                    }
                ) { item ->
                    when (item) {
                        is HistoryListItem.CharacterHeader -> {
                        val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
                        val groupId = item.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
                        val groupAvatarUri by remember(groupId) {
                            groupId?.let { userPreferencesManager.getAiAvatarForCharacterGroupFlow(it) }
                                ?: flowOf(null)
                        }.collectAsState(initial = null)
                        val groupFallbackMemberCardId = remember(groupId, availableCharacterGroups) {
                            val group = if (groupId.isNullOrBlank()) null else {
                                availableCharacterGroups.firstOrNull { it.id == groupId }
                            }
                            val sortedMembers = group?.members?.sortedBy { it.orderIndex }.orEmpty()
                            sortedMembers.firstOrNull()?.characterCardId
                        }
                        val groupFallbackMemberAvatarUri by remember(groupFallbackMemberCardId) {
                            groupFallbackMemberCardId?.let {
                                userPreferencesManager.getAiAvatarForCharacterCardFlow(it)
                            } ?: flowOf(null)
                        }.collectAsState(initial = null)
                        val characterCardId = remember(item.characterCardName, availableCharacterCards) {
                            val cardName = item.characterCardName?.takeIf { it.isNotBlank() } ?: return@remember null
                            availableCharacterCards.firstOrNull { it.name == cardName }?.id
                        }
                        val characterCardAvatarUri by remember(characterCardId) {
                            characterCardId?.let { userPreferencesManager.getAiAvatarForCharacterCardFlow(it) }
                                ?: flowOf(null)
                        }.collectAsState(initial = null)
                        val avatarUri =
                            if (!groupId.isNullOrBlank()) {
                                groupAvatarUri ?: groupFallbackMemberAvatarUri
                            } else {
                                characterCardAvatarUri
                            }

                        val isExpanded = !collapsedCharacters.contains(item.key)
                        val stateDescription = if (isExpanded) {
                            stringResource(R.string.expanded)
                        } else {
                            stringResource(R.string.collapsed)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 4.dp)
                                .clip(KiyoriUiShapes.control)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "${item.name}, $stateDescription"
                                    role = Role.Button
                                }
                                .clickable {
                                    collapsedCharacters =
                                        if (collapsedCharacters.contains(item.key)) {
                                            collapsedCharacters - item.key
                                        } else {
                                            collapsedCharacters + item.key
                                        }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (avatarUri != null) Color.Transparent
                                        else MaterialTheme.colorScheme.primaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clearAndSetSemantics {},
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (!groupId.isNullOrBlank()) Icons.Default.Groups else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clearAndSetSemantics {}
                                    )
                                }
                            }
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clearAndSetSemantics {}
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clearAndSetSemantics {}
                            )
                        }
                    }
                    is HistoryListItem.Header -> {
                        val isExpanded = !collapsedGroups.contains(item.key)
                        val stateDescription = if (isExpanded) {
                            stringResource(R.string.expanded)
                        } else {
                            stringResource(R.string.collapsed)
                        }
                        val manageable = item.groupValue != null && item.scope != null

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD) {
                                HistoryBranchRail(railHeight = 36.dp)
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = KiyoriUiShapes.control,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .semantics(mergeDescendants = true) {
                                                contentDescription = "${item.name}, $stateDescription"
                                                role = Role.Button
                                            }
                                            .pointerInput(item.key) {
                                                detectTapGestures(
                                                    onTap = {
                                                        collapsedGroups = if (collapsedGroups.contains(item.key)) {
                                                            collapsedGroups - item.key
                                                        } else {
                                                            collapsedGroups + item.key
                                                        }
                                                    },
                                                    onLongPress = {
                                                        if (manageable) {
                                                            groupActionTarget = ChatGroupTarget(
                                                                groupName = item.name,
                                                                scope = item.scope
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clearAndSetSemantics {}
                                        )
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clearAndSetSemantics {}
                                        )
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clearAndSetSemantics {}
                                        )
                                    }
                                    // 分组管理此前只有长按入口，界面上没有任何提示。给出显式按钮后
                                    // 不再需要把“长按管理”写进标题里占用本就紧张的一行宽度。
                                    if (manageable) {
                                        IconButton(
                                            onClick = {
                                                groupActionTarget = ChatGroupTarget(
                                                    groupName = item.name,
                                                    scope = item.scope
                                                )
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = stringResource(R.string.manage_group),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                }
                            }
                        }
                    }
                    is HistoryListItem.Item -> {
                        val deleteAction = SwipeAction(
                            onSwipe = { promptDeleteChat(item.history) },
                            icon = {
                                Icon(
                                    modifier = Modifier.padding(16.dp),
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.onError
                                )
                            },
                            background = MaterialTheme.colorScheme.error
                        )

                        val editAction = SwipeAction(
                            onSwipe = { chatToEdit = item.history },
                            icon = {
                                Icon(
                                    modifier = Modifier.padding(16.dp),
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.edit_title),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            },
                            background = MaterialTheme.colorScheme.primary
                        )

                        ReorderableItem(
                            reorderableState,
                            key = item.history.id,
                            animateItemModifier = Modifier.animateItem(placementSpec = null)
                        ) { isDragging ->
                            val isSelected = item.history.id == currentId
                            // 侧滑会把条目内容平移到删除/编辑背景之上，条目必须不透明；
                            // 未选中时沿用抽屉底色，视觉上仍是“无卡片”的导航列表。
                            val containerColor = when {
                                isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
                                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                            }
                            val contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                            val metaColor = if (isSelected) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val isStreaming = activeStreamingChatIds.contains(item.history.id)
                            val parentChat = remember(item.history.parentChatId, chatHistories) {
                                item.history.parentChatId?.let { parentId ->
                                    chatHistories.find { it.id == parentId }
                                }
                            }
                            val relativeTime = historyRelativeTimeLabel(item.history.updatedAt, nowMillis)
                            val groupName = item.history.group ?: ungroupedText
                            val rowDescription = listOfNotNull(
                                item.history.title,
                                relativeTime,
                                groupName,
                                if (isSelected) stringResource(R.string.chat_history_current_chat) else null
                            ).joinToString(separator = ", ")

                            // Room 真实删除后由列表更新移除条目；不提前隐藏尚未删除的聊天。
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD) {
                                    HistoryBranchRail(railHeight = 52.dp)
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    SwipeableActionsBox(
                                        startActions = listOf(editAction),
                                        endActions = listOf(deleteAction),
                                        swipeThreshold = 96.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(KiyoriUiShapes.control)
                                    ) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = containerColor,
                                            shape = KiyoriUiShapes.control,
                                            shadowElevation = if (isDragging) 6.dp else 0.dp
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 52.dp)
                                                    .padding(start = 2.dp, end = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val dragDescription = stringResource(R.string.drag_item, item.history.title)
                                                IconButton(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .draggableHandle()
                                                        .semantics {
                                                            contentDescription = dragDescription
                                                        },
                                                    onClick = {}
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.DragHandle,
                                                        contentDescription = null,
                                                        tint = metaColor.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .semantics(mergeDescendants = true) {
                                                            contentDescription = rowDescription
                                                            role = Role.Button
                                                        }
                                                        .pointerInput(item.history.id) {
                                                            detectTapGestures(
                                                                onTap = { selectChat(item.history.id) },
                                                                onLongPress = { chatItemActionTarget = item.history }
                                                            )
                                                        }
                                                        .padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            text = item.history.title,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                            color = contentColor,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier
                                                                .weight(1f, fill = false)
                                                                .clearAndSetSemantics {}
                                                        )
                                                        if (isStreaming) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier
                                                                    .size(11.dp)
                                                                    .clearAndSetSemantics {},
                                                                strokeWidth = 1.5.dp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        if (item.history.pinned) {
                                                            Icon(
                                                                imageVector = Icons.Default.PushPin,
                                                                contentDescription = null,
                                                                tint = metaColor,
                                                                modifier = Modifier
                                                                    .size(13.dp)
                                                                    .clearAndSetSemantics {}
                                                            )
                                                        }
                                                        if (item.history.locked) {
                                                            Icon(
                                                                imageVector = Icons.Default.Lock,
                                                                contentDescription = null,
                                                                tint = metaColor,
                                                                modifier = Modifier
                                                                    .size(13.dp)
                                                                    .clearAndSetSemantics {}
                                                            )
                                                        }
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.clearAndSetSemantics {}
                                                    ) {
                                                        if (parentChat != null) {
                                                            Icon(
                                                                imageVector = Icons.Default.AccountTree,
                                                                contentDescription = null,
                                                                tint = metaColor,
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = if (parentChat != null) {
                                                                "$relativeTime · ${parentChat.title}"
                                                            } else {
                                                                relativeTime
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = metaColor,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                // 条目操作此前只有长按一个入口。显式“更多”按钮让重命名、
                                                // 置顶、锁定和删除在不知道手势的情况下同样可达。
                                                IconButton(
                                                    onClick = { chatItemActionTarget = item.history },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = stringResource(
                                                            R.string.chat_history_more_actions,
                                                            item.history.title
                                                        ),
                                                        tint = metaColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }

            HistoryQuickScroller(
                listState = actualLazyListState,
                itemCount = flatItems.size,
                onInteractionChange = onQuickScrollInteractionChange,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 12.dp, bottom = 12.dp)
            )
        }
    }
}
