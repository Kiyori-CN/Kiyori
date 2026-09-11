package com.ai.assistance.operit.ui.features.workflow.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 由 ViewModel 主线程持有；静默刷新接管旧读取的加载态，只有当前请求能结束它。 */
internal class WorkflowReadState {
    private var generation = 0L
    var isLoading by mutableStateOf(false)
        private set

    fun begin(showLoading: Boolean): Long {
        generation += 1
        isLoading = isLoading || showLoading
        return generation
    }

    fun isCurrent(request: Long): Boolean = generation == request

    fun finish(request: Long) {
        if (isCurrent(request)) isLoading = false
    }
}
