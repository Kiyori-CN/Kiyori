package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnthropicAuthenticationPolicyTest {
    @Test
    fun officialAndXApiKeyCompatibleProviders_useXApiKey() {
        listOf(
            ApiProviderType.ANTHROPIC,
            ApiProviderType.DEEPSEEK,
            ApiProviderType.SILICONFLOW,
            ApiProviderType.NOVITA,
            ApiProviderType.OTHER,
        ).forEach { providerType ->
            assertEquals(
                AnthropicAuthenticationMode.X_API_KEY,
                AnthropicAuthenticationPolicy.resolve(providerType),
            )
        }
    }

    @Test
    fun bearerCompatibleProviders_useAuthorizationBearer() {
        listOf(
            ApiProviderType.ZHIPU,
            ApiProviderType.MIMO,
            ApiProviderType.INFINIAI,
            ApiProviderType.LMSTUDIO,
            ApiProviderType.OLLAMA,
        ).forEach { providerType ->
            assertEquals(
                AnthropicAuthenticationMode.BEARER,
                AnthropicAuthenticationPolicy.resolve(providerType),
            )
        }
    }

    @Test
    fun authenticationMode_writesOnlyItsDeclaredCredentialHeader() {
        val xApiKeyRequest =
            Request.Builder()
                .url("https://example.com/v1/messages")
                .also { builder ->
                    AnthropicAuthenticationPolicy.apply(
                        builder = builder,
                        apiKey = "secret",
                        mode = AnthropicAuthenticationMode.X_API_KEY,
                    )
                }
                .build()
        assertEquals("secret", xApiKeyRequest.header("x-api-key"))
        assertNull(xApiKeyRequest.header("Authorization"))

        val bearerRequest =
            Request.Builder()
                .url("https://example.com/v1/messages")
                .also { builder ->
                    AnthropicAuthenticationPolicy.apply(
                        builder = builder,
                        apiKey = "secret",
                        mode = AnthropicAuthenticationMode.BEARER,
                    )
                }
                .build()
        assertEquals("Bearer secret", bearerRequest.header("Authorization"))
        assertNull(bearerRequest.header("x-api-key"))
    }

    @Test
    fun blankCredential_writesNoAuthenticationHeader() {
        val request =
            Request.Builder()
                .url("http://localhost:11434/v1/messages")
                .also { builder ->
                    AnthropicAuthenticationPolicy.apply(
                        builder = builder,
                        apiKey = "",
                        mode = AnthropicAuthenticationMode.BEARER,
                    )
                }
                .build()

        assertNull(request.header("Authorization"))
        assertNull(request.header("x-api-key"))
    }
}
