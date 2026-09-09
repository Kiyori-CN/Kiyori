package com.ai.assistance.operit.data.preferences

/** 仅承载一次编辑快照；实际值仍由所属偏好仓储持有。 */
data class EnvironmentValueEdit(val expectedValue: String, val value: String)

class EnvironmentEditConflictException : IllegalStateException("Environment configuration changed")

internal fun validateEnvironmentValueEdits(
    edits: Map<String, EnvironmentValueEdit>,
    readValue: (String) -> String?,
) {
    edits.forEach { (key, edit) ->
        val current = readValue(key).orEmpty().ifBlank { "" }
        val expected = edit.expectedValue.ifBlank { "" }
        val desired = edit.value.ifBlank { "" }
        // 上次磁盘提交失败可能已发布内存值；允许重试同一目标，但绝不覆盖第三种值。
        if (current != expected && current != desired) throw EnvironmentEditConflictException()
    }
}
