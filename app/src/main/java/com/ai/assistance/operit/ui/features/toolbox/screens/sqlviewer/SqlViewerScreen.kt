package com.ai.assistance.operit.ui.features.toolbox.screens.sqlviewer

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.OverScroller
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Clear

import androidx.compose.material.icons.filled.PlayArrow

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.withFrameNanos
import com.kiyori.design.theme.KiyoriUiShapes

private val defaultTables = listOf("chats", "messages")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SqlViewerScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val viewModel: SqlViewerViewModel = viewModel(factory = SqlViewerViewModel.Factory(context.applicationContext))
    val isCurrentScreen = LocalIsCurrentScreen.current
    val state by viewModel.state.collectAsState()
    var showControlsSheet by remember { mutableStateOf(false) }

    var sqlText by rememberSaveable { mutableStateOf("SELECT * FROM chats") }
    var pageSizeText by rememberSaveable { mutableStateOf(state.pageSize.toString()) }
    var enablePaging by rememberSaveable { mutableStateOf(true) }

    val pageSize = pageSizeText.toIntOrNull()?.takeIf { it in 1..1000 }
    val canLoadMore =
        state.result != null && state.canPaginate && state.lastFetchCount >= state.pageSize && !state.isRunning

    Box(modifier = Modifier.fillMaxSize()) {
            if (state.result == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.sql_viewer_empty_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                state.result?.let { result ->
                    SqlResultTable(
                        columns = result.columns,
                        rows = result.rows,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            state.result?.let { result ->
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sql_viewer_row_count, result.rows.size, result.columns.size),
                        style = MaterialTheme.typography.labelMedium
                    )
                    TextButton(
                        onClick = viewModel::loadNextPage,
                        enabled = canLoadMore
                    ) {
                        Text(stringResource(R.string.sql_viewer_load_more))
                    }
                }
            }

            if (state.isRunning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            state.error?.let { error ->
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, end = 60.dp, top = 12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = KiyoriUiShapes.control
                ) {
                    Text(error, modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()).padding(12.dp))
                }
            }

            IconButton(
                onClick = { showControlsSheet = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.sql_viewer_controls)
                )
            }
        }

    if (isCurrentScreen && showControlsSheet) {
        KiyoriModalBottomDrawer(onDismissRequest = { showControlsSheet = false }) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    defaultTables.forEach { tableName ->
                        SuggestionChip(
                            onClick = { sqlText = "SELECT * FROM $tableName" },
                            label = { Text(tableName) }
                        )
                    }
                    SuggestionChip(
                        onClick = { sqlText = "SELECT name FROM sqlite_master WHERE type='table'" },
                        label = { Text(stringResource(R.string.sql_viewer_tables_chip)) }
                    )
                    SuggestionChip(
                        onClick = { sqlText = "PRAGMA table_info(chats)" },
                        label = { Text(stringResource(R.string.sql_viewer_schema_chip)) }
                    )
                }

                OutlinedTextField(
                    value = sqlText,
                    onValueChange = { sqlText = it },
                    label = { Text(stringResource(R.string.sql_viewer_input_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 6,
                    shape = KiyoriUiShapes.field,
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                viewModel.runQuery(sqlText, pageSize ?: state.pageSize, 0, enablePaging, append = false)
                            },
                            enabled = !state.isRunning && sqlText.isNotBlank() && (!enablePaging || pageSize != null),
                            shape = KiyoriUiShapes.control,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.sql_viewer_run))
                        }
                        OutlinedButton(
                            onClick = {
                                sqlText = ""
                            },
                            shape = KiyoriUiShapes.control,
                        ) {
                            Icon(Icons.Outlined.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.sql_viewer_clear))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.sql_viewer_page_size))
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = pageSizeText,
                            onValueChange = { pageSizeText = it },
                            enabled = enablePaging,
                            isError = enablePaging && pageSize == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            shape = KiyoriUiShapes.field,
                        )
                    }
                }

                if (enablePaging && pageSize == null) {
                    Text(stringResource(R.string.sql_viewer_page_size_invalid), color = MaterialTheme.colorScheme.error)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = enablePaging,
                        onCheckedChange = { enablePaging = it }
                    )
                    Text(stringResource(R.string.sql_viewer_paging), style = MaterialTheme.typography.labelSmall)
                }

                state.error?.let {
                    Surface(
                        shape = KiyoriUiShapes.control,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }

                state.message?.let {
                    Surface(
                        shape = KiyoriUiShapes.control,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }

                state.affectedRows?.let { count ->
                    Text(
                        text = stringResource(R.string.sql_viewer_affected_rows, count),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

private fun clampOffset(
    offset: Offset,
    viewportSize: IntSize,
    columns: Int,
    rows: Int,
    headerHeight: Float,
    cellWidth: Float,
    rowHeight: Float
): Offset {
    val maxOffset = calculateMaxOffset(
        viewportSize = viewportSize,
        columns = columns,
        rows = rows,
        headerHeight = headerHeight,
        cellWidth = cellWidth,
        rowHeight = rowHeight
    )
    return Offset(
        x = offset.x.coerceIn(0f, maxOffset.x),
        y = offset.y.coerceIn(0f, maxOffset.y)
    )
}

private fun calculateMaxOffset(
    viewportSize: IntSize,
    columns: Int,
    rows: Int,
    headerHeight: Float,
    cellWidth: Float,
    rowHeight: Float
): Offset {
    val viewportWidth = viewportSize.width.toFloat().coerceAtLeast(0f)
    val viewportHeight = viewportSize.height.toFloat().coerceAtLeast(0f)
    val contentWidth = (columns * cellWidth).coerceAtLeast(0f)
    val contentHeight = (rows * rowHeight).coerceAtLeast(0f)
    val bodyViewportHeight = (viewportHeight - headerHeight).coerceAtLeast(0f)
    return Offset(
        x = (contentWidth - viewportWidth).coerceAtLeast(0f),
        y = (contentHeight - bodyViewportHeight).coerceAtLeast(0f)
    )
}

@Composable
private fun SqlResultTable(
    columns: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val defaultHeaderSize = if (typography.labelSmall.fontSize == TextUnit.Unspecified) 12.sp else typography.labelSmall.fontSize
    val defaultBodySize = if (typography.bodySmall.fontSize == TextUnit.Unspecified) 12.sp else typography.bodySmall.fontSize
    val headerTextColor = colorScheme.onSurfaceVariant
    val bodyTextColor = colorScheme.onSurface
    val headerBackground = colorScheme.surfaceVariant
    val gridLineColor = colorScheme.outlineVariant
    val headerFontSize = defaultHeaderSize.value
    val bodyFontSize = defaultBodySize.value

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SqlTableView(context).apply {
                updateStyle(
                    headerTextColor = headerTextColor.toArgb(),
                    bodyTextColor = bodyTextColor.toArgb(),
                    headerBackgroundColor = headerBackground.toArgb(),
                    gridLineColor = gridLineColor.toArgb(),
                    headerFontSizeSp = headerFontSize,
                    bodyFontSizeSp = bodyFontSize
                )
                setData(columns, rows)
            }
        },
        update = { view ->
            view.updateStyle(
                headerTextColor = headerTextColor.toArgb(),
                bodyTextColor = bodyTextColor.toArgb(),
                headerBackgroundColor = headerBackground.toArgb(),
                gridLineColor = gridLineColor.toArgb(),
                headerFontSizeSp = headerFontSize,
                bodyFontSizeSp = bodyFontSize
            )
            view.setData(columns, rows)
        }
    )
}

private fun drawCellText(
    canvas: android.graphics.Canvas,
    text: String,
    paint: TextPaint,
    cellLeft: Float,
    cellTop: Float,
    cellWidth: Float,
    cellHeight: Float,
    paddingX: Float,
    paddingY: Float
) {
    if (text.isEmpty()) return
    val maxWidth = cellWidth - paddingX * 2
    if (maxWidth <= 0f) return
    val displayText = if (paint.measureText(text) <= maxWidth) {
        text
    } else {
        TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
    }
    val baseline = cellTop + paddingY - paint.fontMetrics.ascent
    canvas.save()
    canvas.clipRect(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight)
    canvas.drawText(displayText, cellLeft + paddingX, baseline, paint)
    canvas.restore()
}

private class SqlTableGestureHandler(context: Context) {
    var onScale: (Float) -> Unit = {}
    var onScroll: (Float, Float) -> Unit = { _, _ -> }
    var onFling: (Float, Float) -> Unit = { _, _ -> }

    var lastFocus: Offset = Offset.Zero
        private set

    private var isScaling = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            lastFocus = Offset(detector.focusX, detector.focusY)
            onScale(detector.scaleFactor)
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            lastFocus = Offset(detector.focusX, detector.focusY)
            isScaling = true
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!isScaling) {
                onScroll(distanceX, distanceY)
                return true
            }
            return false
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (!isScaling) {
                onFling(velocityX, velocityY)
                return true
            }
            return false
        }
    })

    fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)
        return scaleHandled || gestureHandled
    }
}
