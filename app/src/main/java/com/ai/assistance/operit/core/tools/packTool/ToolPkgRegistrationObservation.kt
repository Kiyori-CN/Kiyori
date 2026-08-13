package com.ai.assistance.operit.core.tools.packTool

import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchContract

internal data class ToolPkgRegistrationExecutionObservation(
    val elapsedMs: Long,
    val threadName: String,
) {
    init {
        require(elapsedMs >= 0L) { "elapsedMs must not be negative" }
        require(threadName.isNotBlank()) { "threadName must not be blank" }
    }
}

/**
 * ToolPkg 注册成功日志的唯一格式 owner。
 *
 * 这里只输出制品身份、来源分类、注册线程和单调 wall time，不输出绝对路径、脚本正文、环境变量
 * 或异常文本。否则现场日志无法证明实际加载制品，同时又会把用户私有目录或插件数据带入日志。
 */
internal object ToolPkgRegistrationObservationPolicy {
    fun format(
        runtime: ToolPkgContainerRuntime,
        execution: ToolPkgRegistrationExecutionObservation,
    ): String =
        format(
            toolPkgId = runtime.packageName,
            version = runtime.version,
            artifactSha256 = runtime.artifactSha256,
            sourceKind = runtime.sourceType.wireValue,
            registrationThread = execution.threadName,
            elapsedMs = execution.elapsedMs,
            responseSchemaRevision =
                if (
                    runtime.packageName == OpenAIHostedWebSearchContract.TOOLPKG_ID
                ) {
                    OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION
                } else {
                    null
                },
        )

    internal fun format(
        toolPkgId: String,
        version: String,
        artifactSha256: String,
        sourceKind: String,
        registrationThread: String,
        elapsedMs: Long,
        responseSchemaRevision: Int?,
    ): String {
        require(elapsedMs >= 0L) { "elapsedMs must not be negative" }
        require(responseSchemaRevision == null || responseSchemaRevision > 0) {
            "responseSchemaRevision must be positive"
        }
        val digest = artifactSha256.trim().lowercase()
        require(digest.matches(Regex("[0-9a-f]{64}"))) {
            "artifactSha256 must be a SHA-256 hex digest"
        }

        return buildString {
            append("ToolPkg registration completed: ")
            append("toolpkg_id=").append(sanitizeToken(toolPkgId))
            append(" version=").append(sanitizeToken(version))
            append(" artifact_sha256=").append(digest)
            append(" source_kind=").append(sanitizeToken(sourceKind))
            append(" registration_thread=").append(sanitizeToken(registrationThread))
            append(" elapsed_ms=").append(elapsedMs)
            if (responseSchemaRevision != null) {
                append(" response_schema_revision=").append(responseSchemaRevision)
            }
        }
    }

    private fun sanitizeToken(value: String): String =
        value
            .trim()
            .take(MAX_TOKEN_CHARACTERS)
            .map { character ->
                if (
                    character.isLetterOrDigit() ||
                        character == '.' ||
                        character == '_' ||
                        character == ':' ||
                        character == '-'
                ) {
                    character
                } else {
                    '_'
                }
            }
            .joinToString("")
            .ifBlank { "empty" }

    private val ToolPkgSourceType.wireValue: String
        get() =
            when (this) {
                ToolPkgSourceType.ASSET -> "asset"
                ToolPkgSourceType.EXTERNAL -> "external"
            }

    private const val MAX_TOKEN_CHARACTERS = 96
}
