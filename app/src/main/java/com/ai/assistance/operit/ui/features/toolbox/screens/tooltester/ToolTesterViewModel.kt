package com.ai.assistance.operit.ui.features.toolbox.screens.tooltester

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/** 执行状态属于页面会话；点击时先占位，阻止单项与批量互相覆盖。 */
class ToolTesterViewModel(private val execute: suspend (ToolTest) -> ToolResult) : ViewModel() {
    val sessionId: String = UUID.randomUUID().toString()
    var results by mutableStateOf<Map<String, ToolTestResult>>(emptyMap())
        private set
    var isRunning by mutableStateOf(false)
        private set
    var stopRequested by mutableStateOf(false)
        private set
    private var generation = 0L

    fun requestStop() {
        if (isRunning) stopRequested = true
    }

    fun run(tests: List<ToolTest>, prepare: suspend (ToolTest) -> Boolean = { true }) {
        if (isRunning || tests.isEmpty()) return
        val submitted = tests.toList()
        require(submitted.map { it.caseId }.distinct().size == submitted.size)
        val request = ++generation
        isRunning = true
        stopRequested = false
        results = results + submitted.associate { it.caseId to ToolTestResult(TestStatus.QUEUED, null) }
        viewModelScope.launch {
            try {
                // 工具存在文件依赖及外部副作用，串行发出以保持可解释的执行顺序。
                for (test in submitted) {
                    if (stopRequested) break
                    try {
                        if (!prepare(test) || stopRequested) break
                        record(test, TestStatus.RUNNING)
                        val result = execute(test)
                        record(test, if (result.success) TestStatus.SUCCESS else TestStatus.FAILED, result)
                    } catch (cancelled: CancellationException) {
                        record(test, if (results[test.caseId]?.status == TestStatus.RUNNING) {
                            TestStatus.INTERRUPTED
                        } else TestStatus.SKIPPED)
                        throw cancelled
                    } catch (error: Exception) {
                        record(test, TestStatus.FAILED, ToolResult(
                            toolName = test.id,
                            success = false,
                            result = StringResultData(""),
                            error = error.message ?: error.javaClass.simpleName,
                        ))
                    }
                }
            } finally {
                finish(request, submitted)
            }
        }.invokeOnCompletion { finish(request, submitted) }
    }

    private fun record(test: ToolTest, status: TestStatus, result: ToolResult? = null) {
        results = results + (test.caseId to ToolTestResult(status, result))
    }

    private fun finish(request: Long, tests: List<ToolTest>) {
        if (request != generation) return
        results = results + tests.mapNotNull { test ->
            when (results[test.caseId]?.status) {
                TestStatus.QUEUED -> test.caseId to ToolTestResult(TestStatus.SKIPPED, null)
                TestStatus.RUNNING -> test.caseId to ToolTestResult(TestStatus.INTERRUPTED, null)
                else -> null
            }
        }
        isRunning = false
    }
}
