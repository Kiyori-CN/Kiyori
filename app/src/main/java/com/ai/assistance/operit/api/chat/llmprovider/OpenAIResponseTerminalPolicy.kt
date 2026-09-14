package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONObject

/** HTTP 200 不是生成成功；非流式和 SSE 都必须保留服务端终态含义。 */
internal object OpenAIResponseTerminalPolicy {
    fun missingChatSnapshotSuffix(streamed: String, snapshot: String): String {
        if (snapshot.isEmpty() || streamed.startsWith(snapshot)) return ""
        require(snapshot.startsWith(streamed)) { "AI_CHAT_SNAPSHOT_CONFLICT: snapshot differs from confirmed content" }
        return snapshot.substring(streamed.length)
    }

    fun requireComplete(response: JSONObject, responses: Boolean) {
        val terminal = if (responses) response.optString("status") else
            response.optJSONArray("choices")?.optJSONObject(0)?.optString("finish_reason").orEmpty()
        val complete = if (responses) terminal == "completed" else
            terminal in setOf("stop", "tool_calls", "function_call")
        require(complete) { "AI_RESPONSE_INCOMPLETE: ${if (responses) "status" else "finish_reason"}=${terminal.ifBlank { "missing" }}" }
    }
}
