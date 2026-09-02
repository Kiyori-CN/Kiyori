package com.ai.assistance.operit.ui.common.markdown

import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.ui.common.copyPlainTextToClipboard
import com.ai.assistance.operit.ui.common.gestures.rememberAiContentHorizontalGestureOwner
import com.ai.assistance.operit.util.RenderProcessSafeWebViewClient
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ai.assistance.operit.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CodeBlockPreviewType {
    MERMAID,
    HTML
}

/**
 * 增强型代码块组件
 *
 * 具有以下功能：
 * 1. 代码语法高亮
 * 2. 复制按钮
 * 3. 行号显示
 * 4. 夜间模式风格
 * 5. 行渲染保护（使用key机制避免重复渲染）
 * 6. 记忆优化（减少不必要的重组）
 * 7. 高效处理流式更新
 * 8. Mermaid图表支持
 */
@Composable
fun EnhancedCodeBlock(code: String, language: String = "", modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCopiedToast by remember { mutableStateOf(false) }
    var autoWrapEnabled by remember { mutableStateOf(true) }
    val markdownRenderMode = LocalMarkdownRenderMode.current
    val preferStreamingBody = markdownRenderMode == MarkdownRenderMode.STREAMING

    // 检测是否为Mermaid代码
    val isMermaid = language.equals("mermaid", ignoreCase = true)

    // 检测是否为HTML代码
    val isHtml = language.equals("html", ignoreCase = true) || language.equals("htm", ignoreCase = true)

    // Mermaid渲染状态
    var showRenderedMermaid by remember { mutableStateOf(false) }

    // HTML预览状态
    var showRenderedHtml by remember { mutableStateOf(false) }

    var showFullscreenPreview by remember { mutableStateOf(false) }
    var fullscreenPreviewType by remember { mutableStateOf<CodeBlockPreviewType?>(null) }

    val configuration = LocalConfiguration.current
    val maxScrollableHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp.dp * 0.5f).coerceIn(240.dp, 560.dp)
    }

    val isPreviewMode = (isMermaid && showRenderedMermaid) || (isHtml && showRenderedHtml)

    // 处理复制事件
    val handleCopy: () -> Unit = {
        context.copyPlainTextToClipboard("Kiyori code", code)
        scope.launch {
            showCopiedToast = true
            delay(1500)
            showCopiedToast = false
        }
    }

    // 处理Mermaid渲染切换
    val handleToggleMermaid: () -> Unit = { showRenderedMermaid = !showRenderedMermaid }

    // 处理HTML预览切换
    val handleToggleHtml: () -> Unit = { showRenderedHtml = !showRenderedHtml }

    // 暗色代码块背景颜色
    val codeBlockBackground = Color(0xFF1E1E1E) // VS Code 暗色主题背景色
    val toolbarBackground = Color(0xFF252526) // 比背景稍亮一点的颜色

    // 直接从 `code` prop 派生行列表。
    // 这种方法比使用 LaunchedEffect 和 mutableStateListOf 更稳定，
    // 可以防止因状态更新时序问题而导致的双重渲染。
    val codeLines = remember(code) { code.lines() }

    // 缓存已计算过的行，避免重复创建
    val lineCache = remember { mutableMapOf<String, AnnotatedString>() }
    val highlightedLines =
        remember(codeLines, language, preferStreamingBody) {
            if (preferStreamingBody) {
                emptyList()
            } else {
                codeLines.map { line ->
                    val cacheKey = "$language:$line"
                    lineCache[cacheKey] ?: highlightSyntaxLine(line, language).also {
                        lineCache[cacheKey] = it
                    }
                }
            }
        }

    // 无障碍朗读描述：只朗读块类型
    val accessibilityDesc = if (language.isNotEmpty()) {
        "$language ${stringResource(R.string.code_block)}"
    } else {
        stringResource(R.string.code_block)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics { contentDescription = accessibilityDesc },
        color = codeBlockBackground,
        shape = RoundedCornerShape(4.dp)
    ) {
        Column {
            // 顶部工具栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(toolbarBackground)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 语言标记（如果有）
                if (language.isNotEmpty()) {
                    Text(
                        text = language,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAAAAAA),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // 工具栏按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Mermaid渲染按钮（如果是Mermaid图表则显示）
                    if (isMermaid) {
                        IconButton(
                            onClick = handleToggleMermaid,
                            modifier = Modifier.size(40.dp),
                            enabled = !preferStreamingBody,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription =
                                    if (showRenderedMermaid) stringResource(R.string.common_show_code) else stringResource(R.string.common_render_mermaid),
                                tint =
                                    if (preferStreamingBody)
                                        Color(0xFF666666)
                                    else if (showRenderedMermaid)
                                        MaterialTheme.colorScheme.primary
                                    else Color(0xFFAAAAAA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // HTML预览按钮（如果是HTML则显示）
                    if (isHtml) {
                        IconButton(
                            onClick = handleToggleHtml,
                            modifier = Modifier.size(40.dp),
                            enabled = !preferStreamingBody,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = if (showRenderedHtml) stringResource(R.string.common_show_code) else stringResource(R.string.common_preview_html),
                                tint =
                                    if (preferStreamingBody)
                                        Color(0xFF666666)
                                    else if (showRenderedHtml)
                                        MaterialTheme.colorScheme.primary
                                    else Color(0xFFAAAAAA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (isPreviewMode) {
                        IconButton(
                            onClick = {
                                fullscreenPreviewType =
                                    when {
                                        isMermaid && showRenderedMermaid -> CodeBlockPreviewType.MERMAID
                                        isHtml && showRenderedHtml -> CodeBlockPreviewType.HTML
                                        else -> null
                                    }
                                if (fullscreenPreviewType != null) {
                                    showFullscreenPreview = true
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.common_fullscreen_preview),
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { autoWrapEnabled = !autoWrapEnabled },
                        modifier = Modifier.size(40.dp),
                        enabled = !isPreviewMode
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = if (autoWrapEnabled) stringResource(R.string.common_disable_autowrap) else stringResource(R.string.common_enable_autowrap),
                            tint = if (!autoWrapEnabled) MaterialTheme.colorScheme.primary else Color(
                                0xFFAAAAAA
                            ),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // 复制按钮
                    IconButton(onClick = handleCopy, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.common_copy_code),
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 代码内容
            if (isMermaid && showRenderedMermaid) {
                // 渲染Mermaid图表
                MermaidRenderer(code = code, modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp))
            } else if (isHtml && showRenderedHtml) {
                HtmlPreviewRenderer(
                    code = code,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                )
            } else {
                CanvasMonospaceCodeBlockBody(
                    code = code,
                    codeLines = codeLines,
                    highlightedLines = if (preferStreamingBody) null else highlightedLines,
                    autoWrapEnabled = autoWrapEnabled,
                    maxScrollableHeight = maxScrollableHeight,
                )
            }

            if (showCopiedToast) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(4.dp),
                    color = Color(0xFF0366D6),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_copied),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    if (showFullscreenPreview) {
        Dialog(
            onDismissRequest = {
                showFullscreenPreview = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (fullscreenPreviewType) {
                        CodeBlockPreviewType.MERMAID -> MermaidRenderer(code = code, modifier = Modifier.fillMaxSize())
                        CodeBlockPreviewType.HTML -> HtmlPreviewRenderer(code = code, modifier = Modifier.fillMaxSize())
                        null -> Unit
                    }

                    IconButton(
                        onClick = { showFullscreenPreview = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_exit_fullscreen),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

internal fun quoteMermaidSourceForJavaScript(source: String): String {
    return buildString(source.length + 2) {
        append('"')
        source.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '<' -> append("\\u003c")
                '>' -> append("\\u003e")
                '&' -> append("\\u0026")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }
}

internal fun buildMermaidPreviewHtml(code: String): String {
    // The caller supplies the already-normalized fenced payload.  Preserve its
    // leading/trailing whitespace so Mermaid receives exactly the same source as
    // the code card and copy action.
    val quotedSource = quoteMermaidSourceForJavaScript(code)
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>Mermaid Diagram</title>
            <script src="https://cdn.jsdelivr.net/npm/mermaid@10.6.1/dist/mermaid.min.js"></script>
            <style>
                html, body {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    overflow: hidden;
                    background: #1E1E1E;
                }
                #diagram-viewport {
                    position: absolute;
                    inset: 0;
                    overflow: auto;
                    overscroll-behavior: contain;
                    touch-action: pan-x pan-y;
                    -webkit-overflow-scrolling: touch;
                }
                #diagram-wrapper {
                    position: relative;
                    min-width: 100%;
                    min-height: 100%;
                }
                #diagram {
                    position: absolute;
                    display: block;
                    transform-origin: 0 0;
                    font-family: 'Courier New', Courier, monospace;
                    font-size: 14px;
                }
                #diagram svg {
                    display: block;
                    max-width: none !important;
                }
                #render-error {
                    box-sizing: border-box;
                    padding: 16px;
                    color: #FFB4AB;
                    white-space: pre-wrap;
                    overflow-wrap: anywhere;
                }
                .zoom-controls {
                    position: fixed;
                    right: 10px;
                    bottom: 10px;
                    z-index: 10;
                    display: flex;
                    flex-direction: column;
                    padding: 4px;
                    border-radius: 4px;
                    background: rgba(30, 30, 30, 0.78);
                    touch-action: manipulation;
                }
                .zoom-btn {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    width: 30px;
                    height: 30px;
                    margin: 2px;
                    border: 0;
                    border-radius: 3px;
                    background: #383838;
                    color: #E6E1E5;
                    font-size: 18px;
                }
            </style>
        </head>
        <body>
            <div id="diagram-viewport">
                <div id="diagram-wrapper">
                    <div id="diagram"></div>
                    <div id="render-error" hidden></div>
                </div>
            </div>
            <div class="zoom-controls">
                <button class="zoom-btn" type="button" onclick="zoomBy(0.2)">+</button>
                <button class="zoom-btn" type="button" onclick="zoomBy(-0.2)">−</button>
                <button class="zoom-btn" type="button" onclick="resetZoom()">↺</button>
            </div>
            <script>
                const diagramSource = $quotedSource;
                const viewport = document.getElementById('diagram-viewport');
                const wrapper = document.getElementById('diagram-wrapper');
                const diagram = document.getElementById('diagram');
                const renderError = document.getElementById('render-error');
                const contentPadding = 16;
                let scale = 1.0;
                let baseWidth = 0;
                let baseHeight = 0;

                function applyZoom() {
                    if (baseWidth <= 0 || baseHeight <= 0) return;
                    const scaledWidth = baseWidth * scale;
                    const scaledHeight = baseHeight * scale;
                    const left = Math.max(contentPadding, (viewport.clientWidth - scaledWidth) / 2);
                    const top = Math.max(contentPadding, (viewport.clientHeight - scaledHeight) / 2);
                    diagram.style.left = left + 'px';
                    diagram.style.top = top + 'px';
                    diagram.style.transform = 'scale(' + scale + ')';
                    wrapper.style.width =
                        Math.max(viewport.clientWidth, left + scaledWidth + contentPadding) + 'px';
                    wrapper.style.height =
                        Math.max(viewport.clientHeight, top + scaledHeight + contentPadding) + 'px';
                }

                function setScale(nextScale) {
                    if (baseWidth <= 0 || baseHeight <= 0) return;
                    const oldScale = scale;
                    const oldLeft = parseFloat(diagram.style.left) || contentPadding;
                    const oldTop = parseFloat(diagram.style.top) || contentPadding;
                    const contentCenterX =
                        (viewport.scrollLeft + viewport.clientWidth / 2 - oldLeft) / oldScale;
                    const contentCenterY =
                        (viewport.scrollTop + viewport.clientHeight / 2 - oldTop) / oldScale;
                    scale = Math.min(Math.max(nextScale, 0.5), 3.0);
                    applyZoom();
                    const nextLeft = parseFloat(diagram.style.left) || contentPadding;
                    const nextTop = parseFloat(diagram.style.top) || contentPadding;
                    viewport.scrollTo(
                        nextLeft + contentCenterX * scale - viewport.clientWidth / 2,
                        nextTop + contentCenterY * scale - viewport.clientHeight / 2
                    );
                }

                function zoomBy(delta) {
                    setScale(scale + delta);
                }

                function resetZoom() {
                    setScale(1.0);
                }

                function measureRenderedDiagram() {
                    const svg = diagram.querySelector('svg');
                    if (!svg) {
                        throw new Error('Mermaid did not produce an SVG element.');
                    }
                    const viewBox = svg.viewBox && svg.viewBox.baseVal;
                    const bounds = svg.getBBox();
                    baseWidth = Math.max(
                        1,
                        Math.ceil((viewBox && viewBox.width) || bounds.width || svg.scrollWidth)
                    );
                    baseHeight = Math.max(
                        1,
                        Math.ceil((viewBox && viewBox.height) || bounds.height || svg.scrollHeight)
                    );
                    svg.setAttribute('width', String(baseWidth));
                    svg.setAttribute('height', String(baseHeight));
                    svg.style.width = baseWidth + 'px';
                    svg.style.height = baseHeight + 'px';
                    svg.style.maxWidth = 'none';
                    applyZoom();
                    document.body.dataset.renderState = 'ready';
                }

                function showRenderError(error) {
                    diagram.hidden = true;
                    renderError.hidden = false;
                    renderError.textContent =
                        'Mermaid render failed: ' + (error && error.message ? error.message : String(error));
                    document.body.dataset.renderState = 'error';
                    console.error(error);
                }

                mermaid.initialize({
                    startOnLoad: false,
                    theme: 'dark',
                    securityLevel: 'loose',
                    flowchart: {
                        htmlLabels: true,
                        useMaxWidth: false
                    }
                });
                diagram.textContent = diagramSource;
                mermaid.run({ nodes: [diagram] })
                    .then(function() {
                        requestAnimationFrame(function() {
                            try {
                                measureRenderedDiagram();
                            } catch (error) {
                                showRenderError(error);
                            }
                        });
                    })
                    .catch(showRenderError);

                let pinchStartDistance = 0;
                let pinchStartScale = 1.0;
                viewport.addEventListener('touchstart', function(event) {
                    if (event.touches.length === 2) {
                        pinchStartDistance = Math.hypot(
                            event.touches[0].clientX - event.touches[1].clientX,
                            event.touches[0].clientY - event.touches[1].clientY
                        );
                        pinchStartScale = scale;
                        event.preventDefault();
                    }
                }, { passive: false });
                viewport.addEventListener('touchmove', function(event) {
                    if (event.touches.length === 2 && pinchStartDistance > 0) {
                        const distance = Math.hypot(
                            event.touches[0].clientX - event.touches[1].clientX,
                            event.touches[0].clientY - event.touches[1].clientY
                        );
                        setScale(pinchStartScale * distance / pinchStartDistance);
                        event.preventDefault();
                    }
                }, { passive: false });
                viewport.addEventListener('touchend', function(event) {
                    if (event.touches.length < 2) {
                        pinchStartDistance = 0;
                    }
                }, { passive: true });
                window.addEventListener('resize', applyZoom);
            </script>
        </body>
        </html>
    """.trimIndent()
}

/** Mermaid图表渲染组件 */
@Composable
fun MermaidRenderer(code: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val horizontalGestureOwner = rememberAiContentHorizontalGestureOwner()
    val htmlContent = remember(code) { buildMermaidPreviewHtml(code) }

    // 记住WebView实例以便重用
    val webView = remember(context, horizontalGestureOwner) {
        WebView(context).apply {
            // 基本设置
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true // 允许DOM存储
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadWithOverviewMode = false
            settings.useWideViewPort = true

            // 禁用WebView内置缩放：Mermaid 使用页面内自定义缩放/拖拽，避免出现二级缩放
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            // 设置混合内容模式
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // 设置WebViewClient来拦截事件
            webViewClient =
                object : RenderProcessSafeWebViewClient("MermaidRenderer") {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView,
                        request: android.webkit.WebResourceRequest
                    ): Boolean {
                        // 拦截所有URL导航，保持在当前WebView内
                        return true
                    }

                    override fun onPageFinished(view: android.webkit.WebView, url: String) {
                        super.onPageFinished(view, url)
                        // 页面加载完成后，可以在这里执行JavaScript
                        view.evaluateJavascript(
                            """
                        // 防止长按文本选择
                        document.body.style.webkitUserSelect = 'none';
                        document.body.style.userSelect = 'none';
                    """.trimIndent(),
                            null
                        )
                    }
                }

            // 处理触摸事件
            setOnTouchListener { v, event ->
                horizontalGestureOwner.updateFromMotionEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP -> {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                        v.performClick()
                    }
                    MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }

            // 设置背景颜色
            setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
        }
    }

    DisposableEffect(webView) {
        onDispose {
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    // 每次代码变化时更新内容
    LaunchedEffect(htmlContent) {
        webView.loadDataWithBaseURL(
            "https://mermaid.js.org/",
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}

@Composable
fun HtmlPreviewRenderer(code: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val horizontalGestureOwner = rememberAiContentHorizontalGestureOwner()

    val htmlContent = remember(code) { code.trim() }

    val webView = remember(context, horizontalGestureOwner) {
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadWithOverviewMode = false
            settings.useWideViewPort = true

            webViewClient =
                object : RenderProcessSafeWebViewClient("HtmlPreviewRenderer") {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView,
                        request: android.webkit.WebResourceRequest
                    ): Boolean {
                        return true
                    }
                }

            setBackgroundColor(android.graphics.Color.WHITE)

            setOnTouchListener { v, event ->
                horizontalGestureOwner.updateFromMotionEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP -> {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                        v.performClick()
                    }
                    MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    LaunchedEffect(htmlContent) {
        webView.loadDataWithBaseURL(
            null,
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}

/** 处理单行代码的语法高亮 */
private fun highlightSyntaxLine(line: String, language: String): AnnotatedString {
    // 夜间模式语法高亮颜色
    val keywordColor = Color(0xFF569CD6) // 蓝色 - 关键字
    val stringColor = Color(0xFFCE9178) // 橙红色 - 字符串
    val commentColor = Color(0xFF6A9955) // 绿色 - 注释
    val numberColor = Color(0xFFB5CEA8) // 淡绿色 - 数字
    val typeColor = Color(0xFF4EC9B0) // 青色 - 类型
    val functionColor = Color(0xFFDCDCAA) // 黄色 - 函数
    val textColor = Color(0xFFD4D4D4) // 浅灰色 - 普通文本

    return buildAnnotatedString {
        when (language.lowercase()) {
            "kotlin", "java", "swift", "typescript", "javascript", "dart" -> {
                // 关键字列表
                val keywords =
                    setOf(
                        "fun",
                        "val",
                        "var",
                        "class",
                        "interface",
                        "object",
                        "return",
                        "if",
                        "else",
                        "when",
                        "for",
                        "while",
                        "do",
                        "break",
                        "continue",
                        "package",
                        "import",
                        "public",
                        "private",
                        "protected",
                        "internal",
                        "const",
                        "final",
                        "static",
                        "abstract",
                        "override",
                        "suspend",
                        "true",
                        "false",
                        "null",
                        "function",
                        "let",
                        "const",
                        "export",
                        "import",
                        "async",
                        "await",
                        "void",
                        "int",
                        "double"
                    )

                val types =
                    setOf(
                        "String",
                        "Int",
                        "Double",
                        "Float",
                        "Boolean",
                        "List",
                        "Map",
                        "Set",
                        "Array",
                        "Number",
                        "Object",
                        "Promise",
                        "void",
                        "any",
                        "never"
                    )

                // 处理注释行
                if (line.trim().startsWith("//")) {
                    withStyle(SpanStyle(color = commentColor)) { append(line) }
                    return@buildAnnotatedString
                }

                // 处理包含内联注释的行
                val commentIndex = line.indexOf("//")
                if (commentIndex > 0) {
                    // 处理注释前的代码
                    processCodePart(
                        line.substring(0, commentIndex),
                        keywords,
                        types,
                        keywordColor,
                        typeColor,
                        functionColor,
                        stringColor,
                        numberColor,
                        textColor
                    )

                    // 处理注释部分
                    withStyle(SpanStyle(color = commentColor)) {
                        append(line.substring(commentIndex))
                    }
                } else {
                    // 处理完整行的代码
                    processCodePart(
                        line,
                        keywords,
                        types,
                        keywordColor,
                        typeColor,
                        functionColor,
                        stringColor,
                        numberColor,
                        textColor
                    )
                }
            }

            "mermaid" -> {
                // Mermaid语法高亮
                // 关键字列表
                val mermaidKeywords =
                    setOf(
                        "graph",
                        "flowchart",
                        "sequenceDiagram",
                        "classDiagram",
                        "stateDiagram",
                        "pie",
                        "gantt",
                        "journey",
                        "gitGraph",
                        "LR",
                        "RL",
                        "TB",
                        "BT",
                        "TD",
                        "class",
                        "subgraph",
                        "end",
                        "title",
                        "participant",
                        "actor",
                        "note",
                        "activate",
                        "deactivate",
                        "loop",
                        "alt",
                        "else",
                        "opt",
                        "par",
                        "state",
                        "section"
                    )

                // 特殊语法
                val arrows = setOf("-->", "---", "===", "---|", "--o", "--x", "===>", "->", "=>")
                val arrowPattern = arrows.joinToString("|") { Regex.escape(it) }
                val arrowRegex = Regex("(\\s*)(${arrowPattern})(\\s*)")

                // 是否有箭头
                val arrowMatch = arrowRegex.find(line)
                if (arrowMatch != null) {
                    // 处理箭头前的部分
                    val beforeArrow = line.substring(0, arrowMatch.range.first)
                    val arrow = arrowMatch.groupValues[2]
                    val afterArrow = line.substring(arrowMatch.range.last + 1)

                    // 正常文本颜色
                    withStyle(SpanStyle(color = textColor)) { append(beforeArrow) }
                    // 箭头颜色（突出显示）
                    withStyle(SpanStyle(color = functionColor)) { append(arrow) }
                    // 之后的文本
                    withStyle(SpanStyle(color = textColor)) { append(afterArrow) }
                } else if (line.trim().startsWith("%")) {
                    // 注释行
                    withStyle(SpanStyle(color = commentColor)) { append(line) }
                } else {
                    // 检查是否有关键字
                    var hasKeyword = false
                    for (keyword in mermaidKeywords) {
                        if (line.contains(keyword, ignoreCase = true)) {
                            val regex = Regex("\\b$keyword\\b", RegexOption.IGNORE_CASE)
                            val parts = regex.split(line)
                            if (parts.size > 1) {
                                hasKeyword = true
                                append(parts[0])
                                for (i in 1 until parts.size) {
                                    withStyle(SpanStyle(color = keywordColor)) { append(keyword) }
                                    append(parts[i])
                                }
                                break
                            }
                        }
                    }

                    // 如果没有关键字，则使用默认颜色
                    if (!hasKeyword) {
                        withStyle(SpanStyle(color = textColor)) { append(line) }
                    }
                }
            }

            else -> {
                // 对于未知语言，使用默认颜色
                withStyle(SpanStyle(color = textColor)) { append(line) }
            }
        }
    }
}

/** 处理代码段，保留空格和标点符号 */
private fun AnnotatedString.Builder.processCodePart(
    code: String,
    keywords: Set<String>,
    types: Set<String>,
    keywordColor: Color,
    typeColor: Color,
    functionColor: Color,
    stringColor: Color,
    numberColor: Color,
    textColor: Color
) {
    var inString = false
    var currentWord = ""
    var currentStringContent = ""

    fun appendWord() {
        if (currentWord.isEmpty()) return

        when {
            currentWord in keywords ->
                withStyle(SpanStyle(color = keywordColor)) { append(currentWord) }

            currentWord in types -> withStyle(SpanStyle(color = typeColor)) { append(currentWord) }
            currentWord.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*\\s*\\(")) -> {
                val functionName = currentWord.substring(0, currentWord.indexOfFirst { it == '(' })
                val params = currentWord.substring(functionName.length)
                withStyle(SpanStyle(color = functionColor)) { append(functionName) }
                withStyle(SpanStyle(color = textColor)) { append(params) }
            }

            currentWord.matches(Regex("\\d+(\\.\\d+)?")) ->
                withStyle(SpanStyle(color = numberColor)) { append(currentWord) }

            else -> withStyle(SpanStyle(color = textColor)) { append(currentWord) }
        }
        currentWord = ""
    }

    for (i in code.indices) {
        val c = code[i]

        // 处理字符串
        if (c == '"' || c == '\'') {
            if (!inString) {
                // 开始字符串
                appendWord()
                inString = true
                currentStringContent = c.toString()
            } else {
                // 结束字符串
                currentStringContent += c
                withStyle(SpanStyle(color = stringColor)) { append(currentStringContent) }
                currentStringContent = ""
                inString = false
            }
            continue
        }

        if (inString) {
            currentStringContent += c
            continue
        }

        // 处理非字符串内容
        when {
            c.isLetterOrDigit() || c == '_' -> currentWord += c
            c.isWhitespace() -> {
                appendWord()
                append(c.toString())
            }

            else -> {
                appendWord()
                withStyle(SpanStyle(color = textColor)) { append(c.toString()) }
            }
        }
    }

    // 处理最后一个单词
    appendWord()

    // 如果有未闭合的字符串
    if (currentStringContent.isNotEmpty()) {
        withStyle(SpanStyle(color = stringColor)) { append(currentStringContent) }
    }
}
