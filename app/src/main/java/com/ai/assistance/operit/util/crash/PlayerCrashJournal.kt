package com.ai.assistance.operit.util.crash

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal enum class PlayerCrashEventType {
    RUNTIME_BINDING,
    RUNTIME_READY,
    ATTACH_SENT,
    ATTACH_ACK,
    DETACH_SENT,
    DETACH_ACK,
    LOAD_SENT,
    RUNTIME_CLOSE,
    RUNTIME_DEATH,
}

@Serializable
internal data class PlayerCrashEvent(
    val timestampEpochMs: Long,
    val type: PlayerCrashEventType,
    val runtimeGeneration: Long,
    val surfaceGeneration: Long? = null,
    val commandId: Long? = null,
)

internal object PlayerCrashJournal {
    private const val FILE_NAME = "player-crash-journal.json"
    private const val MAX_EVENTS = 64
    private val lock = Any()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun clear(context: Context) {
        synchronized(lock) {
            AtomicFile(file(context)).delete()
        }
    }

    fun record(
        context: Context,
        type: PlayerCrashEventType,
        runtimeGeneration: Long,
        surfaceGeneration: Long? = null,
        commandId: Long? = null,
    ) {
        if (runtimeGeneration <= 0L) return
        synchronized(lock) {
            val events =
                (readLocked(context) +
                    PlayerCrashEvent(
                        timestampEpochMs = System.currentTimeMillis(),
                        type = type,
                        runtimeGeneration = runtimeGeneration,
                        surfaceGeneration = surfaceGeneration,
                        commandId = commandId,
                    )).takeLast(MAX_EVENTS)
            writeLocked(context, events)
        }
    }

    fun snapshot(context: Context): String =
        synchronized(lock) {
            readLocked(context).joinToString("\n") { event ->
                buildString {
                    append(event.timestampEpochMs)
                    append(' ')
                    append(event.type)
                    append(" runtime=")
                    append(event.runtimeGeneration)
                    event.surfaceGeneration?.let { append(" surface=").append(it) }
                    event.commandId?.let { append(" command=").append(it) }
                }
            }
        }

    private fun readLocked(context: Context): List<PlayerCrashEvent> {
        val atomicFile = AtomicFile(file(context))
        if (!atomicFile.baseFile.exists() && !File("${atomicFile.baseFile}.bak").exists()) {
            return emptyList()
        }
        return atomicFile.openRead().use { input ->
            json.decodeFromString<List<PlayerCrashEvent>>(
                input.readBytes().toString(StandardCharsets.UTF_8),
            )
        }
    }

    private fun writeLocked(context: Context, events: List<PlayerCrashEvent>) {
        val atomicFile = AtomicFile(file(context))
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(json.encodeToString(events).toByteArray(StandardCharsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            output?.let(atomicFile::failWrite)
            throw error
        }
    }

    private fun file(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NAME)
}
