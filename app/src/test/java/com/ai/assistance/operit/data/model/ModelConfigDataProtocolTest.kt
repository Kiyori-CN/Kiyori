package com.ai.assistance.operit.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelConfigDataProtocolTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @Test
    fun legacyProviderIds_deriveTheirFormerWireProtocolWhenTheFieldIsMissing() {
        assertEquals(
            ApiProtocol.OPENAI_RESPONSES,
            decodeLegacyProvider(ApiProviderType.OPENAI_RESPONSES).apiProtocol,
        )
        assertEquals(
            ApiProtocol.ANTHROPIC_MESSAGES,
            decodeLegacyProvider(ApiProviderType.ANTHROPIC_GENERIC).apiProtocol,
        )
        assertEquals(
            ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            decodeLegacyProvider(ApiProviderType.OTHER).apiProtocol,
        )
    }

    @Test
    fun legacyNovitaEndpoint_derivesAnthropicMessagesWithoutChangingTheSupplier() {
        val config =
            json.decodeModelConfigDataWithLegacyProtocol(
                """
                {
                  "id": "novita-legacy",
                  "name": "Novita legacy",
                  "apiEndpoint": "https://api.novita.ai/anthropic/v1/messages",
                  "apiProviderType": "NOVITA",
                  "apiProviderTypeId": "NOVITA"
                }
                """.trimIndent()
            )

        assertEquals(ApiProviderType.NOVITA, config.apiProviderType)
        assertEquals("NOVITA", config.apiProviderTypeId)
        assertEquals(ApiProtocol.ANTHROPIC_MESSAGES, config.apiProtocol)
    }

    @Test
    fun persistedProtocol_isNeverReinterpretedFromTheEndpoint() {
        val config =
            json.decodeModelConfigDataWithLegacyProtocol(
                """
                {
                  "id": "novita-explicit",
                  "name": "Novita explicit",
                  "apiEndpoint": "https://api.novita.ai/anthropic/v1/messages",
                  "apiProviderType": "NOVITA",
                  "apiProviderTypeId": "NOVITA",
                  "apiProtocol": "OPENAI_CHAT_COMPLETIONS"
                }
                """.trimIndent()
            )

        assertEquals(ApiProtocol.OPENAI_CHAT_COMPLETIONS, config.apiProtocol)
    }

    private fun decodeLegacyProvider(providerType: ApiProviderType): ModelConfigData {
        return json.decodeModelConfigDataWithLegacyProtocol(
            """
            {
              "id": "${providerType.name.lowercase()}",
              "name": "${providerType.name}",
              "apiProviderType": "${providerType.name}",
              "apiProviderTypeId": "${providerType.name}"
            }
            """.trimIndent()
        )
    }
}
