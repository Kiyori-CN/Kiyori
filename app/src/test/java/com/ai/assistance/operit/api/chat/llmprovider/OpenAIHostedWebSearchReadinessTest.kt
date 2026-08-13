package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIHostedWebSearchReadinessTest {
    @Test
    fun missingEndpointAndCredentialStillReturnEveryReadinessField() {
        val inspection =
            OpenAIHostedWebSearchReadinessEvaluator.evaluate(
                environment = emptyMap(),
                recordSet = OpenAIHostedWebSearchCompatibilityRecordSet(),
            )

        assertFalse(inspection.localValid)
        assertEquals(
            setOf(
                "provider_contract",
                "endpoint",
                "model",
                "credential",
                "auth",
                "extra_headers",
                "search_options",
                "admission",
                "compatibility",
            ),
            inspection.readiness.keys,
        )
        assertEquals("official", inspection.readiness.getValue("provider_contract").state)
        assertEquals("missing", inspection.readiness.getValue("endpoint").state)
        assertEquals("ready", inspection.readiness.getValue("model").state)
        assertEquals("missing", inspection.readiness.getValue("credential").state)
        assertEquals("ready", inspection.readiness.getValue("auth").state)
        assertEquals("ready", inspection.readiness.getValue("extra_headers").state)
        assertEquals("ready", inspection.readiness.getValue("search_options").state)
        assertEquals("ready", inspection.readiness.getValue("admission").state)
        assertEquals("not_required", inspection.readiness.getValue("compatibility").state)
        assertNull(inspection.endpointHost)
        assertFalse(inspection.apiKeyConfigured)
        assertNull(inspection.apiKeyRevision)
        assertNull(inspection.resolvedBinding)
    }

    @Test
    fun invalidGroupsRemainIndependentAndUseSanitizedMessages() {
        val privateEndpoint = "https://private-user.example/v1/responses"
        val inspection =
            OpenAIHostedWebSearchReadinessEvaluator.evaluate(
                environment =
                    mapOf(
                        OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT to privateEndpoint,
                        OpenAIHostedWebSearchContract.ENV_API_KEY to
                            "secret-key-that-must-not-appear",
                        OpenAIHostedWebSearchContract.ENV_EXTRA_HEADERS_JSON to
                            """{"User-Agent":"forged-client"}""",
                        OpenAIHostedWebSearchContract.ENV_CONTEXT_SIZE to "gigantic",
                        OpenAIHostedWebSearchContract.ENV_TIMEOUT_SECONDS to "301",
                    ),
                recordSet = OpenAIHostedWebSearchCompatibilityRecordSet(),
            )

        assertFalse(inspection.localValid)
        assertEquals("invalid", inspection.readiness.getValue("endpoint").state)
        assertEquals("ready", inspection.readiness.getValue("credential").state)
        assertEquals("invalid", inspection.readiness.getValue("extra_headers").state)
        assertEquals("invalid", inspection.readiness.getValue("search_options").state)
        assertEquals("invalid", inspection.readiness.getValue("admission").state)
        assertTrue(inspection.apiKeyConfigured)
        assertFalse(requireNotNull(inspection.apiKeyRevision).contains("secret-key"))
        inspection.readiness.values.forEach { readiness ->
            assertFalse(readiness.message.orEmpty().contains(privateEndpoint))
            assertFalse(readiness.message.orEmpty().contains("secret-key"))
            assertFalse(readiness.message.orEmpty().contains("forged-client"))
        }
    }

    @Test
    fun completeRelayBindingReturnsCompatibilityMissingWithoutBlockingLocalValidity() {
        val inspection =
            OpenAIHostedWebSearchReadinessEvaluator.evaluate(
                environment =
                    mapOf(
                        OpenAIHostedWebSearchContract.ENV_PROVIDER_CONTRACT to
                            "RESPONSES_RELAY_STRICT",
                        OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT to
                            "https://relay.example/v1/responses",
                        OpenAIHostedWebSearchContract.ENV_MODEL to "relay-search-model",
                        OpenAIHostedWebSearchContract.ENV_API_KEY to "relay-key",
                    ),
                recordSet = OpenAIHostedWebSearchCompatibilityRecordSet(),
            )

        assertTrue(inspection.localValid)
        assertEquals("relay", inspection.readiness.getValue("provider_contract").state)
        assertEquals("missing", inspection.readiness.getValue("compatibility").state)
        assertEquals("relay.example", inspection.endpointHost)
        assertEquals("relay-search-model", inspection.modelName)
        assertTrue(inspection.resolvedBinding != null)
    }
}
