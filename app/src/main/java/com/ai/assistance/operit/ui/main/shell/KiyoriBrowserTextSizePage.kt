package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.DEFAULT_WEB_TEXT_ZOOM_PERCENT
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.MAX_WEB_TEXT_ZOOM_PERCENT
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.MIN_WEB_TEXT_ZOOM_PERCENT
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WEB_TEXT_ZOOM_STEP_PERCENT
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.formatWebTextZoomPercent
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import kotlin.math.roundToInt

@Composable
internal fun KiyoriBrowserTextSizePage(
    currentPercent: Int,
    onBack: () -> Unit,
    onSetPercent: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewPercent by remember { mutableFloatStateOf(currentPercent.toFloat()) }

    LaunchedEffect(currentPercent) {
        previewPercent = currentPercent.toFloat()
    }

    fun normalizedPreviewPercent(): Int =
        ((previewPercent / WEB_TEXT_ZOOM_STEP_PERCENT).roundToInt() *
            WEB_TEXT_ZOOM_STEP_PERCENT)
            .coerceIn(MIN_WEB_TEXT_ZOOM_PERCENT, MAX_WEB_TEXT_ZOOM_PERCENT)

    fun commitPercent(percent: Int) {
        val normalized =
            percent.coerceIn(MIN_WEB_TEXT_ZOOM_PERCENT, MAX_WEB_TEXT_ZOOM_PERCENT)
        previewPercent = normalized.toFloat()
        onSetPercent(normalized)
    }

    KiyoriCollapsingSettingsPage(
        title = "网页文字大小",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            KiyoriSettingsGroupSection(
                title = "实时预览",
                description = "示例只展示比例变化；实际网页由同一 WebView 文字缩放设置控制",
            ) {
                BrowserTextSizePreview(percent = normalizedPreviewPercent())
            }
        }
        item {
            KiyoriSettingsGroupSection(
                title = "文字比例",
                description = "范围 50%–200%，每次调整 5%；默认值为 100%",
            ) {
                BrowserTextSizeControls(
                    percent = normalizedPreviewPercent(),
                    onSliderChange = { value -> previewPercent = value },
                    onSliderFinished = {
                        commitPercent(normalizedPreviewPercent())
                    },
                    onDecrease = {
                        commitPercent(
                            normalizedPreviewPercent() - WEB_TEXT_ZOOM_STEP_PERCENT,
                        )
                    },
                    onIncrease = {
                        commitPercent(
                            normalizedPreviewPercent() + WEB_TEXT_ZOOM_STEP_PERCENT,
                        )
                    },
                    onReset = {
                        commitPercent(DEFAULT_WEB_TEXT_ZOOM_PERCENT)
                    },
                )
            }
        }
    }
}

@Composable
private fun BrowserTextSizePreview(percent: Int) {
    val colors = LocalKiyoriSettingsColors.current
    val scale = percent / 100f
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = colors.pageBackground,
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 24.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "生活不只眼前的苟且",
            color = colors.primaryText,
            fontSize = (18f * scale).sp,
            lineHeight = (27f * scale).sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "还有诗和远方的田野",
            color = colors.primaryText,
            fontSize = (18f * scale).sp,
            lineHeight = (27f * scale).sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BrowserTextSizeControls(
    percent: Int,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatWebTextZoomPercent(percent),
            color = colors.primaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onDecrease,
                enabled = percent > MIN_WEB_TEXT_ZOOM_PERCENT,
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "减小网页文字",
                    tint =
                        if (percent > MIN_WEB_TEXT_ZOOM_PERCENT) {
                            colors.accent
                        } else {
                            colors.mutedIcon
                        },
                )
            }
            Slider(
                value = percent.toFloat(),
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderFinished,
                valueRange =
                    MIN_WEB_TEXT_ZOOM_PERCENT.toFloat()..
                        MAX_WEB_TEXT_ZOOM_PERCENT.toFloat(),
                steps =
                    ((MAX_WEB_TEXT_ZOOM_PERCENT - MIN_WEB_TEXT_ZOOM_PERCENT) /
                        WEB_TEXT_ZOOM_STEP_PERCENT) - 1,
                colors =
                    SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.disabledTrack,
                    ),
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            IconButton(
                onClick = onIncrease,
                enabled = percent < MAX_WEB_TEXT_ZOOM_PERCENT,
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "增大网页文字",
                    tint =
                        if (percent < MAX_WEB_TEXT_ZOOM_PERCENT) {
                            colors.accent
                        } else {
                            colors.mutedIcon
                        },
                )
            }
        }
        TextButton(
            onClick = onReset,
            enabled = percent != DEFAULT_WEB_TEXT_ZOOM_PERCENT,
        ) {
            Text(
                text = "恢复默认",
                color =
                    if (percent != DEFAULT_WEB_TEXT_ZOOM_PERCENT) {
                        colors.accent
                    } else {
                        colors.mutedIcon
                    },
            )
        }
    }
}
