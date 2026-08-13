package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject

/**
 * Hosted Web Search 发送给后续主模型的有界结果投影。
 *
 * 完整 all_sources 仍保存在原始 ToolResult 并交给专用结果卡；模型上下文只接收回答、marker、
 * cited sources、来源计数、warning 与必要诊断，避免把未引用来源再次完整发送给 provider。
 */
internal object OpenAIHostedWebSearchMainModelProjection {
    fun project(
        toolName: String,
        serializedResult: String,
    ): String? =
        when (
            val parsed =
                OpenAIHostedWebSearchEvidenceParser.parse(
                    toolName = toolName,
                    resultJson = serializedResult,
                )
        ) {
            OpenAIHostedWebSearchEvidenceParseResult.NotApplicable -> null
            is OpenAIHostedWebSearchEvidenceParseResult.Parsed ->
                OpenAIHostedWebSearchToolResultMarkupCodec.encodeSerializedJson(
                    buildParsedProjection(parsed.evidence).toString()
                )

            is OpenAIHostedWebSearchEvidenceParseResult.Invalid ->
                OpenAIHostedWebSearchToolResultMarkupCodec.encodeSerializedJson(
                    buildInvalidProjection(parsed).toString()
                )
        }

    private fun buildParsedProjection(
        evidence: OpenAIHostedWebSearchEvidence,
    ): JSONObject =
        JSONObject()
            .put("success", true)
            .put("schema_version", OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION)
            .put("projection", "main_model")
            .put("request_id", evidence.requestId)
            .put("response_id", evidence.responseId)
            .put("provider", "openai")
            .put("backend", "responses_web_search")
            .put("mode", evidence.mode)
            .put("model", evidence.model)
            .put("evidence_mode", evidence.evidenceMode.wireValue)
            .put("answer", evidence.answer)
            .put("answer_with_source_markers", evidence.answerWithSourceMarkers)
            .put(
                "cited_sources",
                JSONArray().apply {
                    evidence.citedSources.forEach { source ->
                        put(
                            JSONObject()
                                .put("source_id", source.sourceId)
                                .put("type", source.type)
                                .put("title", source.title)
                                .put("url", source.url ?: JSONObject.NULL)
                        )
                    }
                },
            )
            .put("source_summary", evidence.sourceSummary.toJson())
            .put("warnings", JSONArray(evidence.warnings))
            .put(
                "source_diagnostics",
                JSONObject()
                    .put(
                        "action_source_coverage",
                        evidence.sourceDiagnostics.actionSourceCoverage.wireValue,
                    )
                    .put(
                        "missing_source_action_count",
                        evidence.sourceDiagnostics.missingSourceActionIndexes.size,
                    )
                    .put(
                        "invalid_action_source_count",
                        evidence.sourceDiagnostics.invalidActionSourceCount,
                    )
                    .put(
                        "domain_policy_state",
                        evidence.sourceDiagnostics.domainPolicyState.wireValue,
                    ),
            )
            .put("execution_diagnostics", evidence.executionDiagnostics.toJson())

    private fun buildInvalidProjection(
        invalid: OpenAIHostedWebSearchEvidenceParseResult.Invalid,
    ): JSONObject =
        JSONObject()
            .put("success", false)
            .put("projection", "main_model")
            .put("schema_version", invalid.schemaRevision ?: JSONObject.NULL)
            .put(
                "error",
                JSONObject()
                    .put("code", "WEB_SEARCH_RESULT_INVALID")
                    .put("reason", invalid.code.name)
                    .put("message", invalid.sanitizedSummary)
                    .put("field", invalid.fieldName ?: JSONObject.NULL),
            )
}
