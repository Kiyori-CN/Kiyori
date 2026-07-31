package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.storage

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserscriptStorageTransactionTest {
    @Test
    fun `recovery keeps a revision already referenced by the registry`() {
        assertEquals(
            UserscriptTransactionRecoveryDecision.KEEP_COMMITTED_REVISION,
            decideUserscriptTransactionRecovery(
                activeRevisionId = "revision-b",
                journalRevisionId = "revision-b",
            ),
        )
    }

    @Test
    fun `recovery removes a revision that never reached the registry`() {
        assertEquals(
            UserscriptTransactionRecoveryDecision.REMOVE_UNCOMMITTED_REVISION,
            decideUserscriptTransactionRecovery(
                activeRevisionId = "revision-a",
                journalRevisionId = "revision-b",
            ),
        )
    }

    @Test
    fun `bounded response reader accepts the exact byte limit`() {
        val bytes = byteArrayOf(1, 2, 3, 4)

        assertEquals(
            bytes.toList(),
            ByteArrayInputStream(bytes).readUserscriptLimitedBytes(4).toList(),
        )
    }

    @Test
    fun `bounded response reader rejects data beyond the byte limit`() {
        assertThrows(IllegalArgumentException::class.java) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))
                .readUserscriptLimitedBytes(4)
        }
    }
}
