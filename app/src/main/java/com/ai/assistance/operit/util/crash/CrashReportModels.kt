package com.ai.assistance.operit.util.crash

import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal enum class CrashReportType {
    APP_FATAL,
    PLAYER_RUNTIME_FATAL,
}

@Serializable
internal enum class CrashReportStatus {
    PENDING,
    DISPLAYED,
    RESOLVED,
}

internal enum class CrashReportPrimaryAction {
    RESTART_APP,
    RESTART_PLAYER,
}

@Serializable
internal data class CrashReportRecord(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val reportId: String,
    val createdAtEpochMs: Long,
    val type: CrashReportType,
    val status: CrashReportStatus = CrashReportStatus.PENDING,
    val processName: String,
    val processId: Int,
    val threadName: String? = null,
    val throwableText: String = "",
    val runtimeGeneration: Long? = null,
    val exitReason: Int? = null,
    val exitStatus: Int? = null,
    val exitDescription: String? = null,
    val processImportance: Int? = null,
    val processStateSummary: String? = null,
    val playerJournal: String = "",
    val attachmentNames: List<String> = emptyList(),
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported crash report schema: $schemaVersion"
        }
        require(CRASH_REPORT_ID_PATTERN.matches(reportId)) { "Invalid crash report ID" }
        require(createdAtEpochMs > 0L) { "Crash report timestamp is invalid" }
        require(processName.isNotBlank()) { "Crash report process name is blank" }
        require(processId > 0) { "Crash report process ID is invalid" }
        require(throwableText.length <= MAX_THROWABLE_CHARS) {
            "Crash report throwable text is too large"
        }
        require(playerJournal.length <= MAX_PLAYER_JOURNAL_CHARS) {
            "Crash report player journal is too large"
        }
        require(attachmentNames.size <= MAX_ATTACHMENTS) {
            "Crash report has too many attachments"
        }
        require(attachmentNames.all(::isValidCrashAttachmentName)) {
            "Crash report attachment name is invalid"
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_THROWABLE_CHARS = 24_000
        const val MAX_PLAYER_JOURNAL_CHARS = 48_000
        const val MAX_ATTACHMENTS = 4
        private val CRASH_REPORT_ID_PATTERN =
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        fun isValidReportId(value: String): Boolean = CRASH_REPORT_ID_PATTERN.matches(value)
    }
}

internal data class CrashReportDraft(
    val type: CrashReportType,
    val processName: String,
    val processId: Int,
    val threadName: String? = null,
    val throwableText: String = "",
    val runtimeGeneration: Long? = null,
    val exitReason: Int? = null,
    val exitStatus: Int? = null,
    val exitDescription: String? = null,
    val processImportance: Int? = null,
    val processStateSummary: String? = null,
    val playerJournal: String = "",
    val attachmentNames: List<String> = emptyList(),
)

internal object CrashReportCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

    fun encode(record: CrashReportRecord): String = json.encodeToString(record)

    fun decode(text: String): CrashReportRecord = json.decodeFromString(text)
}

internal object CrashReportFormatter {
    fun format(record: CrashReportRecord): String =
        buildString {
            appendLine("Kiyori Crash Report")
            appendLine("Report ID: ${record.reportId}")
            appendLine("Created: ${Instant.ofEpochMilli(record.createdAtEpochMs)}")
            appendLine("Type: ${record.type}")
            appendLine("Status: ${record.status}")
            appendLine("Process: ${record.processName}")
            appendLine("PID: ${record.processId}")
            record.threadName?.let { appendLine("Thread: $it") }
            record.runtimeGeneration?.let { appendLine("Runtime generation: $it") }
            record.exitReason?.let { appendLine("Exit reason: $it") }
            record.exitStatus?.let { appendLine("Exit status: $it") }
            record.exitDescription?.let { appendLine("Exit description: $it") }
            record.processImportance?.let { appendLine("Process importance: $it") }
            record.processStateSummary?.let { appendLine("Process state: $it") }

            if (record.throwableText.isNotBlank()) {
                appendLine()
                appendLine("Throwable")
                appendLine(record.throwableText)
            }
            if (record.playerJournal.isNotBlank()) {
                appendLine()
                appendLine("Player journal")
                appendLine(record.playerJournal)
            }
            if (record.attachmentNames.isNotEmpty()) {
                appendLine()
                appendLine("Attachments")
                record.attachmentNames.forEach { appendLine("- $it") }
            }
        }.trimEnd()
}

internal fun updateCrashReportStatus(
    record: CrashReportRecord,
    status: CrashReportStatus,
): CrashReportRecord {
    val nextStatus =
        when (record.status) {
            CrashReportStatus.PENDING -> status
            CrashReportStatus.DISPLAYED ->
                if (status == CrashReportStatus.RESOLVED) {
                    CrashReportStatus.RESOLVED
                } else {
                    CrashReportStatus.DISPLAYED
                }
            CrashReportStatus.RESOLVED -> CrashReportStatus.RESOLVED
        }
    return if (nextStatus == record.status) record else record.copy(status = nextStatus)
}

internal fun selectCrashReportPrimaryAction(
    reportType: CrashReportType?,
): CrashReportPrimaryAction =
    if (reportType == CrashReportType.PLAYER_RUNTIME_FATAL) {
        CrashReportPrimaryAction.RESTART_PLAYER
    } else {
        CrashReportPrimaryAction.RESTART_APP
    }

internal fun selectRetainedCrashReports(
    reports: List<CrashReportRecord>,
    maxReports: Int,
): List<CrashReportRecord> {
    require(maxReports > 0) { "Crash report retention limit must be positive" }
    return reports
        .sortedWith(
            compareByDescending<CrashReportRecord> { it.createdAtEpochMs }
                .thenByDescending { it.reportId },
        )
        .take(maxReports)
}

private fun isValidCrashAttachmentName(value: String): Boolean =
    value.isNotBlank() &&
        value.length <= 160 &&
        value != "." &&
        value != ".." &&
        '/' !in value &&
        '\\' !in value &&
        '\u0000' !in value
