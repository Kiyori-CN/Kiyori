package com.ai.assistance.operit.util.crash

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CrashReportStoreAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        CrashReportStore.deleteAllForTest(context)
    }

    @After
    fun tearDown() {
        CrashReportStore.deleteAllForTest(context)
    }

    @Test
    fun createReadAndResolve_persistsLifecycleState() {
        val report = createReport(index = 1)

        assertEquals(report, CrashReportStore.readReport(context, report.reportId))
        assertTrue(CrashReportStore.hasUnresolvedReports(context))
        assertEquals(
            CrashReportStatus.DISPLAYED,
            CrashReportStore.markDisplayed(context, report.reportId)?.status,
        )
        assertEquals(
            CrashReportStatus.RESOLVED,
            CrashReportStore.markResolved(context, report.reportId)?.status,
        )
        assertFalse(CrashReportStore.hasUnresolvedReports(context))
    }

    @Test
    fun invalidReportId_isRejectedWithoutFilesystemTraversal() {
        assertNull(CrashReportStore.readReport(context, "../report"))
        assertNull(CrashReportStore.markResolved(context, "../report"))
    }

    @Test
    fun retention_keepsFiveNewestReports() {
        repeat(7) { index -> createReport(index + 1) }

        val reports = CrashReportStore.listReports(context)

        assertEquals(5, reports.size)
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), reports.map { it.createdAtEpochMs })
    }

    @Test
    fun fatalReport_capturesCurrentProcessAndThread() {
        val report =
            CrashReportStore.createFatalReport(
                context,
                Thread.currentThread(),
                IllegalStateException("boom"),
            )

        assertTrue(report.processName.isNotBlank())
        assertTrue(report.processId > 0)
        assertEquals(Thread.currentThread().name, report.threadName)
        assertTrue(report.throwableText.contains("IllegalStateException: boom"))
        assertNotNull(CrashReportStore.readReport(context, report.reportId))
    }

    @Test
    fun concurrentWrites_areSerializedWithinProcess() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val futures =
                (1..8).map { index ->
                    executor.submit<CrashReportRecord> { createReport(index) }
                }

            futures.forEach { future -> assertNotNull(future.get(10, TimeUnit.SECONDS)) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(5, CrashReportStore.listReports(context).size)
    }

    private fun createReport(index: Int): CrashReportRecord =
        CrashReportStore.createReport(
            context = context,
            draft =
                CrashReportDraft(
                    type = CrashReportType.APP_FATAL,
                    processName = "com.kiyori",
                    processId = 1234,
                    threadName = "main",
                    throwableText = "error-$index",
                ),
            createdAtEpochMs = index.toLong(),
            reportId =
                "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}",
        )
}
