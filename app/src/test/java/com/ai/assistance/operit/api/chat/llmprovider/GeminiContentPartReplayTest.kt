package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiContentPartReplayTest {
    @Test
    fun preservesExactPartOrderAndCamelCaseThoughtSignatures() {
        val parts =
            JSONArray()
                .put(
                    JSONObject()
                        .put("text", "reasoning")
                        .put("thought", true)
                        .put("thoughtSignature", "sig-thought")
                )
                .put(JSONObject().put("text", "answer"))
                .put(
                    JSONObject()
                        .put(
                            "functionCall",
                            JSONObject()
                                .put("name", "read_file")
                                .put("args", JSONObject().put("path", "one.txt"))
                        )
                        .put("thoughtSignature", "sig-tool")
                )

        val tag =
            requireNotNull(
                GeminiContentPartReplayCodec.createMetadataTag(
                    modelName = "gemini-2.5-pro",
                    parts = parts,
                )
            )
        val snapshot =
            GeminiContentPartReplayCodec.extractSnapshots(tag).single()
        val replay =
            GeminiContentPartReplayCodec.replayPartsForModel(
                snapshots = listOf(snapshot),
                currentModelName = "gemini-2.5-pro",
            )

        assertEquals(3, replay.length())
        assertEquals("sig-thought", replay.getJSONObject(0).getString("thoughtSignature"))
        assertEquals("answer", replay.getJSONObject(1).getString("text"))
        assertEquals("sig-tool", replay.getJSONObject(2).getString("thoughtSignature"))
        assertFalse(replay.toString().contains("thought_signature"))
    }

    @Test
    fun modelSwitchRemovesThoughtPartsAndOpaqueSignaturesButKeepsFunctionCall() {
        val tag =
            requireNotNull(
                GeminiContentPartReplayCodec.createMetadataTag(
                    modelName = "gemini-2.5-pro",
                    parts =
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("text", "reasoning")
                                    .put("thought", true)
                                    .put("thoughtSignature", "sig-thought")
                            )
                            .put(
                                JSONObject()
                                    .put(
                                        "functionCall",
                                        JSONObject()
                                            .put("name", "read_file")
                                            .put("args", JSONObject())
                                    )
                                    .put("thoughtSignature", "sig-tool")
                            ),
                )
            )
        val snapshot =
            GeminiContentPartReplayCodec.extractSnapshots(tag).single()
        val replay =
            GeminiContentPartReplayCodec.replayPartsForModel(
                snapshots = listOf(snapshot),
                currentModelName = "gemini-2.5-flash",
            )

        assertEquals(1, replay.length())
        assertTrue(replay.getJSONObject(0).has("functionCall"))
        assertFalse(replay.getJSONObject(0).has("thoughtSignature"))
    }

    @Test
    fun textOnlyPartsDoNotCreateHiddenProtocolMetadata() {
        assertNull(
            GeminiContentPartReplayCodec.createMetadataTag(
                modelName = "gemini-2.5-pro",
                parts = JSONArray().put(JSONObject().put("text", "answer")),
            )
        )
    }

    @Test
    fun responseAttemptAccumulatesStreamingPartsInOrderAndFinalizesOnce() {
        val attempt = GeminiResponseAttemptState("gemini-2.5-pro")
        attempt.appendResponseParts(
            JSONArray().put(JSONObject().put("text", "visible text"))
        )
        attempt.appendResponseParts(
            JSONArray().put(
                JSONObject()
                    .put(
                        "functionCall",
                        JSONObject()
                            .put("name", "read_file")
                            .put("args", JSONObject().put("path", "final.txt"))
                    )
                    .put("thoughtSignature", "sig-final")
            )
        )

        val tag = requireNotNull(attempt.finishMetadataTag())
        val parts =
            GeminiContentPartReplayCodec
                .extractSnapshots(tag)
                .single()
                .parts

        assertEquals(2, parts.length())
        assertEquals("visible text", parts.getJSONObject(0).getString("text"))
        assertEquals(
            "read_file",
            parts.getJSONObject(1).getJSONObject("functionCall").getString("name"),
        )
        assertEquals("sig-final", parts.getJSONObject(1).getString("thoughtSignature"))
        assertThrows(IllegalStateException::class.java) {
            attempt.finishMetadataTag()
        }
    }

    @Test
    fun retryAttemptDoesNotInheritAbandonedResponseParts() {
        val abandonedAttempt = GeminiResponseAttemptState("gemini-2.5-pro")
        abandonedAttempt.appendResponseParts(
            JSONArray().put(
                JSONObject()
                    .put(
                        "functionCall",
                        JSONObject()
                            .put("name", "stale_call")
                            .put("args", JSONObject())
                    )
                    .put("thoughtSignature", "sig-stale")
            )
        )

        val successfulAttempt = GeminiResponseAttemptState("gemini-2.5-pro")
        successfulAttempt.appendResponseParts(
            JSONArray().put(
                JSONObject()
                    .put(
                        "functionCall",
                        JSONObject()
                            .put("name", "final_call")
                            .put("args", JSONObject())
                    )
                    .put("thoughtSignature", "sig-final")
            )
        )

        val tag = requireNotNull(successfulAttempt.finishMetadataTag())
        val parts =
            GeminiContentPartReplayCodec
                .extractSnapshots(tag)
                .single()
                .parts

        assertEquals(1, parts.length())
        assertEquals(
            "final_call",
            parts.getJSONObject(0).getJSONObject("functionCall").getString("name"),
        )
        assertFalse(tag.contains("stale_call"))
        assertFalse(tag.contains("sig-stale"))
    }

    @Test
    fun textOnlyResponseAttemptDoesNotCreateHiddenProtocolMetadata() {
        val attempt = GeminiResponseAttemptState("gemini-2.5-pro")
        attempt.appendResponseParts(
            JSONArray().put(JSONObject().put("text", "answer"))
        )

        assertNull(attempt.finishMetadataTag())
    }

    @Test
    fun malformedMetadataFailsExplicitly() {
        assertThrows(IllegalArgumentException::class.java) {
            GeminiContentPartReplayCodec.extractSnapshots(
                """<meta provider="gemini:content_parts">not-base64</meta>"""
            )
        }
    }
}
