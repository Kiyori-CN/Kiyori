package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelConfigSummary
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class ModelConfigSummariesFlowTest {
    @Test
    fun `test parameters come from frozen config instead of later saved edits`() = runBlocking {
        val manager = ModelConfigManager(Mockito.mock(Context::class.java), TestStore())
        val frozen = ModelConfigData(id = "fixed", name = "Fixed", temperature = 0.25f, temperatureEnabled = true)
        manager.saveModelConfig(frozen.copy(temperature = 0.9f))
        val parameters = manager.getModelParametersForSnapshot(frozen)
        assertEquals(0.25f, parameters.single { it.id == "temperature" }.currentValue)
    }

    @Test
    fun `subscription observes create edit protocol and delete without reopening selector`() = runBlocking {
        val store = TestStore()
        val manager = ModelConfigManager(Mockito.mock(Context::class.java), store)
        val events = Channel<List<ModelConfigSummary>>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            manager.configSummariesFlow.collect { events.send(it) }
        }
        try {
            assertTrue(withTimeout(2000) { events.receive() }.isEmpty())
            val id = manager.createConfig("first")
            assertEquals(id, withTimeout(2000) { events.receive() }.single().id)
            manager.saveModelConfig(ModelConfigData(
                id = id, name = "renamed", modelName = "model-b",
                apiProviderType = ApiProviderType.OPENAI_GENERIC,
                apiProtocol = ApiProtocol.OPENAI_RESPONSES,
            ))
            val updated = withTimeout(2000) { events.receive() }.single()
            assertEquals("renamed", updated.name)
            assertEquals("model-b", updated.modelName)
            assertEquals(ApiProtocol.OPENAI_RESPONSES, updated.apiProtocol)
            manager.deleteConfig(id)
            assertTrue(withTimeout(2000) { events.receive() }.isEmpty())
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `single config and summaries decode the same legacy protocol snapshot`() = runBlocking {
        val id = "legacy"
        val store = TestStore()
        store.updateData {
            mutablePreferencesOf(
                ModelConfigManager.CONFIG_LIST_KEY to Json.encodeToString(listOf(id)),
                stringPreferencesKey("config_$id") to
                    """{"id":"legacy","name":"old","modelName":"gpt","apiProviderType":"OPENAI_RESPONSES"}""",
            )
        }
        val manager = ModelConfigManager(Mockito.mock(Context::class.java), store)
        assertEquals(manager.getModelConfigFlow(id).first().apiProtocol,
            manager.configSummariesFlow.first().single().apiProtocol)
        assertEquals(ApiProtocol.OPENAI_RESPONSES, manager.getAllConfigSummaries().single().apiProtocol)
    }

    private class TestStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        private val mutex = Mutex()
        override val data = state.asStateFlow()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock {
                transform(state.value.toMutablePreferences()).also { state.value = it }
            }
    }
}
