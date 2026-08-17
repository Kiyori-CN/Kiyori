package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class BrowserSessionRecoveryStore private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val browserSessionRecoveryFileProducer: () -> File = {
        browserSessionRecoveryFile(appContext)
    }
    private val dataStore: DataStore<BrowserSessionRecoveryEnvelope> =
        DataStoreFactory.create(
            serializer = BrowserSessionRecoverySerializer,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = browserSessionRecoveryFileProducer,
        )

    suspend fun readSnapshot(): BrowserSessionRecoverySnapshot? =
        dataStore.data.first().snapshot?.also(::requireBrowserSessionRecoverySnapshot)

    suspend fun writeSnapshot(snapshot: BrowserSessionRecoverySnapshot) {
        requireBrowserSessionRecoverySnapshot(snapshot)
        dataStore.updateData {
            BrowserSessionRecoveryEnvelope(snapshot = snapshot)
        }
    }

    suspend fun clear() {
        dataStore.updateData {
            BrowserSessionRecoveryEnvelope()
        }
    }

    companion object {
        @Volatile private var instance: BrowserSessionRecoveryStore? = null

        fun getInstance(context: Context): BrowserSessionRecoveryStore =
            instance ?: synchronized(this) {
                instance
                    ?: BrowserSessionRecoveryStore(context.applicationContext).also { store ->
                        instance = store
                    }
            }
    }
}

private fun browserSessionRecoveryFile(context: Context): File {
    val directory = File(context.noBackupFilesDir, "kiyori")
    check(directory.exists() || directory.mkdirs()) {
        "Unable to create browser recovery directory"
    }
    return File(directory, "browser_session_recovery.json")
}

internal object BrowserSessionRecoverySerializer :
    Serializer<BrowserSessionRecoveryEnvelope> {
    override val defaultValue: BrowserSessionRecoveryEnvelope =
        BrowserSessionRecoveryEnvelope()

    override suspend fun readFrom(input: InputStream): BrowserSessionRecoveryEnvelope {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) {
            return defaultValue
        }
        return try {
            BrowserSessionRecoveryJson.decodeFromString(
                BrowserSessionRecoveryEnvelope.serializer(),
                bytes.decodeToString(),
            )
        } catch (error: SerializationException) {
            throw CorruptionException("Browser recovery file is not valid JSON", error)
        } catch (error: IllegalArgumentException) {
            throw CorruptionException("Browser recovery file violates its schema", error)
        }
    }

    override suspend fun writeTo(
        t: BrowserSessionRecoveryEnvelope,
        output: OutputStream,
    ) {
        output.write(
            BrowserSessionRecoveryJson
                .encodeToString(BrowserSessionRecoveryEnvelope.serializer(), t)
                .encodeToByteArray(),
        )
    }
}

private val BrowserSessionRecoveryJson =
    Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }
