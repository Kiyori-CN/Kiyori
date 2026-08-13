package com.ai.assistance.operit.core.tools.javascript

import java.util.Locale
import org.json.JSONObject
import org.json.JSONTokener

internal enum class JsToolPkgTerminalErrorSeverity {
    WARNING,
    ERROR,
}

internal data class PendingJsErrorDiagnostic(
    val errorType: String,
    val errorLine: Int,
    val stackPresent: Boolean,
) {
    fun format(): String =
        "error_type=$errorType error_line=$errorLine stack_present=$stackPresent"
}

internal data class JsToolPkgTerminalErrorDecision(
    val severity: JsToolPkgTerminalErrorSeverity,
    val code: String?,
    val messageClass: String,
    val messageCharacters: Int,
    val diagnostic: PendingJsErrorDiagnostic?,
) {
    fun format(): String =
        buildString {
            append("JS terminal failure: severity=")
            append(severity.name.lowercase(Locale.ROOT))
            append(" code=")
            append(code ?: "none")
            append(" message_class=")
            append(messageClass)
            append(" message_chars=")
            append(messageCharacters)
            diagnostic?.let {
                append(' ')
                append(it.format())
            }
        }
}

internal object JsToolPkgTerminalErrorPolicy {
    private const val MAX_ERROR_TYPE_CHARACTERS = 80
    private val stableCode = Regex("""^[A-Z][A-Z0-9_]{1,63}$""")
    private val codePrefix = Regex("""^\[([A-Z][A-Z0-9_]{1,63})]\s*""")
    private val stableErrorType = Regex("""^[A-Za-z][A-Za-z0-9_.-]{0,79}$""")
    private val expectedArgumentCodes =
        setOf(
            "INVALID_ARGUMENT",
            "CONFIG_SOURCE_INVALID",
        )

    fun captureDiagnostic(
        errorType: String,
        errorLine: Int,
        errorStack: String,
    ): PendingJsErrorDiagnostic =
        PendingJsErrorDiagnostic(
            errorType = normalizeErrorType(errorType),
            errorLine = errorLine.coerceAtLeast(0),
            stackPresent = errorStack.isNotBlank(),
        )

    fun decide(
        rawError: String,
        diagnostic: PendingJsErrorDiagnostic?,
    ): JsToolPkgTerminalErrorDecision {
        val extracted = extract(rawError)
        val code = extracted.code
        return JsToolPkgTerminalErrorDecision(
            severity =
                if (code in expectedArgumentCodes) {
                    JsToolPkgTerminalErrorSeverity.WARNING
                } else {
                    JsToolPkgTerminalErrorSeverity.ERROR
                },
            code = code,
            messageClass = classifyMessage(extracted.message, code),
            messageCharacters = extracted.message.length,
            diagnostic = diagnostic,
        )
    }

    private data class ExtractedError(
        val code: String?,
        val message: String,
    )

    private fun extract(rawError: String): ExtractedError {
        val normalized = rawError.trim()
        val root =
            runCatching { JSONTokener(normalized).nextValue() }
                .getOrNull() as? JSONObject
        val nestedError = root?.optJSONObject("error")
        val message =
            listOf(
                nestedError?.optString("message"),
                root?.optString("formatted"),
                root?.optString("message"),
                normalized,
            ).first { candidate -> !candidate.isNullOrBlank() }.orEmpty().trim()
        val explicitCode =
            listOf(
                nestedError?.optString("code"),
                root?.optString("code"),
            ).firstOrNull { candidate -> candidate?.matches(stableCode) == true }
        val prefixMatch = codePrefix.find(message)
        val code = explicitCode ?: prefixMatch?.groupValues?.get(1)
        val messageWithoutCode =
            if (prefixMatch != null) {
                message.removeRange(prefixMatch.range).trim()
            } else {
                message
            }
        return ExtractedError(
            code = code,
            message = messageWithoutCode,
        )
    }

    private fun classifyMessage(message: String, code: String?): String {
        if (code != null) {
            return "coded_failure"
        }
        val normalized = message.lowercase(Locale.ROOT)
        return when {
            normalized.isBlank() -> "empty_message"
            "timed out" in normalized -> "execution_timeout"
            "runtime bridge" in normalized -> "runtime_bridge_failure"
            "serialization" in normalized -> "serialization_failure"
            "promise rejection" in normalized -> "promise_rejection"
            "script error" in normalized -> "script_error"
            else -> "unclassified_failure"
        }
    }

    private fun normalizeErrorType(errorType: String): String {
        val normalized = errorType.trim().take(MAX_ERROR_TYPE_CHARACTERS)
        return normalized.takeIf(stableErrorType::matches) ?: "Error"
    }
}
