package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchContract
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchMainModelProjection
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchToolResultMarkupCodec
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.data.model.ToolResult

/**
 * Manages the markup elements used in conversations with the AI assistant.
 *
 * This class handles the generation of standardized XML-formatted status messages, tool invocation
 * formats, and tool results to be displayed in the conversation.
 */
class ConversationMarkupManager {

    companion object {
        /** 宿主警告没有工具事务身份；只有真实执行结果才编译成 Provider tool 消息。 */
        internal fun feedbackHistoryTurn(results: List<ToolResult>, message: String) =
            com.ai.assistance.operit.core.chat.hooks.PromptTurn(
                kind = if (results.isEmpty()) com.ai.assistance.operit.core.chat.hooks.PromptTurnKind.USER
                    else com.ai.assistance.operit.core.chat.hooks.PromptTurnKind.TOOL_RESULT,
                content = message,
                toolName = results.joinToString(", ") { it.toolName }.ifBlank { null },
            )

        private const val TOOL_RESULT_TRUNCATION_SUFFIX =
            "\n[工具结果过长，已截断]"

        /**
         * Creates an 'error' status markup element for a tool.
         *
         * @param toolName The name of the tool that produced the error
         * @param errorMessage The error message
         * @return The formatted status element
         */
        fun createToolErrorStatus(toolName: String, errorMessage: String): String {
            return createToolResultXml(
                toolName = toolName,
                status = "error",
                content = "<content><error>${errorMessage}</error></content>"
            )
        }

        /**
         * Creates a 'warning' status markup element.
         *
         * @param warningMessage The warning message to display
         * @return The formatted status element
         */
        fun createWarningStatus(warningMessage: String): String {
            return "<status type=\"warning\">$warningMessage</status>"
        }


        /**
         * Formats a tool result message for sending to the AI.
         *
         * @param result The tool execution result
         * @return The formatted tool result message
         */
        fun formatToolResultForMessage(result: ToolResult): String {
            return formatToolResult(
                result = result,
                useMainModelProjection = false,
            )
        }

        /** Adds replay identity while keeping [ToolResult.toolName] as the UI-facing name. */
        fun formatToolResultForMessage(
            result: ToolResult,
            providerToolName: String,
            providerCallId: String?,
            providerResultTerminal: Boolean = true,
        ): String {
            return formatToolResult(
                result = result,
                useMainModelProjection = false,
                providerToolName = providerToolName,
                providerCallId = providerCallId,
                providerResultTerminal = providerResultTerminal,
            )
        }

        /**
         * Formats a tool result for the follow-up model request.
         *
         * The conversation renderer receives the complete result through
         * [formatToolResultForMessage]. The follow-up model receives this bounded projection so
         * uncited sources, search actions, and provider usage do not expand its context. Keeping
         * these paths separate is required because the renderer must still see all_sources.
         */
        fun formatToolResultForModel(result: ToolResult): String {
            return formatToolResultForModel(
                result = result,
                maxFormattedChars = ToolExecutionLimits.MAX_FINAL_TOOL_RESULT_MESSAGE_CHARS,
            )
        }

        private fun formatToolResultForModel(
            result: ToolResult,
            maxFormattedChars: Int,
        ): String =
            formatToolResult(
                result = result,
                useMainModelProjection = true,
                maxFormattedChars = maxFormattedChars,
            )

        private fun formatToolResult(
            result: ToolResult,
            useMainModelProjection: Boolean,
            providerToolName: String? = null,
            providerCallId: String? = null,
            providerResultTerminal: Boolean? = null,
            maxFormattedChars: Int = ToolExecutionLimits.MAX_FINAL_TOOL_RESULT_MESSAGE_CHARS,
        ): String {
            require(maxFormattedChars >= 0) { "Tool result character budget must not be negative" }
            return if (result.success) {
                val rawResult = result.result.toString()
                val projectedResult =
                    if (useMainModelProjection) {
                        OpenAIHostedWebSearchMainModelProjection.project(
                            toolName = result.toolName,
                            serializedResult = rawResult,
                        ) ?: rawResult
                    } else {
                        rawResult
                    }
                val xmlSafeResult =
                    if (result.toolName == OpenAIHostedWebSearchContract.TOOL_NAME) {
                        OpenAIHostedWebSearchToolResultMarkupCodec.encodeSerializedJson(
                            projectedResult
                        )
                    } else {
                        projectedResult
                    }
                val (toolPayload, imageLinkPayload) =
                    splitImageLinksForModel(xmlSafeResult)
                val minimumToolResultXml =
                    createBoundedToolResultXml(
                        toolName = result.toolName,
                        status = "success",
                        rawPayload = "",
                        maxXmlChars = 0,
                        providerToolName = providerToolName,
                        providerCallId = providerCallId,
                        providerResultTerminal = providerResultTerminal,
                    ) { payload ->
                        "<content>$payload</content>"
                    }
                val maxImageLinkChars =
                    (maxFormattedChars - minimumToolResultXml.length - 1).coerceAtLeast(0)
                val boundedImageLinkPayload =
                    takeWholeLinesWithin(imageLinkPayload, maxImageLinkChars)
                val imageLinkSuffix =
                    boundedImageLinkPayload.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
                val toolResultXml =
                    createBoundedToolResultXml(
                        toolName = result.toolName,
                        status = "success",
                        rawPayload = toolPayload,
                        maxXmlChars = (maxFormattedChars - imageLinkSuffix.length).coerceAtLeast(0),
                        providerToolName = providerToolName,
                        providerCallId = providerCallId,
                        providerResultTerminal = providerResultTerminal,
                    ) { payload ->
                        "<content>$payload</content>"
                    }

                toolResultXml + imageLinkSuffix
            } else {
                val errorPayload = buildString {
                    val message = result.error.orEmpty().trim()
                    val detail = result.result.toString().trim()
                    append(message)
                    if (detail.isNotEmpty()) {
                        if (message.isNotEmpty()) {
                            append("\n\n")
                        }
                        append(detail)
                    }
                }
                createBoundedToolResultXml(
                    toolName = result.toolName,
                    status = "error",
                    rawPayload = errorPayload,
                    maxXmlChars = maxFormattedChars,
                    providerToolName = providerToolName,
                    providerCallId = providerCallId,
                    providerResultTerminal = providerResultTerminal,
                ) { payload ->
                    "<content><error>$payload</error></content>"
                }
            }
        }

        private fun splitImageLinksForModel(rawPayload: String): Pair<String, String> {
            if (!MediaLinkParser.hasImageLinks(rawPayload)) {
                return rawPayload to ""
            }

            val imageLinkPayload =
                MediaLinkParser.extractImageLinkIds(rawPayload)
                    .joinToString("\n") { id -> """<link type="image" id="$id"></link>""" }
            val textPayload = MediaLinkParser.removeImageLinks(rawPayload).trim()
            val toolPayload =
                listOf("Image attached as multimodal input.", textPayload)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")

            return toolPayload to imageLinkPayload
        }

        fun buildBoundedToolResultMessage(results: List<ToolResult>): String {
            if (results.isEmpty()) {
                return ""
            }

            val maxChars = ToolExecutionLimits.MAX_FINAL_TOOL_RESULT_MESSAGE_CHARS
            val separator = "\n"
            // 总上限必须先为每个真实调用预留完整结果信封。直接停止追加或按字符截断会让
            // Provider 保留未闭合调用；剩余空间只用于公平分配各结果的 payload。
            val minimumMessages =
                results.map { result ->
                    formatToolResultForModel(result = result, maxFormattedChars = 0)
                }
            val minimumTotalChars =
                minimumMessages.sumOf { it.length } + separator.length * (results.size - 1)
            check(minimumTotalChars <= maxChars) {
                "Tool result envelopes require $minimumTotalChars characters, limit is $maxChars"
            }
            val builder = StringBuilder()
            var remainingPayloadBudget = maxChars - minimumTotalChars

            results.forEachIndexed { index, result ->
                if (builder.isNotEmpty()) {
                    builder.append(separator)
                }
                val remainingResultCount = results.size - index
                val allocatedPayloadBudget = remainingPayloadBudget / remainingResultCount
                val currentBudget = minimumMessages[index].length + allocatedPayloadBudget
                val formatted =
                    formatToolResultForModel(
                        result = result,
                        maxFormattedChars = currentBudget,
                    )
                check(formatted.length <= currentBudget) {
                    "Structured tool result exceeded its allocated character budget"
                }
                builder.append(formatted)
                val consumedPayloadBudget =
                    (formatted.length - minimumMessages[index].length).coerceAtLeast(0)
                remainingPayloadBudget -= consumedPayloadBudget
            }

            return builder.toString()
        }

        private fun takeWholeLinesWithin(content: String, maxChars: Int): String {
            if (content.isBlank() || maxChars <= 0) {
                return ""
            }
            val builder = StringBuilder()
            for (line in content.lineSequence()) {
                val additionalChars = line.length + if (builder.isEmpty()) 0 else 1
                if (builder.length + additionalChars > maxChars) {
                    break
                }
                if (builder.isNotEmpty()) {
                    builder.append('\n')
                }
                builder.append(line)
            }
            return builder.toString()
        }

        /**
         * Formats a message indicating multiple tool invocations were found but only one will be
         * processed.
         *
         * @param context The context to access string resources
         * @param toolName The name of the tool that will be processed
         * @return The formatted warning message
         */
        fun createMultipleToolsWarning(context: Context, toolName: String): String {
            return createWarningStatus(
                    context.getString(R.string.conversation_markup_multiple_tools_warning, toolName)
            )
        }

        /**
         * Creates a message for when a tool is not available.
         *
         * @param toolName The name of the unavailable tool
         * @param details Optional detailed error message
         * @return The formatted error message
         */
        fun createToolNotAvailableError(toolName: String, details: String? = null): String {
            val errorMessage = details ?: "The tool `$toolName` is not available."
            return createToolErrorStatus(toolName, errorMessage)
        }

        private fun createToolResultXml(
            toolName: String,
            status: String,
            content: String,
            providerToolName: String? = null,
            providerCallId: String? = null,
            providerResultTerminal: Boolean? = null,
        ): String {
            val tagName = ChatMarkupRegex.generateRandomToolResultTagName()
            val replayAttributes = buildString {
                providerToolName?.takeIf { it.isNotBlank() }?.let { name ->
                    append(" provider_tool_name=\"")
                    append(escapeXmlAttribute(name))
                    append('"')
                }
                providerCallId?.takeIf { it.isNotBlank() }?.let { callId ->
                    append(" provider_call_id=\"")
                    append(escapeXmlAttribute(callId))
                    append('"')
                }
                providerResultTerminal?.let { terminal ->
                    append(" provider_result_terminal=\"")
                    append(terminal)
                    append('"')
                }
            }
            return "<$tagName name=\"${escapeXmlAttribute(toolName)}\" " +
                "status=\"${escapeXmlAttribute(status)}\"$replayAttributes>" +
                "$content</$tagName>"
        }

        private fun createBoundedToolResultXml(
            toolName: String,
            status: String,
            rawPayload: String,
            maxXmlChars: Int = ToolExecutionLimits.MAX_FINAL_TOOL_RESULT_MESSAGE_CHARS,
            providerToolName: String? = null,
            providerCallId: String? = null,
            providerResultTerminal: Boolean? = null,
            bodyBuilder: (String) -> String
        ): String {
            val emptyXml =
                createToolResultXml(
                    toolName = toolName,
                    status = status,
                    content = bodyBuilder(""),
                    providerToolName = providerToolName,
                    providerCallId = providerCallId,
                    providerResultTerminal = providerResultTerminal,
                )
            val maxPayloadChars =
                (maxXmlChars - emptyXml.length)
                    .coerceAtLeast(0)
            val boundedPayload = truncatePayload(rawPayload, maxPayloadChars)
            return createToolResultXml(
                toolName = toolName,
                status = status,
                content = bodyBuilder(boundedPayload),
                providerToolName = providerToolName,
                providerCallId = providerCallId,
                providerResultTerminal = providerResultTerminal,
            )
        }

        private fun escapeXmlAttribute(value: String): String =
            value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

        private fun truncatePayload(payload: String, maxChars: Int): String {
            if (payload.length <= maxChars) {
                return payload
            }
            if (maxChars <= 0) {
                return ""
            }
            if (TOOL_RESULT_TRUNCATION_SUFFIX.length >= maxChars) {
                return TOOL_RESULT_TRUNCATION_SUFFIX.take(maxChars)
            }
            return payload
                .take(maxChars - TOOL_RESULT_TRUNCATION_SUFFIX.length)
                .trimEnd() + TOOL_RESULT_TRUNCATION_SUFFIX
        }

    }
}
