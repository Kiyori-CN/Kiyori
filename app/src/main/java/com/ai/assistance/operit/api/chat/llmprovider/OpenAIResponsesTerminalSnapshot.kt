package com.ai.assistance.operit.api.chat.llmprovider

/**
 * 计算 Responses 完成快照中尚未通过增量事件交付的正文尾部。
 *
 * 已确认的流式正文不可撤回。终态快照可以等于或扩展已交付正文；若两者内容分叉，则属于协议
 * 不一致，必须显式失败，不能拼接成损坏的最终回复。
 */
internal object OpenAIResponsesTerminalSnapshot {
    fun missingTextSuffix(
        streamedText: String,
        terminalText: String,
    ): String {
        if (terminalText.isEmpty()) {
            return ""
        }
        if (streamedText.isEmpty()) {
            return terminalText
        }
        if (terminalText.startsWith(streamedText)) {
            return terminalText.substring(streamedText.length)
        }
        if (streamedText.startsWith(terminalText)) {
            return ""
        }
        throw OpenAIResponsesProtocolException(
            "Responses terminal text is inconsistent with streamed text"
        )
    }

    fun resolveToolCallIndex(
        terminalOutputIndex: Int,
        callId: String,
        indexByCallId: Map<String, Int>,
        callIdByIndex: Map<Int, String>,
    ): Int {
        if (callId.isBlank()) {
            return terminalOutputIndex
        }
        indexByCallId[callId]?.let { return it }

        val existingCallId = callIdByIndex[terminalOutputIndex]
        if (existingCallId != null && existingCallId != callId) {
            throw OpenAIResponsesProtocolException(
                "Responses output index $terminalOutputIndex changed tool call identity " +
                    "from $existingCallId to $callId"
            )
        }
        return terminalOutputIndex
    }

    fun requireCompatibleToolName(
        callId: String,
        streamedName: String,
        terminalName: String,
    ) {
        if (
            streamedName.isNotBlank() &&
                terminalName.isNotBlank() &&
                streamedName != terminalName
        ) {
            throw OpenAIResponsesProtocolException(
                "Responses tool call $callId changed name from $streamedName to $terminalName"
            )
        }
    }

    /**
     * 返回终态快照中需要继续喂给流式参数解析器的完整 arguments 快照。
     *
     * 返回空字符串表示终态没有新增参数。终态可以补齐流式前缀，也可以使用语义等价但格式不同的
     * JSON；任何实际参数分叉都必须在工具执行前失败。
     */
    fun toolArgumentsUpdate(
        callId: String,
        streamedArguments: String,
        terminalArguments: String,
    ): String {
        if (terminalArguments.isBlank()) {
            return ""
        }
        if (streamedArguments.isBlank()) {
            return terminalArguments
        }
        if (terminalArguments == streamedArguments) {
            return ""
        }
        if (terminalArguments.startsWith(streamedArguments)) {
            return terminalArguments
        }
        if (
            ProviderToolCallIdentityContract.canonicalJsonText(streamedArguments) ==
                ProviderToolCallIdentityContract.canonicalJsonText(terminalArguments)
        ) {
            return ""
        }
        throw OpenAIResponsesProtocolException(
            "Responses tool call $callId has inconsistent terminal arguments"
        )
    }
}
