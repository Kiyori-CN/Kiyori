package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdMarkingMove
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdMarkingNavigationPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionAdMarkingState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionAdMarkingNavigationRequest
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionWebElementActionState

@Composable
internal fun WebSessionWebElementActionDialog(
    state: WebSessionWebElementActionState,
    onDismiss: () -> Unit,
    onOpenInNewTab: (String, Boolean) -> Unit,
    onCopyUrl: () -> Unit,
    onCopyText: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onSelectText: (Double, Double) -> Unit,
    onBlockElement: () -> Unit,
    onBlockUrl: (String) -> Unit,
) {
    val openableUrl = state.linkUrl ?: state.resourceUrl
    val blockableUrl = state.resourceUrl ?: state.linkUrl
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.TouchApp,
            tone = WebSessionBrowserMenuTone.AD_MARKING,
            title = "网页元素操作",
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
        ) {
            WebElementSummary(state)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            openableUrl?.let { url ->
                WebElementActionRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = "新窗口打开",
                    description = "在新的前台浏览器窗口中打开",
                    onClick = {
                        onDismiss()
                        onOpenInNewTab(url, true)
                    },
                )
                WebElementActionRow(
                    icon = Icons.Filled.AddBox,
                    title = "后台打开",
                    description = "创建新窗口并保留当前网页",
                    onClick = {
                        onDismiss()
                        onOpenInNewTab(url, false)
                    },
                )
                WebElementActionRow(
                    icon = Icons.Filled.ContentCopy,
                    title = "复制链接",
                    description = "复制此元素关联的网址或资源地址",
                    onClick = onCopyUrl,
                )
                WebElementActionRow(
                    icon = Icons.Filled.Language,
                    title = "外部打开",
                    description = "交给系统中的其他应用处理",
                    onClick = {
                        onDismiss()
                        onOpenExternal(url)
                    },
                )
            }
            if (state.text.isNotBlank()) {
                WebElementActionRow(
                    icon = Icons.Filled.TextFields,
                    title = "复制文本",
                    description = "复制此元素当前显示的文本",
                    onClick = onCopyText,
                )
                WebElementActionRow(
                    icon = Icons.Filled.SelectAll,
                    title = "选择文本",
                    description = "在网页中进入可调整的文本选择状态",
                    onClick = {
                        onDismiss()
                        onSelectText(state.clientX, state.clientY)
                    },
                )
            }
            WebElementActionRow(
                icon = Icons.Filled.Block,
                title = "拦截网页元素",
                description = "进入标记工作台，生成当前站点的元素隐藏规则",
                onClick = onBlockElement,
                emphasized = true,
            )
            blockableUrl?.let { url ->
                WebElementActionRow(
                    icon = Icons.Filled.LinkOff,
                    title = "拦截过滤网址",
                    description = "根据此元素的真实请求地址创建网络规则",
                    onClick = {
                        onDismiss()
                        onBlockUrl(url)
                    },
                    emphasized = true,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun WebElementSummary(state: WebSessionWebElementActionState) {
    val toneColors = WebSessionBrowserMenuTone.AD_MARKING.resolveColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "<${state.tagName}>",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = toneColors.icon,
                modifier =
                    Modifier
                        .background(toneColors.container, RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = state.text.ifBlank { "当前元素没有可见文本" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = state.selector,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WebElementActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    val toneColors = WebSessionBrowserMenuTone.AD_MARKING.resolveColors()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .background(
                        if (emphasized) {
                            toneColors.container
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        RoundedCornerShape(10.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (emphasized) {
                        toneColors.icon
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun WebSessionAdMarkingWorkbench(
    state: WebSessionAdMarkingState,
    onChooseNode: () -> Unit,
    onMove: (BrowserAdMarkingMove) -> Unit,
    onSetPreview: (Boolean) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
    onEditRule: () -> Unit,
    onClearIntercept: () -> Unit,
    onOpenNavigationPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var infoTab by remember(state.sessionId, state.pageUrl) { mutableStateOf(AdMarkingInfoTab.RULE) }
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    Surface(
        modifier = modifier.fillMaxWidth().height(320.dp),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, outlineColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.height(46.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AdMarkingTopButton(
                        label = "拦截规则",
                        selected = infoTab == AdMarkingInfoTab.RULE,
                        onClick = { infoTab = AdMarkingInfoTab.RULE },
                    )
                    AdMarkingTopButton(
                        label = "节点",
                        selected = infoTab == AdMarkingInfoTab.NODE,
                        onClick = {
                            infoTab = AdMarkingInfoTab.NODE
                            onChooseNode()
                        },
                    )
                    AdMarkingTopButton(
                        label = "HTML",
                        enabled = state.html.isNotBlank(),
                        selected = infoTab == AdMarkingInfoTab.HTML,
                        onClick = { infoTab = AdMarkingInfoTab.HTML },
                    )
                    AdMarkingTopButton(
                        label = "父",
                        enabled = state.selector.isNotBlank(),
                        onClick = { onMove(BrowserAdMarkingMove.PARENT) },
                    )
                    AdMarkingTopButton(
                        label = "兄",
                        enabled = state.selector.isNotBlank(),
                        onClick = { onMove(BrowserAdMarkingMove.PREVIOUS_SIBLING) },
                    )
                    AdMarkingTopButton(
                        label = "弟",
                        enabled = state.selector.isNotBlank(),
                        onClick = { onMove(BrowserAdMarkingMove.NEXT_SIBLING) },
                    )
                    AdMarkingTopButton(
                        label = "子",
                        enabled = state.selector.isNotBlank(),
                        onClick = { onMove(BrowserAdMarkingMove.FIRST_CHILD) },
                    )
                }
                IconButton(onClick = onExit) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭标记广告",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = outlineColor)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = when (infoTab) {
                        AdMarkingInfoTab.RULE -> "拦截规则"
                        AdMarkingInfoTab.NODE -> "节点信息"
                        AdMarkingInfoTab.HTML -> "HTML"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        text =
                            when (infoTab) {
                                AdMarkingInfoTab.RULE ->
                                    state.selector.ifBlank { "尚未选择网页元素" }
                                AdMarkingInfoTab.NODE ->
                                    buildString {
                                        append("<")
                                        append(state.tagName.ifBlank { "node" })
                                        append(">")
                                        if (state.text.isNotBlank()) {
                                            append("\n")
                                            append(state.text)
                                        }
                                    }
                                AdMarkingInfoTab.HTML ->
                                    state.html.ifBlank { "尚未捕获当前节点 HTML" }
                            },
                        style =
                            if (infoTab == AdMarkingInfoTab.HTML) {
                                MaterialTheme.typography.bodySmall
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                        fontFamily =
                            if (infoTab == AdMarkingInfoTab.NODE) {
                                MaterialTheme.typography.bodyLarge.fontFamily
                            } else {
                                FontFamily.Monospace
                            },
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (state.domain.isNotBlank()) {
                    Text(
                        text = "${state.domain} · ${state.tagName.ifBlank { "未选择节点" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = outlineColor)
            Row(
                modifier =
                    Modifier
                        .height(58.dp)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdMarkingFooterButton(
                    label = "保存规则",
                    icon = Icons.Filled.Save,
                    enabled = state.selector.isNotBlank() && state.domain.isNotBlank(),
                    emphasized = true,
                    onClick = onSave,
                )
                AdMarkingFooterButton(
                    label = "编辑规则",
                    icon = Icons.Filled.Code,
                    enabled = state.selector.isNotBlank(),
                    onClick = onEditRule,
                )
                AdMarkingFooterButton(
                    label = if (state.previewing) "恢复" else "预览",
                    icon = if (state.previewing) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    enabled = state.selector.isNotBlank(),
                    selected = state.previewing,
                    onClick = { onSetPreview(!state.previewing) },
                )
                AdMarkingFooterButton(
                    label = "重置",
                    icon = Icons.Filled.RestartAlt,
                    onClick = onReset,
                )
                AdMarkingFooterButton(
                    label = "清除拦截",
                    icon = Icons.Filled.Block,
                    enabled = state.selector.isNotBlank() && state.domain.isNotBlank(),
                    onClick = onClearIntercept,
                )
                AdMarkingFooterButton(
                    label = "拦截网站跳转",
                    icon = Icons.Filled.LinkOff,
                    selected =
                        state.navigationPolicy == BrowserAdMarkingNavigationPolicy.BLOCK ||
                            state.navigationPolicy == BrowserAdMarkingNavigationPolicy.DEFAULT,
                    onClick = onOpenNavigationPolicy,
                )
            }
        }
    }
}

@Composable
private fun AdMarkingTopButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(46.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color =
                if (selected) {
                    WebSessionBrowserMenuTone.AD_MARKING.resolveColors().icon
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            maxLines = 1,
        )
    }
}

@Composable
private fun AdMarkingFooterButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    emphasized: Boolean = false,
) {
    val toneColors = WebSessionBrowserMenuTone.AD_MARKING.resolveColors()
    val contentColor =
        if (selected || emphasized) toneColors.icon else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = contentColor,
        border =
            BorderStroke(
                1.dp,
                if (enabled) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                },
            ),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(17.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

private enum class AdMarkingInfoTab {
    RULE,
    NODE,
    HTML,
}

@Composable
internal fun WebSessionAdMarkingRuleEditor(
    ruleDraft: String,
    onRuleDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.Code,
            tone = WebSessionBrowserMenuTone.AD_MARKING,
            title = "编辑拦截规则",
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "修改当前网页元素的 CSS selector，确认后回到工作台，点击“保存规则”才会写入浏览器规则。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = ruleDraft,
                    onValueChange = onRuleDraftChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 180.dp),
                    minLines = 3,
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    label = { Text("CSS selector") },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(onClick = onConfirm) { Text("确认") }
                }
            }
        }
    }
}

@Composable
internal fun WebSessionAdMarkingClearConfirmation(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清除拦截") },
        text = {
            Text("将删除当前站点当前 selector 对应的网页元素规则，并立即恢复当前页面的元素。确定继续吗？")
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("清除拦截", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebSessionAdMarkingNavigationPolicySheet(
    selectedPolicy: BrowserAdMarkingNavigationPolicy,
    onDismiss: () -> Unit,
    onSelect: (BrowserAdMarkingNavigationPolicy) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "跳转外部网站策略",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Text(
                text = "标记模式中的网页点击默认不会跳转；只有显式选择“允许跳转”才会改变这一行为。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            BrowserAdMarkingNavigationPolicy.values().forEach { policy ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(policy) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (policy == selectedPolicy) "✓" else "",
                        modifier = Modifier.size(20.dp),
                        color = WebSessionBrowserMenuTone.AD_MARKING.resolveColors().icon,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = navigationPolicyLabel(policy),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = navigationPolicyDescription(policy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).padding(horizontal = 12.dp),
            ) {
                Text("取消")
            }
        }
    }
}

@Composable
internal fun WebSessionAdMarkingNavigationRequestDialog(
    request: WebSessionAdMarkingNavigationRequest,
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认跳转") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("网页请求打开外部网站：")
                Text(
                    text = request.url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (request.text.isNotBlank()) {
                    Text(
                        text = request.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            TextButton(onClick = onAllow) { Text("允许跳转") }
        },
    )
}

private fun navigationPolicyLabel(policy: BrowserAdMarkingNavigationPolicy): String =
    when (policy) {
        BrowserAdMarkingNavigationPolicy.DEFAULT -> "默认"
        BrowserAdMarkingNavigationPolicy.ALLOW -> "允许跳转"
        BrowserAdMarkingNavigationPolicy.ASK -> "跳转前询问"
        BrowserAdMarkingNavigationPolicy.BLOCK -> "拦截跳转"
    }

private fun navigationPolicyDescription(policy: BrowserAdMarkingNavigationPolicy): String =
    when (policy) {
        BrowserAdMarkingNavigationPolicy.DEFAULT ->
            "进入标记广告后锁定网页点击，保证选择元素时不误跳转"
        BrowserAdMarkingNavigationPolicy.ALLOW ->
            "允许网页链接按正常浏览器行为跳转"
        BrowserAdMarkingNavigationPolicy.ASK ->
            "点击网页链接时先显示确认弹窗"
        BrowserAdMarkingNavigationPolicy.BLOCK ->
            "阻止网页链接跳转，只在当前页面选择元素"
    }
