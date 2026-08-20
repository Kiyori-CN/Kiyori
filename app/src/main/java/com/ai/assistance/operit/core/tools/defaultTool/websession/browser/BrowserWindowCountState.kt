package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight process projection of the shared Browser Runtime window registry.
 *
 * Software Home can observe this state without constructing StandardBrowserSessionTools, whose
 * initialization owns profile cleanup, download recovery and ad-block runtime startup.
 */
internal object BrowserWindowCountState {
    private val mutableWindowCount = MutableStateFlow(0)

    val windowCount: StateFlow<Int> = mutableWindowCount.asStateFlow()

    fun publish(count: Int) {
        require(count >= 0) { "Browser window count cannot be negative." }
        mutableWindowCount.value = count
    }
}
