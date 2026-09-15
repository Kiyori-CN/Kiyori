package com.ai.assistance.operit.ui.features.websession.browser

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_READER_EXTRACTION_SCRIPT
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_READER_FONT_SCALES
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_READER_MAX_CHARACTERS
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserReaderArticle
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserReaderBlock
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserReaderBlockKind
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserReaderFontScaleIndex
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserReaderSpeechText
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.parseBrowserReaderArticle
import com.ai.assistance.operit.ui.common.rememberLocal

// 阅读模式固定白底黑字，不跟随应用主题：整页目的就是把网页排版换成一份稳定的长文版式，
// 深色反色会让同一次阅读在页面之间跳变。
private val READER_BACKGROUND = Color(0xFFFFFFFF)
private val READER_SURFACE = Color(0xFFF5F6F7)
private val READER_TEXT = Color(0xFF16181C)
private val READER_SECONDARY_TEXT = Color(0xFF6B7078)
private val READER_DIVIDER = Color(0xFFE6E8EB)
private val READER_ACCENT = Color(0xFF2B6BE4)

private const val READER_BODY_LINE_HEIGHT_RATIO = 1.85f
private val READER_CONTENT_MAX_WIDTH = 720.dp
private val READER_TOP_BAR_HEIGHT = 52.dp

/**
 * 阅读模式整页。
 *
 * 用全屏 Dialog 承载而不是内联在浏览器布局里：浏览器界面保留 WebView 与自身的返回栈，
 * 阅读页需要独占系统 Back 且不能被网页滚动影响，与共享图片查看层是同一套宿主方式。
 */
@Composable
internal fun BrowserReaderScreen(
    webView: WebView,
    documentKey: String,
    tools: BrowserPageTools,
    startSpeaking: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val siteLabel = remember(tools.url) {
        runCatching { Uri.parse(tools.url).host.orEmpty() }.getOrDefault("").ifBlank { tools.url }
    }
    var article by remember(documentKey) { mutableStateOf<BrowserReaderArticle?>(null) }
    var extractionAttempt by remember(documentKey) { mutableIntStateOf(0) }
    var fontScale by rememberLocal("browser_reader_font_scale", 1.0f)
    var fontPanelVisible by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    var speechEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var speechReady by remember { mutableStateOf(false) }

    // 提取结果只在仍然是同一个 WebView 与同一份文档时才落地；离页后的晚到回调被丢弃。
    DisposableEffect(webView, documentKey, extractionAttempt) {
        var active = true
        webView.evaluateJavascript(BROWSER_READER_EXTRACTION_SCRIPT) { raw ->
            if (!active) return@evaluateJavascript
            if (!tools.host.isAssignedTo(webView) || tools.documentKey != documentKey) return@evaluateJavascript
            article = parseBrowserReaderArticle(
                raw = raw,
                fallbackTitle = tools.title.ifBlank { siteLabel },
                siteLabel = siteLabel,
            )
        }
        onDispose { active = false }
    }

    // 朗读引擎随页面生命周期持有；退出阅读页立即停止并释放，不留后台朗读。
    DisposableEffect(Unit) {
        var active = true
        val engine = TextToSpeech(context) { status ->
            if (active) speechReady = status == TextToSpeech.SUCCESS
        }
        speechEngine = engine
        onDispose {
            active = false
            speechEngine = null
            engine.stop()
            engine.shutdown()
        }
    }

    fun stopSpeech() {
        speaking = false
        speechEngine?.stop()
    }

    fun startSpeech() {
        val engine = speechEngine
        val current = article
        if (engine == null || current == null || current.isEmpty) return
        if (!speechReady) {
            speechError = context.getString(R.string.web_session_reader_speak_unavailable)
            return
        }
        speechError = null
        engine.stop()
        val chunkSize = (TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtLeast(1)
        var failed = false
        browserReaderSpeechText(current).chunked(chunkSize).forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (engine.speak(chunk, mode, null, "reader-$index") == TextToSpeech.ERROR) failed = true
        }
        if (failed) {
            speaking = false
            speechError = context.getString(R.string.web_session_reader_speak_failed)
        } else {
            speaking = true
        }
    }

    // 从工具箱的“网页朗读”进入时直接开始朗读；正文还在提取时等待结果到达再启动。
    LaunchedEffect(startSpeaking, speechReady, article) {
        if (startSpeaking && speechReady && !speaking && speechError == null) {
            val current = article
            if (current != null && !current.isEmpty) startSpeech()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogWindow = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect {
            // 整页不透明白底，平台 dim 只会在状态栏区域压出一层灰边。
            dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow.setBackgroundDrawable(AndroidColor.WHITE.toDrawable())
            val insetsController = WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
        }
        BackHandler {
            stopSpeech()
            onDismiss()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(READER_BACKGROUND)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val listState = rememberLazyListState()
            val readProgress by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val total = info.totalItemsCount
                    if (total <= 0) {
                        0f
                    } else {
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        ((last + 1).toFloat() / total).coerceIn(0f, 1f)
                    }
                }
            }
            val animatedProgress by animateFloatAsState(
                targetValue = readProgress,
                label = "reader-progress",
            )

            BrowserReaderTopBar(
                siteLabel = article?.siteLabel?.ifBlank { siteLabel } ?: siteLabel,
                speaking = speaking,
                speakEnabled = article?.isEmpty == false,
                fontPanelVisible = fontPanelVisible,
                onBack = {
                    stopSpeech()
                    onDismiss()
                },
                onToggleFontPanel = { fontPanelVisible = !fontPanelVisible },
                onToggleSpeech = { if (speaking) stopSpeech() else startSpeech() },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(READER_DIVIDER)
                    .semantics {
                        contentDescription = context.getString(
                            R.string.web_session_reader_progress,
                            (animatedProgress * 100).toInt(),
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(2.dp)
                        .background(READER_ACCENT),
                )
            }

            AnimatedVisibility(
                visible = fontPanelVisible,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                BrowserReaderFontPanel(
                    scale = fontScale,
                    onScaleChange = { fontScale = it },
                )
            }

            speechError?.let { message ->
                BrowserReaderNotice(message = message, onDismiss = { speechError = null })
            }

            val current = article
            when {
                current == null -> BrowserReaderStatus(
                    title = stringResource(R.string.web_session_reader_extracting),
                    description = null,
                    showProgress = true,
                )
                current.failed -> BrowserReaderStatus(
                    title = stringResource(R.string.web_session_reader_failed_title),
                    description = stringResource(R.string.web_session_reader_failed_hint),
                    actionLabel = stringResource(R.string.web_session_reader_retry),
                    onAction = {
                        article = null
                        extractionAttempt += 1
                    },
                )
                current.isEmpty -> BrowserReaderStatus(
                    title = stringResource(R.string.web_session_reader_empty_title),
                    description = stringResource(R.string.web_session_reader_empty_hint),
                    actionLabel = stringResource(R.string.web_session_reader_retry),
                    onAction = {
                        article = null
                        extractionAttempt += 1
                    },
                )
                else -> BrowserReaderContent(
                    article = current,
                    fontScale = fontScale,
                    listState = listState,
                )
            }
        }
    }
}

@Composable
private fun BrowserReaderTopBar(
    siteLabel: String,
    speaking: Boolean,
    speakEnabled: Boolean,
    fontPanelVisible: Boolean,
    onBack: () -> Unit,
    onToggleFontPanel: () -> Unit,
    onToggleSpeech: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(READER_TOP_BAR_HEIGHT)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.web_session_reader_close),
                tint = READER_TEXT,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = stringResource(R.string.web_session_reader_mode),
                color = READER_TEXT,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (siteLabel.isNotBlank()) {
                Text(
                    text = siteLabel,
                    color = READER_SECONDARY_TEXT,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggleFontPanel, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Outlined.FormatSize,
                contentDescription = stringResource(R.string.web_session_reader_font_size),
                tint = if (fontPanelVisible) READER_ACCENT else READER_TEXT,
                modifier = Modifier.size(21.dp),
            )
        }
        IconButton(
            onClick = onToggleSpeech,
            enabled = speakEnabled,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = if (speaking) Icons.Outlined.Stop else Icons.Outlined.RecordVoiceOver,
                contentDescription = stringResource(
                    if (speaking) {
                        R.string.web_session_reader_speak_stop
                    } else {
                        R.string.web_session_reader_speak_start
                    },
                ),
                tint = when {
                    !speakEnabled -> READER_SECONDARY_TEXT.copy(alpha = 0.4f)
                    speaking -> READER_ACCENT
                    else -> READER_TEXT
                },
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun BrowserReaderFontPanel(scale: Float, onScaleChange: (Float) -> Unit) {
    val index = browserReaderFontScaleIndex(scale)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(READER_SURFACE)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.web_session_reader_font_size),
            color = READER_SECONDARY_TEXT,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onScaleChange(BROWSER_READER_FONT_SCALES[(index - 1).coerceAtLeast(0)]) },
            enabled = index > 0,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Remove,
                contentDescription = stringResource(R.string.web_session_reader_font_smaller),
                tint = if (index > 0) READER_TEXT else READER_SECONDARY_TEXT.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = stringResource(
                R.string.web_session_reader_font_scale,
                (BROWSER_READER_FONT_SCALES[index] * 100).toInt(),
            ),
            color = READER_TEXT,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 48.dp),
        )
        IconButton(
            onClick = {
                onScaleChange(
                    BROWSER_READER_FONT_SCALES[(index + 1).coerceAtMost(BROWSER_READER_FONT_SCALES.lastIndex)],
                )
            },
            enabled = index < BROWSER_READER_FONT_SCALES.lastIndex,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.web_session_reader_font_larger),
                tint = if (index < BROWSER_READER_FONT_SCALES.lastIndex) {
                    READER_TEXT
                } else {
                    READER_SECONDARY_TEXT.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(18.dp),
            )
        }
        TextButton(onClick = { onScaleChange(1.0f) }, enabled = index != BROWSER_READER_FONT_SCALES.indexOf(1.0f)) {
            Text(
                text = stringResource(R.string.web_session_reader_font_reset),
                color = READER_ACCENT,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun BrowserReaderNotice(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(READER_SURFACE)
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = READER_SECONDARY_TEXT,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            color = READER_TEXT,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(R.string.close), color = READER_ACCENT, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BrowserReaderStatus(
    title: String,
    description: String?,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = READER_ACCENT,
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
        Text(
            text = title,
            color = READER_TEXT,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                color = READER_SECONDARY_TEXT,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onAction) {
                Text(text = actionLabel, color = READER_ACCENT, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BrowserReaderContent(
    article: BrowserReaderArticle,
    fontScale: Float,
    listState: LazyListState,
) {
    val bodySize = (17f * fontScale).sp
    val bodyLineHeight = (17f * fontScale * READER_BODY_LINE_HEIGHT_RATIO).sp
    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "reader-header") {
                Column(modifier = Modifier.widthIn(max = READER_CONTENT_MAX_WIDTH).fillMaxWidth()) {
                    Text(
                        text = article.title,
                        color = READER_TEXT,
                        fontSize = (25f * fontScale).sp,
                        lineHeight = (25f * fontScale * 1.35f).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = listOfNotNull(
                            article.byline.takeIf { it.isNotBlank() },
                            stringResource(
                                R.string.web_session_reader_meta,
                                article.estimatedMinutes,
                                formatReaderCharacterCount(article.characterCount),
                            ),
                        ).joinToString(separator = " · "),
                        color = READER_SECONDARY_TEXT,
                        fontSize = 12.sp,
                    )
                    if (article.truncated) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.web_session_reader_truncated,
                                formatReaderCharacterCount(BROWSER_READER_MAX_CHARACTERS),
                            ),
                            color = READER_SECONDARY_TEXT,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(READER_DIVIDER),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            items(
                count = article.blocks.size,
                key = { index -> "reader-block-$index" },
            ) { index ->
                BrowserReaderBlockView(
                    block = article.blocks[index],
                    fontScale = fontScale,
                    bodySize = bodySize.value,
                    bodyLineHeight = bodyLineHeight.value,
                )
            }

            item(key = "reader-end") {
                Column(
                    modifier = Modifier.widthIn(max = READER_CONTENT_MAX_WIDTH).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(26.dp))
                    Text(
                        text = "— " + stringResource(R.string.web_session_reader_end) + " —",
                        color = READER_SECONDARY_TEXT,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserReaderBlockView(
    block: BrowserReaderBlock,
    fontScale: Float,
    bodySize: Float,
    bodyLineHeight: Float,
) {
    val container = Modifier.widthIn(max = READER_CONTENT_MAX_WIDTH).fillMaxWidth()
    when (block.kind) {
        BrowserReaderBlockKind.HEADING_1,
        BrowserReaderBlockKind.HEADING_2,
        BrowserReaderBlockKind.HEADING_3,
        -> {
            val headingSize = when (block.kind) {
                BrowserReaderBlockKind.HEADING_1 -> 21f
                BrowserReaderBlockKind.HEADING_2 -> 19f
                else -> 17f
            } * fontScale
            Column(modifier = container) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = block.text,
                    color = READER_TEXT,
                    fontSize = headingSize.sp,
                    lineHeight = (headingSize * 1.45f).sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        BrowserReaderBlockKind.LIST_ITEM -> {
            Row(modifier = container.padding(bottom = 10.dp)) {
                Text(
                    text = "·",
                    color = READER_SECONDARY_TEXT,
                    fontSize = bodySize.sp,
                    lineHeight = bodyLineHeight.sp,
                    modifier = Modifier.width(16.dp),
                )
                Text(
                    text = block.text,
                    color = READER_TEXT,
                    fontSize = bodySize.sp,
                    lineHeight = bodyLineHeight.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        BrowserReaderBlockKind.QUOTE -> {
            Row(modifier = container.padding(bottom = 14.dp).height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(READER_DIVIDER),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = block.text,
                    color = READER_SECONDARY_TEXT,
                    fontSize = bodySize.sp,
                    lineHeight = bodyLineHeight.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        BrowserReaderBlockKind.CODE -> {
            Box(
                modifier = container
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(READER_SURFACE)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = block.text,
                    color = READER_TEXT,
                    fontSize = (bodySize * 0.88f).sp,
                    lineHeight = (bodySize * 1.5f).sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        BrowserReaderBlockKind.PARAGRAPH -> {
            Text(
                text = block.text,
                color = READER_TEXT,
                fontSize = bodySize.sp,
                lineHeight = bodyLineHeight.sp,
                modifier = container.padding(bottom = 14.dp),
            )
        }
    }
}

/** 千分位只做展示分组，不引入区域设置相关的数字格式差异。 */
private fun formatReaderCharacterCount(count: Int): String =
    count.toString().reversed().chunked(3).joinToString(",").reversed()
