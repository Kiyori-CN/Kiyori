package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.completion.CompletionItem
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.completion.CompletionPopup
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.EditorTheme
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.getThemeForLanguage
import kotlin.math.roundToInt

@Composable
fun CodeEditor(
    code: String,
    language: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    showLineNumbers: Boolean = true,
    softWrap: Boolean = false,
    enableCompletion: Boolean = true,
    showSymbolBar: Boolean = true,
    symbolBarHeight: Dp = 48.dp,
    editorRef: ((NativeCodeEditor?) -> Unit)? = null,
    onInteractionStateChanged: ((EditorInteractionState) -> Unit)? = null,
) {
    val theme = getThemeForLanguage(language, MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val latestCode = rememberUpdatedState(code)
    val latestOnCodeChange = rememberUpdatedState(onCodeChange)
    val latestEditorRef = rememberUpdatedState(editorRef)
    val latestOnInteractionStateChanged = rememberUpdatedState(onInteractionStateChanged)
    val density = LocalDensity.current
    val popupVerticalOffsetPx = with(density) { 6.dp.toPx().roundToInt() }
    val imeBottomInsetPx = WindowInsets.ime.getBottom(density)
    val keyboardAvoidancePaddingPx = with(density) {
        if (imeBottomInsetPx > 0) 24.dp.roundToPx() else 0
    }

    var completionItems by remember { mutableStateOf<List<CompletionItem>>(emptyList()) }
    var showCompletions by remember { mutableStateOf(false) }
    var popupOffset by remember { mutableStateOf(IntOffset.Zero) }
    var editorWindowOffset by remember { mutableStateOf(IntOffset.Zero) }
    var editorBottomPx by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val editorRefState = remember { mutableStateOf<NativeCodeEditor?>(null) }

    fun updatePopupAnchor(editor: NativeCodeEditor?) {
        val cursor = editor?.getCursorScreenPosition() ?: Point(0, 0)
        popupOffset =
            IntOffset(
                editorWindowOffset.x + cursor.x,
                editorWindowOffset.y + cursor.y + popupVerticalOffsetPx
            )
    }

    CustomScaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(theme.background)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(theme.background)) {
                AndroidView(
                    modifier =
                        Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInWindow()
                            editorWindowOffset =
                                IntOffset(position.x.roundToInt(), position.y.roundToInt())
                            editorBottomPx = position.y.roundToInt() + coordinates.size.height
                            if (showCompletions) {
                                updatePopupAnchor(editorRefState.value)
                            }
                        },
                    factory = { context ->
                        NativeCodeEditor(context).apply {
                            layoutParams =
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            editorRefState.value = this
                            latestEditorRef.value?.invoke(this)
                        }
                    },
                    update = { view ->
                        editorRefState.value = view
                        latestEditorRef.value?.invoke(view)
                        view.setEditorTheme(theme)
                        view.setLanguage(language)
                        view.setReadOnly(readOnly)
                        view.setShowLineNumbers(showLineNumbers)
                        view.setSoftWrap(softWrap)
                        view.setCompletionEnabled(enableCompletion)
                        view.setViewportBottomPadding(keyboardAvoidancePaddingPx)
                        view.setOnTextChangedListener { newText ->
                            if (newText != latestCode.value) {
                                latestOnCodeChange.value(newText)
                            }
                        }
                        view.setOnInteractionStateChangedListener(
                            if (latestOnInteractionStateChanged.value != null) {
                                { state -> latestOnInteractionStateChanged.value?.invoke(state) }
                            } else {
                                null
                            },
                        )
                        view.setCompletionCallback(
                            if (enableCompletion) {
                                object : EditorCompletionCallback {
                                    override fun showCompletions(
                                        items: List<CompletionItem>,
                                        prefix: String
                                    ) {
                                        completionItems = items
                                        showCompletions = items.isNotEmpty()
                                        updatePopupAnchor(view)
                                    }

                                    override fun hideCompletions() {
                                        showCompletions = false
                                        completionItems = emptyList()
                                    }

                                    override fun isCompletionVisible(): Boolean = showCompletions
                                }
                            } else {
                                showCompletions = false
                                completionItems = emptyList()
                                null
                            }
                        )
                        if (view.getText() != latestCode.value) {
                            view.setText(latestCode.value, fromUpdate = true)
                        }
                    },
                    onRelease = { view ->
                        if (editorRefState.value === view) {
                            editorRefState.value = null
                        }
                        latestEditorRef.value?.invoke(null)
                        view.release()
                    }
                )

                if (showCompletions && completionItems.isNotEmpty()) {
                    CompletionPopup(
                        completionItems = completionItems,
                        theme = theme,
                        onItemSelected = { item ->
                            editorRefState.value?.applyCompletion(item)
                            showCompletions = false
                        },
                        onDismissRequest = {
                            showCompletions = false
                        },
                        offset = popupOffset,
                        viewportBottom = editorBottomPx
                    )
                }
            }

            if (showSymbolBar && !readOnly) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(symbolBarHeight),
                    color = theme.gutterBackground,
                    contentColor = theme.textColor,
                    shadowElevation = 4.dp
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(
                            listOf(
                                "{", "}", "(", ")", "[", "]", "=", ".", ",", ";", ":",
                                "\"", "'", "+", "-", "*", "/", "_", "<", ">", "&", "|",
                                "!", "?"
                            )
                            ) { symbol ->
                            SymbolButton(symbol = symbol, theme = theme) {
                                editorRefState.value?.insertSymbol(symbol)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbolButton(symbol: String, theme: EditorTheme, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(width = 1.dp, color = theme.gutterBorder, shape = RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = theme.textColor
        )
    }
}

class NativeCodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val canvasEditorView = CanvasCodeEditorView(context, attrs)
    private var isReleased = false

    init {
        addView(canvasEditorView)
    }

    fun setOnTextChangedListener(listener: (String) -> Unit) {
        if (isReleased) {
            return
        }
        canvasEditorView.setOnTextChangedListener(listener)
    }

    fun setEditorTheme(theme: EditorTheme) {
        if (isReleased) {
            return
        }
        canvasEditorView.setEditorTheme(theme)
    }

    fun setLanguage(language: String) {
        if (isReleased) {
            return
        }
        canvasEditorView.setLanguage(language)
    }

    fun setReadOnly(readOnly: Boolean) {
        if (isReleased) {
            return
        }
        canvasEditorView.setReadOnly(readOnly)
        isEnabled = !readOnly
    }

    fun setShowLineNumbers(showLineNumbers: Boolean) {
        if (isReleased) {
            return
        }
        canvasEditorView.setShowLineNumbers(showLineNumbers)
    }

    fun setSoftWrap(softWrap: Boolean) {
        if (isReleased) {
            return
        }
        canvasEditorView.setSoftWrap(softWrap)
    }

    fun setCompletionEnabled(enableCompletion: Boolean) {
        if (isReleased) {
            return
        }
        canvasEditorView.setCompletionEnabled(enableCompletion)
    }

    fun setViewportBottomPadding(bottomPaddingPx: Int) {
        if (isReleased) {
            return
        }
        canvasEditorView.setViewportBottomPadding(bottomPaddingPx)
    }

    fun setOnInteractionStateChangedListener(listener: ((EditorInteractionState) -> Unit)?) {
        if (isReleased) {
            return
        }
        canvasEditorView.setOnInteractionStateChangedListener(listener)
    }

    fun setText(text: String, fromUpdate: Boolean = false) {
        if (isReleased) {
            return
        }
        if (canvasEditorView.getTextContent() != text || !fromUpdate) {
            canvasEditorView.setTextContent(text)
        }
    }

    fun getText(): String = if (isReleased) "" else canvasEditorView.getTextContent()

    fun setCompletionCallback(callback: EditorCompletionCallback?) {
        if (isReleased) {
            return
        }
        canvasEditorView.setCompletionCallback(callback)
    }

    fun applyCompletion(item: CompletionItem) {
        if (isReleased) {
            return
        }
        canvasEditorView.applyCompletion(item)
    }

    fun undo() {
        if (isReleased) {
            return
        }
        canvasEditorView.undo()
    }

    fun redo() {
        if (isReleased) {
            return
        }
        canvasEditorView.redo()
    }

    fun insertSymbol(symbol: String) {
        if (isReleased) {
            return
        }
        canvasEditorView.insertSymbol(symbol)
    }

    fun replaceAllText(newText: String) {
        if (isReleased) {
            return
        }
        canvasEditorView.replaceAllText(newText)
    }

    fun replaceRange(
        start: Int,
        end: Int,
        replacement: String,
    ) {
        if (isReleased) {
            return
        }
        canvasEditorView.replaceRange(start, end, replacement)
    }

    fun selectRange(
        start: Int,
        end: Int,
    ) {
        if (isReleased) {
            return
        }
        canvasEditorView.selectRange(start, end)
    }

    fun getCursorScreenPosition(): Point =
        if (isReleased) Point(0, 0) else canvasEditorView.getCursorScreenPosition()

    fun release() {
        if (isReleased) {
            return
        }
        isReleased = true
        canvasEditorView.release()
        removeAllViews()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        canvasEditorView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        canvasEditorView.layout(0, 0, r - l, b - t)
    }
}
