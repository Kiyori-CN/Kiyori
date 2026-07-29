package com.ai.assistance.operit.util.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportModelsTest {
    @Test
    fun codec_roundTrip_preservesStructuredReport() {
        val report =
            report(
                reportId = "00000000-0000-0000-0000-000000000001",
                type = CrashReportType.PLAYER_RUNTIME_FATAL,
                runtimeGeneration = 7L,
                playerJournal = "attach:42\nload:abc123",
            )

        assertEquals(report, CrashReportCodec.decode(CrashReportCodec.encode(report)))
    }

    @Test
    fun formatter_includesAvailableEvidenceOnly() {
        val text =
            CrashReportFormatter.format(
                report(
                    reportId = "00000000-0000-0000-0000-000000000002",
                    throwableText = "java.lang.IllegalStateException: boom",
                    exitReason = 5,
                    processStateSummary = "attach:42",
                ),
            )

        assertTrue(text.contains("Type: APP_FATAL"))
        assertTrue(text.contains("Exit reason: 5"))
        assertTrue(text.contains("Process state: attach:42"))
        assertTrue(text.contains("java.lang.IllegalStateException: boom"))
        assertFalse(text.contains("Runtime generation:"))
    }

    @Test
    fun statusUpdate_changesOnlyLifecycleState() {
        val original = report("00000000-0000-0000-0000-000000000003")
        val displayed = updateCrashReportStatus(original, CrashReportStatus.DISPLAYED)
        val resolved = updateCrashReportStatus(displayed, CrashReportStatus.RESOLVED)
        val reopened = updateCrashReportStatus(resolved, CrashReportStatus.DISPLAYED)

        assertEquals(CrashReportStatus.DISPLAYED, displayed.status)
        assertEquals(CrashReportStatus.RESOLVED, resolved.status)
        assertEquals(CrashReportStatus.RESOLVED, reopened.status)
        assertEquals(original.reportId, resolved.reportId)
        assertEquals(original.throwableText, resolved.throwableText)
    }

    @Test
    fun primaryAction_returnsToKiyoriOnlyForPlayerRuntimeFatal() {
        assertEquals(
            CrashReportPrimaryAction.RESTART_PLAYER,
            selectCrashReportPrimaryAction(CrashReportType.PLAYER_RUNTIME_FATAL),
        )
        assertEquals(
            CrashReportPrimaryAction.RESTART_APP,
            selectCrashReportPrimaryAction(CrashReportType.APP_FATAL),
        )
        assertEquals(
            CrashReportPrimaryAction.RESTART_APP,
            selectCrashReportPrimaryAction(null),
        )
    }

    @Test
    fun retention_keepsNewestReportsDeterministically() {
        val reports =
            (1L..7L).map { index ->
                report(
                    reportId =
                        "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}",
                    createdAtEpochMs = index,
                )
            }

        val retained = selectRetainedCrashReports(reports.shuffled(), maxReports = 5)

        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), retained.map { it.createdAtEpochMs })
    }

    @Test(expected = IllegalArgumentException::class)
    fun record_rejectsAttachmentPathTraversal() {
        report(
            reportId = "00000000-0000-0000-0000-000000000004",
            attachmentNames = listOf("../native-trace.pb"),
        )
    }

    private fun report(
        reportId: String,
        createdAtEpochMs: Long = 1L,
        type: CrashReportType = CrashReportType.APP_FATAL,
        throwableText: String = "java.lang.IllegalStateException: boom",
        runtimeGeneration: Long? = null,
        exitReason: Int? = null,
        processStateSummary: String? = null,
        playerJournal: String = "",
        attachmentNames: List<String> = emptyList(),
    ): CrashReportRecord =
        CrashReportRecord(
            reportId = reportId,
            createdAtEpochMs = createdAtEpochMs,
            type = type,
            processName = "com.kiyori",
            processId = 1234,
            threadName = "main",
            throwableText = throwableText,
            runtimeGeneration = runtimeGeneration,
            exitReason = exitReason,
            processStateSummary = processStateSummary,
            playerJournal = playerJournal,
            attachmentNames = attachmentNames,
        )
}
