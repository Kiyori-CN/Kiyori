package com.ai.assistance.operit.util.crash

import android.content.Context
import android.os.Process
import android.util.AtomicFile
import com.kiyori.platform.logging.KiyoriLogTextFormatter
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.UUID

internal object CrashReportStore {
    private const val DIRECTORY_NAME = "crash-reports"
    private const val LOCK_FILE_NAME = ".store.lock"
    private const val REPORT_FILE_PREFIX = "report-"
    private const val REPORT_FILE_SUFFIX = ".json"
    private const val MAX_REPORTS = 5
    private val processLocalLock = Any()

    fun createFatalReport(
        context: Context,
        thread: Thread,
        throwable: Throwable,
    ): CrashReportRecord =
        createReport(
            context,
            CrashReportDraft(
                type = CrashProcessIdentity.reportTypeForCurrentProcess(context),
                processName = CrashProcessIdentity.currentProcessName(context),
                processId = Process.myPid(),
                threadName = thread.name.take(256),
                throwableText =
                    KiyoriLogTextFormatter.format(
                        throwable,
                        CrashReportRecord.MAX_THROWABLE_CHARS,
                    ),
            ),
        )

    fun createReport(
        context: Context,
        draft: CrashReportDraft,
        createdAtEpochMs: Long = System.currentTimeMillis(),
        reportId: String = UUID.randomUUID().toString(),
    ): CrashReportRecord =
        withStoreLock(context) { directory ->
            val report =
                CrashReportRecord(
                    reportId = reportId,
                    createdAtEpochMs = createdAtEpochMs,
                    type = draft.type,
                    processName = draft.processName.take(256),
                    processId = draft.processId,
                    threadName = draft.threadName?.take(256),
                    throwableText =
                        KiyoriLogTextFormatter.truncateText(
                            draft.throwableText,
                            CrashReportRecord.MAX_THROWABLE_CHARS,
                        ),
                    runtimeGeneration = draft.runtimeGeneration,
                    exitReason = draft.exitReason,
                    exitStatus = draft.exitStatus,
                    exitDescription = draft.exitDescription?.take(2_000),
                    processImportance = draft.processImportance,
                    processStateSummary = draft.processStateSummary?.take(512),
                    playerJournal =
                        KiyoriLogTextFormatter.truncateText(
                            draft.playerJournal,
                            CrashReportRecord.MAX_PLAYER_JOURNAL_CHARS,
                        ),
                    attachmentNames = draft.attachmentNames,
                )
            writeLocked(directory, report)
            pruneLocked(directory)
            report
        }

    fun readReport(context: Context, reportId: String): CrashReportRecord? {
        if (!CrashReportRecord.isValidReportId(reportId)) return null
        val file = reportFile(storeDirectory(context), reportId)
        if (!file.exists() && !backupFile(file).exists()) return null
        return readFile(file)
    }

    fun markDisplayed(context: Context, reportId: String): CrashReportRecord? =
        updateStatus(context, reportId, CrashReportStatus.DISPLAYED)

    fun markResolved(context: Context, reportId: String): CrashReportRecord? =
        updateStatus(context, reportId, CrashReportStatus.RESOLVED)

    fun updateReport(
        context: Context,
        reportId: String,
        transform: (CrashReportRecord) -> CrashReportRecord,
    ): CrashReportRecord? {
        if (!CrashReportRecord.isValidReportId(reportId)) return null
        return withStoreLock(context) { directory ->
            val file = reportFile(directory, reportId)
            if (!file.exists() && !backupFile(file).exists()) return@withStoreLock null
            val updated = transform(readFile(file))
            require(updated.reportId == reportId) { "Crash report ID cannot change" }
            writeLocked(directory, updated)
            updated
        }
    }

    fun hasUnresolvedReports(context: Context): Boolean =
        withStoreLock(context) { directory ->
            readAllLocked(directory).any { report -> report.status != CrashReportStatus.RESOLVED }
        }

    fun listReports(context: Context): List<CrashReportRecord> =
        withStoreLock(context) { directory ->
            readAllLocked(directory)
                .sortedWith(
                    compareByDescending<CrashReportRecord> { it.createdAtEpochMs }
                        .thenByDescending { it.reportId },
                )
        }

    fun deleteAllForTest(context: Context) {
        withStoreLock(context) { directory ->
            directory.listFiles()
                .orEmpty()
                .filter { file ->
                    file.name.startsWith(REPORT_FILE_PREFIX) ||
                        file.name.endsWith(REPORT_FILE_SUFFIX + ".bak")
                }
                .forEach(File::delete)
        }
    }

    private fun updateStatus(
        context: Context,
        reportId: String,
        status: CrashReportStatus,
    ): CrashReportRecord? {
        if (!CrashReportRecord.isValidReportId(reportId)) return null
        return withStoreLock(context) { directory ->
            val file = reportFile(directory, reportId)
            if (!file.exists() && !backupFile(file).exists()) return@withStoreLock null
            val updated = updateCrashReportStatus(readFile(file), status)
            writeLocked(directory, updated)
            updated
        }
    }

    private fun pruneLocked(directory: File) {
        val reports = readAllLocked(directory)
        val retainedIds =
            selectRetainedCrashReports(reports, MAX_REPORTS).mapTo(HashSet()) { it.reportId }
        reports
            .filterNot { report -> report.reportId in retainedIds }
            .forEach { report ->
                AtomicFile(reportFile(directory, report.reportId)).delete()
            }
    }

    private fun readAllLocked(directory: File): List<CrashReportRecord> =
        directory.listFiles { file ->
            file.isFile &&
                file.name.startsWith(REPORT_FILE_PREFIX) &&
                file.name.endsWith(REPORT_FILE_SUFFIX)
        }
            .orEmpty()
            .mapNotNull { file -> runCatching { readFile(file) }.getOrNull() }

    private fun readFile(file: File): CrashReportRecord {
        val text =
            AtomicFile(file)
                .openRead()
                .use { input -> input.readBytes().toString(StandardCharsets.UTF_8) }
        return CrashReportCodec.decode(text)
    }

    private fun writeLocked(
        directory: File,
        report: CrashReportRecord,
    ) {
        val atomicFile = AtomicFile(reportFile(directory, report.reportId))
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(CrashReportCodec.encode(report).toByteArray(StandardCharsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            output?.let(atomicFile::failWrite)
            throw error
        }
    }

    private inline fun <T> withStoreLock(
        context: Context,
        block: (File) -> T,
    ): T =
        synchronized(processLocalLock) {
            val directory = storeDirectory(context)
            require(directory.isDirectory || directory.mkdirs()) {
                "Unable to create crash report directory"
            }
            RandomAccessFile(File(directory, LOCK_FILE_NAME), "rw").use { randomAccess ->
                randomAccess.channel.use { channel ->
                    channel.lock().use {
                        block(directory)
                    }
                }
            }
        }

    private fun storeDirectory(context: Context): File =
        File(context.applicationContext.filesDir, DIRECTORY_NAME)

    private fun reportFile(
        directory: File,
        reportId: String,
    ): File = File(directory, "$REPORT_FILE_PREFIX$reportId$REPORT_FILE_SUFFIX")

    private fun backupFile(file: File): File = File(file.parentFile, "${file.name}.bak")
}
