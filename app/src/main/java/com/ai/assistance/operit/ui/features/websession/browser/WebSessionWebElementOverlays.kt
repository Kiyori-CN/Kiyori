package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdMarkingMove
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdMarkingNavigationPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserQrCodeUiStatus
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserWebElementAction
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserWebElementTargetKind
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionAdMarkingState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionAdMarkingNavigationRequest
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionQrCodeState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionWebElementActionState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.blockableUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserAdMarkingSelectablePolicies
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserNetworkHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserOpenUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildBrowserWebElementActionPlan
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.externalOpenUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.imageUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isHttpBrowserNetworkUrl
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_CONTENT_HEIGHT_DP
import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer

internal const val WEB_SESSION_AD_MARKING_WORKBENCH_HEIGHT_DP = 300
internal const val WEB_SESSION_AD_MARKING_FOOTER_BUTTON_HEIGHT_DP = 40
internal const val WEB_SESSION_AD_MARKING_FOOTER_VERTICAL_PADDING_DP =
    (WEB_SESSION_BROWSER_BOTTOM_CONTENT_HEIGHT_DP -
        WEB_SESSION_AD_MARKING_FOOTER_BUTTON_HEIGHT_DP) / 2

@Composable
internal fun WebSessionWebElementActionDialog(
    state: WebSessionWebElementActionState,
    onDismiss: () -> Unit,
    onOpenInNewTab: (String, Boolean) -> Unit,
    onCopyUrl: (String) -> Unit,
    onCopyText: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onSelectText: (Double, Double) -> Unit,
    onOpenImage: () -> Unit,
    onOpenImageMode: () -> Unit,
    onSaveImage: () -> Unit,
    onRecognizeQrCode: () -> Unit,
    onBlockElementQuick: () -> Unit,
    onBlockElementAdvanced: () -> Unit,
    onBlockUrl: (String) -> Unit,
) {
    val actionPlan = remember(state) { buildBrowserWebElementActionPlan(state) }
    KiyoriModalBottomDrawer(onDismissRequest = onDismiss) { dismissDrawer ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WebElementSummary(state = state, targetKind = actionPlan.targetKind)
            WebElementActionSection(
                title = "快捷操作",
                actions =
                    actionPlan.quickActions.map { action ->
                        webElementActionUi(
                            action = action,
                            state = state,
                            onDismiss = dismissDrawer,
                            onOpenInNewTab = onOpenInNewTab,
                            onCopyUrl = onCopyUrl,
                            onCopyText = onCopyText,
                            onOpenExternal = onOpenExternal,
                            onSelectText = onSelectText,
                            onOpenImage = onOpenImage,
                            onOpenImageMode = onOpenImageMode,
                            onSaveImage = onSaveImage,
                            onRecognizeQrCode = onRecognizeQrCode,
                            onBlockElementQuick = onBlockElementQuick,
                            onBlockElementAdvanced = onBlockElementAdvanced,
                            onBlockUrl = onBlockUrl,
                        )
                    },
            )
            if (actionPlan.contentActions.isNotEmpty()) {
                WebElementActionSection(
                    title = "文本与识别",
                    actions =
                        actionPlan.contentActions.map { action ->
                            webElementActionUi(
                                action = action,
                                state = state,
                                onDismiss = dismissDrawer,
                                onOpenInNewTab = onOpenInNewTab,
                                onCopyUrl = onCopyUrl,
                                onCopyText = onCopyText,
                                onOpenExternal = onOpenExternal,
                                onSelectText = onSelectText,
                                onOpenImage = onOpenImage,
                                onOpenImageMode = onOpenImageMode,
                                onSaveImage = onSaveImage,
                                onRecognizeQrCode = onRecognizeQrCode,
                                onBlockElementQuick = onBlockElementQuick,
                                onBlockElementAdvanced = onBlockElementAdvanced,
                                onBlockUrl = onBlockUrl,
                            )
                        },
                )
            }
            WebElementActionSection(
                title = "网页拦截",
                actions =
                    actionPlan.blockingActions.map { action ->
                        webElementActionUi(
                            action = action,
                            state = state,
                            onDismiss = dismissDrawer,
                            onOpenInNewTab = onOpenInNewTab,
                            onCopyUrl = onCopyUrl,
                            onCopyText = onCopyText,
                            onOpenExternal = onOpenExternal,
                            onSelectText = onSelectText,
                            onOpenImage = onOpenImage,
                            onOpenImageMode = onOpenImageMode,
                            onSaveImage = onSaveImage,
                            onRecognizeQrCode = onRecognizeQrCode,
                            onBlockElementQuick = onBlockElementQuick,
                            onBlockElementAdvanced = onBlockElementAdvanced,
                            onBlockUrl = onBlockUrl,
                        )
                    },
            )
        }
    }
}

private data class WebElementActionUi(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val tone: WebElementActionTone,
    val onClick: () -> Unit,
)

private enum class WebElementActionTone {
    PRIMARY,
    IMAGE,
    COPY,
    BLOCK,
}

@Composable
private fun WebElementSummary(
    state: WebSessionWebElementActionState,
    targetKind: BrowserWebElementTargetKind,
) {
    val toneColors = WebSessionBrowserMenuTone.AD_MARKING.resolveColors()
    val host = browserNetworkHost(state.pageUrl).ifBlank { state.pageUrl }
    val summary =
        state.text.ifBlank {
            state.linkUrl ?: state.resourceUrl ?: "当前元素没有可见文本"
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .background(toneColors.container, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector =
                    if (
                        targetKind == BrowserWebElementTargetKind.IMAGE ||
                            targetKind == BrowserWebElementTargetKind.IMAGE_LINK
                    ) {
                        Icons.Filled.Image
                    } else {
                        Icons.Filled.TouchApp
                    },
                contentDescription = null,
                tint = toneColors.icon,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = webElementTargetLabel(targetKind),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "<${state.tagName}>",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = toneColors.icon,
                    modifier =
                        Modifier
                            .background(toneColors.container, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                text = host,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WebElementActionSection(
    title: String,
    actions: List<WebElementActionUi>,
) {
    if (actions.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border =
                BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.chunked(4).forEach { rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowActions.forEach { action ->
                            WebElementActionCard(
                                action = action,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - rowActions.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebElementActionCard(
    action: WebElementActionUi,
    modifier: Modifier = Modifier,
) {
    val colors =
        when (action.tone) {
            WebElementActionTone.PRIMARY ->
                MaterialTheme.colorScheme.primaryContainer to
                    MaterialTheme.colorScheme.onPrimaryContainer
            WebElementActionTone.IMAGE ->
                MaterialTheme.colorScheme.tertiaryContainer to
                    MaterialTheme.colorScheme.onTertiaryContainer
            WebElementActionTone.COPY ->
                MaterialTheme.colorScheme.secondaryContainer to
                    MaterialTheme.colorScheme.onSecondaryContainer
            WebElementActionTone.BLOCK ->
                MaterialTheme.colorScheme.errorContainer to
                    MaterialTheme.colorScheme.onErrorContainer
        }
    Column(
        modifier =
            modifier
                .heightIn(min = 82.dp)
                .clickable(onClick = action.onClick)
                .padding(horizontal = 2.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(colors.first, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = colors.second,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            text = action.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun webElementActionUi(
    action: BrowserWebElementAction,
    state: WebSessionWebElementActionState,
    onDismiss: () -> Unit,
    onOpenInNewTab: (String, Boolean) -> Unit,
    onCopyUrl: (String) -> Unit,
    onCopyText: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onSelectText: (Double, Double) -> Unit,
    onOpenImage: () -> Unit,
    onOpenImageMode: () -> Unit,
    onSaveImage: () -> Unit,
    onRecognizeQrCode: () -> Unit,
    onBlockElementQuick: () -> Unit,
    onBlockElementAdvanced: () -> Unit,
    onBlockUrl: (String) -> Unit,
): WebElementActionUi =
    when (action) {
        BrowserWebElementAction.OPEN_NEW_WINDOW ->
            WebElementActionUi(
                Icons.AutoMirrored.Filled.OpenInNew,
                "新窗口打开",
                "在前台新窗口打开链接",
                WebElementActionTone.PRIMARY,
            ) {
                state.browserOpenUrl()?.let { url ->
                    onDismiss()
                    onOpenInNewTab(url, true)
                }
            }
        BrowserWebElementAction.OPEN_BACKGROUND ->
            WebElementActionUi(
                Icons.Filled.AddBox,
                "后台打开",
                "保留当前网页并创建窗口",
                WebElementActionTone.PRIMARY,
            ) {
                state.browserOpenUrl()?.let { url ->
                    onDismiss()
                    onOpenInNewTab(url, false)
                }
            }
        BrowserWebElementAction.VIEW_IMAGE ->
            WebElementActionUi(
                Icons.Filled.Fullscreen,
                "全屏查看",
                "完整显示当前图片",
                WebElementActionTone.IMAGE,
                onOpenImage,
            )
        BrowserWebElementAction.SAVE_IMAGE ->
            WebElementActionUi(
                Icons.Filled.Download,
                "保存图片",
                "进入浏览器下载流程",
                WebElementActionTone.IMAGE,
                onSaveImage,
            )
        BrowserWebElementAction.IMAGE_MODE ->
            WebElementActionUi(
                Icons.Filled.Collections,
                "看图模式",
                "浏览当前网页中的图片",
                WebElementActionTone.IMAGE,
                onOpenImageMode,
            )
        BrowserWebElementAction.COPY_LINK ->
            WebElementActionUi(
                Icons.Filled.Link,
                "复制链接",
                "复制元素跳转链接",
                WebElementActionTone.COPY,
            ) {
                state.linkUrl?.let(onCopyUrl)
            }
        BrowserWebElementAction.COPY_IMAGE_LINK ->
            WebElementActionUi(
                Icons.Filled.Image,
                "复制图片链接",
                "复制当前图片资源地址",
                WebElementActionTone.COPY,
            ) {
                state.imageUrl()?.let(onCopyUrl)
            }
        BrowserWebElementAction.COPY_RESOURCE_LINK ->
            WebElementActionUi(
                Icons.Filled.Link,
                "复制资源链接",
                "复制当前资源地址",
                WebElementActionTone.COPY,
            ) {
                state.resourceUrl?.let(onCopyUrl)
            }
        BrowserWebElementAction.OPEN_EXTERNAL ->
            WebElementActionUi(
                Icons.Filled.Language,
                "外部打开",
                "交给其他应用处理",
                WebElementActionTone.PRIMARY,
            ) {
                state.externalOpenUrl()?.let { url ->
                    onDismiss()
                    onOpenExternal(url)
                }
            }
        BrowserWebElementAction.COPY_TEXT ->
            WebElementActionUi(
                Icons.Filled.ContentCopy,
                "复制文本",
                "复制当前元素文本",
                WebElementActionTone.COPY,
                onCopyText,
            )
        BrowserWebElementAction.SELECT_TEXT ->
            WebElementActionUi(
                Icons.Filled.SelectAll,
                "选择文本",
                "调整普通网页正文选区",
                WebElementActionTone.COPY,
            ) {
                onDismiss()
                onSelectText(state.clientX, state.clientY)
            }
        BrowserWebElementAction.RECOGNIZE_QR ->
            WebElementActionUi(
                Icons.Filled.QrCodeScanner,
                "识别二维码",
                "读取图片中的二维码内容",
                WebElementActionTone.IMAGE,
                onRecognizeQrCode,
            )
        BrowserWebElementAction.BLOCK_ELEMENT_QUICK ->
            WebElementActionUi(
                Icons.Filled.Block,
                "拦截元素",
                "立即保存当前元素规则",
                WebElementActionTone.BLOCK,
                onBlockElementQuick,
            )
        BrowserWebElementAction.BLOCK_ELEMENT_ADVANCED ->
            WebElementActionUi(
                Icons.Filled.TouchApp,
                "拦截网页元素",
                "进入标记工作台精细调整",
                WebElementActionTone.BLOCK,
                onBlockElementAdvanced,
            )
        BrowserWebElementAction.BLOCK_URL ->
            WebElementActionUi(
                Icons.Filled.LinkOff,
                "拦截过滤网址",
                "保存当前资源或跳转网址规则",
                WebElementActionTone.BLOCK,
            ) {
                state.blockableUrl()?.let { url ->
                    onDismiss()
                    onBlockUrl(url)
                }
            }
    }

@Composable
internal fun WebSessionQrCodeDialog(
    state: WebSessionQrCodeState,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.QrCodeScanner,
            tone = WebSessionBrowserMenuTone.NETWORK_LOG,
            title = "识别二维码",
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state.status) {
                    BrowserQrCodeUiStatus.LOADING -> {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text(
                            text = "正在读取图片并识别二维码",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BrowserQrCodeUiStatus.SUCCESS -> {
                        SelectionContainer {
                            Text(
                                text = state.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerLow,
                                            RoundedCornerShape(14.dp),
                                        )
                                        .padding(14.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onDismiss) { Text("关闭") }
                            TextButton(onClick = onCopy) { Text("复制内容") }
                            if (isHttpBrowserNetworkUrl(state.content)) {
                                TextButton(onClick = onOpen) { Text("打开网页") }
                            }
                        }
                    }
                    BrowserQrCodeUiStatus.IMAGE_LOAD_FAILED ->
                        Text(
                            text = "图片加载失败，无法识别二维码",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    BrowserQrCodeUiStatus.NOT_RECOGNIZED ->
                        Text(
                            text = "当前图片中没有识别到二维码",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
                if (state.status != BrowserQrCodeUiStatus.SUCCESS) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

private fun webElementTargetLabel(targetKind: BrowserWebElementTargetKind): String =
    when (targetKind) {
        BrowserWebElementTargetKind.IMAGE_LINK -> "图片链接"
        BrowserWebElementTargetKind.IMAGE -> "图片"
        BrowserWebElementTargetKind.LINK -> "网页链接"
        BrowserWebElementTargetKind.MEDIA_LINK -> "媒体链接"
        BrowserWebElementTargetKind.MEDIA -> "媒体资源"
        BrowserWebElementTargetKind.TEXT -> "网页文本"
        BrowserWebElementTargetKind.ELEMENT -> "网页元素"
    }

@Composable
internal fun WebSessionAdMarkingWorkbench(
    state: WebSessionAdMarkingState,
    onMove: (BrowserAdMarkingMove) -> Unit,
    onSetPreview: (Boolean) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
    onEditRule: () -> Unit,
    onOpenHtmlEditor: () -> Unit,
    onClearIntercept: () -> Unit,
    onOpenNavigationPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var infoTab by remember(state.sessionId, state.pageUrl) { mutableStateOf(AdMarkingInfoTab.RULE) }
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .height(WEB_SESSION_AD_MARKING_WORKBENCH_HEIGHT_DP.dp),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, outlineColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .height(44.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                AdMarkingTopButton(
                    label = "拦截规则",
                    selected = infoTab == AdMarkingInfoTab.RULE,
                    onClick = { infoTab = AdMarkingInfoTab.RULE },
                    modifier = Modifier.weight(1.7f),
                )
                AdMarkingTopButton(
                    label = "节点",
                    selected = infoTab == AdMarkingInfoTab.NODE,
                    onClick = { infoTab = AdMarkingInfoTab.NODE },
                    modifier = Modifier.weight(1f),
                )
                AdMarkingTopButton(
                    label = "HTML",
                    enabled = state.html.isNotBlank(),
                    onClick = onOpenHtmlEditor,
                    modifier = Modifier.weight(1.15f),
                )
                AdMarkingTopButton(
                    label = "父",
                    enabled = state.selector.isNotBlank(),
                    onClick = { onMove(BrowserAdMarkingMove.PARENT) },
                    modifier = Modifier.weight(0.78f),
                )
                AdMarkingTopButton(
                    label = "兄",
                    enabled = state.selector.isNotBlank(),
                    onClick = { onMove(BrowserAdMarkingMove.PREVIOUS_SIBLING) },
                    modifier = Modifier.weight(0.78f),
                )
                AdMarkingTopButton(
                    label = "弟",
                    enabled = state.selector.isNotBlank(),
                    onClick = { onMove(BrowserAdMarkingMove.NEXT_SIBLING) },
                    modifier = Modifier.weight(0.78f),
                )
                AdMarkingTopButton(
                    label = "子",
                    enabled = state.selector.isNotBlank(),
                    onClick = { onMove(BrowserAdMarkingMove.FIRST_CHILD) },
                    modifier = Modifier.weight(0.78f),
                )
                IconButton(onClick = onExit, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭标记广告",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
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
                        AdMarkingInfoTab.NODE ->
                            "节点 HTML · <${state.tagName.ifBlank { "node" }}>"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        text =
                            when (infoTab) {
                                AdMarkingInfoTab.RULE ->
                                    state.selector.ifBlank { "点击网页中的任意元素开始标记" }
                                AdMarkingInfoTab.NODE ->
                                    state.html.ifBlank { "尚未捕获当前节点 HTML" }
                            },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        fontFamily = FontFamily.Monospace,
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
            if (infoTab == AdMarkingInfoTab.RULE) {
                HorizontalDivider(color = outlineColor)
                Row(
                    modifier =
                        Modifier
                            .height(WEB_SESSION_BROWSER_BOTTOM_CONTENT_HEIGHT_DP.dp)
                            .fillMaxWidth()
                            .padding(
                                horizontal = 4.dp,
                                vertical = WEB_SESSION_AD_MARKING_FOOTER_VERTICAL_PADDING_DP.dp,
                            ),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AdMarkingFooterButton(
                        label = "保存规则",
                        enabled = state.selector.isNotBlank() && state.domain.isNotBlank(),
                        emphasized = true,
                        onClick = onSave,
                        modifier = Modifier.weight(1.05f),
                    )
                    AdMarkingFooterButton(
                        label = "编辑规则",
                        enabled = state.selector.isNotBlank(),
                        onClick = onEditRule,
                        modifier = Modifier.weight(1.05f),
                    )
                    AdMarkingFooterButton(
                        label = if (state.previewing) "恢复" else "预览",
                        enabled = state.selector.isNotBlank(),
                        selected = state.previewing,
                        onClick = { onSetPreview(!state.previewing) },
                        modifier = Modifier.weight(0.72f),
                    )
                    AdMarkingFooterButton(
                        label = "重置",
                        onClick = onReset,
                        modifier = Modifier.weight(0.72f),
                    )
                    AdMarkingFooterButton(
                        label = "清除拦截",
                        enabled = state.domain.isNotBlank(),
                        onClick = onClearIntercept,
                        modifier = Modifier.weight(1.18f),
                    )
                    AdMarkingFooterButton(
                        label = "拦截网站跳转",
                        selected =
                            state.navigationPolicy !=
                                BrowserAdMarkingNavigationPolicy.DEFAULT,
                        onClick = onOpenNavigationPolicy,
                        modifier = Modifier.weight(1.68f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdMarkingTopButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val toneColors = WebSessionBrowserMenuTone.AD_MARKING.resolveColors()
    Surface(
        modifier = modifier.height(38.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color =
            if (selected) {
                toneColors.container
            } else {
                MaterialTheme.colorScheme.surface
            },
        contentColor =
            if (selected) {
                toneColors.icon
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color =
                    if (enabled) {
                        androidx.compose.ui.graphics.Color.Unspecified
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AdMarkingFooterButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    emphasized: Boolean = false,
) {
    val toneColors = WebSessionBrowserMenuTone.AD_MARKING.resolveColors()
    val active = selected || emphasized
    Surface(
        modifier =
            modifier
                .height(WEB_SESSION_AD_MARKING_FOOTER_BUTTON_HEIGHT_DP.dp)
                .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color =
            if (active) {
                toneColors.container
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
            },
        contentColor =
            if (active) {
                toneColors.icon
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        border =
            BorderStroke(
                0.6.dp,
                if (enabled) {
                    if (active) {
                        toneColors.icon.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                },
            ),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 12.sp,
                color =
                    if (enabled) {
                        androidx.compose.ui.graphics.Color.Unspecified
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                    },
            )
        }
    }
}

private enum class AdMarkingInfoTab {
    RULE,
    NODE,
}

@Composable
internal fun WebSessionAdMarkingRuleEditor(
    ruleDraft: String,
    onRuleDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
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
                    text = "支持 hiker 路径规则和标准 CSS selector。预览或保存只更新当前工作台，仍需点击“保存规则”才会持久化。",
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
                    label = { Text("拦截规则") },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = onPreview,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 11.dp),
                    ) {
                        Text("预览")
                    }
                    TextButton(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 11.dp),
                    ) {
                        Text(
                            "保存",
                            color = WebSessionBrowserMenuTone.AD_MARKING.resolveColors().icon,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WebSessionAdMarkingClearConfirmation(
    domain: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WebSessionBrowserModalDialog(onDismissRequest = onDismiss) {
        WebSessionBrowserDialogSurface(
            icon = Icons.Filled.Block,
            tone = WebSessionBrowserMenuTone.AD_MARKING,
            title = "温馨提示",
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text =
                        "确定要清除 ${domain.ifBlank { "当前网站" }} 下的所有自定义广告拦截规则吗？",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "当前网页域名",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = domain.ifBlank { "未识别当前网站域名" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    "确认后将立即删除该域名下由你添加的元素规则和明确指向该域名的网址规则，原来标记的广告会重新显示。订阅规则和无域名范围的全局规则不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 11.dp),
                    ) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 11.dp),
                    ) {
                        Text(
                            "确定",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WebSessionAdMarkingNavigationPolicySheet(
    selectedPolicy: BrowserAdMarkingNavigationPolicy,
    onDismiss: () -> Unit,
    onSelect: (BrowserAdMarkingNavigationPolicy) -> Unit,
) {
    KiyoriModalBottomDrawer(onDismissRequest = onDismiss) { dismissDrawer ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "拦截网站跳转",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Text(
                text = "退出“标记广告”后，仅处理当前网页发起的跨域跳转；站内链接仍正常打开。标记模式本身始终禁止任何跳转。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            browserAdMarkingSelectablePolicies().forEach { policy ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(policy)
                                dismissDrawer()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
                    Text(
                        text = if (policy == selectedPolicy) "✓" else "",
                        modifier = Modifier.size(22.dp),
                        color = WebSessionBrowserMenuTone.AD_MARKING.resolveColors().icon,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            TextButton(
                onClick = dismissDrawer,
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
        BrowserAdMarkingNavigationPolicy.DEFAULT -> "默认允许"
        BrowserAdMarkingNavigationPolicy.ASK -> "跳转前询问"
        BrowserAdMarkingNavigationPolicy.BLOCK -> "拦截跳转"
    }

private fun navigationPolicyDescription(policy: BrowserAdMarkingNavigationPolicy): String =
    when (policy) {
        BrowserAdMarkingNavigationPolicy.DEFAULT ->
            "正常浏览时允许站内与跨域链接按网页原有行为跳转"
        BrowserAdMarkingNavigationPolicy.ASK ->
            "正常浏览点击跨域链接时先显示确认弹窗"
        BrowserAdMarkingNavigationPolicy.BLOCK ->
            "正常浏览时阻止跨域链接，点击后不执行任何跳转"
    }
