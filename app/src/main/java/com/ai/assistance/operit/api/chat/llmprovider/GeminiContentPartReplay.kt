package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.ChatMarkupRegex
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal data class GeminiContentPartSnapshot(
    val modelName: String,
    val parts: JSONArray,
)

/**
 * Owns the response-only protocol state for one Gemini HTTP attempt.
 *
 * A provider instance is leased and can outlive one request. Keeping these fields on the provider
 * would let a failed attempt, a retry, or another request contribute Parts to the wrong persisted
 * model turn. Each attempt therefore gets a fresh state object and only the successful attempt is
 * finalized into replay metadata.
 */
internal class GeminiResponseAttemptState(
    private val modelName: String,
) {
    private val replayParts = JSONArray()
    private var metadataFinalized = false

    var isInThinkingMode: Boolean = false

    fun appendResponseParts(parts: JSONArray) {
        check(!metadataFinalized) {
            "Gemini response Parts cannot be appended after metadata finalization"
        }
        for (index in 0 until parts.length()) {
            replayParts.put(
                JSONObject(
                    parts.getJSONObject(index).toString()
                )
            )
        }
    }

    fun finishMetadataTag(): String? {
        check(!metadataFinalized) {
            "Gemini response metadata was already finalized"
        }
        metadataFinalized = true
        return GeminiContentPartReplayCodec.createMetadataTag(
            modelName = modelName,
            parts = replayParts,
        )
    }
}

/**
 * Gemini thought signatures are opaque protocol state attached to a model-generated Part.
 * Persisting only the signature loses its exact Part position and cannot represent signatures on
 * thought text. Store the original Part sequence so multi-step function calling can replay the
 * model turn byte-stably after a tool hop or process restart.
 */
internal object GeminiContentPartReplayCodec {
    fun createMetadataTag(
        modelName: String,
        parts: JSONArray,
    ): String? {
        validateModelName(modelName)
        if (parts.length() == 0) {
            return null
        }
        val clonedParts = cloneAndValidateParts(parts)
        val hasProtocolState =
            (0 until clonedParts.length()).any { index ->
                val part = clonedParts.getJSONObject(index)
                part.optJSONObject("functionCall") != null ||
                    part.optBoolean("thought", false) ||
                    part.optString(
                        "thoughtSignature",
                        part.optString("thought_signature", ""),
                    ).isNotBlank()
            }
        if (!hasProtocolState) {
            return null
        }
        val payload =
            JSONObject().apply {
                put("version", 1)
                put("model", modelName)
                put("parts", clonedParts)
            }
        return ChatMarkupRegex.geminiContentPartsMetaTag(
            Base64.getEncoder().encodeToString(
                payload.toString().toByteArray(Charsets.UTF_8)
            )
        )
    }

    fun extractSnapshots(content: String): List<GeminiContentPartSnapshot> {
        return ChatMarkupRegex.extractGeminiContentPartsPayloads(content)
            .map(::decodePayload)
    }

    fun replayPartsForModel(
        snapshots: List<GeminiContentPartSnapshot>,
        currentModelName: String,
    ): JSONArray {
        validateModelName(currentModelName)
        if (snapshots.isEmpty()) {
            throw IllegalArgumentException("Gemini content-part replay has no snapshots")
        }
        val replayParts = JSONArray()
        snapshots.forEach { snapshot ->
            val sameModel = snapshot.modelName == currentModelName
            for (index in 0 until snapshot.parts.length()) {
                val part = JSONObject(snapshot.parts.getJSONObject(index).toString())
                if (!sameModel && part.optBoolean("thought", false)) {
                    continue
                }
                if (!sameModel) {
                    part.remove("thoughtSignature")
                    part.remove("thought_signature")
                }
                replayParts.put(part)
            }
        }
        if (replayParts.length() == 0) {
            throw IllegalArgumentException(
                "Gemini content-part replay became empty after model switch"
            )
        }
        return cloneAndValidateParts(replayParts)
    }

    fun functionCalls(parts: JSONArray): List<GeminiFunctionCallSnapshot> {
        val calls = mutableListOf<GeminiFunctionCallSnapshot>()
        for (index in 0 until parts.length()) {
            val part = parts.getJSONObject(index)
            val functionCall = part.optJSONObject("functionCall") ?: continue
            calls.add(
                GeminiFunctionCallSnapshot(
                    name = functionCall.optString("name", "").trim(),
                    canonicalArguments =
                        canonicalGeminiJsonObject(
                            functionCall.optJSONObject("args")?.toString() ?: "{}"
                        ),
                    thoughtSignature =
                        part.optString(
                            "thoughtSignature",
                            part.optString("thought_signature", ""),
                        ).trim().ifEmpty { null },
                )
            )
        }
        return calls
    }

    private fun decodePayload(payloadBase64: String): GeminiContentPartSnapshot {
        val payload =
            try {
                JSONObject(
                    String(
                        Base64.getDecoder().decode(payloadBase64),
                        Charsets.UTF_8,
                    )
                )
            } catch (error: Exception) {
                throw IllegalArgumentException(
                    "Gemini content-part metadata cannot be decoded",
                    error,
                )
            }
        if (payload.optInt("version", -1) != 1) {
            throw IllegalArgumentException(
                "Unsupported Gemini content-part metadata version"
            )
        }
        val modelName = payload.optString("model", "").trim()
        validateModelName(modelName)
        val parts =
            payload.optJSONArray("parts")
                ?: throw IllegalArgumentException(
                    "Gemini content-part metadata has no parts array"
                )
        return GeminiContentPartSnapshot(
            modelName = modelName,
            parts = cloneAndValidateParts(parts),
        )
    }

    private fun cloneAndValidateParts(parts: JSONArray): JSONArray {
        if (parts.length() == 0) {
            throw IllegalArgumentException("Gemini content-part metadata is empty")
        }
        val cloned = JSONArray()
        for (index in 0 until parts.length()) {
            val source =
                parts.optJSONObject(index)
                    ?: throw IllegalArgumentException(
                        "Gemini content Part at index $index is not an object"
                    )
            val part = JSONObject(source.toString())
            if (
                !part.has("thoughtSignature") &&
                    part.optString("thought_signature", "").isNotBlank()
            ) {
                part.put("thoughtSignature", part.getString("thought_signature"))
                part.remove("thought_signature")
            }
            val functionCall = part.optJSONObject("functionCall")
            if (functionCall != null) {
                if (functionCall.optString("name", "").isBlank()) {
                    throw IllegalArgumentException(
                        "Gemini functionCall Part at index $index has no name"
                    )
                }
                if (functionCall.optJSONObject("args") == null) {
                    functionCall.put("args", JSONObject())
                }
            }
            if (
                part.has("thoughtSignature") &&
                    part.optString("thoughtSignature", "").isBlank()
            ) {
                throw IllegalArgumentException(
                    "Gemini Part at index $index has an empty thoughtSignature"
                )
            }
            cloned.put(part)
        }
        return cloned
    }

    private fun validateModelName(modelName: String) {
        if (modelName.isBlank()) {
            throw IllegalArgumentException(
                "Gemini content-part metadata has no model identity"
            )
        }
    }
}
