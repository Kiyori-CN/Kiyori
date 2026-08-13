package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchEvidenceParseResult
import com.ai.assistance.operit.util.AppLogger

internal data class OpenAIWebSearchEvidenceParseFailureLogKey(
    val renderInstanceKey: Any?,
    val code: String,
    val schemaRevision: Int?,
    val hasRequestId: Boolean,
    val fieldName: String?,
)

internal class OpenAIWebSearchEvidenceParseFailureLogGate(
    private val maxEntries: Int,
) {
    init {
        require(maxEntries > 0)
    }

    private val claimed =
        object :
            LinkedHashMap<OpenAIWebSearchEvidenceParseFailureLogKey, Unit>(
                maxEntries,
                0.75f,
                true,
            ) {
            override fun removeEldestEntry(
                eldest:
                    MutableMap.MutableEntry<
                        OpenAIWebSearchEvidenceParseFailureLogKey,
                        Unit
                    >?,
            ): Boolean = size > maxEntries
        }

    @Synchronized
    fun claim(key: OpenAIWebSearchEvidenceParseFailureLogKey): Boolean {
        if (claimed.containsKey(key)) {
            claimed[key]
            return false
        }
        claimed[key] = Unit
        return true
    }
}

internal object OpenAIWebSearchEvidenceParseFailureLogger {
    private const val TAG = "OpenAIWebSearchEvidence"
    private const val MAX_LOG_KEYS = 128
    private val gate =
        OpenAIWebSearchEvidenceParseFailureLogGate(MAX_LOG_KEYS)

    fun logOnce(
        renderInstanceKey: Any?,
        invalid: OpenAIHostedWebSearchEvidenceParseResult.Invalid,
    ) {
        val key =
            OpenAIWebSearchEvidenceParseFailureLogKey(
                renderInstanceKey = renderInstanceKey,
                code = invalid.code.name,
                schemaRevision = invalid.schemaRevision,
                hasRequestId = invalid.hasRequestId,
                fieldName = invalid.fieldName,
            )
        if (!gate.claim(key)) {
            return
        }
        AppLogger.w(
            TAG,
            "evidence_parse_invalid" +
                " code=${invalid.code.name}" +
                " schema_revision=${invalid.schemaRevision ?: "absent"}" +
                " request_id_present=${invalid.hasRequestId}" +
                " field=${invalid.fieldName ?: "none"}",
        )
    }
}
