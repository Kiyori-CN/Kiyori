package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ChatUtils
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

internal object StructuredToolCallBridge {
    private enum class ProviderHistoryBlockType {
        ASSISTANT,
        USER_INPUT,
        TOOL_RESULT
    }

    private data class ToolResultRecord(
        val name: String?,
        val content: String
    )

    fun buildToolsJson(toolPrompts: List<ToolPrompt>?): String? {
        if (toolPrompts.isNullOrEmpty()) {
            return null
        }
        val tools = buildToolDefinitions(toolPrompts)
        return if (tools.length() > 0) {
            ProviderToolCallIdentityContract.canonicalJsonText(tools.toString())
        } else {
            null
        }
    }

    fun buildToolsArray(toolPrompts: List<ToolPrompt>?): JSONArray {
        if (toolPrompts.isNullOrEmpty()) {
            return JSONArray()
        }
        return buildToolDefinitions(toolPrompts)
    }

    fun buildMessagesJson(
        history: List<PromptTurn>,
        preserveThinkInHistory: Boolean
    ): String {
        return buildStructuredMessages(history, preserveThinkInHistory).toString()
    }

    fun buildMnnChatHistory(
        history: List<PromptTurn>,
        preserveThinkInHistory: Boolean
    ): List<Pair<String, String>> {
        val messages = buildStructuredMessages(history, preserveThinkInHistory)
        val compiledHistory = ArrayList<Pair<String, String>>(messages.length())
        for (index in 0 until messages.length()) {
            val message = messages.optJSONObject(index) ?: continue
            val role = message.optString("role", "").trim()
            val contentValue = message.opt("content")
            val content =
                if (contentValue == null || contentValue === JSONObject.NULL) {
                    ""
                } else if (contentValue is String) {
                    contentValue
                } else {
                    contentValue.toString()
                }
            val isPlainRoleContentMessage =
                role.isNotEmpty() &&
                    message.length() == 2 &&
                    message.has("role") &&
                    message.has("content") &&
                    (contentValue == null || contentValue == JSONObject.NULL || contentValue is String)
            if (isPlainRoleContentMessage) {
                compiledHistory.add(role to content)
            } else {
                compiledHistory.add("json" to message.toString())
            }
        }
        return compiledHistory
    }

    fun compileHistoryForProvider(
        history: List<PromptTurn>,
        useToolCall: Boolean
    ): List<PromptTurn> {
        if (history.isEmpty()) {
            return history
        }

        val compiled = mutableListOf<PromptTurn>()
        var currentBlockType: ProviderHistoryBlockType? = null
        var currentContent = StringBuilder()
        var currentMetadata: Map<String, Any?> = emptyMap()
        var currentBlockContainsAssistant = false
        var currentBlockContainsToolCall = false

        fun flushCurrentBlock() {
            val blockType = currentBlockType ?: return
            compiled.add(
                PromptTurn(
                    kind =
                        when (blockType) {
                            ProviderHistoryBlockType.ASSISTANT ->
                                if (
                                    currentBlockContainsToolCall &&
                                        !currentBlockContainsAssistant
                                ) {
                                    PromptTurnKind.TOOL_CALL
                                } else {
                                    PromptTurnKind.ASSISTANT
                                }
                            ProviderHistoryBlockType.USER_INPUT -> PromptTurnKind.USER
                            ProviderHistoryBlockType.TOOL_RESULT ->
                                PromptTurnKind.TOOL_RESULT
                        },
                    content = currentContent.toString().trim(),
                    metadata = currentMetadata
                )
            )
            currentBlockType = null
            currentContent = StringBuilder()
            currentMetadata = emptyMap()
            currentBlockContainsAssistant = false
            currentBlockContainsToolCall = false
        }

        fun appendToBlock(blockType: ProviderHistoryBlockType, turn: PromptTurn) {
            if (currentBlockType != blockType) {
                flushCurrentBlock()
                currentBlockType = blockType
            }
            val trimmedContent = turn.content.trim()
            if (trimmedContent.isNotEmpty()) {
                if (currentContent.isNotEmpty()) {
                    currentContent.append("\n")
                }
                currentContent.append(trimmedContent)
            }
            if (turn.metadata.isNotEmpty()) {
                currentMetadata = currentMetadata + turn.metadata
            }
            currentBlockContainsAssistant =
                currentBlockContainsAssistant || turn.kind == PromptTurnKind.ASSISTANT
            currentBlockContainsToolCall =
                currentBlockContainsToolCall || turn.kind == PromptTurnKind.TOOL_CALL
        }

        for (turn in history) {
            when (turn.kind) {
                PromptTurnKind.SYSTEM -> {
                    flushCurrentBlock()
                    compiled.add(turn)
                }
                PromptTurnKind.ASSISTANT,
                PromptTurnKind.TOOL_CALL -> appendToBlock(ProviderHistoryBlockType.ASSISTANT, turn)
                PromptTurnKind.TOOL_RESULT -> appendToBlock(ProviderHistoryBlockType.TOOL_RESULT, turn)
                PromptTurnKind.USER,
                PromptTurnKind.SUMMARY -> appendToBlock(ProviderHistoryBlockType.USER_INPUT, turn)
            }
        }

        flushCurrentBlock()
        return compiled
    }

    fun convertToolCallPayloadToXml(content: String): String {
        if (content.isBlank()) {
            return content
        }

        if (ChatMarkupRegex.containsAnyToolLikeTag(content)) {
            return content
        }

        val toolCalls = parsePossibleToolCallsFromText(content) ?: return content
        val xml = convertToolCallsToXml(toolCalls)
        return if (xml.isBlank()) content else xml
    }

    private fun buildStructuredMessages(
        history: List<PromptTurn>,
        preserveThinkInHistory: Boolean
    ): JSONArray {
        val mergedHistory = compileHistoryForProvider(history, useToolCall = true)
        val messagesArray = JSONArray()
        var queuedAssistantToolText: String? = null
        var queuedToolCalls = JSONArray()
        val queuedToolCallIds = mutableListOf<String>()
        val openToolCallIds = mutableListOf<String>()
        val toolHistoryState = ProviderToolHistoryState()

        fun appendQueuedAssistantToolText(text: String) {
            if (text.isBlank()) return
            queuedAssistantToolText =
                if (queuedAssistantToolText.isNullOrBlank()) {
                    text
                } else {
                    queuedAssistantToolText + "\n" + text
                }
        }

        fun queueToolCalls(textContent: String, toolCalls: JSONArray) {
            appendQueuedAssistantToolText(textContent)
            for (i in 0 until toolCalls.length()) {
                val sourceToolCall = toolCalls.optJSONObject(i) ?: continue
                val toolCall = JSONObject(sourceToolCall.toString())
                val callId = toolCall.optString("id", "").trim()
                if (callId.isEmpty()) {
                    throw ProviderToolHistoryProtocolException(
                        violation = ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                        detail = "Structured tool call has no stable call ID",
                    )
                }
                toolCall.put("id", callId)
                queuedToolCalls.put(toolCall)
                queuedToolCallIds.add(callId)
            }
        }

        fun emitQueuedToolCallsIfNeeded() {
            if (queuedToolCalls.length() == 0) return

            messagesArray.put(
                JSONObject().apply {
                    put("role", "assistant")
                    put(
                        "content",
                        if (!queuedAssistantToolText.isNullOrBlank()) {
                            queuedAssistantToolText
                        } else {
                            JSONObject.NULL
                        }
                    )
                    put("tool_calls", queuedToolCalls)
                }
            )

            openToolCallIds.addAll(queuedToolCallIds)
            toolHistoryState.acceptToolCalls(
                callIds = queuedToolCallIds.toList(),
                boundary = "structured_assistant_tool_calls",
            )
            queuedAssistantToolText = null
            queuedToolCalls = JSONArray()
            queuedToolCallIds.clear()
        }

        fun requireNoOpenToolCalls(boundary: String) {
            emitQueuedToolCallsIfNeeded()
            toolHistoryState.requireClosed(boundary)
            openToolCallIds.clear()
        }

        for (turn in mergedHistory) {
            val content =
                if (!preserveThinkInHistory && turn.kind == PromptTurnKind.ASSISTANT) {
                    ChatUtils.removeThinkingContent(turn.content)
                } else {
                    turn.content
                }

            when (turn.kind) {
                PromptTurnKind.SYSTEM -> {
                    requireNoOpenToolCalls("system_boundary")
                    messagesArray.put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", content)
                        }
                    )
                }

                PromptTurnKind.USER,
                PromptTurnKind.SUMMARY -> {
                    requireNoOpenToolCalls("user_boundary")
                    messagesArray.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", content)
                        }
                    )
                }

                PromptTurnKind.ASSISTANT -> {
                    val (textContent, parsedToolCalls) = parseXmlToolCalls(content)
                    val toolCalls =
                        if (parsedToolCalls != null) {
                            wrapPackageToolCallsWithProxy(parsedToolCalls)
                        } else {
                            null
                        }

                    if (toolCalls != null && toolCalls.length() > 0) {
                        requireNoOpenToolCalls("assistant_tool_call_before_result")
                        queueToolCalls(textContent, toolCalls)
                    } else {
                        messagesArray.put(
                            JSONObject().apply {
                                put("role", "assistant")
                                put(
                                    "content",
                                    if (content.isBlank()) JSONObject.NULL else content,
                                )
                            }
                        )
                    }
                }

                PromptTurnKind.TOOL_CALL -> {
                    val (textContent, parsedToolCalls) = parseXmlToolCalls(content)
                    val toolCalls =
                        if (parsedToolCalls != null) {
                            wrapPackageToolCallsWithProxy(parsedToolCalls)
                        } else {
                            null
                        }

                    if (toolCalls != null && toolCalls.length() > 0) {
                        requireNoOpenToolCalls("typed_tool_call_before_result")
                        queueToolCalls(textContent, toolCalls)
                    } else {
                        throw ProviderToolHistoryProtocolException(
                            violation = ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                            detail = "Typed TOOL_CALL has no structured payload",
                        )
                    }
                }

                PromptTurnKind.TOOL_RESULT -> {
                    emitQueuedToolCallsIfNeeded()
                    val (textContent, toolResults) = parseXmlToolResults(content)
                    val resultsList =
                        toolResults
                            ?: throw ProviderToolHistoryProtocolException(
                                violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_PAYLOAD,
                                detail = "Typed TOOL_RESULT has no structured payload",
                            )
                    toolHistoryState.acceptToolResults(
                        resultCount = resultsList.size,
                        boundary = "structured_tool_result",
                    )
                    repeat(resultsList.size) { index ->
                        val result = resultsList[index]
                        val toolMessage = JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", openToolCallIds[index])
                            if (!result.name.isNullOrBlank()) {
                                put("name", result.name)
                            }
                            put("content", result.content)
                        }
                        messagesArray.put(toolMessage)
                    }
                    repeat(resultsList.size) {
                        openToolCallIds.removeAt(0)
                    }
                    if (textContent.isNotBlank()) {
                        toolHistoryState.requireClosed("structured_tool_result_text_boundary")
                        messagesArray.put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", textContent)
                            }
                        )
                    }
                }
            }
        }

        requireNoOpenToolCalls("history_end")
        return messagesArray
    }

    private fun buildToolDefinitions(toolPrompts: List<ToolPrompt>): JSONArray {
        val tools = JSONArray()

        val sortedTools = toolPrompts.sortedBy { it.name }
        val duplicateToolName =
            sortedTools
                .groupingBy { it.name }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
                ?.key
        require(duplicateToolName == null) {
            "Structured tool definitions contain duplicate name: $duplicateToolName"
        }

        for (tool in sortedTools) {
            tools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.name)
                    val fullDescription = if (tool.details.isNotEmpty()) {
                        "${tool.description}\n${tool.details}"
                    } else {
                        tool.description
                    }
                    put("description", fullDescription)
                    put("parameters", buildSchemaFromStructured(tool.parametersStructured ?: emptyList()))
                })
            })
        }

        return tools
    }

    private fun buildSchemaFromStructured(params: List<ToolParameterSchema>): JSONObject {
        val schema = JSONObject().apply {
            put("type", "object")
        }

        val properties = JSONObject()
        val required = JSONArray()

        val sortedParams = params.sortedBy { it.name }
        val duplicateParameterName =
            sortedParams
                .groupingBy { it.name }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
                ?.key
        require(duplicateParameterName == null) {
            "Structured tool schema contains duplicate parameter: $duplicateParameterName"
        }

        for (param in sortedParams) {
            properties.put(param.name, JSONObject().apply {
                put("type", param.type)
                put("description", param.description)
                if (param.default != null) {
                    put("default", param.default)
                }
            })

            if (param.required) {
                required.put(param.name)
            }
        }

        schema.put("properties", properties)
        // OpenAI-style tool schemas must keep `required` as an array, including an empty one.
        schema.put("required", required)

        return schema
    }

    private fun convertToolCallsToXml(toolCalls: JSONArray): String {
        val xml = StringBuilder()

        for (i in 0 until toolCalls.length()) {
            val toolCall = toolCalls.optJSONObject(i) ?: continue
            val function = toolCall.optJSONObject("function") ?: continue
            val name = function.optString("name", "")
            if (name.isBlank()) {
                continue
            }

            val argumentsRaw = function.optString("arguments", "")
            val paramsObj = kotlin.runCatching {
                JSONObject(argumentsRaw)
            }.getOrNull()

            val toolTagName = ChatMarkupRegex.generateRandomToolTagName()
            xml.append("\n<")
                .append(toolTagName)
                .append(" name=\"")
                .append(name)
                .append("\">")

            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = paramsObj.opt(key)
                    xml.append("\n<param name=\"")
                        .append(key)
                        .append("\">")
                        .append(escapeXml(value?.toString() ?: ""))
                        .append("</param>")
                }
            } else if (argumentsRaw.isNotBlank()) {
                xml.append("\n<param name=\"_raw_arguments\">")
                    .append(escapeXml(argumentsRaw))
                    .append("</param>")
            }

            xml.append("\n</")
                .append(toolTagName)
                .append(">\n")
        }

        return xml.toString().trimEnd()
    }

    private fun parsePossibleToolCallsFromText(content: String): JSONArray? {
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            return null
        }

        val candidates = LinkedHashSet<String>()
        candidates.add(trimmed)

        val extractedJson = ChatUtils.extractJson(trimmed).trim()
        if (extractedJson.isNotBlank()) {
            candidates.add(extractedJson)
        }

        val extractedArray = ChatUtils.extractJsonArray(trimmed).trim()
        if (extractedArray.isNotBlank()) {
            candidates.add(extractedArray)
        }

        val fencedRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        fencedRegex.findAll(trimmed).forEach { match ->
            val fenced = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (fenced.isNotBlank()) {
                candidates.add(fenced)
            }
        }

        for (candidate in candidates) {
            val fromObject = kotlin.runCatching {
                extractToolCallsFromAny(JSONObject(candidate))
            }.getOrNull()
            if (fromObject != null && fromObject.length() > 0) {
                return fromObject
            }

            val fromArray = kotlin.runCatching {
                extractToolCallsFromAny(JSONArray(candidate))
            }.getOrNull()
            if (fromArray != null && fromArray.length() > 0) {
                return fromArray
            }
        }

        return null
    }

    private fun extractToolCallsFromAny(root: JSONObject): JSONArray? {
        root.optJSONArray("tool_calls")?.let { array ->
            val normalized = normalizeToolCalls(array)
            if (normalized.length() > 0) {
                return normalized
            }
        }

        root.optJSONObject("function_call")?.let { functionCall ->
            val normalized = normalizeSingleToolCall(functionCall, 0)
            if (normalized != null) {
                return JSONArray().put(normalized)
            }
        }

        if (root.optString("type", "") == "function_call") {
            val normalized = normalizeSingleToolCall(root, 0)
            if (normalized != null) {
                return JSONArray().put(normalized)
            }
        }

        root.optJSONArray("output")?.let { outputArray ->
            val normalized = normalizeToolCalls(outputArray)
            if (normalized.length() > 0) {
                return normalized
            }
        }

        return null
    }

    private fun extractToolCallsFromAny(root: JSONArray): JSONArray? {
        val normalized = normalizeToolCalls(root)
        return if (normalized.length() > 0) normalized else null
    }

    private fun normalizeToolCalls(source: JSONArray): JSONArray {
        val normalized = JSONArray()
        for (i in 0 until source.length()) {
            val item = source.optJSONObject(i) ?: continue
            val normalizedCall = normalizeSingleToolCall(item, i) ?: continue
            normalized.put(normalizedCall)
        }
        return normalized
    }

    private fun normalizeSingleToolCall(raw: JSONObject, index: Int): JSONObject? {
        val functionObject = raw.optJSONObject("function")
        val functionCallObject = raw.optJSONObject("function_call")

        val name = when {
            functionObject != null -> functionObject.optString("name", "")
            raw.optString("name", "").isNotBlank() -> raw.optString("name", "")
            functionCallObject != null -> functionCallObject.optString("name", "")
            else -> ""
        }
        if (name.isBlank()) {
            return null
        }

        val argumentsValue: Any? = when {
            functionObject != null && functionObject.has("arguments") -> functionObject.opt("arguments")
            raw.has("arguments") -> raw.opt("arguments")
            functionCallObject != null && functionCallObject.has("arguments") -> functionCallObject.opt("arguments")
            else -> null
        }

        val arguments = when (argumentsValue) {
            is JSONObject, is JSONArray -> argumentsValue.toString()
            is String -> if (argumentsValue.isBlank()) "{}" else argumentsValue
            null -> "{}"
            else -> argumentsValue.toString()
        }

        val rawId = raw.optString("id", "")
            .ifBlank { raw.optString("call_id", "") }
            .ifBlank { "call_${sanitizeToolCallId(name)}_$index" }
        val callId = sanitizeToolCallId(rawId)

        return JSONObject().apply {
            put("id", callId)
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("arguments", arguments)
            })
        }
    }

    private fun parseXmlToolCalls(content: String): Pair<String, JSONArray?> {
        val matches = ChatMarkupRegex.toolCallPattern.findAll(content)
        if (!matches.any()) {
            return content to null
        }

        val toolCalls = JSONArray()
        var textContent = content
        var callIndex = 0
        val providerCallSignatures =
            linkedMapOf<ProviderToolCallKey, ProviderToolCallSignature>()

        matches.forEach { match ->
            val toolName = match.groupValues[2]
            val toolBody = match.groupValues[3]
            val openingTag = match.value.substringBefore('>')

            val params = JSONObject()
            ChatMarkupRegex.toolParamPattern.findAll(toolBody).forEach { paramMatch ->
                val paramName = paramMatch.groupValues[1]
                val paramValue = XmlEscaper.unescape(paramMatch.groupValues[2].trim())
                params.put(paramName, paramValue)
            }

            val providerCallId =
                extractXmlAttribute(openingTag, "provider_call_id")
            val providerName =
                extractXmlAttribute(openingTag, "provider_name").orEmpty()
            val argumentsJson =
                ProviderToolCallIdentityContract.canonicalJsonText(
                    params.toString()
                )
            val providerIdentity =
                ProviderToolCallIdentityContract.key(providerName, providerCallId)
            if (providerIdentity != null) {
                val signature =
                    ProviderToolCallIdentityContract.signature(
                        toolName = toolName,
                        argumentsJson = argumentsJson,
                    )
                val existing = providerCallSignatures[providerIdentity]
                if (existing != null) {
                    ProviderToolCallIdentityContract.requireSame(
                        key = providerIdentity,
                        existing = existing,
                        incoming = signature,
                    )
                    callIndex++
                    textContent = textContent.replace(match.value, "")
                    return@forEach
                }
                providerCallSignatures[providerIdentity] = signature
            }

            val callId =
                providerCallId
                    ?: run {
                        val toolNamePart = sanitizeToolCallId(toolName)
                        val hashPart = stableIdHashPart("${toolName}:${argumentsJson}")
                        sanitizeToolCallId("call_${toolNamePart}_${hashPart}_$callIndex")
                    }

            toolCalls.put(JSONObject().apply {
                put("id", callId)
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", toolName)
                    put("arguments", params.toString())
                })
            })

            callIndex++
            textContent = textContent.replace(match.value, "")
        }

        return textContent.trim() to toolCalls
    }

    private fun extractXmlAttribute(openingTag: String, attributeName: String): String? {
        if (openingTag.isEmpty()) {
            return null
        }
        val match =
            Regex("""\b${Regex.escape(attributeName)}="([^"]*)"""")
                .find(openingTag)
                ?: return null
        return XmlEscaper.unescape(match.groupValues[1]).takeIf { it.isNotBlank() }
    }

    private fun wrapPackageToolCallsWithProxy(toolCalls: JSONArray): JSONArray {
        val wrappedToolCalls = JSONArray()

        for (i in 0 until toolCalls.length()) {
            val toolCall = toolCalls.optJSONObject(i) ?: continue
            val function = toolCall.optJSONObject("function")
            if (function == null) {
                wrappedToolCalls.put(toolCall)
                continue
            }

            val toolName = function.optString("name", "")
            if (!toolName.contains(":") || toolName == "package_proxy") {
                wrappedToolCalls.put(toolCall)
                continue
            }

            val rawArguments = function.optString("arguments", "{}")
            val originalArguments = JSONObject(if (rawArguments.isBlank()) "{}" else rawArguments)
            val wrappedFunction = JSONObject(function.toString()).apply {
                put("name", "package_proxy")
                put(
                    "arguments",
                    ProviderToolCallIdentityContract.canonicalJsonText(
                        JSONObject().apply {
                            put("tool_name", toolName)
                            put("params", originalArguments)
                        }.toString()
                    )
                )
            }

            wrappedToolCalls.put(JSONObject(toolCall.toString()).apply {
                put("function", wrappedFunction)
            })
        }

        return wrappedToolCalls
    }

    private fun parseXmlToolResults(content: String): Pair<String, List<ToolResultRecord>?> {
        val matches = ChatMarkupRegex.toolResultAnyPattern.findAll(content)
        if (!matches.any()) {
            return content to null
        }

        val results = mutableListOf<ToolResultRecord>()
        var textContent = content

        matches.forEach { match ->
            val fullContent = match.groupValues[2].trim()
            val contentMatch = ChatMarkupRegex.contentTag.find(fullContent)
            val resultContent = if (contentMatch != null) {
                contentMatch.groupValues[1].trim()
            } else {
                fullContent
            }
            val resultName = ChatMarkupRegex.extractToolResultProtocolName(match.value)
            results.add(ToolResultRecord(resultName, resultContent))
            textContent = textContent.replace(match.value, "").trim()
        }

        return textContent.trim() to results
    }

    private object XmlEscaper {
        fun escape(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        fun unescape(text: String): String {
            return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
        }
    }

    private fun escapeXml(text: String): String {
        return XmlEscaper.escape(text)
    }

    private fun sanitizeToolCallId(raw: String): String {
        val output = buildString(raw.length) {
            raw.forEach { ch ->
                if (ch.isLetterOrDigit() || ch == '_' || ch == '-') {
                    append(ch)
                } else {
                    append('_')
                }
            }
        }.replace(Regex("_+"), "_").trim('_')
        return if (output.isEmpty()) "call" else output
    }

    private fun stableIdHashPart(raw: String): String {
        val hash = raw.hashCode()
        val positive = if (hash == Int.MIN_VALUE) 0 else abs(hash)
        val base = positive.toString(36).filter { it.isLetterOrDigit() }.lowercase()
        return if (base.isEmpty()) "0" else base
    }
}
