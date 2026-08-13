package com.ai.assistance.operit.api.chat

import java.util.UUID

/**
 * 一次工具批次及其后续递归工具链的关联身份。
 *
 * depth 只描述当前消息执行上下文内的递归层级，不改变工具调度或取消所有权。每批工具使用新的
 * invocationId，使父子 subtree 的 wall time 不会再被误解为某一次 Web Search 的请求耗时。
 */
internal data class ToolSubtreeTrace(
    val round: Int,
    val depth: Int,
    val invocationId: String,
) {
    init {
        require(round >= 0) { "round must not be negative" }
        require(depth > 0) { "depth must be positive" }
        require(invocationId.isNotBlank()) { "invocationId must not be blank" }
    }

    fun correlationDetails(): String =
        "round=$round, depth=$depth, invocationId=$invocationId"

    fun completionDetails(resultCount: Int): String {
        require(resultCount >= 0) { "resultCount must not be negative" }
        return "${correlationDetails()}, resultCount=$resultCount"
    }

    companion object {
        fun create(
            round: Int,
            parent: ToolSubtreeTrace?,
            invocationId: String = UUID.randomUUID().toString(),
        ): ToolSubtreeTrace =
            ToolSubtreeTrace(
                round = round,
                depth = (parent?.depth ?: 0) + 1,
                invocationId = invocationId,
            )
    }
}
