package com.ai.assistance.operit.ui.features.websession.browser

import android.graphics.Color as AndroidColor
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_IMAGE_VIEWER_MIN_SCALE
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserImageViewerItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserImageViewerSnapshot
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserImageViewerBackgroundAlpha
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.clampBrowserImageViewerScale
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.sanitizeBrowserImageRequestHeaders
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.shouldDismissBrowserImageViewer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Headers

@Composable
internal fun WebSessionBrowserImageViewer(
    snapshot: BrowserImageViewerSnapshot,
    onDismiss: () -> Unit,
    onSave: (BrowserImageViewerItem) -> Unit,
) {
    val context = LocalContext.current
    val gestureScope = rememberCoroutineScope()
    val viewConfiguration = remember(context) { ViewConfiguration.get(context) }
    val pagerState =
        rememberPagerState(
            initialPage = snapshot.initialPage,
            pageCount = { snapshot.items.size },
        )
    var verticalOffsetPx by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var saveActionVisible by remember { mutableStateOf(false) }
    var imageScale by remember(snapshot.items) {
        mutableFloatStateOf(BROWSER_IMAGE_VIEWER_MIN_SCALE)
    }

    LaunchedEffect(pagerState.currentPage) {
        imageScale = BROWSER_IMAGE_VIEWER_MIN_SCALE
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        val dialogWindow = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect {
            // 共享图片查看层必须真正露出底层网页；平台 dim 会让透明退出只能看到暗幕。
            dialogWindow.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow.setBackgroundDrawable(AndroidColor.TRANSPARENT.toDrawable())
            val insetsController =
                WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
        BackHandler {
            if (saveActionVisible) {
                saveActionVisible = false
            } else {
                onDismiss()
            }
        }

        val backgroundAlpha =
            browserImageViewerBackgroundAlpha(
                verticalOffsetPx = verticalOffsetPx,
                viewportHeightPx = viewportHeightPx,
            )
        val currentItem = snapshot.items[pagerState.currentPage]
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { size -> viewportHeightPx = size.height.toFloat() }
                        .clipToBounds()
                        .graphicsLayer { translationY = verticalOffsetPx }
                        .pointerInput(snapshot.items, saveActionVisible) {
                            if (saveActionVisible) {
                                return@pointerInput
                            }
                            awaitEachGesture {
                                val down =
                                    awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                var horizontalGesture = false
                                var verticalGesture = false
                                var movedBeyondSlop = false
                                var longPressTriggered = false
                                var pointerIsDown = true
                                var completedWithUp = false
                                var pinchGesture = false
                                var previousPinchDistancePx = 0f
                                val longPressJob =
                                    gestureScope.launch {
                                        delay(ViewConfiguration.getLongPressTimeout().toLong())
                                        if (pointerIsDown && !movedBeyondSlop) {
                                            longPressTriggered = true
                                            saveActionVisible = true
                                        }
                                    }
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val pressedPointers =
                                            event.changes.filter { change -> change.pressed }
                                        if (pressedPointers.size >= 2) {
                                            movedBeyondSlop = true
                                            pinchGesture = true
                                            longPressJob.cancel()
                                            val firstPointer = pressedPointers[0]
                                            val secondPointer = pressedPointers[1]
                                            val pinchDistancePx =
                                                (firstPointer.position - secondPointer.position)
                                                    .getDistance()
                                            if (pinchDistancePx > 0f) {
                                                if (previousPinchDistancePx > 0f) {
                                                    imageScale =
                                                        clampBrowserImageViewerScale(
                                                            imageScale *
                                                                (
                                                                    pinchDistancePx /
                                                                        previousPinchDistancePx
                                                                    ),
                                                        )
                                                }
                                                previousPinchDistancePx = pinchDistancePx
                                            }
                                            pressedPointers.forEach { change -> change.consume() }
                                            continue
                                        }
                                        if (pinchGesture) {
                                            event.changes.forEach { change -> change.consume() }
                                            if (pressedPointers.isEmpty()) {
                                                completedWithUp = true
                                                break
                                            }
                                            continue
                                        }
                                        val pointer =
                                            event.changes.firstOrNull { change ->
                                                change.id == down.id
                                            } ?: break
                                        val totalDelta = pointer.position - down.position
                                        if (
                                            !movedBeyondSlop &&
                                                totalDelta.getDistance() >
                                                    viewConfiguration.scaledTouchSlop
                                        ) {
                                            movedBeyondSlop = true
                                            longPressJob.cancel()
                                            if (
                                                kotlin.math.abs(totalDelta.x) >=
                                                    kotlin.math.abs(totalDelta.y)
                                            ) {
                                                horizontalGesture = true
                                            } else {
                                                verticalGesture = true
                                            }
                                        }
                                        if (horizontalGesture) {
                                            break
                                        }
                                        if (verticalGesture) {
                                            verticalOffsetPx = totalDelta.y
                                            pointer.consume()
                                        }
                                        if (!pointer.pressed) {
                                            completedWithUp = true
                                            break
                                        }
                                    }
                                } finally {
                                    pointerIsDown = false
                                    longPressJob.cancel()
                                }
                                if (verticalGesture && completedWithUp && !longPressTriggered) {
                                    if (
                                        shouldDismissBrowserImageViewer(
                                            verticalOffsetPx = verticalOffsetPx,
                                            touchSlopPx =
                                                viewConfiguration.scaledTouchSlop.toFloat(),
                                        )
                                    ) {
                                        onDismiss()
                                    } else {
                                        verticalOffsetPx = 0f
                                    }
                                } else if (
                                    completedWithUp &&
                                        !movedBeyondSlop &&
                                        !longPressTriggered
                                ) {
                                    onDismiss()
                                } else if (verticalGesture) {
                                    verticalOffsetPx = 0f
                                }
                            }
                        },
            ) { page ->
                val item = snapshot.items[page]
                val request =
                    remember(item.identity, item.requestHeaders) {
                        buildBrowserResourceImageRequest(
                            context = context,
                            url = item.url,
                            requestHeaders = item.requestHeaders,
                            thumbnail = false,
                        )
                    }
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = imageScale
                                scaleY = imageScale
                            },
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "正在加载图片",
                                color = Color.White.copy(alpha = 0.76f),
                                fontSize = 14.sp,
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "图片加载失败",
                                color = Color.White,
                                fontSize = 14.sp,
                            )
                        }
                    },
                )
            }

            if (!saveActionVisible) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .graphicsLayer { alpha = backgroundAlpha },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${snapshot.items.size}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "保存",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier =
                            Modifier
                                .clickable(role = Role.Button) { onSave(currentItem) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.22f))
                            .clickable { saveActionVisible = false },
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(20.dp)
                                .clickable(role = Role.Button) { onSave(currentItem) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                    ) {
                        Text(
                            text = "保存原图",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

internal fun buildBrowserResourceImageRequest(
    context: android.content.Context,
    url: String,
    requestHeaders: Map<String, String>,
    thumbnail: Boolean,
): ImageRequest {
    val headers = Headers.Builder()
    sanitizeBrowserImageRequestHeaders(requestHeaders).forEach { (name, value) ->
        headers.set(name, value)
    }
    return ImageRequest.Builder(context)
        .data(url)
        .headers(headers.build())
        .apply {
            if (thumbnail) {
                size(160, 160)
            }
        }
        .crossfade(false)
        .build()
}
