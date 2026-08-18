package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolCallIdentityContract
import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolCallKey
import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolCallSignature
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.AIToolHookDecision
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.core.tools.climode.CliToolModeSupport
import com.ai.assistance.operit.core.tools.climode.ToolExposureMode
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolInvocationLedgerEntity
import com.ai.assistance.operit.data.model.ToolInvocationStatus
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.audit.ConversationAuditEventRequest
import com.ai.assistance.operit.data.audit.ConversationAuditPayloadInput
import com.ai.assistance.operit.data.audit.ConversationAuditRepository
import com.ai.assistance.operit.data.model.ConversationAuditCompletenessStatus
import com.ai.assistance.operit.data.repository.ProviderExecutionRepository
import com.ai.assistance.operit.data.repository.ToolInvocationClaim
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.data.preferences.CharacterCardToolAccessResolver
import java.util.concurrent.ConcurrentHashMap
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/** Utility class for managing tool executions */
object ToolExecutionManager {
    private const val TAG = "ToolExecutionManager"
    private const val PACKAGE_PROXY_TOOL_NAME = "package_proxy"
    private const val PACKAGE_CALLER_NAME_PARAM = "__operit_package_caller_name"
    private const val PACKAGE_CHAT_ID_PARAM = "__operit_package_chat_id"
    private const val PACKAGE_CALLER_CARD_ID_PARAM = "__operit_package_caller_card_id"
    private val toolRuntimeContextThreadLocal = ThreadLocal<ToolRuntimeContext?>()

    data class ToolRuntimeContext(
        val callerCardId: String? = null,
        val toolExposureMode: ToolExposureMode = ToolExposureMode.FULL
    )

    private data class ResolvedToolTarget(
        val tool: AITool,
        val displayName: String
    )

    private fun ensureEndsWithNewline(content: String): String {
        return if (content.endsWith("\n")) content else "$content\n"
    }

    private fun resolveToolTarget(tool: AITool): ResolvedToolTarget {
        if (tool.name != PACKAGE_PROXY_TOOL_NAME &&
            tool.name != CliToolModeSupport.PROXY_TOOL_NAME
        ) {
            return ResolvedToolTarget(tool = tool, displayName = tool.name)
        }

        val targetToolName = tool.parameters
            .firstOrNull { it.name == "tool_name" }
            ?.value
            ?.trim()
            .orEmpty()
        if (targetToolName.isBlank()) {
            return ResolvedToolTarget(tool = tool, displayName = tool.name)
        }

        val forwardedParameters = resolveProxyParameters(tool)
        return ResolvedToolTarget(
            tool = AITool(name = targetToolName, parameters = forwardedParameters),
            displayName = targetToolName
        )
    }

    private fun resolveDisplayToolName(tool: AITool): String {
        return resolveToolTarget(tool).displayName
    }

    private fun isJsPackageTool(toolName: String, jsPackageNames: Set<String>): Boolean {
        val toolNameParts = toolName.split(':', limit = 2)
        val packageName = toolNameParts.getOrNull(0)
        return toolNameParts.size == 2 &&
            packageName != null &&
            jsPackageNames.contains(packageName)
    }

    internal fun currentToolRuntimeContext(): ToolRuntimeContext? =
        toolRuntimeContextThreadLocal.get()

    private fun addPackageContextParamIfMissing(
        params: MutableList<ToolParameter>,
        name: String,
        value: String?
    ) {
        if (value.isNullOrBlank()) {
            return
        }
        if (params.any { it.name == name }) {
            return
        }
        params.add(ToolParameter(name, value))
    }

    private fun injectPackageCallContext(
        invocation: ToolInvocation,
        jsPackageNames: Set<String>,
        callerName: String?,
        callerChatId: String?,
        callerCardId: String?
    ): ToolInvocation {
        val resolvedTargetTool = resolveToolTarget(invocation.tool).tool
        if (!isJsPackageTool(resolvedTargetTool.name, jsPackageNames)) {
            return invocation
        }

        val updatedParams = invocation.tool.parameters.toMutableList()
        addPackageContextParamIfMissing(updatedParams, PACKAGE_CALLER_NAME_PARAM, callerName)
        addPackageContextParamIfMissing(updatedParams, PACKAGE_CHAT_ID_PARAM, callerChatId)
        addPackageContextParamIfMissing(updatedParams, PACKAGE_CALLER_CARD_ID_PARAM, callerCardId)

        if (updatedParams.size == invocation.tool.parameters.size) {
            return invocation
        }

        return invocation.copy(
            tool = invocation.tool.copy(parameters = updatedParams)
        )
    }

    private fun getParameterValue(tool: AITool, name: String): String? {
        return tool.parameters.firstOrNull { it.name == name }?.value?.trim()
    }

    private fun isInvocationAllowedForRoleCard(
        invocation: ToolInvocation,
        roleCardToolAccess: com.ai.assistance.operit.data.preferences.ResolvedCharacterCardToolAccess
    ): Boolean {
        val toolName = invocation.tool.name.trim()
        val resolvedTarget = resolveToolTarget(invocation.tool).tool

        return when {
            toolName == CliToolModeSupport.SEARCH_TOOL_NAME -> true

            toolName == CliToolModeSupport.PROXY_TOOL_NAME -> {
                isResolvedTargetAllowedForRoleCard(resolvedTarget, roleCardToolAccess)
            }

            toolName == "use_package" -> {
                if (!roleCardToolAccess.isBuiltinToolAllowed("use_package")) {
                    false
                } else {
                    val sourceName = getParameterValue(invocation.tool, "package_name").orEmpty()
                    sourceName.isBlank() || roleCardToolAccess.isExternalSourceAllowed(sourceName)
                }
            }

            toolName == PACKAGE_PROXY_TOOL_NAME -> {
                if (!roleCardToolAccess.isBuiltinToolAllowed("package_proxy")) {
                    false
                } else {
                    val resolvedTargetName = resolvedTarget.name.trim()
                    if (resolvedTargetName.isBlank() || !resolvedTargetName.contains(':')) {
                        true
                    } else {
                        isResolvedTargetAllowedForRoleCard(resolvedTarget, roleCardToolAccess)
                    }
                }
            }

            toolName.contains(':') -> {
                val sourceName = toolName.substringBefore(':').trim()
                sourceName.isBlank() || roleCardToolAccess.isExternalSourceAllowed(sourceName)
            }

            else -> roleCardToolAccess.isBuiltinToolAllowed(toolName)
        }
    }

    private fun buildRoleCardDeniedResult(
        context: Context,
        invocation: ToolInvocation
    ): ToolResult {
        return ToolResult(
            toolName = resolveDisplayToolName(invocation.tool),
            success = false,
            result = StringResultData(""),
            error = context.getString(R.string.character_card_tool_access_denied_runtime)
        )
    }

    private fun isEnglishLanguage(context: Context): Boolean {
        return LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
    }

    private fun buildToolExposureDeniedResult(
        context: Context,
        invocation: ToolInvocation,
        toolExposureMode: ToolExposureMode
    ): ToolResult? {
        val toolName = invocation.tool.name.trim()
        val useEnglish = isEnglishLanguage(context)
        val errorMessage =
            when {
                toolExposureMode == ToolExposureMode.CLI &&
                    !CliToolModeSupport.isCliPublicTool(toolName) -> {
                    CliToolModeSupport.buildCliTopLevelRestrictionErrorMessage(
                        attemptedToolName = resolveDisplayToolName(invocation.tool),
                        useEnglish = useEnglish
                    )
                }

                toolExposureMode == ToolExposureMode.FULL &&
                    CliToolModeSupport.isCliPublicTool(toolName) -> {
                    CliToolModeSupport.buildCliModeUnavailableMessage(useEnglish)
                }

                else -> null
            } ?: return null

        val resultToolName =
            if (toolExposureMode == ToolExposureMode.CLI &&
                !CliToolModeSupport.isCliPublicTool(toolName)
            ) {
                resolveDisplayToolName(invocation.tool)
            } else {
                toolName
            }

        return ToolResult(
            toolName = resultToolName,
            success = false,
            result = StringResultData(""),
            error = errorMessage
        )
    }

    private fun isResolvedTargetAllowedForRoleCard(
        resolvedTarget: AITool,
        roleCardToolAccess: com.ai.assistance.operit.data.preferences.ResolvedCharacterCardToolAccess
    ): Boolean {
        val resolvedTargetName = resolvedTarget.name.trim()
        if (resolvedTargetName.isBlank()) {
            return true
        }

        val usePackageSourceName =
            if (resolvedTargetName == "use_package") {
                getParameterValue(resolvedTarget, "package_name")
            } else {
                null
            }

        return CliToolModeSupport.isToolNameAllowedForRoleCard(
            toolName = resolvedTargetName,
            usePackageSourceName = usePackageSourceName,
            roleCardToolAccess = roleCardToolAccess
        )
    }

    private fun resolveProxyParameters(tool: AITool): List<ToolParameter> {
        val paramsRaw = tool.parameters
            .firstOrNull { it.name == "params" }
            ?.value
            ?.trim()
            .orEmpty()
        if (paramsRaw.isBlank()) {
            return emptyList()
        }

        val paramsObject = runCatching { JSONObject(paramsRaw) }.getOrNull() ?: return emptyList()
        val forwardedParameters = mutableListOf<ToolParameter>()
        val keys = paramsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = paramsObject.opt(key)
            val valueString =
                if (value == null || value === JSONObject.NULL) {
                    "null"
                } else if (value is String) {
                    value
                } else {
                    value.toString()
                }
            forwardedParameters.add(ToolParameter(name = key, value = valueString))
        }
        return forwardedParameters
    }

    /**
     * 从 AI 响应中提取工具调用。
     * @param response AI 的响应字符串。
     * @return 检测到的工具调用列表。
     */
    suspend fun extractToolInvocations(response: String): List<ToolInvocation> {
        val invocations = mutableListOf<ToolInvocation>()
        val content = response

        val charStream = content.stream()
        val plugins = listOf(StreamXmlPlugin())

        charStream.splitBy(plugins).collect { group ->
            val chunkContent = StringBuilder()
            group.stream.collect { chunk -> chunkContent.append(chunk) }
            val chunkString = chunkContent.toString()

            if (chunkString.isEmpty()) return@collect

            if (group.tag is StreamXmlPlugin) {
                ChatMarkupRegex.toolCallPattern.findAll(chunkString).forEach { toolMatch ->
                    val toolName = toolMatch.groupValues.getOrNull(2) ?: return@forEach
                    val toolBody = toolMatch.groupValues.getOrNull(3).orEmpty()
                    val openingTag = toolMatch.value.substringBefore('>', "")

                    val parameters = mutableListOf<ToolParameter>()
                    MessageContentParser.toolParamPattern.findAll(toolBody)
                        .forEach { paramMatch ->
                            val paramName = paramMatch.groupValues[1]
                            val paramValue = paramMatch.groupValues[2]
                            parameters.add(ToolParameter(paramName, unescapeXml(paramValue)))
                        }

                    val tool = AITool(name = toolName, parameters = parameters)
                    invocations.add(
                        ToolInvocation(
                            tool = tool,
                            rawText = toolMatch.value,
                            responseLocation = toolMatch.range,
                            providerName =
                                extractXmlAttribute(openingTag, "provider_name"),
                            providerCallId =
                                extractXmlAttribute(openingTag, "provider_call_id"),
                            providerResponseId =
                                extractXmlAttribute(openingTag, "provider_response_id"),
                        )
                    )
                }
            }
        }

        AppLogger.d(
            TAG,
            "Found ${invocations.size} tool invocations: ${invocations.map { resolveDisplayToolName(it.tool) }}"
        )
        return invocations
    }

    /**
     * 在一次工具回合内按 Provider 原始 call_id 建立唯一执行列表。
     *
     * compatible Responses endpoint 没有可持久化的 remote response ID 时，数据库账本不能
     * 伪造远端身份；当前回合仍必须阻止同一 call_id 被重复执行。相同身份的同名同参调用只保留
     * 第一份，身份冲突则在任何权限检查或副作用前失败。
     */
    internal fun normalizeProviderInvocations(
        invocations: List<ToolInvocation>,
    ): List<ToolInvocation> {
        if (invocations.size < 2) {
            return invocations
        }

        val signatures = linkedMapOf<ProviderToolCallKey, ProviderToolCallSignature>()
        val normalized = ArrayList<ToolInvocation>(invocations.size)
        for (invocation in invocations) {
            val key =
                ProviderToolCallIdentityContract.key(
                    providerName = invocation.providerName,
                    callId = invocation.providerCallId,
                )
            if (key == null) {
                normalized.add(invocation)
                continue
            }

            val signature =
                ProviderToolCallIdentityContract.signature(
                    toolName = invocation.tool.name,
                    argumentsJson = canonicalToolArguments(invocation),
                )
            val existing = signatures[key]
            if (existing == null) {
                signatures[key] = signature
                normalized.add(invocation)
            } else {
                ProviderToolCallIdentityContract.requireSame(key, existing, signature)
            }
        }
        return normalized
    }

    private fun extractXmlAttribute(
        openingTag: String,
        attributeName: String,
    ): String? {
        if (openingTag.isEmpty()) {
            return null
        }
        val match =
            Regex("""\b${Regex.escape(attributeName)}="([^"]*)"""")
                .find(openingTag)
                ?: return null
        return unescapeXml(match.groupValues[1]).takeIf { it.isNotBlank() }
    }

    /**
     * Unescapes XML special characters
     * @param input The XML escaped string
     * @return Unescaped string
     */
    private fun unescapeXml(input: String): String {
        var result = input

        // 处理 CDATA 标记
        if (result.startsWith("<![CDATA[") && result.endsWith("]]>")) {
            result = result.substring(9, result.length - 3)
        }

        // 即使没有完整的 CDATA 标记，也尝试清理末尾的 ]]> 和开头的 <![CDATA[
        if (result.endsWith("]]>")) {
            result = result.substring(0, result.length - 3)
        }

        if (result.startsWith("<![CDATA[")) {
            result = result.substring(9)
        }

        return result.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    /**
     * Execute a tool safely, with parameter validation
     *
     * @param invocation The tool invocation to execute
     * @param executor The tool executor to use
     * @return The result of the tool execution
     */
    fun executeToolSafely(
        invocation: ToolInvocation,
        executor: ToolExecutor,
        toolHandler: AIToolHandler? = null
    ): Flow<ToolResult> {
        val validationResult = executor.validateParameters(invocation.tool)
        if (!validationResult.valid) {
            return flow {
                emit(
                    ToolResult(
                        toolName = invocation.tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Invalid parameters: ${validationResult.errorMessage}"
                    )
                )
            }
        }

        return executor.invokeAndStream(invocation.tool).catch { e ->
            AppLogger.e(TAG, "Tool execution error: ${invocation.tool.name}", e)
            toolHandler?.notifyToolExecutionError(invocation.tool, e)
            emit(
                ToolResult(
                    toolName = invocation.tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Tool execution error: ${e.message}"
                )
            )
        }
    }

    /**
     * Check if a tool requires permission and verify if it has permission
     *
     * @param toolHandler The AIToolHandler instance to use for permission checks
     * @param invocation The tool invocation to check permissions for
     * @return A pair containing (has permission, error result if no permission)
     */
    suspend fun checkToolPermission(
        toolHandler: AIToolHandler,
        invocation: ToolInvocation,
        toolExposureMode: ToolExposureMode = ToolExposureMode.FULL
    ): Pair<Boolean, ToolResult?> {
        val resolvedTarget = resolveToolTarget(invocation.tool)
        val permissionTool =
            if (toolExposureMode == ToolExposureMode.CLI &&
                invocation.tool.name == CliToolModeSupport.PROXY_TOOL_NAME
            ) {
                invocation.tool
            } else {
                resolvedTarget.tool
            }

        if (toolExposureMode == ToolExposureMode.CLI &&
            (invocation.tool.name == CliToolModeSupport.SEARCH_TOOL_NAME ||
                invocation.tool.name == CliToolModeSupport.PROXY_TOOL_NAME)
        ) {
            toolHandler.notifyToolPermissionChecked(
                permissionTool,
                granted = true,
                reason = "CLI public tool"
            )
            return Pair(true, null)
        }

        // 检查是否强制拒绝权限（deny_tool标记）
        val hasPromptForPermission = !invocation.rawText.contains("deny_tool")

        if (hasPromptForPermission) {
            // 检查权限，如果需要则弹出权限请求界面
            val toolPermissionSystem = toolHandler.getToolPermissionSystem()
            val hasPermission = toolPermissionSystem.checkToolPermission(permissionTool)

            // 如果权限被拒绝，创建错误结果
            if (!hasPermission) {
                val errorResult =
                    ToolResult(
                        toolName = resolvedTarget.displayName,
                        success = false,
                        result = StringResultData(""),
                        error = "User cancelled the tool execution."
                    )
                toolHandler.notifyToolPermissionChecked(
                    permissionTool,
                    granted = false,
                    reason = errorResult.error
                )
                return Pair(false, errorResult)
            }

            toolHandler.notifyToolPermissionChecked(permissionTool, granted = true)
            return Pair(true, null)
        }

        toolHandler.notifyToolPermissionChecked(
            permissionTool,
            granted = true,
            reason = "Permission check bypassed by deny_tool tag."
        )
        return Pair(true, null)
    }

    /**
     *
     * 执行工具调用，包括权限检查、并行/串行执行和结果聚合。
     * @param invocations 要执行的工具调用列表。
     * @param toolHandler AIToolHandler 的实例。
     * @param packageManager PackageManager 的实例。
     * @param collector 用于实时输出结果的 StreamCollector。
     * @return 所有工具执行结果的列表。
     */
    suspend fun executeInvocations(
        invocations: List<ToolInvocation>,
        context: Context,
        toolHandler: AIToolHandler,
        packageManager: PackageManager,
        collector: StreamCollector<String>,
        toolExposureMode: ToolExposureMode = ToolExposureMode.FULL,
        callerName: String? = null,
        callerChatId: String? = null,
        callerCardId: String? = null
    ): List<ToolResult> = coroutineScope {
        val normalizedInvocations = normalizeProviderInvocations(invocations)
        val auditedChatId = callerChatId?.takeIf { chatId -> chatId.isNotBlank() }
        val conversationAuditRepository =
            auditedChatId?.let { ConversationAuditRepository.from(context) }
        auditedChatId?.let { chatId ->
            val repository = requireNotNull(conversationAuditRepository)
            normalizedInvocations.forEach { invocation ->
                appendToolRequestAudit(
                    repository = repository,
                    chatId = chatId,
                    invocation = invocation,
                )
            }
        }
        if (normalizedInvocations.size != invocations.size) {
            AppLogger.w(
                TAG,
                "Provider-native duplicate tool calls removed before execution: " +
                    "${invocations.size} -> ${normalizedInvocations.size}"
            )
        }

        // 默认工具注册现在可能在启动阶段被延后；这里确保在真正执行工具前已完成注册
        // registerDefaultTools() 是幂等且线程安全的，可安全重复调用
        withContext(Dispatchers.Default) {
            toolHandler.registerDefaultTools()
        }

        val roleCardToolAccess = if (callerCardId.isNullOrBlank()) {
            null
        } else {
            runCatching {
                CharacterCardToolAccessResolver
                    .getInstance(context)
                    .resolve(callerCardId, packageManager)
            }.onFailure { error ->
                AppLogger.e(TAG, "角色卡工具权限解析失败: callerCardId=$callerCardId", error)
            }.getOrNull()
        }
        val toolRuntimeContext =
            ToolRuntimeContext(
                callerCardId = callerCardId,
                toolExposureMode = toolExposureMode
            )
        val providerExecutionRepository =
            if (
                normalizedInvocations.any { invocation ->
                    !invocation.providerResponseId.isNullOrBlank()
                }
            ) {
                ProviderExecutionRepository.from(context)
            } else {
                null
            }

        // 1. 顶层工具暴露模式拦截
        val toolExposurePermittedInvocations = mutableListOf<ToolInvocation>()
        val toolExposureDeniedResults = mutableListOf<ToolResult>()
        for (invocation in normalizedInvocations) {
            val deniedResult = buildToolExposureDeniedResult(context, invocation, toolExposureMode)
            if (deniedResult == null) {
                toolExposurePermittedInvocations.add(invocation)
            } else {
                toolExposureDeniedResults.add(deniedResult)
                appendToolResultAudit(
                    repository = conversationAuditRepository,
                    chatId = callerChatId,
                    invocation = invocation,
                    result = deniedResult,
                    eventType = "TOOL_CALL_FAILED",
                    summary = "工具调用被工具暴露策略拒绝",
                )
                toolHandler.notifyToolExecutionResult(invocation.tool, deniedResult)
                val toolResultStatusContent =
                    ConversationMarkupManager.formatToolResultForMessage(deniedResult)
                collector.emit(ensureEndsWithNewline(toolResultStatusContent))
            }
        }

        // 2. 角色卡工具权限拦截（优先于权限弹窗与包自动激活）
        val roleCardPermittedInvocations = mutableListOf<ToolInvocation>()
        val roleCardDeniedResults = mutableListOf<ToolResult>()
        for (invocation in toolExposurePermittedInvocations) {
            val deniedResult = if (roleCardToolAccess?.customEnabled == true &&
                !isInvocationAllowedForRoleCard(invocation, roleCardToolAccess)
            ) {
                buildRoleCardDeniedResult(context, invocation)
            } else {
                null
            }

            if (deniedResult == null) {
                roleCardPermittedInvocations.add(invocation)
            } else {
                roleCardDeniedResults.add(deniedResult)
                appendToolResultAudit(
                    repository = conversationAuditRepository,
                    chatId = callerChatId,
                    invocation = invocation,
                    result = deniedResult,
                    eventType = "TOOL_CALL_FAILED",
                    summary = "工具调用被角色卡权限策略拒绝",
                )
                toolHandler.notifyToolExecutionResult(invocation.tool, deniedResult)
                val toolResultStatusContent =
                    ConversationMarkupManager.formatToolResultForMessage(deniedResult)
                collector.emit(ensureEndsWithNewline(toolResultStatusContent))
            }
        }

        // 3. Hook 拦截与权限检查
        val permittedInvocations = mutableListOf<ToolInvocation>()
        val hookDeniedResults = mutableListOf<ToolResult>()
        val permissionDeniedResults = mutableListOf<ToolResult>()
        for (invocation in roleCardPermittedInvocations) {
            toolHandler.notifyToolCallRequested(invocation.tool)
            val interceptionTool = resolveToolTarget(invocation.tool).tool
            when (val interception = toolHandler.checkToolInterception(interceptionTool)) {
                AIToolHookDecision.Allow -> {
                    val (hasPermission, errorResult) =
                        checkToolPermission(toolHandler, invocation, toolExposureMode)
                    if (hasPermission) {
                        permittedInvocations.add(invocation)
                    } else {
                        errorResult?.let {
                            permissionDeniedResults.add(it)
                            appendToolResultAudit(
                                repository = conversationAuditRepository,
                                chatId = callerChatId,
                                invocation = invocation,
                                result = it,
                                eventType = "TOOL_CALL_FAILED",
                                summary = "工具调用未获得所需权限",
                            )
                            val toolResultStatusContent =
                                ConversationMarkupManager.formatToolResultForMessage(it)
                            collector.emit(ensureEndsWithNewline(toolResultStatusContent))
                        }
                    }
                }

                is AIToolHookDecision.Block -> {
                    val interceptedResult =
                        toolHandler.buildToolInterceptionResult(
                            resolveDisplayToolName(invocation.tool),
                            interception
                        )
                    hookDeniedResults.add(interceptedResult)
                    appendToolResultAudit(
                        repository = conversationAuditRepository,
                        chatId = callerChatId,
                        invocation = invocation,
                        result = interceptedResult,
                        eventType = "TOOL_CALL_FAILED",
                        summary = "工具调用被 Tool Hook 拦截",
                    )
                    toolHandler.notifyToolExecutionResult(invocation.tool, interceptedResult)
                    toolHandler.notifyToolExecutionFinished(invocation.tool)
                    val toolResultStatusContent =
                        ConversationMarkupManager.formatToolResultForMessage(interceptedResult)
                    collector.emit(ensureEndsWithNewline(toolResultStatusContent))
                }
            }
        }

        val injectedInvocations =
            if (callerName.isNullOrBlank() && callerChatId.isNullOrBlank() && callerCardId.isNullOrBlank()) {
                permittedInvocations
            } else {
                val jsPackageNames = packageManager.getAvailablePackages().keys
                permittedInvocations.map { invocation ->
                    injectPackageCallContext(
                        invocation = invocation,
                        jsPackageNames = jsPackageNames,
                        callerName = callerName,
                        callerChatId = callerChatId,
                        callerCardId = callerCardId
                    )
                }
            }

        // 4. 按并行/串行对工具进行分组
        val parallelizableToolNames = setOf(
            "list_files", "read_file", "read_file_part", "read_file_full", "file_exists",
            "find_files", "file_info", "grep_code", "calculate", "ffmpeg_info",
            "visit_web", "download_file"
        )
        val (parallelInvocations, serialInvocations) = injectedInvocations.partition {
            parallelizableToolNames.contains(
                it.tool.name
            )
        }

        // 5. 执行工具并收集聚合结果
        val executionResults = ConcurrentHashMap<ToolInvocation, ToolResult>()

        // 启动并行工具
        val parallelJobs = parallelInvocations.map { invocation ->
            async {
                val result =
                    executeAndEmitTool(
                        invocation = invocation,
                        toolHandler = toolHandler,
                        packageManager = packageManager,
                        collector = collector,
                        runtimeContext = toolRuntimeContext,
                        providerExecutionRepository = providerExecutionRepository,
                        conversationAuditRepository = conversationAuditRepository,
                        callerChatId = callerChatId,
                    )
                executionResults[invocation] = result
            }
        }

        // 顺序执行串行工具
        for (invocation in serialInvocations) {
            val result =
                executeAndEmitTool(
                    invocation = invocation,
                    toolHandler = toolHandler,
                    packageManager = packageManager,
                    collector = collector,
                    runtimeContext = toolRuntimeContext,
                    providerExecutionRepository = providerExecutionRepository,
                    conversationAuditRepository = conversationAuditRepository,
                    callerChatId = callerChatId,
                )
            executionResults[invocation] = result
        }

        // 等待所有并行任务完成
        parallelJobs.awaitAll()

        // 6. 按原始顺序重新排序结果
        val orderedAggregated = injectedInvocations.mapNotNull { executionResults[it] }

        // 7. 组合所有结果并返回
        toolExposureDeniedResults +
            roleCardDeniedResults +
            hookDeniedResults +
            permissionDeniedResults +
            orderedAggregated
    }

    /**
     * 封装单个工具的执行、实时输出和结果聚合的辅助函数
     */
    private suspend fun executeAndEmitTool(
        invocation: ToolInvocation,
        toolHandler: AIToolHandler,
        packageManager: PackageManager,
        collector: StreamCollector<String>,
        runtimeContext: ToolRuntimeContext,
        providerExecutionRepository: ProviderExecutionRepository?,
        conversationAuditRepository: ConversationAuditRepository?,
        callerChatId: String?,
    ): ToolResult {
        val toolName = invocation.tool.name
        val displayToolName = resolveDisplayToolName(invocation.tool)

        return withContext(toolRuntimeContextThreadLocal.asContextElement(runtimeContext)) {
            var ledgerIdentity: ToolLedgerIdentity? = null
            var ledgerSettled = false
            try {
                val ledgerClaim =
                    prepareToolLedgerClaim(
                        invocation = invocation,
                        repository = providerExecutionRepository,
                    )
                when (ledgerClaim) {
                    null -> Unit
                    is ToolLedgerClaim.Execute -> ledgerIdentity = ledgerClaim.identity
                    is ToolLedgerClaim.Reuse -> {
                        appendToolResultAudit(
                            repository = conversationAuditRepository,
                            chatId = callerChatId,
                            invocation = invocation,
                            result = ledgerClaim.result,
                            eventType = "TOOL_CALL_REUSED",
                            summary = "复用已持久化的工具执行结果",
                        )
                        emitToolResult(ledgerClaim.result, collector)
                        toolHandler.notifyToolExecutionResult(invocation.tool, ledgerClaim.result)
                        return@withContext ledgerClaim.result
                    }

                    is ToolLedgerClaim.DoNotExecute -> {
                        appendToolResultAudit(
                            repository = conversationAuditRepository,
                            chatId = callerChatId,
                            invocation = invocation,
                            result = ledgerClaim.result,
                            eventType = "TOOL_CALL_FAILED",
                            summary = "工具账本拒绝重复执行",
                        )
                        emitToolResult(ledgerClaim.result, collector)
                        toolHandler.notifyToolExecutionResult(invocation.tool, ledgerClaim.result)
                        return@withContext ledgerClaim.result
                    }
                }

                val executor = toolHandler.getToolExecutorOrActivate(toolName)
                if (executor == null) {
                    val errorMessage =
                        buildToolNotAvailableErrorMessage(toolName, packageManager, toolHandler)
                    val notAvailableContent =
                        ConversationMarkupManager.createToolNotAvailableError(toolName, errorMessage)
                    val notAvailableResult =
                        ToolResult(
                            toolName = displayToolName,
                            success = false,
                            result = StringResultData(""),
                            error = errorMessage
                        )
                    appendToolResultAudit(
                        repository = conversationAuditRepository,
                        chatId = callerChatId,
                        invocation = invocation,
                        result = notAvailableResult,
                        eventType = "TOOL_CALL_FAILED",
                        summary = "工具不可用",
                    )
                    collector.emit(ensureEndsWithNewline(notAvailableContent))
                    toolHandler.notifyToolExecutionResult(invocation.tool, notAvailableResult)
                    completeToolLedger(
                        repository = providerExecutionRepository,
                        identity = ledgerIdentity,
                        result = notAvailableResult,
                    )
                    ledgerSettled = ledgerIdentity != null
                    return@withContext notAvailableResult
                }

                toolHandler.notifyToolExecutionStarted(invocation.tool)
                appendToolStartedAudit(
                    repository = conversationAuditRepository,
                    chatId = callerChatId,
                    invocation = invocation,
                )

                val collectedResults = mutableListOf<ToolResult>()
                executeToolSafely(invocation, executor, toolHandler).collect { result ->
                    collectedResults.add(result)
                    appendToolResultAudit(
                        repository = conversationAuditRepository,
                        chatId = callerChatId,
                        invocation = invocation,
                        result = result,
                        eventType = "TOOL_RESULT_EMITTED",
                        summary = "工具返回了一个语义结果",
                    )
                    // 实时输出每个结果
                    val toolResultStatusContent =
                        ConversationMarkupManager.formatToolResultForMessage(result)
                    collector.emit(ensureEndsWithNewline(toolResultStatusContent))
                }

                // 为此调用聚合最终结果
                if (collectedResults.isEmpty()) {
                    val emptyResult =
                        ToolResult(
                            toolName = displayToolName,
                            success = false,
                            result = StringResultData(""),
                            error = "The tool execution returned no results."
                        )
                    appendToolResultAudit(
                        repository = conversationAuditRepository,
                        chatId = callerChatId,
                        invocation = invocation,
                        result = emptyResult,
                        eventType = "TOOL_CALL_FAILED",
                        summary = "工具执行未返回结果",
                    )
                    toolHandler.notifyToolExecutionResult(invocation.tool, emptyResult)
                    completeToolLedger(
                        repository = providerExecutionRepository,
                        identity = ledgerIdentity,
                        result = emptyResult,
                    )
                    ledgerSettled = ledgerIdentity != null
                    return@withContext emptyResult
                }

                val lastResult = collectedResults.last()
                val combinedResultString = collectedResults.joinToString("\n") { res ->
                    (if (res.success) res.result.toString() else "Step error: ${res.error ?: "Unknown error"}").trim()
                }.trim()

                val finalResult =
                    ToolResult(
                        toolName = displayToolName,
                        success = lastResult.success,
                        result = StringResultData(combinedResultString),
                        error = lastResult.error
                    )
                appendToolResultAudit(
                    repository = conversationAuditRepository,
                    chatId = callerChatId,
                    invocation = invocation,
                    result = finalResult,
                    eventType =
                        if (finalResult.success) {
                            "TOOL_CALL_COMPLETED"
                        } else {
                            "TOOL_CALL_FAILED"
                        },
                    summary =
                        if (finalResult.success) {
                            "工具调用已完成"
                        } else {
                            "工具调用以失败结果完成"
                        },
                )
                toolHandler.notifyToolExecutionResult(invocation.tool, finalResult)
                completeToolLedger(
                    repository = providerExecutionRepository,
                    identity = ledgerIdentity,
                    result = finalResult,
                )
                ledgerSettled = ledgerIdentity != null
                return@withContext finalResult
            } catch (error: Exception) {
                if (!ledgerSettled) {
                    failToolLedger(
                        repository = providerExecutionRepository,
                        identity = ledgerIdentity,
                        error = error,
                    )
                }
                if (conversationAuditRepository != null && !callerChatId.isNullOrBlank()) {
                    try {
                        conversationAuditRepository.appendEvent(
                            ConversationAuditEventRequest(
                                chatId = callerChatId,
                                category = "TOOL",
                                eventType = "TOOL_CALL_FAILED",
                                actor = "KIYORI",
                                summary = "工具执行抛出异常：$displayToolName",
                                providerCallId = invocation.providerCallId,
                                terminalState = "FAILED",
                                completeness =
                                    ConversationAuditCompletenessStatus.IN_PROGRESS,
                                payloads =
                                    listOf(
                                        ConversationAuditPayloadInput.text(
                                            label = "tool_error",
                                            role = "error",
                                            value = error.stackTraceToString(),
                                            mediaType = "text/x-java-stacktrace",
                                        )
                                ),
                            )
                        )
                    } catch (auditError: Exception) {
                        error.addSuppressed(auditError)
                        AppLogger.e(TAG, "工具异常审计写入失败", auditError)
                    }
                }
                throw error
            } finally {
                toolHandler.notifyToolExecutionFinished(invocation.tool)
            }
        }
    }

    private data class ToolLedgerIdentity(
        val provider: String,
        val remoteResponseId: String,
        val callId: String,
    )

    private sealed interface ToolLedgerClaim {
        data class Execute(val identity: ToolLedgerIdentity) : ToolLedgerClaim

        data class Reuse(val result: ToolResult) : ToolLedgerClaim

        data class DoNotExecute(val result: ToolResult) : ToolLedgerClaim
    }

    private suspend fun prepareToolLedgerClaim(
        invocation: ToolInvocation,
        repository: ProviderExecutionRepository?,
    ): ToolLedgerClaim? {
        val provider = invocation.providerName
        val remoteResponseId = invocation.providerResponseId
        val callId = invocation.providerCallId
        if (remoteResponseId.isNullOrBlank()) {
            // Chat Completions 仍需保留原始 call_id 供下一 hop 重放，但没有 response_id 时不存在
            // 可持久化执行身份，不能把它误接入 Responses exactly-once 账本。
            return null
        }
        requireNotNull(repository) {
            "Provider execution repository is required for provider-native tool calls"
        }
        require(!provider.isNullOrBlank()) { "Provider-native tool call has no provider name" }
        require(!remoteResponseId.isNullOrBlank()) {
            "Provider-native tool call has no response ID"
        }
        require(!callId.isNullOrBlank()) { "Provider-native tool call has no call ID" }

        val execution =
            repository.getExecutionByRemoteResponse(provider, remoteResponseId)
                ?: error(
                    "Provider execution not found for $provider/$remoteResponseId"
                )
        val argumentsJson = canonicalToolArguments(invocation)
        val now = System.currentTimeMillis()
        repository.registerToolInvocation(
            ToolInvocationLedgerEntity(
                provider = provider,
                remoteResponseId = remoteResponseId,
                callId = callId,
                localExecutionId = execution.localExecutionId,
                toolName = invocation.tool.name,
                argumentsJson = argumentsJson,
                argumentsSha256 = sha256(argumentsJson),
                status = ToolInvocationStatus.PENDING.name,
                createdAt = now,
                updatedAt = now,
            )
        )

        return when (
            val claim =
                repository.claimToolInvocation(
                    provider = provider,
                    remoteResponseId = remoteResponseId,
                    callId = callId,
                )
        ) {
            is ToolInvocationClaim.Claimed ->
                ToolLedgerClaim.Execute(
                    ToolLedgerIdentity(provider, remoteResponseId, callId)
                )

            is ToolInvocationClaim.Completed -> {
                val resultJson =
                    claim.invocation.resultJson
                        ?: error(
                            "Completed tool ledger row has no result for " +
                                "$provider/$remoteResponseId/$callId"
                        )
                ToolLedgerClaim.Reuse(decodeToolResult(resultJson))
            }

            is ToolInvocationClaim.AlreadyRunning ->
                ToolLedgerClaim.DoNotExecute(
                    ToolResult(
                        toolName = invocation.tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                            "The provider tool call is already running; Kiyori will not execute it twice.",
                    )
                )

            is ToolInvocationClaim.Failed ->
                ToolLedgerClaim.DoNotExecute(
                    ToolResult(
                        toolName = invocation.tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                            claim.invocation.errorMessage
                                ?: "The provider tool call previously failed and will not be executed again.",
                    )
                )
        }
    }

    private suspend fun completeToolLedger(
        repository: ProviderExecutionRepository?,
        identity: ToolLedgerIdentity?,
        result: ToolResult,
    ) {
        if (repository == null || identity == null) {
            return
        }
        repository.completeToolInvocation(
            provider = identity.provider,
            remoteResponseId = identity.remoteResponseId,
            callId = identity.callId,
            resultJson = encodeToolResult(result),
        )
    }

    private suspend fun failToolLedger(
        repository: ProviderExecutionRepository?,
        identity: ToolLedgerIdentity?,
        error: Exception,
    ) {
        if (repository == null || identity == null) {
            return
        }
        repository.failToolInvocation(
            provider = identity.provider,
            remoteResponseId = identity.remoteResponseId,
            callId = identity.callId,
            errorMessage = error.message ?: error::class.java.simpleName,
        )
    }

    private suspend fun emitToolResult(
        result: ToolResult,
        collector: StreamCollector<String>,
    ) {
        val content = ConversationMarkupManager.formatToolResultForMessage(result)
        collector.emit(ensureEndsWithNewline(content))
    }

    private suspend fun appendToolRequestAudit(
        repository: ConversationAuditRepository,
        chatId: String,
        invocation: ToolInvocation,
    ) {
        repository.appendEvent(
            ConversationAuditEventRequest(
                chatId = chatId,
                category = "TOOL",
                eventType = "TOOL_CALL_REQUESTED",
                actor = "PROVIDER",
                summary = "Provider 请求调用工具：${resolveDisplayToolName(invocation.tool)}",
                providerCallId = invocation.providerCallId,
                completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
                payloads =
                    listOf(
                        ConversationAuditPayloadInput.text(
                            label = "tool_arguments",
                            role = "tool",
                            value = canonicalToolArguments(invocation),
                            mediaType = "application/json",
                        )
                    ),
            )
        )
    }

    private suspend fun appendToolStartedAudit(
        repository: ConversationAuditRepository?,
        chatId: String?,
        invocation: ToolInvocation,
    ) {
        if (repository == null || chatId.isNullOrBlank()) {
            return
        }
        repository.appendEvent(
            ConversationAuditEventRequest(
                chatId = chatId,
                category = "TOOL",
                eventType = "TOOL_CALL_STARTED",
                actor = "KIYORI",
                summary = "开始执行工具：${resolveDisplayToolName(invocation.tool)}",
                providerCallId = invocation.providerCallId,
                completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
            )
        )
    }

    private suspend fun appendToolResultAudit(
        repository: ConversationAuditRepository?,
        chatId: String?,
        invocation: ToolInvocation,
        result: ToolResult,
        eventType: String,
        summary: String,
    ) {
        if (repository == null || chatId.isNullOrBlank()) {
            return
        }
        repository.appendEvent(
            ConversationAuditEventRequest(
                chatId = chatId,
                category = "TOOL",
                eventType = eventType,
                actor = "KIYORI",
                summary = "$summary：${resolveDisplayToolName(invocation.tool)}",
                providerCallId = invocation.providerCallId,
                terminalState =
                    when (eventType) {
                        "TOOL_CALL_COMPLETED", "TOOL_CALL_REUSED" -> "COMPLETED"
                        "TOOL_CALL_FAILED" -> "FAILED"
                        else -> null
                    },
                completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
                payloads =
                    listOf(
                        ConversationAuditPayloadInput.text(
                            label = "tool_result",
                            role = "tool",
                            value = encodeToolResult(result),
                            mediaType = "application/json",
                        )
                    ),
            )
        )
    }

    private fun canonicalToolArguments(invocation: ToolInvocation): String {
        val arguments = JSONObject()
        invocation.tool.parameters.sortedBy { parameter -> parameter.name }.forEach { parameter ->
            arguments.put(parameter.name, parameter.value)
        }
        return arguments.toString()
    }

    private fun encodeToolResult(result: ToolResult): String =
        JSONObject()
            .apply {
                put("toolName", result.toolName)
                put("success", result.success)
                put("result", result.result.toString())
                if (result.error == null) {
                    put("error", JSONObject.NULL)
                } else {
                    put("error", result.error)
                }
            }
            .toString()

    private fun decodeToolResult(json: String): ToolResult {
        val value = JSONObject(json)
        return ToolResult(
            toolName = value.getString("toolName"),
            success = value.getBoolean("success"),
            result = StringResultData(value.optString("result", "")),
            error =
                if (value.isNull("error")) {
                    null
                } else {
                    value.getString("error")
                },
        )
    }

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    /**
     * 构建工具不可用的错误信息，统一逻辑避免重复
     */
    private suspend fun buildToolNotAvailableErrorMessage(
        toolName: String,
        packageManager: PackageManager,
        toolHandler: AIToolHandler
    ): String {
        return when {
            toolName.contains('.') && !toolName.contains(':') -> {
                val parts = toolName.split('.', limit = 2)
                "Tool invocation syntax error: for tools inside a package, use the 'packName:toolName' format instead of '${toolName}'. You may want to call '${parts.getOrNull(0)}:${parts.getOrNull(1)}'."
            }

            toolName.contains(':') -> {
                val parts = toolName.split(':', limit = 2)
                val packName = parts[0]
                val toolNamePart = parts.getOrNull(1) ?: ""
                val isJsPackageAvailable = packageManager.getAvailablePackages().containsKey(packName)
                val isMcpServerAvailable = packageManager.getAvailableServerPackages().containsKey(packName)
                val isAvailable = isJsPackageAvailable || isMcpServerAvailable

                if (!isAvailable) {
                    "The tool package or MCP server '$packName' does not exist."
                } else {
                    // 包存在，检查是否已激活（通过检查该包的任何工具是否已注册）
                    val packageTools =
                        packageManager.getPackageTools(packName)?.tools ?: emptyList()
                    val isAdviceTool = packageTools.any { it.advice && it.name == toolNamePart }
                    val isPackageActivated = packageTools
                        .filter { !it.advice }
                        .any { toolHandler.getToolExecutor("$packName:${it.name}") != null }

                    if (isAdviceTool) {
                        "Tool '$toolNamePart' is an advice-only entry in package '$packName' and is not executable."
                    } else if (isPackageActivated) {
                        // 包已激活但工具不存在
                        "Tool '$toolNamePart' does not exist in tool package '$packName'. Please use the 'use_package' tool and specify package name '$packName' to list all available tools in this package."
                    } else {
                        // 包未激活
                        "Tool package '$packName' is not activated. Auto-activation was attempted but failed, or tool '$toolNamePart' does not exist. Please use 'use_package' with package name '$packName' to check available tools."
                    }
                }
            }

            else -> {
                // 检查是否直接把包名当作工具名调用了
                val isPackageName = packageManager.getAvailablePackages().containsKey(toolName)
                if (isPackageName) {
                    "Error: '$toolName' is a tool package, not a tool. Please use the 'use_package' tool with package name '$toolName' to activate this package before using its tools."
                } else {
                    "Tool '${toolName}' is unavailable or does not exist. If this is a tool inside a package, call it using the 'packName:toolName' format."
                }
            }
        }
    }

}
