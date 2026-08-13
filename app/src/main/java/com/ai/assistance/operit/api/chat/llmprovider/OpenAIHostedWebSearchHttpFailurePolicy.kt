package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONObject

internal data class OpenAIHostedWebSearchHttpFailureDetails(
    val providerErrorType: String?,
    val providerErrorCode: String?,
    val providerMessage: String?,
    val providerRequestId: String?,
)

internal object OpenAIHostedWebSearchHttpFailurePolicy {
    private val SECRET_PATTERNS =
        listOf(
            Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""") to "Bearer <redacted>",
            Regex("""\bsk-[A-Za-z0-9_-]{8,}\b""") to "<redacted>",
            Regex(
                """(?i)\b(api[_-]?key|authorization|token|secret|cookie)\b\s*[:=]\s*["']?[^,\s}"']+"""
            ) to "\$1=<redacted>",
        )

    fun parseDetails(
        responseBody: String,
        providerRequestId: String?,
    ): OpenAIHostedWebSearchHttpFailureDetails {
        val normalizedRequestId = sanitizeRequestId(providerRequestId)
        val trimmedBody = responseBody.trim()
        if (trimmedBody.isEmpty()) {
            return OpenAIHostedWebSearchHttpFailureDetails(
                providerErrorType = null,
                providerErrorCode = null,
                providerMessage = null,
                providerRequestId = normalizedRequestId,
            )
        }

        val parsed = runCatching { JSONObject(trimmedBody) }.getOrNull()
        val errorValue = parsed?.opt("error")
        val errorObject = errorValue as? JSONObject
        val providerErrorType =
            sanitizeDiagnostic(
                errorObject?.nullableString("type") ?: parsed?.nullableString("type"),
                MAX_PROVIDER_ERROR_TYPE_CHARACTERS,
            )
        val providerErrorCode =
            sanitizeDiagnostic(
                errorObject?.nullableString("code") ?: parsed?.nullableString("code"),
                MAX_PROVIDER_ERROR_CODE_CHARACTERS,
            )
        val providerMessage =
            sanitizeDiagnostic(
                errorObject?.nullableString("message")
                    ?: parsed?.nullableString("message")
                    ?: (errorValue as? String)
                    ?: trimmedBody.takeIf { parsed == null },
                MAX_PROVIDER_MESSAGE_CHARACTERS,
            )
        return OpenAIHostedWebSearchHttpFailureDetails(
            providerErrorType = providerErrorType,
            providerErrorCode = providerErrorCode,
            providerMessage = providerMessage,
            providerRequestId = normalizedRequestId,
        )
    }

    fun exception(
        statusCode: Int,
        details: OpenAIHostedWebSearchHttpFailureDetails =
            OpenAIHostedWebSearchHttpFailureDetails(
                providerErrorType = null,
                providerErrorCode = null,
                providerMessage = null,
                providerRequestId = null,
            ),
    ): OpenAIHostedWebSearchException =
        when (statusCode) {
            401,
            403,
            ->
                OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.AUTH_REJECTED,
                    message =
                        detailedMessage(
                            base = "OpenAI Web Search authentication was rejected.",
                            details = details,
                        ),
                    httpStatus = statusCode,
                    providerErrorType = details.providerErrorType,
                    providerErrorCode = details.providerErrorCode,
                    providerRequestId = details.providerRequestId,
                )

            429 ->
                OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.RATE_LIMITED,
                    message =
                        detailedMessage(
                            base = "OpenAI Web Search was rate limited.",
                            details = details,
                        ),
                    httpStatus = statusCode,
                    providerErrorType = details.providerErrorType,
                    providerErrorCode = details.providerErrorCode,
                    providerRequestId = details.providerRequestId,
                )

            else ->
                OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.OPENAI_HTTP_FAILURE,
                    message =
                        detailedMessage(
                            base = "OpenAI Web Search failed with HTTP $statusCode.",
                            details = details,
                        ),
                    httpStatus = statusCode,
                    providerErrorType = details.providerErrorType,
                    providerErrorCode = details.providerErrorCode,
                    providerRequestId = details.providerRequestId,
                )
        }

    private fun detailedMessage(
        base: String,
        details: OpenAIHostedWebSearchHttpFailureDetails,
    ): String =
        buildString {
            append(base)
            details.providerMessage?.let { message ->
                append(" Provider message: ").append(message)
            }
            details.providerErrorType?.let { type ->
                append(" Provider type: ").append(type).append('.')
            }
            details.providerErrorCode?.let { code ->
                append(" Provider code: ").append(code).append('.')
            }
            details.providerRequestId?.let { requestId ->
                append(" Provider request ID: ").append(requestId).append('.')
            }
        }.take(MAX_FAILURE_MESSAGE_CHARACTERS)

    private fun sanitizeDiagnostic(
        value: String?,
        maxCharacters: Int,
    ): String? {
        var sanitized =
            value
                ?.replace(Regex("""[\u0000-\u001F\u007F]+"""), " ")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return null
        SECRET_PATTERNS.forEach { (pattern, replacement) ->
            sanitized = sanitized.replace(pattern, replacement)
        }
        return sanitized.trim().take(maxCharacters).takeIf(String::isNotEmpty)
    }

    private fun sanitizeRequestId(value: String?): String? {
        val sanitized =
            sanitizeDiagnostic(value, MAX_PROVIDER_REQUEST_ID_CHARACTERS)
                ?: return null
        return sanitized.takeIf { requestId ->
            requestId.all { character ->
                character.isLetterOrDigit() ||
                    character == '-' ||
                    character == '_' ||
                    character == '.' ||
                    character == ':' ||
                    character == '/' ||
                    character == ' '
            }
        }
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) {
            null
        } else {
            optString(name).trim().takeIf(String::isNotEmpty)
        }

    private const val MAX_PROVIDER_ERROR_TYPE_CHARACTERS = 96
    private const val MAX_PROVIDER_ERROR_CODE_CHARACTERS = 96
    private const val MAX_PROVIDER_MESSAGE_CHARACTERS = 384
    private const val MAX_PROVIDER_REQUEST_ID_CHARACTERS = 256
    private const val MAX_FAILURE_MESSAGE_CHARACTERS = 1024
}
