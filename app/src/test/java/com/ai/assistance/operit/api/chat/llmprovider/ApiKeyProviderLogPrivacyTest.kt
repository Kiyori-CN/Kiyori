package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiKeyInfo
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ApiKeyProviderLogPrivacyTest {
    @Test
    fun singleKeyLogContainsOnlyPresenceState() = runBlocking {
        val secret = "single-sensitive-value-1234"

        Mockito.mockStatic(AppLogger::class.java).use { logger ->
            val selected = SingleApiKeyProvider(secret).getApiKey()

            assertEquals(secret, selected)
            logger.verify {
                AppLogger.d(
                    "ApiKeyProvider",
                    "Using single API key configuration: keyPresent=true",
                )
            }
            logger.verifyNoMoreInteractions()
        }
    }

    @Test
    fun poolSelectionLogContainsOnlyCountsAndIndex() = runBlocking {
        val firstSecret = "pool-sensitive-value-1111"
        val secondSecret = "pool-sensitive-value-2222"
        val manager = mock<ModelConfigManager>()
        whenever(manager.getModelConfig("config-id")).thenReturn(
            ModelConfigData(
                id = "config-id",
                name = "Private config",
                apiKeyPool =
                    listOf(
                        ApiKeyInfo(id = "first", key = firstSecret, name = "private first"),
                        ApiKeyInfo(id = "second", key = secondSecret, name = "private second"),
                    ),
            )
        )

        Mockito.mockStatic(AppLogger::class.java).use { logger ->
            val selected = MultiApiKeyProvider("config-id", manager).getApiKey()

            assertEquals(firstSecret, selected)
            logger.verify {
                AppLogger.d(
                    "ApiKeyProvider",
                    "Config Private config: Found 2 enabled keys out of 2 total keys",
                )
            }
            logger.verify {
                AppLogger.d(
                    "ApiKeyProvider",
                    "Config Private config: selectedKeyIndex=1/2",
                )
            }
            logger.verifyNoMoreInteractions()
        }
        verify(manager).updateConfigKeyIndex("config-id", 1)
    }
}
