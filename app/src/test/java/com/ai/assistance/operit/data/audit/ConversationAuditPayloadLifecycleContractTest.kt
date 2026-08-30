package com.ai.assistance.operit.data.audit

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAuditPayloadLifecycleContractTest {
    @Test
    fun `payload lifecycle lock covers every write and collection entry point`() {
        val source = repositoryFile(
            "app/src/main/java/com/ai/assistance/operit/data/audit/" +
                "ConversationAuditRepository.kt",
        ).readText()

        assertTrue(source.contains("private val payloadLifecycleMutex = Mutex()"))
        assertTrue(
            source
                .substringAfter("suspend fun appendEvent(")
                .substringBefore("    /**")
                .contains("payloadLifecycleMutex.withLock"),
        )
        assertTrue(
            source
                .substringAfter("suspend fun <T> mutateAndAppendEvent(")
                .substringBefore("    /**")
                .contains("payloadLifecycleMutex.withLock"),
        )
        assertTrue(
            source
                .substringAfter("suspend fun reviseMessage(")
                .substringBefore("    suspend fun appendThrowable(")
                .contains("payloadLifecycleMutex.withLock"),
        )
        assertTrue(
            source
                .substringAfter("suspend fun importPortableAudit(")
                .substringBefore("    private fun validatePortableAudit(")
                .contains("payloadLifecycleMutex.withLock"),
        )
        assertTrue(
            source
                .substringAfter("suspend fun reconstructLegacyAuditIfNeeded(")
                .substringBefore("    suspend fun getTotalStoredPayloadBytes")
                .contains("payloadLifecycleMutex.withLock"),
        )
        assertTrue(
            source
                .substringAfter("suspend fun cleanupUnreferencedPayloads()")
                .substringBefore("    private suspend fun ensureAuditInsideTransaction")
                .contains("payloadLifecycleMutex.withLock"),
        )
        assertTrue(source.contains("check(dao.deletePayloadMetadata(payload.payloadSha256) == 1)"))
    }

    private fun repositoryFile(relativePath: String): File {
        var current = File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(current, relativePath)
            if (candidate.isFile) {
                return candidate
            }
            current = current.parentFile ?: error("Unable to locate repository file: $relativePath")
        }
    }
}
