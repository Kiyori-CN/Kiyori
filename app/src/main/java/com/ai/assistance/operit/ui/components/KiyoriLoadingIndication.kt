package com.ai.assistance.operit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** 低于该时长的查询不展示指示器；本地刷新和模式切换通常在此之内完成。 */
private const val LOADING_INDICATION_DELAY_MS = 200L

/**
 * 把「正在加载」与「已经加载够久，值得提示用户」分开：前者用于禁用重复触发，后者才驱动指示器。
 * 本地查询往往几十毫秒就返回，直接绑定会让进度条一闪而过，看起来像界面在抖动。
 */
@Composable
internal fun rememberDelayedLoading(loading: Boolean, delayMillis: Long = LOADING_INDICATION_DELAY_MS): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (!loading) {
            visible = false
        } else {
            delay(delayMillis)
            visible = true
        }
    }
    return visible
}
