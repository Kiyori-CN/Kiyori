package com.ai.assistance.operit.data.audit

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ConversationAuditPayloadCleanupTest {
    @Test fun largeDeletionCommitsBoundedBatchesOnlyAfterFilesAreDeleted() = runTest {
        val files = (0 until 1000).toMutableSet()
        val metadata = files.toMutableSet()
        val batchSizes = mutableListOf<Int>()
        val count = cleanupAuditPayloadBatches(
            files.toList(),
            deleteFile = { assertTrue(files.remove(it)) },
            deleteMetadataBatch = { batch ->
                assertTrue(batch.none { it in files })
                assertTrue(batch.all { it in metadata })
                batchSizes += batch.size
                metadata.removeAll(batch.toSet())
            },
        )
        assertEquals(1000, count)
        assertEquals(listOf(128, 128, 128, 128, 128, 128, 128, 104), batchSizes)
        assertTrue(files.isEmpty())
        assertTrue(metadata.isEmpty())
    }

    @Test fun fileFailurePreservesFailedBatchMetadataAndCanResume() = runTest {
        val files = (0 until 260).toMutableSet()
        val metadata = files.toMutableSet()
        val failure = IOException("fixture")
        try {
            cleanupAuditPayloadBatches(files.toList(), deleteFile = {
                if (it == 130) throw failure
                files.remove(it)
            }) { metadata.removeAll(it.toSet()) }
            fail("file error must propagate")
        } catch (actual: IOException) {
            assertSame(failure, actual)
        }
        assertEquals((128 until 260).toSet(), metadata)
        assertEquals((130 until 260).toSet(), files)
        // PayloadStore.delete 允许文件已不存在，恢复时仍应完成元数据清理。
        assertEquals(132, cleanupAuditPayloadBatches(metadata.toList(), deleteFile = {
            files.remove(it)
        }) { metadata.removeAll(it.toSet()) })
        assertTrue(files.isEmpty())
        assertTrue(metadata.isEmpty())
    }

    @Test fun databaseFailureDoesNotDeleteFilesFromLaterBatches() = runTest {
        val files = (0 until 260).toMutableSet()
        val failure = IllegalStateException("transaction failed")
        try {
            cleanupAuditPayloadBatches(files.toList(), deleteFile = { files.remove(it) }) {
                throw failure
            }
            fail("database error must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertEquals((128 until 260).toSet(), files)
    }

    @Test fun cancellationPropagatesWithoutStartingNextBatch() = runTest {
        val files = (0 until 260).toMutableSet()
        val cancelled = CancellationException("cancelled")
        try {
            cleanupAuditPayloadBatches(files.toList(), deleteFile = { files.remove(it) }) {
                throw cancelled
            }
            fail("cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
        assertEquals((128 until 260).toSet(), files)
    }

    @Test fun emptyDeletionDoesNotTouchFilesOrDatabase() = runTest {
        assertEquals(0, cleanupAuditPayloadBatches(emptyList<Int>(), {
            fail("no files")
        }) { fail("no transaction") })
    }
}
