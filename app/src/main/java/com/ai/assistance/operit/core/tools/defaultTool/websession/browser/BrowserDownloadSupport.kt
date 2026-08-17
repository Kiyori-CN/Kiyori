package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.AtomicFile
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeClient
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeTerminalState
import com.ai.assistance.operit.core.player.PlayerSettingsStore
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.platform.storage.KiyoriPaths
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.net.URI
import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val DOWNLOAD_SUPPORT_TAG = "BrowserDownloadSupport"
private const val BROWSER_DOWNLOAD_STATE_FILE = "browser_download_tasks.json"
internal const val BROWSER_DOWNLOAD_TYPE_HTTP = "http"
private const val MAX_BROWSER_DOWNLOAD_FILE_NAME_LENGTH = 180
internal const val MAX_PENDING_BROWSER_DOWNLOAD_REQUESTS = 32
internal const val MIN_BROWSER_DOWNLOAD_SEGMENT_BYTES = 1024L * 1024L

internal enum class BrowserDownloadStatus(val wireName: String) {
    QUEUED("queued"),
    CONNECTING("connecting"),
    DOWNLOADING("downloading"),
    PAUSED("paused"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELED("canceled");

    companion object {
        fun fromWireName(value: String?): BrowserDownloadStatus =
            entries.firstOrNull { it.wireName == value } ?: FAILED
    }
}

internal enum class BrowserDownloadAction {
    PAUSE,
    RESUME,
    CANCEL,
    RETRY,
    DELETE_RECORD,
    DELETE_WITH_FILE
}

internal enum class BrowserDownloadRenameMode {
    RENAME,
    SUFFIX,
}

internal fun browserDownloadRenameInput(
    fileName: String,
    mode: BrowserDownloadRenameMode,
): String =
    when (mode) {
        BrowserDownloadRenameMode.RENAME -> fileName.substringBeforeLast('.', fileName)
        BrowserDownloadRenameMode.SUFFIX -> fileName.substringAfterLast('.', "")
    }

internal fun buildBrowserDownloadRenameTarget(
    fileName: String,
    mode: BrowserDownloadRenameMode,
    rawInput: String,
): String {
    val input = rawInput.trim().removePrefix(".")
    if (input.isBlank()) {
        return ""
    }
    return when (mode) {
        BrowserDownloadRenameMode.RENAME -> {
            val extension = fileName.substringAfterLast('.', "")
            if (input.contains('.') || extension.isBlank()) input else "$input.$extension"
        }
        BrowserDownloadRenameMode.SUFFIX ->
            "${fileName.substringBeforeLast('.', fileName)}.$input"
    }
}

internal data class BrowserDownloadQueueEntry(
    val taskId: String,
    val status: BrowserDownloadStatus,
    val queuedAt: Long,
    val createdAt: Long,
)

internal fun selectQueuedBrowserDownloadTaskIds(
    entries: List<BrowserDownloadQueueEntry>,
    activeTaskIds: Set<String>,
    availableSlots: Int,
): List<String> {
    if (availableSlots <= 0) {
        return emptyList()
    }
    return entries
        .withIndex()
        .asSequence()
        .filter { (_, entry) ->
            entry.status == BrowserDownloadStatus.QUEUED && entry.taskId !in activeTaskIds
        }
        .sortedWith(
            compareBy<IndexedValue<BrowserDownloadQueueEntry>> { it.value.queuedAt }
                .thenBy { it.value.createdAt }
                .thenBy { it.index },
        )
        .take(availableSlots)
        .map { it.value.taskId }
        .toList()
}

internal fun resolveBrowserDownloadSegmentThreadCount(
    totalBytes: Long,
    supportsRanges: Boolean,
    requestedThreadCount: Int,
): Int {
    require(isSupportedBrowserDownloadSegmentThreadCount(requestedThreadCount)) {
        "Unsupported browser download segment thread count: $requestedThreadCount"
    }
    if (!supportsRanges || totalBytes <= 0L) {
        return 1
    }
    val maximumSegmentsBySize = (totalBytes / MIN_BROWSER_DOWNLOAD_SEGMENT_BYTES).coerceAtLeast(1L)
    return minOf(requestedThreadCount.toLong(), maximumSegmentsBySize).toInt()
}

internal fun resolveBrowserDownloadTransportMaxConcurrentTasks(
    requestedMaxConcurrentTasks: Int,
    normalThreadCount: Int,
    m3u8ThreadCount: Int,
): Int {
    require(isSupportedBrowserDownloadConcurrency(requestedMaxConcurrentTasks)) {
        "Unsupported browser download concurrency: $requestedMaxConcurrentTasks"
    }
    return minOf(
        requestedMaxConcurrentTasks,
        resolveBrowserDownloadMaxConcurrentTasksLimit(
            normalThreadCount = normalThreadCount,
            m3u8ThreadCount = m3u8ThreadCount,
        ),
    )
}

internal fun shouldAutoTransferBrowserDownload(
    autoTransferEnabled: Boolean,
    hasCustomDirectory: Boolean,
    isM3u8Package: Boolean,
): Boolean {
    require(!autoTransferEnabled || !hasCustomDirectory) {
        "Browser download public transfer and custom directory are mutually exclusive"
    }
    return autoTransferEnabled && !isM3u8Package
}

internal fun isBrowserDownloadApkPackage(mimeType: String, fileName: String): Boolean =
    mimeType.equals("application/vnd.android.package-archive", ignoreCase = true) ||
        fileName.substringAfterLast('.', "").equals("apk", ignoreCase = true)

internal fun isCompleteBrowserDownloadSegmentPlan(
    segments: List<BrowserDownloadSegmentRecord>,
    totalBytes: Long,
): Boolean {
    if (totalBytes <= 0L || segments.isEmpty()) {
        return false
    }
    val ordered = segments.sortedBy { it.index }
    if (ordered.map { it.index } != ordered.indices.toList()) {
        return false
    }
    var nextStart = 0L
    ordered.forEach { segment ->
        if (segment.startInclusive != nextStart || segment.endInclusive < segment.startInclusive) {
            return false
        }
        nextStart = segment.endInclusive + 1L
    }
    return nextStart == totalBytes
}

internal data class BrowserDownloadSegmentRecord(
    val index: Int,
    val startInclusive: Long,
    val endInclusive: Long,
    val tempPath: String
) {
    fun expectedLength(): Long =
        if (endInclusive >= startInclusive) {
            endInclusive - startInclusive + 1L
        } else {
            -1L
        }

    fun toJson(): JSONObject =
        JSONObject()
            .put("index", index)
            .put("start", startInclusive)
            .put("end", endInclusive)
            .put("temp_path", tempPath)

    companion object {
        fun fromJson(json: JSONObject): BrowserDownloadSegmentRecord =
            BrowserDownloadSegmentRecord(
                index = json.optInt("index"),
                startInclusive = json.optLong("start"),
                endInclusive = json.optLong("end"),
                tempPath = json.optString("temp_path")
            )
    }
}

internal data class BrowserDownloadTaskRecord(
    val id: String,
    val sessionId: String?,
    val type: String,
    val sourceUrl: String?,
    var destinationPath: String,
    var destinationUri: String? = null,
    var targetDirectoryUri: String? = null,
    var fileName: String,
    val headers: Map<String, String>,
    val createdAt: Long,
    var updatedAt: Long,
    var mimeType: String?,
    var status: BrowserDownloadStatus,
    var totalBytes: Long,
    var downloadedBytes: Long,
    var speedBytesPerSecond: Long,
    var supportsResume: Boolean,
    var resourceEtag: String? = null,
    var resourceLastModified: String? = null,
    var threadCount: Int,
    val m3u8ThreadCount: Int = DEFAULT_BROWSER_DOWNLOAD_M3U8_THREAD_COUNT,
    val packageM3u8Offline: Boolean = false,
    val autoTransferToPublicDirectory: Boolean = false,
    val chunkSizeKb: Int = DEFAULT_BROWSER_DOWNLOAD_CHUNK_SIZE_KB,
    val enableHttp2: Boolean = true,
    val networkPolicy: BrowserDownloadNetworkPolicy = BrowserDownloadNetworkPolicy.ANY,
    val allowRoaming: Boolean = false,
    var pendingInstallPackageName: String? = null,
    var pendingInstallVersionCode: Long? = null,
    var pendingInstallPreviousVersionCode: Long? = null,
    var isM3u8Package: Boolean = false,
    var errorMessage: String?,
    var completedAt: Long?,
    val segments: MutableList<BrowserDownloadSegmentRecord> = mutableListOf()
) {
    fun snapshot(): BrowserDownloadTaskRecord =
        copy(
            headers = LinkedHashMap(headers),
            segments = segments.map { it.copy() }.toMutableList()
        )

    fun activeOrPending(): Boolean =
        status == BrowserDownloadStatus.QUEUED ||
            status == BrowserDownloadStatus.CONNECTING ||
            status == BrowserDownloadStatus.DOWNLOADING

    fun supportsRetry(): Boolean = status == BrowserDownloadStatus.FAILED

    fun supportsResumeAction(): Boolean =
        status == BrowserDownloadStatus.PAUSED || status == BrowserDownloadStatus.CANCELED

    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("session_id", sessionId)
            .put("type", type)
            .put("source_url", sourceUrl)
            .put("destination_path", destinationPath)
            .put("destination_uri", destinationUri)
            .put("target_directory_uri", targetDirectoryUri)
            .put("file_name", fileName)
            .put("created_at", createdAt)
            .put("updated_at", updatedAt)
            .put("mime_type", mimeType)
            .put("status", status.wireName)
            .put("total_bytes", totalBytes)
            .put("downloaded_bytes", downloadedBytes)
            .put("speed_bytes_per_second", speedBytesPerSecond)
            .put("supports_resume", supportsResume)
            .put("resource_etag", resourceEtag)
            .put("resource_last_modified", resourceLastModified)
            .put("thread_count", threadCount)
            .put("m3u8_thread_count", m3u8ThreadCount)
            .put("auto_merge_m3u8", packageM3u8Offline)
            .put("auto_transfer_to_public_directory", autoTransferToPublicDirectory)
            .put("chunk_size_kb", chunkSizeKb)
            .put("enable_http2", enableHttp2)
            .put("network_policy", networkPolicy.persistedId)
            .put("allow_roaming", allowRoaming)
            .put("pending_install_package_name", pendingInstallPackageName)
            .put("pending_install_version_code", pendingInstallVersionCode)
            .put("pending_install_previous_version_code", pendingInstallPreviousVersionCode)
            .put("is_m3u8_package", isM3u8Package)
            .put("error_message", errorMessage)
            .put("completed_at", completedAt)
            .put(
                "headers",
                JSONObject().also { json ->
                    headers.forEach { (name, value) ->
                        json.put(name, value)
                    }
                }
            ).put(
                "segments",
                JSONArray().also { array ->
                    segments.forEach { segment ->
                        array.put(segment.toJson())
                    }
                }
            )

    companion object {
        fun fromJson(json: JSONObject): BrowserDownloadTaskRecord {
            val headersJson = json.optJSONObject("headers") ?: JSONObject()
            val headers = LinkedHashMap<String, String>()
            headersJson.keys().forEach { key ->
                headers[key] = headersJson.optString(key)
            }
            val segmentsJson = json.optJSONArray("segments") ?: JSONArray()
            val segments =
                MutableList(segmentsJson.length()) { index ->
                    BrowserDownloadSegmentRecord.fromJson(segmentsJson.getJSONObject(index))
                }
            return BrowserDownloadTaskRecord(
                id = json.optString("id"),
                sessionId = json.optString("session_id").ifBlank { null },
                type = json.optString("type", BROWSER_DOWNLOAD_TYPE_HTTP),
                sourceUrl = json.optString("source_url").ifBlank { null },
                destinationPath = json.optString("destination_path"),
                destinationUri = json.optString("destination_uri").ifBlank { null },
                targetDirectoryUri = json.optString("target_directory_uri").ifBlank { null },
                fileName = json.optString("file_name"),
                headers = headers,
                createdAt = json.optLong("created_at"),
                updatedAt = json.optLong("updated_at"),
                mimeType = json.optString("mime_type").ifBlank { null },
                status = BrowserDownloadStatus.fromWireName(json.optString("status")),
                totalBytes = json.optLong("total_bytes"),
                downloadedBytes = json.optLong("downloaded_bytes"),
                speedBytesPerSecond = json.optLong("speed_bytes_per_second"),
                supportsResume = json.optBoolean("supports_resume"),
                resourceEtag = json.optString("resource_etag").ifBlank { null },
                resourceLastModified = json.optString("resource_last_modified").ifBlank { null },
                threadCount =
                    json.optInt(
                        "thread_count",
                        DEFAULT_BROWSER_DOWNLOAD_SEGMENT_THREAD_COUNT,
                    ),
                m3u8ThreadCount =
                    json.optInt(
                        "m3u8_thread_count",
                        DEFAULT_BROWSER_DOWNLOAD_M3U8_THREAD_COUNT,
                    ),
                packageM3u8Offline = json.optBoolean("auto_merge_m3u8", false),
                autoTransferToPublicDirectory =
                    json.optBoolean("auto_transfer_to_public_directory", false),
                chunkSizeKb =
                    json.optInt(
                        "chunk_size_kb",
                        DEFAULT_BROWSER_DOWNLOAD_CHUNK_SIZE_KB,
                    ),
                enableHttp2 = json.optBoolean("enable_http2", true),
                networkPolicy =
                    BrowserDownloadNetworkPolicy.fromPersistedId(
                        json.optString(
                            "network_policy",
                            BrowserDownloadNetworkPolicy.ANY.persistedId,
                        ),
                    ),
                allowRoaming = json.optBoolean("allow_roaming", false),
                pendingInstallPackageName =
                    json.optString("pending_install_package_name").ifBlank { null },
                pendingInstallVersionCode =
                    json.optLong("pending_install_version_code")
                        .takeIf {
                            json.has("pending_install_version_code") &&
                                !json.isNull("pending_install_version_code")
                        },
                pendingInstallPreviousVersionCode =
                    json.optLong("pending_install_previous_version_code")
                        .takeIf {
                            json.has("pending_install_previous_version_code") &&
                                !json.isNull("pending_install_previous_version_code")
                        },
                isM3u8Package = json.optBoolean("is_m3u8_package", false),
                errorMessage = json.optString("error_message").ifBlank { null },
                completedAt = json.optLong("completed_at").takeIf { it > 0L },
                segments = segments
            )
        }
    }
}

private enum class BrowserDownloadWorkerStopAction {
    PAUSE,
    CANCEL,
    DELETE_RECORD,
    DELETE_WITH_FILE,
    RUNTIME_SUSPEND,
}

private data class BrowserDownloadActiveControl(
    val job: Job,
    @Volatile var stopAction: BrowserDownloadWorkerStopAction? = null,
)

private data class BrowserDownloadInlinePayload(
    val bytes: ByteArray
)

internal class BrowserDownloadManager private constructor(
    private val appContext: Context
) {
    companion object {
        @Volatile
        private var instance: BrowserDownloadManager? = null

        fun getInstance(context: Context): BrowserDownloadManager =
            instance ?: synchronized(this) {
                instance ?: BrowserDownloadManager(context.applicationContext).also {
                    instance = it
                }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsStore = BrowserDownloadSettingsStore.getInstance(appContext)
    private val schedulerLock = Any()
    private val persistenceLock = Any()
    private val tasks = LinkedHashMap<String, BrowserDownloadTaskRecord>()
    private val transientReservedDestinationPaths = mutableSetOf<String>()
    private val activeControls = ConcurrentHashMap<String, BrowserDownloadActiveControl>()
    private val inlinePayloads = ConcurrentHashMap<String, BrowserDownloadInlinePayload>()
    private val stateFile = AtomicFile(File(appContext.filesDir, BROWSER_DOWNLOAD_STATE_FILE))
    private val _taskSnapshots = MutableStateFlow<List<BrowserDownloadTaskRecord>>(emptyList())

    val taskSnapshots: StateFlow<List<BrowserDownloadTaskRecord>> = _taskSnapshots.asStateFlow()

    @Volatile private var taskListener: ((BrowserDownloadTaskRecord, WebDownloadEvent) -> Unit)? = null
    @Volatile private var uiRefreshListener: (() -> Unit)? = null
    @Volatile private var lastUiDispatchAt: Long = 0L
    @Volatile private var lastPersistAt: Long = 0L
    @Volatile private var lastEventAt: Long = 0L
    @Volatile private var lastEvent: WebDownloadEvent? = null
    @Volatile private var runtimeExecutionEnabled = false

    init {
        loadState()
        normalizeRestoredTasks(
            resumeInterruptedTasks =
                BrowserDownloadRuntimeCoordinator.shouldResumeRestoredTasks(appContext),
        )
        publishTaskSnapshots()
        deleteBrowserDownloadApkMetadataStaging(appContext)
        scope.launch {
            reconcilePendingInstalledPackages()
        }
        scope.launch {
            settingsStore.state.collect { settings ->
                scheduleQueuedTasks()
                if (!settings.autoCleanApk) {
                    clearAllPendingInstallRequests()
                }
            }
        }
    }

    fun setTaskListener(listener: ((BrowserDownloadTaskRecord, WebDownloadEvent) -> Unit)?) {
        taskListener = listener
    }

    fun setUiRefreshListener(listener: (() -> Unit)?) {
        uiRefreshListener = listener
    }

    fun snapshotTasks(): List<BrowserDownloadTaskRecord> =
        _taskSnapshots.value.map { task -> task.snapshot() }

    internal fun acquireRuntimeExecution() {
        synchronized(schedulerLock) {
            runtimeExecutionEnabled = true
        }
        scheduleQueuedTasks()
    }

    internal fun isRuntimeExecutionEnabled(): Boolean = runtimeExecutionEnabled

    internal fun releaseRuntimeExecutionIfIdle(): Boolean =
        synchronized(schedulerLock) {
            val hasRunnableTasks =
                synchronized(tasks) {
                    tasks.values.any { task ->
                        task.type == BROWSER_DOWNLOAD_TYPE_HTTP && task.activeOrPending()
                    }
                }
            if (hasRunnableTasks) {
                false
            } else {
                runtimeExecutionEnabled = false
                true
            }
        }

    internal fun suspendRuntimeExecutionForSystemStop(): Boolean {
        val controls =
            synchronized(schedulerLock) {
                runtimeExecutionEnabled = false
                val runtimeTaskIds =
                    synchronized(tasks) {
                        tasks.values
                            .filter { task -> task.type == BROWSER_DOWNLOAD_TYPE_HTTP }
                            .map { task -> task.id }
                            .toSet()
                    }
                activeControls
                    .filterKeys { taskId -> taskId in runtimeTaskIds }
                    .values
                    .toList()
                    .also { currentControls ->
                    currentControls.forEach { control ->
                        control.stopAction = BrowserDownloadWorkerStopAction.RUNTIME_SUSPEND
                    }
                }
            }
        controls.forEach { control ->
            control.job.cancel(CancellationException("download runtime stopped"))
        }
        val now = System.currentTimeMillis()
        synchronized(tasks) {
            tasks.values.forEach { task ->
                if (
                    task.type == BROWSER_DOWNLOAD_TYPE_HTTP &&
                        (
                            task.status == BrowserDownloadStatus.CONNECTING ||
                                task.status == BrowserDownloadStatus.DOWNLOADING
                        )
                ) {
                    task.status = BrowserDownloadStatus.QUEUED
                    task.updatedAt = now
                    task.speedBytesPerSecond = 0L
                    task.downloadedBytes = computeDownloadedBytes(task)
                }
            }
        }
        publishTaskSnapshots()
        persistState(force = true)
        dispatchUiRefresh(force = true)
        return synchronized(tasks) {
            tasks.values.any { task ->
                task.type == BROWSER_DOWNLOAD_TYPE_HTTP &&
                    task.status == BrowserDownloadStatus.QUEUED
            }
        }
    }

    internal fun pauseAllForUser() {
        val controls =
            synchronized(schedulerLock) {
                runtimeExecutionEnabled = false
                activeControls.values.toList().also { currentControls ->
                    currentControls.forEach { control ->
                        control.stopAction = BrowserDownloadWorkerStopAction.PAUSE
                    }
                }
            }
        controls.forEach { control ->
            control.job.cancel(CancellationException("pause all requested"))
        }
        val now = System.currentTimeMillis()
        synchronized(tasks) {
            tasks.values.forEach { task ->
                if (task.activeOrPending()) {
                    task.status = BrowserDownloadStatus.PAUSED
                    task.updatedAt = now
                    task.speedBytesPerSecond = 0L
                    task.errorMessage = null
                    task.downloadedBytes = computeDownloadedBytes(task)
                }
            }
        }
        publishTaskSnapshots()
        persistState(force = true)
        dispatchUiRefresh(force = true)
    }

    internal fun hasRunnableTasks(): Boolean =
        synchronized(tasks) {
            tasks.values.any { task ->
                task.type == BROWSER_DOWNLOAD_TYPE_HTTP && task.activeOrPending()
            }
        }

    internal fun requiresUnmeteredRuntimeNetwork(): Boolean =
        browserDownloadRuntimeRequiresUnmeteredNetwork(snapshotTasks())

    internal fun refreshRuntimeNetworkPolicy() {
        val blockedTaskIds =
            snapshotTasks()
                .filter { task ->
                    task.type == BROWSER_DOWNLOAD_TYPE_HTTP &&
                        (
                            task.status == BrowserDownloadStatus.CONNECTING ||
                                task.status == BrowserDownloadStatus.DOWNLOADING
                        ) &&
                        !isRuntimeNetworkAllowed(task)
                }
                .map { task -> task.id }
                .toSet()
        if (blockedTaskIds.isNotEmpty()) {
            val controls =
                synchronized(schedulerLock) {
                    activeControls
                        .filterKeys { taskId -> taskId in blockedTaskIds }
                        .values
                        .toList()
                        .also { blockedControls ->
                            blockedControls.forEach { control ->
                                control.stopAction =
                                    BrowserDownloadWorkerStopAction.RUNTIME_SUSPEND
                            }
                        }
                }
            controls.forEach { control ->
                control.job.cancel(CancellationException("download network policy changed"))
            }
            val now = System.currentTimeMillis()
            synchronized(tasks) {
                blockedTaskIds.forEach { taskId ->
                    tasks[taskId]?.let { task ->
                        task.status = BrowserDownloadStatus.QUEUED
                        task.updatedAt = now
                        task.speedBytesPerSecond = 0L
                        task.downloadedBytes = computeDownloadedBytes(task)
                    }
                }
            }
            publishTaskSnapshots()
            persistState(force = true)
            dispatchUiRefresh(force = true)
        }
        scheduleQueuedTasks()
    }

    internal fun estimatedRemainingNetworkBytes(): Long {
        val remaining =
            synchronized(tasks) {
                tasks.values
                    .filter { task ->
                        task.type == BROWSER_DOWNLOAD_TYPE_HTTP && task.activeOrPending()
                    }
                    .map { task ->
                        task.totalBytes
                            .takeIf { total -> total > 0L }
                            ?.minus(task.downloadedBytes.coerceAtLeast(0L))
                            ?.coerceAtLeast(0L)
                    }
            }
        if (remaining.isEmpty() || remaining.any { bytes -> bytes == null }) {
            return -1L
        }
        return remaining.filterNotNull().fold(0L) { total, bytes ->
            if (Long.MAX_VALUE - total < bytes) {
                Long.MAX_VALUE
            } else {
                total + bytes
            }
        }
    }

    fun releasePersistedDirectoryPermissionIfUnused(treeUriString: String?) {
        val normalizedUri = treeUriString?.trim().orEmpty()
        if (normalizedUri.isBlank()) {
            return
        }
        val taskDirectoryUris =
            synchronized(tasks) {
                tasks.values.mapNotNull { task -> task.targetDirectoryUri }
            }
        val playerSettings = PlayerSettingsStore.getInstance(appContext).current
        if (
            !shouldReleaseBrowserDownloadDirectoryPermission(
                candidateUri = normalizedUri,
                settingsDirectoryUris =
                    listOf(
                        settingsStore.current.customDirectoryUri,
                        playerSettings.screenshotDirectoryUri,
                        playerSettings.videoDownloadDirectoryUri,
                    ),
                taskDirectoryUris = taskDirectoryUris,
            )
        ) {
            return
        }
        val treeUri = normalizedUri.toUri()
        val permission =
            appContext.contentResolver.persistedUriPermissions
                .firstOrNull { persisted -> persisted.uri == treeUri }
                ?: return
        val releaseFlags =
            (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        if (releaseFlags == 0) {
            return
        }
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(treeUri, releaseFlags)
        }.onFailure { error ->
            logBrowserDownloadOperationFailure(
                operation = "Failed to release unused browser download directory permission",
                error = error,
                sensitiveValues = listOf(normalizedUri),
            )
        }
    }

    fun latestSessionUpdateAt(sessionId: String): Long =
        synchronized(tasks) {
            tasks.values
                .asSequence()
                .filter { it.sessionId == sessionId }
                .maxOfOrNull { it.updatedAt }
                ?: 0L
        }

    fun latestEventAt(): Long = lastEventAt

    fun latestEventAfter(marker: Long): WebDownloadEvent? =
        if (lastEventAt > marker) {
            lastEvent
        } else {
            null
        }

    private fun resolveUniqueApplicationDestinationFileLocked(
        suggestedFileName: String,
    ): File {
        val reservedPaths =
            tasks.values
                .asSequence()
                .map { task -> task.destinationPath }
                .filter { path -> path.isNotBlank() }
                .map { path -> File(path).absolutePath }
                .toMutableSet()
                .apply { addAll(transientReservedDestinationPaths) }
        return resolveUniqueBrowserDownloadFile(
            directory = browserDownloadApplicationDirectory(appContext),
            suggestedFileName = suggestedFileName,
            reservedPaths = reservedPaths,
        )
    }

    fun startHttpDownload(
        sessionId: String?,
        url: String,
        suggestedFileName: String,
        mimeType: String?,
        headers: Map<String, String>,
        destinationPolicy: BrowserDownloadDestination = BrowserDownloadDestination.FollowSettings,
    ): BrowserDownloadTaskRecord {
        val settings = settingsStore.current
        val targetDirectoryUri =
            when (destinationPolicy) {
                BrowserDownloadDestination.FollowSettings ->
                    settings.customDirectoryUri.takeIf { it.isNotBlank() }
                is BrowserDownloadDestination.DocumentTree -> destinationPolicy.treeUri
            }
        val autoTransferToPublicDirectory =
            when (destinationPolicy) {
                BrowserDownloadDestination.FollowSettings ->
                    settings.autoTransferToPublicDirectory
                is BrowserDownloadDestination.DocumentTree -> false
            }
        val now = System.currentTimeMillis()
        val task =
            synchronized(tasks) {
                val destination = resolveUniqueApplicationDestinationFileLocked(suggestedFileName)
                val reservedTask =
                    BrowserDownloadTaskRecord(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        type = BROWSER_DOWNLOAD_TYPE_HTTP,
                        sourceUrl = url,
                        destinationPath = destination.absolutePath,
                        targetDirectoryUri = targetDirectoryUri,
                        fileName = destination.name,
                        headers = LinkedHashMap(headers),
                        createdAt = now,
                        updatedAt = now,
                        mimeType = mimeType,
                        status = BrowserDownloadStatus.QUEUED,
                        totalBytes = -1L,
                        downloadedBytes = 0L,
                        speedBytesPerSecond = 0L,
                        supportsResume = false,
                        threadCount = settings.segmentThreadCount,
                        m3u8ThreadCount = settings.m3u8ThreadCount,
                        packageM3u8Offline = settings.packageM3u8Offline,
                        autoTransferToPublicDirectory = autoTransferToPublicDirectory,
                        chunkSizeKb = settings.chunkSizeKb,
                        enableHttp2 = settings.enableHttp2,
                        networkPolicy = settings.networkPolicy,
                        allowRoaming = settings.allowRoaming,
                        errorMessage = null,
                        completedAt = null,
                    )
                tasks[reservedTask.id] = reservedTask
                reservedTask
            }
        persistState(force = true)
        notifyTaskChanged(task, buildTaskEvent(task, "started"), forceUi = true)
        check(requestRuntimeExecution(task.id)) {
            "系统未允许启动后台下载，请保持应用在前台并重试"
        }
        scheduleQueuedTasks()
        return task.snapshot()
    }

    fun startInlineDownload(
        sessionId: String?,
        type: String,
        suggestedFileName: String,
        mimeType: String?,
        bytes: ByteArray,
        sourceUrl: String? = null
    ): BrowserDownloadTaskRecord {
        val settings = settingsStore.current
        val now = System.currentTimeMillis()
        val task =
            synchronized(tasks) {
                val destination = resolveUniqueApplicationDestinationFileLocked(suggestedFileName)
                val reservedTask =
                    BrowserDownloadTaskRecord(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        type = type,
                        sourceUrl = sourceUrl,
                        destinationPath = destination.absolutePath,
                        targetDirectoryUri = settings.customDirectoryUri.takeIf { it.isNotBlank() },
                        fileName = destination.name,
                        headers = emptyMap(),
                        createdAt = now,
                        updatedAt = now,
                        mimeType = mimeType,
                        status = BrowserDownloadStatus.QUEUED,
                        totalBytes = bytes.size.toLong(),
                        downloadedBytes = 0L,
                        speedBytesPerSecond = 0L,
                        supportsResume = false,
                        threadCount = 1,
                        m3u8ThreadCount = settings.m3u8ThreadCount,
                        packageM3u8Offline = settings.packageM3u8Offline,
                        autoTransferToPublicDirectory = settings.autoTransferToPublicDirectory,
                        chunkSizeKb = settings.chunkSizeKb,
                        enableHttp2 = settings.enableHttp2,
                        networkPolicy = settings.networkPolicy,
                        allowRoaming = settings.allowRoaming,
                        errorMessage = null,
                        completedAt = null,
                        segments =
                            mutableListOf(
                                BrowserDownloadSegmentRecord(
                                    index = 0,
                                    startInclusive = 0L,
                                    endInclusive = bytes.size.toLong() - 1L,
                                    tempPath = buildSinglePartPath(destination).absolutePath,
                                ),
                            ),
                    )
                inlinePayloads[reservedTask.id] = BrowserDownloadInlinePayload(bytes)
                tasks[reservedTask.id] = reservedTask
                reservedTask
            }
        persistState(force = true)
        notifyTaskChanged(task, buildTaskEvent(task, "started"), forceUi = true)
        scheduleQueuedTasks()
        return task.snapshot()
    }

    fun performAction(taskId: String, action: BrowserDownloadAction) {
        when (action) {
            BrowserDownloadAction.PAUSE -> pauseTask(taskId)
            BrowserDownloadAction.RESUME -> resumeTask(taskId)
            BrowserDownloadAction.CANCEL -> cancelTask(taskId)
            BrowserDownloadAction.RETRY -> retryTask(taskId)
            BrowserDownloadAction.DELETE_RECORD -> deleteTask(taskId, deleteFile = false)
            BrowserDownloadAction.DELETE_WITH_FILE -> deleteTask(taskId, deleteFile = true)
        }
    }

    fun renderDownloads(marker: Long = 0L, includeAll: Boolean = false): String? {
        val visibleTasks =
            snapshotTasks()
                .filter { includeAll || it.updatedAt > marker || it.activeOrPending() }
        if (visibleTasks.isEmpty()) {
            return null
        }
        return visibleTasks.joinToString("\n\n") { task ->
            buildString {
                appendLine("- File: ${task.fileName}")
                appendLine("- Status: ${task.status.wireName}")
                appendLine("- Type: ${task.type}")
                if (!task.sourceUrl.isNullOrBlank()) {
                    appendLine("- URL: ${browserDownloadDisplayUrl(task.sourceUrl)}")
                }
                if (task.totalBytes > 0L) {
                    appendLine("- Progress: ${formatTaskProgress(task)}")
                } else if (task.downloadedBytes > 0L) {
                    appendLine("- Downloaded: ${formatBytes(task.downloadedBytes)}")
                }
                if (task.speedBytesPerSecond > 0L) {
                    appendLine("- Speed: ${formatBytes(task.speedBytesPerSecond)}/s")
                }
                appendLine("- Saved path: ${browserDownloadSavedLocation(task)}")
                appendLine("- Resume supported: ${if (task.supportsResume) "yes" else "no"}")
                if (!task.errorMessage.isNullOrBlank()) {
                    append("- Error: ${task.errorMessage}")
                }
            }.trim()
        }
    }

    fun openDownloadedFile(taskId: String): Boolean {
        val task = snapshotTasks().firstOrNull { current -> current.id == taskId } ?: return false
        if (task.status != BrowserDownloadStatus.COMPLETED) {
            return false
        }
        val mimeType = resolveTaskMimeType(task)
        val isApkPackage =
            isBrowserDownloadApkPackage(
                mimeType = mimeType,
                fileName = task.fileName,
            )
        val preparedInstall =
            if (isApkPackage && settingsStore.current.autoCleanApk) {
                runCatching {
                    prepareBrowserDownloadApkInstall(appContext, task)
                }.onFailure { error ->
                    AppLogger.e(
                        DOWNLOAD_SUPPORT_TAG,
                        "Failed to prepare browser download APK installation",
                        error,
                    )
                }.getOrNull() ?: return false
            } else {
                null
            }
        if (preparedInstall != null) {
            mutateTask(taskId, forcePersist = true, forceUi = true) { current ->
                current.pendingInstallPackageName = preparedInstall.packageName
                current.pendingInstallVersionCode = preparedInstall.versionCode
                current.pendingInstallPreviousVersionCode =
                    preparedInstall.previouslyInstalledVersionCode
            }
        }
        val uri = preparedInstall?.installUri ?: resolveTaskOpenUri(task) ?: return false
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val launched = launchBrowserExternalIntent(appContext, intent)
        if (!launched && preparedInstall != null) {
            clearPendingInstallRequest(taskId)
        }
        return launched
    }

    internal fun handleInstalledPackage(
        packageName: String,
        installedVersionCode: Long,
    ) {
        val matchingTasks =
            snapshotTasks().filter { task ->
                matchesBrowserDownloadInstalledPackage(
                    pendingPackageName = task.pendingInstallPackageName,
                    pendingVersionCode = task.pendingInstallVersionCode,
                    installedPackageName = packageName,
                    installedVersionCode = installedVersionCode,
                )
            }
        if (!settingsStore.current.autoCleanApk) {
            matchingTasks.forEach { task -> clearPendingInstallRequest(task.id) }
            return
        }
        matchingTasks.forEach(::deleteInstalledPackageTask)
    }

    private fun reconcilePendingInstalledPackages() {
        val pendingTasks =
            snapshotTasks().filter { task -> !task.pendingInstallPackageName.isNullOrBlank() }
        if (!settingsStore.current.autoCleanApk) {
            pendingTasks.forEach { task -> clearPendingInstallRequest(task.id) }
            return
        }
        pendingTasks.forEach { task ->
            val packageName = requireNotNull(task.pendingInstallPackageName)
            val installedVersionCode =
                readInstalledBrowserDownloadPackageVersion(appContext, packageName)
                    ?: return@forEach
            if (
                shouldReconcileBrowserDownloadInstalledPackage(
                    pendingPackageName = packageName,
                    pendingVersionCode = task.pendingInstallVersionCode,
                    previouslyInstalledVersionCode = task.pendingInstallPreviousVersionCode,
                    installedPackageName = packageName,
                    installedVersionCode = installedVersionCode,
                )
            ) {
                deleteInstalledPackageTask(task)
            }
        }
    }

    private fun deleteInstalledPackageTask(task: BrowserDownloadTaskRecord) {
        runCatching {
            deleteTaskLocation(task)
        }.onSuccess {
            deleteTaskArtifacts(task.id, deleteFile = false)
        }.onFailure { error ->
            AppLogger.e(
                DOWNLOAD_SUPPORT_TAG,
                "Failed to clean installed browser download APK",
                error,
            )
        }
    }

    private fun clearAllPendingInstallRequests() {
        snapshotTasks()
            .filter { task -> !task.pendingInstallPackageName.isNullOrBlank() }
            .forEach { task -> clearPendingInstallRequest(task.id) }
    }

    private fun clearPendingInstallRequest(taskId: String) {
        mutateTask(taskId, forcePersist = true, forceUi = true) { task ->
            task.pendingInstallPackageName = null
            task.pendingInstallVersionCode = null
            task.pendingInstallPreviousVersionCode = null
        }
    }

    fun copyDownloadUrl(taskId: String): String? =
        snapshotTasks()
            .firstOrNull { task -> task.id == taskId }
            ?.sourceUrl
            ?.takeIf { url -> url.isNotBlank() }

    fun redownloadCompletedTask(taskId: String): Result<BrowserDownloadTaskRecord> =
        runCatching {
            val task = requireCompletedTask(taskId)
            val sourceUrl = requireNotNull(task.sourceUrl?.takeIf(::isBrowserDownloadNetworkUrl)) {
                "当前任务没有可重新下载的网络地址"
            }
            startHttpDownload(
                sessionId = task.sessionId,
                url = sourceUrl,
                suggestedFileName = task.fileName,
                mimeType = task.mimeType,
                headers = task.headers,
            )
        }.onFailure { error ->
            logBrowserDownloadOperationFailure(
                operation = "Failed to redownload browser task",
                error = error,
                taskId = taskId,
            )
        }

    fun copyDownloadLocation(taskId: String): String? =
        snapshotTasks()
            .firstOrNull { task -> task.id == taskId }
            ?.takeIf { task -> task.status == BrowserDownloadStatus.COMPLETED }
            ?.let(::browserDownloadSavedLocation)

    fun shareDownloadedFile(taskId: String): Boolean {
        val task = snapshotTasks().firstOrNull { current -> current.id == taskId } ?: return false
        if (task.status != BrowserDownloadStatus.COMPLETED) {
            return false
        }
        val uri = resolveTaskOpenUri(task) ?: return false
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = resolveTaskMimeType(task)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return try {
            launchBrowserExternalIntent(
                appContext,
                Intent.createChooser(intent, "分享本地文件"),
            )
        } catch (error: Exception) {
            logBrowserDownloadOperationFailure(
                operation = "Failed to share browser download",
                error = error,
                task = task,
            )
            false
        }
    }

    suspend fun renameDownloadedFile(taskId: String, targetFileName: String): Result<BrowserDownloadTaskRecord> =
        withContext(Dispatchers.IO) {
            var reservedRenamePath: String? = null
            runCatching {
                val task = requireCompletedTask(taskId)
                require(targetFileName.trim().isNotBlank()) { "文件名不能为空" }
                val safeFileName = normalizeBrowserDownloadFileName(targetFileName)
                val currentUri = task.destinationUri?.takeIf { it.isNotBlank() }?.toUri()
                if (currentUri != null) {
                    val renamedUri =
                        DocumentsContract.renameDocument(
                            appContext.contentResolver,
                            currentUri,
                            safeFileName,
                        ) ?: throw IOException("当前文件暂不支持重命名")
                    updateCompletedTaskLocation(
                        taskId,
                        safeFileName,
                        "",
                        renamedUri.toString(),
                        task.targetDirectoryUri,
                    )
                } else {
                    val currentFile = File(task.destinationPath)
                    require(currentFile.exists()) { "当前文件不存在" }
                    val renamedFile = File(currentFile.parentFile, safeFileName)
                    synchronized(tasks) {
                        val normalizedPath = renamedFile.absolutePath
                        val ownedByAnotherTask =
                            tasks.values.any { current ->
                                current.id != taskId &&
                                    current.destinationPath.isNotBlank() &&
                                    File(current.destinationPath).absolutePath == normalizedPath
                            }
                        require(!ownedByAnotherTask) { "目标文件名已由其他下载任务预留" }
                        require(transientReservedDestinationPaths.add(normalizedPath)) {
                            "目标文件名正在被其他文件操作使用"
                        }
                        reservedRenamePath = normalizedPath
                    }
                    if (task.isM3u8Package) {
                        renameBrowserM3u8Package(currentFile, renamedFile)
                    } else {
                        require(renamedFile.absolutePath != currentFile.absolutePath) { "文件名未发生变化" }
                        require(!renamedFile.exists()) { "目标文件已存在" }
                        require(currentFile.renameTo(renamedFile)) { "重命名失败" }
                    }
                    updateCompletedTaskLocation(
                        taskId,
                        renamedFile.name,
                        renamedFile.absolutePath,
                        null,
                        task.targetDirectoryUri,
                    )
                }
                snapshotTask(taskId)
            }.onFailure { error ->
                logBrowserDownloadOperationFailure(
                    operation = "Failed to rename browser download",
                    error = error,
                    taskId = taskId,
                )
            }.also {
                reservedRenamePath?.let { path ->
                    synchronized(tasks) {
                        transientReservedDestinationPaths.remove(path)
                    }
                }
            }
        }

    suspend fun moveDownloadedFileToDirectory(
        taskId: String,
        treeUriString: String,
    ): Result<BrowserDownloadTaskRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
                val task = requireCompletedTask(taskId)
                require(!task.isM3u8Package) { "M3U8离线包需要保留在应用下载目录" }
                val target = copyTaskToDocumentTree(task, treeUriString)
                val targetUri = requireNotNull(target.uri)
                try {
                    deleteTaskLocation(task)
                } catch (error: Throwable) {
                    runCatching { appContext.contentResolver.delete(targetUri, null, null) }
                        .onFailure { cleanupError ->
                            logBrowserDownloadOperationFailure(
                                operation =
                                    "Failed to remove copied download after source deletion failed",
                                error = cleanupError,
                                taskId = taskId,
                                sensitiveValues = listOf(treeUriString),
                            )
                        }
                    throw error
                }
                updateCompletedTaskLocation(
                    taskId,
                    target.displayName,
                    "",
                    targetUri.toString(),
                    treeUriString,
                )
                snapshotTask(taskId)
            }.onFailure { error ->
                // The document picker grants this tree before the copy starts. A failed move must
                // release that grant once no setting or retained task owns it, or repeated failures
                // would permanently accumulate unrelated SAF permissions.
                releasePersistedDirectoryPermissionIfUnused(treeUriString)
                logBrowserDownloadOperationFailure(
                    operation = "Failed to move browser download",
                    error = error,
                    taskId = taskId,
                    sensitiveValues = listOf(treeUriString),
                )
            }
        }

    suspend fun transferDownloadedFileToPublicDirectory(
        taskId: String,
    ): Result<BrowserDownloadTaskRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
                val task = requireCompletedTask(taskId)
                require(!task.isM3u8Package) { "M3U8离线包需要保留在应用下载目录" }
                val publicDirectory = KiyoriPaths.browserDownloadsDir()
                require(
                    task.destinationPath.isBlank() ||
                        File(task.destinationPath).parentFile?.canonicalFile != publicDirectory.canonicalFile,
                ) { "当前文件已位于公开下载目录" }
                val targetFile = reserveUniquePublicDestinationFile(task.fileName)
                try {
                    openTaskInputStream(task)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                            output.flush()
                        }
                    } ?: throw IOException("当前文件不存在")
                } catch (error: Throwable) {
                    targetFile.delete()
                    throw error
                }
                try {
                    deleteTaskLocation(task)
                } catch (error: Throwable) {
                    targetFile.delete()
                    throw error
                }
                MediaScannerConnection.scanFile(
                    appContext,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(resolveTaskMimeType(task)),
                    null,
                )
                updateCompletedTaskLocation(taskId, targetFile.name, targetFile.absolutePath, null, null)
                snapshotTask(taskId)
            }.onFailure { error ->
                logBrowserDownloadOperationFailure(
                    operation = "Failed to transfer browser download",
                    error = error,
                    taskId = taskId,
                )
            }
        }

    suspend fun mergeM3u8PackageToMp4(taskId: String): Result<BrowserDownloadTaskRecord> =
        withContext(Dispatchers.IO) {
            var temporaryOutput: File? = null
            var completedOutput: File? = null
            var reservedOutputPath: String? = null
            var taskUpdated = false
            runCatching {
                val task = requireCompletedTask(taskId)
                require(task.isM3u8Package) { "仅支持M3U8离线包合并" }
                require(task.destinationUri.isNullOrBlank()) { "M3U8离线包必须位于应用下载目录" }
                val playlistFile = File(task.destinationPath)
                require(playlistFile.isFile) { "M3U8离线播放列表不存在" }
                require(browserM3u8PackageDirectoryFor(playlistFile).isDirectory) {
                    "M3U8离线包资源目录不存在"
                }
                val outputFile =
                    synchronized(tasks) {
                        val resolved =
                            resolveUniqueApplicationDestinationFileLocked(
                            resolveBrowserDownloadMp4FileName(task.fileName),
                        )
                        check(transientReservedDestinationPaths.add(resolved.absolutePath))
                        reservedOutputPath = resolved.absolutePath
                        resolved
                    }
                temporaryOutput =
                    File(
                        outputFile.parentFile,
                        ".${outputFile.name}.${UUID.randomUUID()}.partial",
                    )
                val response =
                    FFmpegRuntimeClient.getInstance(appContext).executeArguments(
                        listOf(
                            "-y",
                            "-protocol_whitelist",
                            "file,crypto,data",
                            "-allowed_extensions",
                            "ALL",
                            "-i",
                            playlistFile.absolutePath,
                            "-map",
                            "0:v:0?",
                            "-map",
                            "0:a:0?",
                            "-c",
                            "copy",
                            "-bsf:a",
                            "aac_adtstoasc",
                            "-movflags",
                            "+faststart",
                            "-f",
                            "mp4",
                            requireNotNull(temporaryOutput).absolutePath,
                        ),
                    )
                require(
                    response.result.terminalState ==
                        FFmpegRuntimeTerminalState.SUCCEEDED,
                ) {
                    "M3U8合并失败：${response.output}"
                }
                val completedTemporaryOutput = requireNotNull(temporaryOutput)
                require(completedTemporaryOutput.isFile && completedTemporaryOutput.length() > 0L) {
                    "M3U8合并未生成有效MP4文件"
                }
                require(completedTemporaryOutput.renameTo(outputFile)) { "MP4文件写入失败" }
                completedOutput = outputFile
                // The task-record switch is the commit point. The source package must remain
                // untouched until the generated MP4 is durable and owned by the task; otherwise
                // a partial source cleanup could leave neither a usable package nor a usable MP4.
                mutateTask(taskId, forcePersist = true, forceUi = true) { current ->
                    current.fileName = outputFile.name
                    current.destinationPath = outputFile.absolutePath
                    current.destinationUri = null
                    current.targetDirectoryUri = null
                    current.mimeType = "video/mp4"
                    current.totalBytes = outputFile.length()
                    current.downloadedBytes = outputFile.length()
                    current.speedBytesPerSecond = 0L
                    current.isM3u8Package = false
                    current.errorMessage = null
                    current.updatedAt = System.currentTimeMillis()
                }
                taskUpdated = true
                runCatching { deleteTaskLocation(task) }
                    .onFailure { cleanupError ->
                        logBrowserDownloadOperationFailure(
                            operation = "M3U8 source cleanup failed after MP4 commit",
                            error = cleanupError,
                            task = task,
                        )
                    }
                MediaScannerConnection.scanFile(
                    appContext,
                    arrayOf(outputFile.absolutePath),
                    arrayOf("video/mp4"),
                    null,
                )
                snapshotTask(taskId)
            }.onFailure { error ->
                temporaryOutput?.takeIf { file -> file.exists() }?.delete()
                if (!taskUpdated) {
                    completedOutput?.takeIf { file -> file.exists() }?.delete()
                }
                logBrowserDownloadOperationFailure(
                    operation = "Failed to merge browser M3U8 package",
                    error = error,
                    taskId = taskId,
                )
            }.also {
                reservedOutputPath?.let { path ->
                    synchronized(tasks) {
                        transientReservedDestinationPaths.remove(path)
                    }
                }
            }
        }

    private fun requireCompletedTask(taskId: String): BrowserDownloadTaskRecord =
        snapshotTasks()
            .firstOrNull { task -> task.id == taskId }
            ?.also { task ->
                require(task.status == BrowserDownloadStatus.COMPLETED) {
                    "Only completed browser downloads can use this action"
                }
            }
            ?: throw IllegalArgumentException("Browser download task not found: $taskId")

    private fun snapshotTask(taskId: String): BrowserDownloadTaskRecord =
        snapshotTasks().firstOrNull { task -> task.id == taskId }
            ?: throw IllegalArgumentException("Browser download task not found: $taskId")

    private fun updateCompletedTaskLocation(
        taskId: String,
        fileName: String,
        destinationPath: String,
        destinationUri: String?,
        targetDirectoryUri: String?,
    ) {
        val previousTargetDirectoryUri = snapshotTask(taskId).targetDirectoryUri
        mutateTask(taskId, forcePersist = true, forceUi = true) { task ->
            task.fileName = fileName
            task.destinationPath = destinationPath
            task.destinationUri = destinationUri
            task.targetDirectoryUri = targetDirectoryUri
        }
        if (previousTargetDirectoryUri != targetDirectoryUri) {
            releasePersistedDirectoryPermissionIfUnused(previousTargetDirectoryUri)
        }
    }

    private fun resolveTaskOpenUri(task: BrowserDownloadTaskRecord): Uri? {
        task.destinationUri?.takeIf { it.isNotBlank() }?.let { return it.toUri() }
        val file = File(task.destinationPath)
        if (!file.exists()) {
            return null
        }
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
    }

    private fun resolveTaskMimeType(task: BrowserDownloadTaskRecord): String =
        task.mimeType?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(task.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT))
            ?: "application/octet-stream"

    private fun openTaskInputStream(task: BrowserDownloadTaskRecord): java.io.InputStream? {
        task.destinationUri?.takeIf { it.isNotBlank() }?.let { uriString ->
            return appContext.contentResolver.openInputStream(uriString.toUri())
        }
        return File(task.destinationPath).takeIf { it.exists() }?.inputStream()
    }

    private fun deleteTaskLocation(task: BrowserDownloadTaskRecord) {
        task.destinationUri?.takeIf { it.isNotBlank() }?.let { uriString ->
            val uri = uriString.toUri()
            when (uri.scheme?.lowercase(Locale.ROOT)) {
                "content" -> {
                    val deleted = appContext.contentResolver.delete(uri, null, null)
                    require(browserDownloadContentDeleteSucceeded(deleted)) {
                        "无法删除当前 SAF 文件"
                    }
                }
                "file" -> require(File(uri.path.orEmpty()).delete()) { "无法删除当前文件" }
                else -> throw IOException("Unsupported browser download URI: $uriString")
            }
            return
        }
        val file = File(task.destinationPath)
        if (task.isM3u8Package) {
            deleteBrowserM3u8PackageDirectory(browserM3u8PackageDirectoryFor(file))
        }
        require(file.exists() && file.delete()) { "无法删除当前文件" }
    }

    private fun copyTaskToDocumentTree(
        task: BrowserDownloadTaskRecord,
        treeUriString: String,
    ): BrowserDownloadSavedLocation {
        require(treeUriString.isNotBlank()) { "目标目录不能为空" }
        val directory =
            DocumentFile.fromTreeUri(appContext, treeUriString.toUri())
                ?: throw IOException("目标目录不可用")
        require(directory.exists() && directory.isDirectory) { "目标目录不可用" }
        val displayName = resolveAvailableDocumentName(directory, task.fileName)
        val targetDocument =
            directory.createFile(resolveTaskMimeType(task), displayName)
                ?: throw IOException("无法在目标目录创建文件")
        try {
            appContext.contentResolver.openOutputStream(targetDocument.uri, "w")?.use { output ->
                openTaskInputStream(task)?.use { input -> input.copyTo(output) }
                    ?: throw IOException("当前文件不存在")
                output.flush()
            } ?: throw IOException("无法写入目标目录")
        } catch (error: Throwable) {
            runCatching { appContext.contentResolver.delete(targetDocument.uri, null, null) }
                .onFailure { cleanupError ->
                    AppLogger.w(
                        DOWNLOAD_SUPPORT_TAG,
                        "Failed to remove incomplete moved download: ${cleanupError.message}",
                    )
                }
            throw error
        }
        return BrowserDownloadSavedLocation(
            uri = targetDocument.uri,
            absolutePath = "",
            displayName = targetDocument.name ?: displayName,
            fileSizeBytes = targetDocument.length(),
        )
    }

    fun openDownloadLocation(taskId: String? = null): Boolean {
        val task = taskId?.let { id -> snapshotTasks().firstOrNull { current -> current.id == id } }
        if (taskId != null && task == null) {
            return false
        }
        val customDirectoryUri = task?.targetDirectoryUri?.takeIf { it.isNotBlank() }
        val intent =
            if (customDirectoryUri != null) {
                Intent(Intent.ACTION_VIEW, customDirectoryUri.toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            } else {
                Intent("android.intent.action.VIEW_DOWNLOADS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        return launchBrowserExternalIntent(appContext, intent)
    }

    private fun pauseTask(taskId: String) {
        val control = activeControls[taskId]
        if (control != null) {
            control.stopAction = BrowserDownloadWorkerStopAction.PAUSE
            control.job.cancel(CancellationException("pause requested"))
            return
        }
        mutateTask(taskId, eventStatus = "paused") { task ->
            if (task.activeOrPending()) {
                task.status = BrowserDownloadStatus.PAUSED
                task.speedBytesPerSecond = 0L
                task.errorMessage = null
            }
        }
        scheduleQueuedTasks()
    }

    private fun cancelTask(taskId: String) {
        val control = activeControls[taskId]
        if (control != null) {
            control.stopAction = BrowserDownloadWorkerStopAction.CANCEL
            control.job.cancel(CancellationException("cancel requested"))
            return
        }
        mutateTask(taskId, eventStatus = "canceled") { task ->
            if (task.status != BrowserDownloadStatus.COMPLETED) {
                task.status = BrowserDownloadStatus.CANCELED
                task.speedBytesPerSecond = 0L
                task.errorMessage = null
            }
        }
        scheduleQueuedTasks()
    }

    private fun resumeTask(taskId: String) {
        val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        if (!task.supportsResumeAction()) {
            return
        }
        if (!task.supportsResume) {
            clearTemporaryArtifacts(task)
        }
        mutateTask(taskId, eventStatus = "queued") { current ->
            current.status = BrowserDownloadStatus.QUEUED
            current.errorMessage = null
            current.speedBytesPerSecond = 0L
            current.completedAt = null
            current.downloadedBytes = if (current.supportsResume) current.downloadedBytes else 0L
            resetSegmentsForRestart(current)
        }
        if (
            task.type != BROWSER_DOWNLOAD_TYPE_HTTP ||
                requestRuntimeExecution(taskId)
        ) {
            scheduleQueuedTasks()
        }
    }

    private fun retryTask(taskId: String) {
        val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        if (!task.supportsRetry()) {
            return
        }
        if (!task.supportsResume) {
            clearTemporaryArtifacts(task)
        }
        mutateTask(taskId, eventStatus = "queued") { current ->
            current.status = BrowserDownloadStatus.QUEUED
            current.errorMessage = null
            current.speedBytesPerSecond = 0L
            current.completedAt = null
            current.downloadedBytes = if (current.supportsResume) current.downloadedBytes else 0L
            resetSegmentsForRestart(current)
        }
        if (
            task.type != BROWSER_DOWNLOAD_TYPE_HTTP ||
                requestRuntimeExecution(taskId)
        ) {
            scheduleQueuedTasks()
        }
    }

    private fun requestRuntimeExecution(taskId: String): Boolean {
        if (
            BrowserDownloadRuntimeCoordinator.requestExecution(
                context = appContext,
                estimatedDownloadBytes = estimatedRemainingNetworkBytes(),
                runtimeExecutionEnabled = isRuntimeExecutionEnabled(),
                requiresUnmeteredNetwork = requiresUnmeteredRuntimeNetwork(),
            )
        ) {
            return true
        }
        val error =
            IllegalStateException(
                "系统未允许启动后台下载，请保持应用在前台并重试",
            )
        failTask(taskId, error)
        return false
    }

    private fun deleteTask(taskId: String, deleteFile: Boolean) {
        val control = activeControls[taskId]
        if (control != null) {
            control.stopAction =
                if (deleteFile) {
                    BrowserDownloadWorkerStopAction.DELETE_WITH_FILE
                } else {
                    BrowserDownloadWorkerStopAction.DELETE_RECORD
                }
            scope.launch {
                runCatching {
                    control.job.cancel(CancellationException("delete requested"))
                    control.job.join()
                }
                deleteTaskArtifacts(taskId, deleteFile)
            }
            return
        }
        deleteTaskArtifacts(taskId, deleteFile)
    }

    private fun scheduleQueuedTasks() {
        val jobsToStart =
            synchronized(schedulerLock) {
                val availableSlots =
                    (settingsStore.current.maxConcurrentTasks - activeControls.size).coerceAtLeast(0)
                val queueEntries =
                    synchronized(tasks) {
                        tasks.values
                            .filter { task ->
                                task.type != BROWSER_DOWNLOAD_TYPE_HTTP ||
                                    (
                                        runtimeExecutionEnabled &&
                                            isRuntimeNetworkAllowed(task)
                                    )
                            }
                            .map { task ->
                                BrowserDownloadQueueEntry(
                                    taskId = task.id,
                                    status = task.status,
                                    queuedAt = task.updatedAt,
                                    createdAt = task.createdAt,
                                )
                            }
                    }
                selectQueuedBrowserDownloadTaskIds(
                    entries = queueEntries,
                    activeTaskIds = activeControls.keys.toSet(),
                    availableSlots = availableSlots,
                ).map { taskId ->
                    val job =
                        scope.launch(start = CoroutineStart.LAZY) {
                            val task =
                                synchronized(tasks) {
                                    tasks[taskId]
                                        ?.takeIf { current -> current.status == BrowserDownloadStatus.QUEUED }
                                        ?.snapshot()
                                } ?: return@launch
                            when (task.type) {
                                BROWSER_DOWNLOAD_TYPE_HTTP -> runHttpTask(taskId)
                                else -> runInlineTask(taskId)
                            }
                        }
                    activeControls[taskId] = BrowserDownloadActiveControl(job = job)
                    job.invokeOnCompletion {
                        onWorkerCompleted(taskId, job)
                    }
                    job
                }
            }
        jobsToStart.forEach { job -> job.start() }
    }

    private fun isRuntimeNetworkAllowed(task: BrowserDownloadTaskRecord): Boolean =
        BrowserDownloadRuntimeCoordinator.isNetworkAllowed(
            context = appContext,
            policy = task.networkPolicy,
            allowRoaming = task.allowRoaming,
        )

    private fun onWorkerCompleted(taskId: String, completedJob: Job) {
        synchronized(schedulerLock) {
            if (activeControls[taskId]?.job === completedJob) {
                activeControls.remove(taskId)
            }
        }
        scheduleQueuedTasks()
    }

    private suspend fun runInlineTask(taskId: String) {
        val payload = inlinePayloads[taskId]?.bytes
        if (payload == null) {
            mutateTask(taskId, eventStatus = "failed") { task ->
                task.status = BrowserDownloadStatus.FAILED
                task.errorMessage = "Inline download payload is no longer available."
                task.speedBytesPerSecond = 0L
            }
            return
        }
        updateTaskStatus(taskId, BrowserDownloadStatus.CONNECTING)
        updateTaskStatus(taskId, BrowserDownloadStatus.DOWNLOADING)
        try {
            val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
            val segment = task.segments.firstOrNull()
                ?: throw IllegalStateException("Inline download segment is missing.")
            val partFile = File(segment.tempPath)
            partFile.parentFile?.mkdirs()
            FileOutputStream(partFile, false).use { output ->
                output.write(payload)
                output.flush()
            }
            currentCoroutineContext().ensureActive()
            mutateTask(taskId, forcePersist = false, forceUi = false) { current ->
                current.downloadedBytes = payload.size.toLong()
                current.totalBytes = payload.size.toLong()
            }
            mergeCompletedTask(taskId)
        } catch (cancelled: CancellationException) {
            handleTaskCancellation(taskId, cancelled)
        } catch (error: Throwable) {
            handleTaskError(taskId, error)
        }
    }

    private suspend fun runHttpTask(taskId: String) {
        updateTaskStatus(taskId, BrowserDownloadStatus.CONNECTING)
        val originalTask = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        try {
            createBrowserDownloadTransport(originalTask).use { transport ->
                val probe = transport.probe(originalTask.sourceUrl.orEmpty(), originalTask.headers)
                currentCoroutineContext().ensureActive()
                updateTaskProbeMimeType(taskId, probe.mimeType)
                if (
                    isBrowserDownloadM3u8Resource(
                        url = probe.finalUrl,
                        fileName = originalTask.fileName,
                        mimeType = probe.mimeType,
                    )
                ) {
                    runM3u8Task(
                        taskId = taskId,
                        task = originalTask,
                        probe = probe,
                        transport = transport,
                    )
                    return@use
                }
                configureTaskSegments(taskId, probe)
                initializeDownloadedBytes(taskId)
                updateTaskStatus(taskId, BrowserDownloadStatus.DOWNLOADING)

                val speedLock = Any()
                var bytesAtLastSample = currentDownloadedBytes(taskId)
                var sampledAt = System.currentTimeMillis()
                val onChunk: (Int) -> Unit = { chunkBytes ->
                    incrementDownloadedBytes(taskId, chunkBytes.toLong())
                    val now = System.currentTimeMillis()
                    synchronized(speedLock) {
                        if (now - sampledAt >= 500L) {
                            val downloadedNow = currentDownloadedBytes(taskId)
                            val delta = max(0L, downloadedNow - bytesAtLastSample)
                            val speed = (delta * 1000L) / max(1L, now - sampledAt)
                            updateTaskSpeed(taskId, speed)
                            bytesAtLastSample = downloadedNow
                            sampledAt = now
                        }
                    }
                }

                val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
                if (task.supportsResume && task.segments.size > 1) {
                    val limiter =
                        Semaphore(
                            resolveBrowserDownloadSegmentThreadCount(
                                totalBytes = task.totalBytes,
                                supportsRanges = true,
                                requestedThreadCount = task.threadCount,
                            ),
                        )
                    coroutineScope {
                        task.segments.map { segment ->
                            async(Dispatchers.IO) {
                                limiter.withPermit {
                                    downloadSegment(
                                        taskId = taskId,
                                        task = task,
                                        resolvedUrl = probe.finalUrl,
                                        segment = segment,
                                        transport = transport,
                                        onChunk = onChunk,
                                    )
                                }
                            }
                        }.awaitAll()
                    }
                } else {
                    downloadSegment(
                        taskId = taskId,
                        task = task,
                        resolvedUrl = probe.finalUrl,
                        segment = task.segments.first(),
                        transport = transport,
                        onChunk = onChunk,
                    )
                }

                currentCoroutineContext().ensureActive()
                updateTaskSpeed(taskId, 0L)
                mergeCompletedTask(taskId)
            }
        } catch (cancelled: CancellationException) {
            handleTaskCancellation(taskId, cancelled)
        } catch (error: Throwable) {
            handleTaskError(taskId, error)
        }
    }

    private suspend fun downloadSegment(
        taskId: String,
        task: BrowserDownloadTaskRecord,
        resolvedUrl: String,
        segment: BrowserDownloadSegmentRecord,
        transport: BrowserDownloadTransport,
        onChunk: (Int) -> Unit
    ) {
        val currentTask = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        val segmentFile = File(segment.tempPath)
        val expectedLength = segment.expectedLength()
        val existingBytes = segmentFile.takeIf { it.exists() }?.length() ?: 0L
        if (expectedLength > 0L && existingBytes > expectedLength) {
            throw IOException(
                "Stored browser download part exceeds its planned length: " +
                    "$existingBytes > $expectedLength",
            )
        }

        if (expectedLength > 0L && existingBytes >= expectedLength) {
            return
        }

        if (!currentTask.supportsResume && segmentFile.exists()) {
            segmentFile.delete()
        }

        val startInclusive =
            if (currentTask.supportsResume) {
                segment.startInclusive + existingBytes
            } else {
                segment.startInclusive
            }
        val append = currentTask.supportsResume && existingBytes > 0L
        val endInclusive = segment.endInclusive.takeIf { it >= 0L }
        val coroutineContext = currentCoroutineContext()
        val isCancelled = {
            activeControls[taskId]?.stopAction != null || !coroutineContext.isActive
        }

        if (endInclusive != null) {
            transport.downloadRangeWithRetry(
                url = resolvedUrl,
                headers = task.headers,
                startInclusive = startInclusive,
                endInclusive = endInclusive,
                destination = segmentFile,
                append = append,
                expectedTotalBytes = currentTask.totalBytes.takeIf { it > 0L },
                ifRangeValidator =
                    resolveBrowserDownloadIfRangeValidator(
                        etag = currentTask.resourceEtag,
                        lastModified = currentTask.resourceLastModified,
                    ),
                onChunk = onChunk,
                isCancelled = isCancelled,
            )
        } else {
            transport.downloadStreamWithRetry(
                url = resolvedUrl,
                headers = task.headers,
                destination = segmentFile,
                bufferSizeBytes = task.chunkSizeKb * 1024,
                append = append,
                expectedLength = currentTask.totalBytes.takeIf { it > 0L },
                onChunk = onChunk,
                isCancelled = isCancelled,
            )
        }
    }

    private suspend fun runM3u8Task(
        taskId: String,
        task: BrowserDownloadTaskRecord,
        probe: BrowserDownloadProbeResult,
        transport: BrowserDownloadTransport,
    ) {
        val packageOffline = task.packageM3u8Offline
        configureTaskForM3u8(taskId, packageOffline)
        initializeDownloadedBytes(taskId)
        updateTaskStatus(taskId, BrowserDownloadStatus.DOWNLOADING)
        val preparedTask = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        val playlistPart =
            preparedTask.segments.singleOrNull()?.let { segment -> File(segment.tempPath) }
                ?: throw IOException("M3U8 playlist staging file is missing")
        val packageDirectory = browserM3u8PackageDirectoryFor(File(preparedTask.destinationPath))
        val speedLock = Any()
        var bytesAtLastSample = currentDownloadedBytes(taskId)
        var sampledAt = System.currentTimeMillis()
        val coroutineContext = currentCoroutineContext()
        val onChunk: (Int) -> Unit = { chunkBytes ->
            incrementDownloadedBytes(taskId, chunkBytes.toLong())
            val now = System.currentTimeMillis()
            synchronized(speedLock) {
                if (now - sampledAt >= 500L) {
                    val downloadedNow = currentDownloadedBytes(taskId)
                    val delta = max(0L, downloadedNow - bytesAtLastSample)
                    val speed = (delta * 1000L) / max(1L, now - sampledAt)
                    updateTaskSpeed(taskId, speed)
                    bytesAtLastSample = downloadedNow
                    sampledAt = now
                }
            }
        }
        try {
            val result =
                downloadBrowserM3u8(
                    playlistUrl = probe.finalUrl,
                    headers = preparedTask.headers,
                    transport = transport,
                    packageOffline = packageOffline,
                    m3u8ThreadCount = preparedTask.m3u8ThreadCount,
                    outputPlaylistFile = playlistPart,
                    packageDirectory = packageDirectory,
                    onChunk = onChunk,
                    isCancelled = {
                        activeControls[taskId]?.stopAction != null || !coroutineContext.isActive
                    },
                )
            currentCoroutineContext().ensureActive()
            mutateTask(taskId, forcePersist = true, forceUi = true) { current ->
                current.totalBytes = result.storedBytes
                current.downloadedBytes = result.storedBytes
                current.isM3u8Package = result.isPackage
                if (result.mimeType.isNotBlank()) {
                    current.mimeType = result.mimeType
                }
            }
            updateTaskSpeed(taskId, 0L)
            mergeCompletedTask(taskId)
        } catch (error: Throwable) {
            clearM3u8RuntimeArtifacts(taskId)
            mutateTask(taskId, forcePersist = false, forceUi = false) { current ->
                current.totalBytes = 0L
                current.downloadedBytes = 0L
                current.speedBytesPerSecond = 0L
            }
            throw error
        }
    }

    private fun configureTaskForM3u8(taskId: String, packageOffline: Boolean) {
        val previous = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        val destination = File(previous.destinationPath)
        previous.segments.forEach { segment ->
            val segmentFile = File(segment.tempPath)
            if (segmentFile.exists() && !segmentFile.delete()) {
                throw IOException("Unable to remove previous M3U8 staging file: ${segmentFile.absolutePath}")
            }
        }
        deleteBrowserM3u8PackageDirectory(browserM3u8PackageDirectoryFor(destination))
        if (previous.status != BrowserDownloadStatus.COMPLETED) {
            if (destination.exists() && !destination.delete()) {
                throw IOException("Unable to remove previous M3U8 playlist: ${destination.absolutePath}")
            }
        }
        mutateTask(taskId, forcePersist = true, forceUi = true) { current ->
            current.totalBytes = 0L
            current.downloadedBytes = 0L
            current.supportsResume = false
            current.isM3u8Package = packageOffline
            if (packageOffline) {
                current.targetDirectoryUri = null
            }
            current.segments.clear()
            current.segments +=
                BrowserDownloadSegmentRecord(
                    index = 0,
                    startInclusive = 0L,
                    endInclusive = -1L,
                    tempPath = buildSinglePartPath(destination).absolutePath,
                )
        }
    }

    private fun clearM3u8RuntimeArtifacts(taskId: String) {
        val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        task.segments.forEach { segment ->
            runCatching { File(segment.tempPath).delete() }
        }
        runCatching {
            deleteBrowserM3u8PackageDirectory(
                browserM3u8PackageDirectoryFor(File(task.destinationPath)),
            )
        }.onFailure { cleanupError ->
            AppLogger.w(
                DOWNLOAD_SUPPORT_TAG,
                "Failed to clean incomplete browser M3U8 package: ${cleanupError.message}",
            )
        }
    }

    private fun createBrowserDownloadTransport(
        task: BrowserDownloadTaskRecord,
    ): BrowserDownloadTransport =
        BrowserDownloadTransport(
            BrowserDownloadTransportConfig(
                maxConcurrentTasks =
                    resolveBrowserDownloadTransportMaxConcurrentTasks(
                        requestedMaxConcurrentTasks = settingsStore.current.maxConcurrentTasks,
                        normalThreadCount = task.threadCount,
                        m3u8ThreadCount = task.m3u8ThreadCount,
                    ),
                normalThreadCount = task.threadCount,
                m3u8ThreadCount = task.m3u8ThreadCount,
                chunkSizeKb = task.chunkSizeKb,
                enableHttp2 = task.enableHttp2,
            ),
            network = BrowserDownloadRuntimeCoordinator.currentJobNetwork(),
        )

    private fun updateTaskProbeMimeType(taskId: String, mimeType: String) {
        if (mimeType.isBlank()) {
            return
        }
        mutateTask(taskId, forcePersist = true, forceUi = false) { task ->
            if (task.mimeType.isNullOrBlank()) {
                task.mimeType = mimeType
            }
        }
    }

    private fun configureTaskSegments(
        taskId: String,
        probe: BrowserDownloadProbeResult,
    ) {
        val ifRangeValidator =
            resolveBrowserDownloadIfRangeValidator(
                etag = probe.etag,
                lastModified = probe.lastModified,
            )
        val supportsResume =
            probe.acceptsRanges &&
                probe.contentLength > 0L &&
                ifRangeValidator != null
        val previous = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        val existingBytes = computeDownloadedBytes(previous)
        val previousIfRangeValidator =
            resolveBrowserDownloadIfRangeValidator(
                etag = previous.resourceEtag,
                lastModified = previous.resourceLastModified,
            )
        if (
            existingBytes > 0L &&
                (
                    !previous.supportsResume ||
                        !supportsResume ||
                        previous.totalBytes != probe.contentLength ||
                        previousIfRangeValidator != ifRangeValidator
                )
        ) {
            throw BrowserDownloadRepresentationChangedException()
        }
        mutateTask(taskId, forcePersist = true, forceUi = true) { task ->
            task.totalBytes = probe.contentLength
            task.supportsResume = supportsResume
            task.resourceEtag = probe.etag
            task.resourceLastModified = probe.lastModified
            task.errorMessage = null
            task.completedAt = null
            task.isM3u8Package = false
            val destination = File(task.destinationPath)
            if (!supportsResume || probe.contentLength <= 0L) {
                task.segments.clear()
                task.segments +=
                    BrowserDownloadSegmentRecord(
                        index = 0,
                        startInclusive = 0L,
                        endInclusive = -1L,
                        tempPath = buildSinglePartPath(destination).absolutePath
                    )
                return@mutateTask
            }

            if (
                task.segments.isNotEmpty() &&
                    isCompleteBrowserDownloadSegmentPlan(task.segments, probe.contentLength)
            ) {
                return@mutateTask
            }
            if (existingBytes > 0L) {
                throw BrowserDownloadRepresentationChangedException()
            }
            task.segments.clear()
            buildBrowserDownloadRangePlan(probe.contentLength, task.chunkSizeKb).forEach { plan ->
                task.segments +=
                    BrowserDownloadSegmentRecord(
                        index = plan.index,
                        startInclusive = plan.startInclusive,
                        endInclusive = plan.endInclusive,
                        tempPath = buildSegmentPartPath(destination, plan.index).absolutePath,
                    )
                }
        }
    }

    private fun initializeDownloadedBytes(taskId: String) {
        mutateTask(taskId, forcePersist = false, forceUi = false) { task ->
            task.downloadedBytes = computeDownloadedBytes(task)
        }
    }

    private fun currentDownloadedBytes(taskId: String): Long =
        synchronized(tasks) {
            tasks[taskId]?.downloadedBytes ?: 0L
        }

    private fun incrementDownloadedBytes(taskId: String, delta: Long) {
        mutateTask(taskId, forcePersist = false, forceUi = false) { task ->
            task.downloadedBytes += delta
        }
    }

    private fun updateTaskSpeed(taskId: String, speedBytesPerSecond: Long) {
        mutateTask(taskId, forcePersist = false, forceUi = true) { task ->
            task.speedBytesPerSecond = speedBytesPerSecond
        }
    }

    private fun updateTaskStatus(taskId: String, status: BrowserDownloadStatus) {
        mutateTask(taskId, forcePersist = true, forceUi = true, eventStatus = status.wireName) { task ->
            task.status = status
            task.errorMessage = null
            task.speedBytesPerSecond = 0L
        }
    }

    private fun mergeCompletedTask(taskId: String) {
        val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        val initialLocation =
            if (task.targetDirectoryUri.isNullOrBlank()) {
                mergeTaskSegmentsToDefaultFile(task)
            } else {
                mergeTaskSegmentsToDocumentTree(task, requireNotNull(task.targetDirectoryUri))
            }
        val savedLocation =
            if (
                shouldAutoTransferBrowserDownload(
                    autoTransferEnabled = task.autoTransferToPublicDirectory,
                    hasCustomDirectory = !task.targetDirectoryUri.isNullOrBlank(),
                    isM3u8Package = task.isM3u8Package,
                )
            ) {
                transferCompletedTaskToPublicDirectory(initialLocation)
            } else {
                initialLocation
            }
        inlinePayloads.remove(taskId)
        if (savedLocation.absolutePath.isNotBlank()) {
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(savedLocation.absolutePath),
                task.mimeType?.let { arrayOf(it) },
                null,
            )
        }
        mutateTask(taskId, eventStatus = "completed") { current ->
            current.destinationPath = savedLocation.absolutePath
            current.destinationUri = savedLocation.uri?.toString()
            current.fileName = savedLocation.displayName
            current.status = BrowserDownloadStatus.COMPLETED
            current.completedAt = System.currentTimeMillis()
            current.errorMessage = null
            current.speedBytesPerSecond = 0L
            current.downloadedBytes =
                if (current.totalBytes > 0L) {
                    current.totalBytes
                } else {
                    savedLocation.fileSizeBytes
                }
        }
    }

    private fun transferCompletedTaskToPublicDirectory(
        sourceLocation: BrowserDownloadSavedLocation,
    ): BrowserDownloadSavedLocation {
        require(sourceLocation.uri == null && sourceLocation.absolutePath.isNotBlank()) {
            "Automatic public transfer requires an application-directory file"
        }
        val sourceFile = File(sourceLocation.absolutePath)
        require(sourceFile.exists()) { "Completed browser download is missing" }
        val targetFile = reserveUniquePublicDestinationFile(sourceLocation.displayName)
        try {
            sourceFile.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
        } catch (error: Throwable) {
            // The public copy is not the task owner until the entire file is durable. Keeping a
            // partial target would expose a corrupt download that no task record can resume.
            targetFile.delete()
            throw error
        }
        if (!sourceFile.delete()) {
            targetFile.delete()
            throw IOException("Unable to remove application-directory download after public transfer")
        }
        return BrowserDownloadSavedLocation(
            uri = null,
            absolutePath = targetFile.absolutePath,
            displayName = targetFile.name,
            fileSizeBytes = targetFile.length(),
        )
    }

    private fun mergeTaskSegmentsToDefaultFile(
        task: BrowserDownloadTaskRecord,
    ): BrowserDownloadSavedLocation {
        val destinationFile = File(task.destinationPath)
        destinationFile.parentFile?.mkdirs()
        if (task.segments.size == 1) {
            val partFile = requireDownloadPartFile(task, task.segments.first())
            if (destinationFile.exists() && !destinationFile.delete()) {
                throw IOException("Unable to replace existing download file: ${destinationFile.name}")
            }
            if (!partFile.renameTo(destinationFile)) {
                partFile.copyTo(destinationFile, overwrite = true)
                if (!partFile.delete()) {
                    throw IOException("Unable to remove merged download part: ${partFile.name}")
                }
            }
        } else {
            FileOutputStream(destinationFile, false).use { output ->
                task.segments.sortedBy { it.index }.forEach { segment ->
                    val partFile = requireDownloadPartFile(task, segment)
                    partFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                output.flush()
            }
            deleteMergedTaskSegments(task)
        }
        return BrowserDownloadSavedLocation(
            uri = null,
            absolutePath = destinationFile.absolutePath,
            displayName = destinationFile.name,
            fileSizeBytes = destinationFile.length(),
        )
    }

    private fun mergeTaskSegmentsToDocumentTree(
        task: BrowserDownloadTaskRecord,
        treeUriString: String,
    ): BrowserDownloadSavedLocation {
        val directory =
            DocumentFile.fromTreeUri(appContext, treeUriString.toUri())
                ?: throw IOException("Custom download directory is unavailable")
        require(directory.exists() && directory.isDirectory) {
            "Custom download directory is unavailable"
        }
        val availableName = resolveAvailableDocumentName(directory, task.fileName)
        val targetDocument =
            directory.createFile(resolveTaskMimeType(task), availableName)
                ?: throw IOException("Unable to create a file in the custom download directory")
        try {
            appContext.contentResolver.openOutputStream(targetDocument.uri, "w")?.use { output ->
                task.segments.sortedBy { it.index }.forEach { segment ->
                    requireDownloadPartFile(task, segment).inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                output.flush()
            } ?: throw IOException("Unable to write to the custom download directory")
        } catch (error: Throwable) {
            runCatching { appContext.contentResolver.delete(targetDocument.uri, null, null) }
                .onFailure { cleanupError ->
                    AppLogger.w(
                        DOWNLOAD_SUPPORT_TAG,
                        "Failed to remove incomplete SAF download: ${cleanupError.message}",
                    )
                }
            throw error
        }
        deleteMergedTaskSegments(task)
        return BrowserDownloadSavedLocation(
            uri = targetDocument.uri,
            absolutePath = "",
            displayName = targetDocument.name ?: availableName,
            fileSizeBytes = targetDocument.length().takeIf { it > 0L } ?: task.downloadedBytes,
        )
    }

    private fun requireDownloadPartFile(
        task: BrowserDownloadTaskRecord,
        segment: BrowserDownloadSegmentRecord,
    ): File {
        val partFile = File(segment.tempPath)
        if (!partFile.exists()) {
            throw IllegalStateException(
                "Missing segment ${segment.index} for ${task.fileName}",
            )
        }
        val expectedLength = segment.expectedLength()
        if (expectedLength > 0L && partFile.length() != expectedLength) {
            throw IOException(
                "Segment ${segment.index} length does not match its plan: " +
                    "${partFile.length()} != $expectedLength",
            )
        }
        return partFile
    }

    private fun deleteMergedTaskSegments(task: BrowserDownloadTaskRecord) {
        task.segments.forEach { segment ->
            val partFile = File(segment.tempPath)
            if (partFile.exists() && !partFile.delete()) {
                throw IOException("Unable to remove merged download part: ${partFile.name}")
            }
        }
    }

    private fun logBrowserDownloadOperationFailure(
        operation: String,
        error: Throwable,
        taskId: String? = null,
        task: BrowserDownloadTaskRecord? = null,
        sensitiveValues: Collection<String> = emptyList(),
    ) {
        val resolvedTask =
            task
                ?: taskId?.let { id ->
                    synchronized(tasks) {
                        tasks[id]?.snapshot()
                    }
                }
        val safeMessage =
            browserDownloadSafeErrorMessage(
                error = error,
                sensitiveValues =
                    buildList {
                        addAll(sensitiveValues)
                        resolvedTask?.sourceUrl?.let(::add)
                        resolvedTask?.headers?.values?.let(::addAll)
                    },
            )
        AppLogger.w(
            DOWNLOAD_SUPPORT_TAG,
            "$operation (${error::class.java.simpleName}): $safeMessage",
        )
    }

    private fun failTask(taskId: String, error: Throwable) {
        val task = synchronized(tasks) { tasks[taskId]?.snapshot() }
        val safeMessage =
            browserDownloadSafeErrorMessage(
                error = error,
                sensitiveValues =
                    buildList {
                        task?.sourceUrl?.let(::add)
                        task?.headers?.values?.let(::addAll)
                    },
            )
        AppLogger.w(
            DOWNLOAD_SUPPORT_TAG,
            "Browser download failed (${error::class.java.simpleName}): $safeMessage",
        )
        mutateTask(taskId, eventStatus = "failed") { task ->
            task.status = BrowserDownloadStatus.FAILED
            task.errorMessage = safeMessage
            task.speedBytesPerSecond = 0L
        }
    }

    private suspend fun handleTaskError(taskId: String, error: Throwable) {
        if (error is CancellationException) {
            handleTaskCancellation(taskId, error)
            return
        }
        if (error is BrowserDownloadRepresentationChangedException) {
            failTaskAfterRepresentationChange(taskId)
            return
        }

        if (activeControls[taskId]?.stopAction != null && !currentCoroutineContext().isActive) {
            val cancellation = CancellationException(error.message ?: "Download cancelled")
            cancellation.initCause(error)
            handleTaskCancellation(taskId, cancellation)
            return
        }

        failTask(taskId, error)
    }

    private fun failTaskAfterRepresentationChange(taskId: String) {
        val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        clearTemporaryArtifacts(task)
        val message = "远程文件已更新，旧分片已清理，请重新下载"
        AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Browser download representation changed")
        mutateTask(taskId, eventStatus = "failed") { current ->
            current.status = BrowserDownloadStatus.FAILED
            current.totalBytes = -1L
            current.downloadedBytes = 0L
            current.speedBytesPerSecond = 0L
            current.supportsResume = false
            current.resourceEtag = null
            current.resourceLastModified = null
            current.errorMessage = message
            current.completedAt = null
            current.segments.clear()
            resetSegmentsForRestart(current)
        }
    }

    private fun handleTaskCancellation(taskId: String, error: CancellationException) {
        when (activeControls[taskId]?.stopAction) {
            BrowserDownloadWorkerStopAction.PAUSE ->
                mutateTask(taskId, eventStatus = "paused") { task ->
                    task.status = BrowserDownloadStatus.PAUSED
                    task.errorMessage = null
                    task.speedBytesPerSecond = 0L
                }

            BrowserDownloadWorkerStopAction.CANCEL ->
                mutateTask(taskId, eventStatus = "canceled") { task ->
                    task.status = BrowserDownloadStatus.CANCELED
                    task.errorMessage = null
                    task.speedBytesPerSecond = 0L
                }

            BrowserDownloadWorkerStopAction.RUNTIME_SUSPEND ->
                mutateTask(taskId, eventStatus = "queued") { task ->
                    task.status = BrowserDownloadStatus.QUEUED
                    task.errorMessage = null
                    task.speedBytesPerSecond = 0L
                    task.downloadedBytes = computeDownloadedBytes(task)
                }

            BrowserDownloadWorkerStopAction.DELETE_RECORD,
            BrowserDownloadWorkerStopAction.DELETE_WITH_FILE -> Unit
            null -> failTask(taskId, error)
        }
    }

    private fun deleteTaskArtifacts(taskId: String, deleteFile: Boolean) {
        val task = synchronized(tasks) { tasks[taskId]?.snapshot() } ?: return
        // A record owns every partial file. Keeping those files after the record is removed makes
        // them unreachable and can block the same destination name on the next download.
        clearTaskFiles(task, includeDestinationFile = deleteFile)
        synchronized(tasks) {
            tasks.remove(taskId)
        }
        inlinePayloads.remove(taskId)
        publishTaskSnapshots()
        persistState(force = true)
        releasePersistedDirectoryPermissionIfUnused(task.targetDirectoryUri)
        dispatchUiRefresh(force = true)
        scheduleQueuedTasks()
    }

    private fun clearTemporaryArtifacts(task: BrowserDownloadTaskRecord) {
        clearTaskFiles(task, includeDestinationFile = task.status != BrowserDownloadStatus.COMPLETED)
    }

    private fun clearTaskFiles(task: BrowserDownloadTaskRecord, includeDestinationFile: Boolean) {
        task.segments.forEach { segment ->
            runCatching { File(segment.tempPath).delete() }
        }
        if (includeDestinationFile) {
            runCatching {
                task.destinationUri?.takeIf { it.isNotBlank() }?.let { uriString ->
                    val uri = uriString.toUri()
                    if (uri.scheme.equals("content", ignoreCase = true)) {
                        appContext.contentResolver.delete(uri, null, null)
                    } else if (uri.scheme.equals("file", ignoreCase = true)) {
                        File(uri.path.orEmpty()).delete()
                    }
                } ?: File(task.destinationPath).delete()
            }.onFailure { error ->
                logBrowserDownloadOperationFailure(
                    operation = "Failed to delete browser download file",
                    error = error,
                    task = task,
                )
            }
        }
        if (task.isM3u8Package && (includeDestinationFile || task.status != BrowserDownloadStatus.COMPLETED)) {
            runCatching {
                deleteBrowserM3u8PackageDirectory(browserM3u8PackageDirectoryFor(File(task.destinationPath)))
            }.onFailure { error ->
                logBrowserDownloadOperationFailure(
                    operation = "Failed to delete browser M3U8 package directory",
                    error = error,
                    task = task,
                )
            }
        }
    }

    private fun computeDownloadedBytes(task: BrowserDownloadTaskRecord): Long =
        task.segments.sumOf { segment ->
            val file = File(segment.tempPath)
            if (!file.exists()) {
                0L
            } else {
                val expected = segment.expectedLength()
                if (expected > 0L) {
                    file.length().coerceAtMost(expected)
                } else {
                    file.length()
                }
            }
        }

    private fun resetSegmentsForRestart(task: BrowserDownloadTaskRecord) {
        val destination = File(task.destinationPath)
        if (task.type != BROWSER_DOWNLOAD_TYPE_HTTP) {
            task.segments.clear()
            task.segments +=
                BrowserDownloadSegmentRecord(
                    index = 0,
                    startInclusive = 0L,
                    endInclusive = task.totalBytes - 1L,
                    tempPath = buildSinglePartPath(destination).absolutePath
                )
            task.threadCount = 1
            return
        }
        if (!task.supportsResume) {
            task.segments.clear()
            task.segments +=
                BrowserDownloadSegmentRecord(
                    index = 0,
                    startInclusive = 0L,
                    endInclusive = -1L,
                    tempPath = buildSinglePartPath(destination).absolutePath
                )
        }
    }

    private fun mutateTask(
        taskId: String,
        forcePersist: Boolean = true,
        forceUi: Boolean = true,
        eventStatus: String? = null,
        block: (BrowserDownloadTaskRecord) -> Unit
    ): BrowserDownloadTaskRecord? {
        val snapshot =
            synchronized(tasks) {
                tasks[taskId]?.let { task ->
                    block(task)
                    task.updatedAt = System.currentTimeMillis()
                    task.snapshot()
                }
            } ?: return null
        if (forcePersist) {
            persistState(force = false)
        }
        notifyTaskChanged(
            task = snapshot,
            event = eventStatus?.let { buildTaskEvent(snapshot, it) },
            forceUi = forceUi
        )
        return snapshot
    }

    private fun notifyTaskChanged(
        task: BrowserDownloadTaskRecord,
        event: WebDownloadEvent?,
        forceUi: Boolean
    ) {
        publishTaskSnapshots()
        if (event != null) {
            lastEvent = event
            lastEventAt = System.currentTimeMillis()
            taskListener?.invoke(task.snapshot(), event)
            if (
                task.status == BrowserDownloadStatus.COMPLETED ||
                    task.status == BrowserDownloadStatus.FAILED
            ) {
                BrowserDownloadRuntimeCoordinator.notifyTerminalTask(
                    context = appContext,
                    task = task,
                )
            }
        }
        dispatchUiRefresh(
            force =
                forceUi ||
                    task.status == BrowserDownloadStatus.COMPLETED ||
                    task.status == BrowserDownloadStatus.FAILED
        )
        persistState(
            force =
                forceUi ||
                    task.status == BrowserDownloadStatus.COMPLETED ||
                    task.status == BrowserDownloadStatus.FAILED
        )
    }

    private fun publishTaskSnapshots() {
        _taskSnapshots.value =
            synchronized(tasks) {
                tasks.values
                    .map { task -> task.snapshot() }
                    .sortedWith(
                        compareByDescending<BrowserDownloadTaskRecord> { task -> task.updatedAt }
                            .thenByDescending { task -> task.createdAt },
                    )
            }
    }

    private fun dispatchUiRefresh(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUiDispatchAt < 250L) {
            return
        }
        lastUiDispatchAt = now
        uiRefreshListener?.invoke()
    }

    private fun buildTaskEvent(task: BrowserDownloadTaskRecord, status: String): WebDownloadEvent =
        WebDownloadEvent(
            status = status,
            type = task.type,
            fileName = task.fileName,
            url = task.sourceUrl,
            mimeType = task.mimeType,
            savedPath = browserDownloadSavedLocation(task),
            error = task.errorMessage
        )

    private fun persistState(force: Boolean) {
        synchronized(persistenceLock) {
            val now = System.currentTimeMillis()
            if (!force && now - lastPersistAt < 500L) {
                return
            }
            val payload =
                JSONArray().also { array ->
                    synchronized(tasks) {
                        tasks.values.forEach { task ->
                            array.put(task.toJson())
                        }
                    }
                }
            var output: FileOutputStream? = null
            try {
                val stream = stateFile.startWrite()
                output = stream
                stream.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                stream.flush()
                stateFile.finishWrite(stream)
                output = null
                lastPersistAt = now
            } catch (error: Throwable) {
                output?.let(stateFile::failWrite)
                logBrowserDownloadOperationFailure(
                    operation = "Failed to persist browser downloads",
                    error = error,
                )
            }
        }
    }

    private fun loadState() {
        try {
            val raw =
                stateFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    reader.readText()
                }
            if (raw.isBlank()) {
                return
            }
            val array = JSONArray(raw)
            synchronized(tasks) {
                tasks.clear()
                for (index in 0 until array.length()) {
                    val task = BrowserDownloadTaskRecord.fromJson(array.getJSONObject(index))
                    tasks[task.id] = task
                }
            }
        } catch (_: FileNotFoundException) {
            return
        } catch (error: Throwable) {
            logBrowserDownloadOperationFailure(
                operation = "Failed to load browser downloads",
                error = error,
            )
        }
    }

    private fun normalizeRestoredTasks(resumeInterruptedTasks: Boolean) {
        val now = System.currentTimeMillis()
        var changed = false
        synchronized(tasks) {
            tasks.values.forEach { task ->
                if (
                    task.type != BROWSER_DOWNLOAD_TYPE_HTTP &&
                        task.status != BrowserDownloadStatus.COMPLETED
                ) {
                    task.status = BrowserDownloadStatus.FAILED
                    task.errorMessage = "Inline download could not be resumed after app restart."
                    task.updatedAt = now
                    task.speedBytesPerSecond = 0L
                    changed = true
                    return@forEach
                }
                if (task.activeOrPending()) {
                    task.status =
                        if (resumeInterruptedTasks) {
                            BrowserDownloadStatus.QUEUED
                        } else {
                            BrowserDownloadStatus.PAUSED
                        }
                    task.errorMessage = null
                    task.speedBytesPerSecond = 0L
                    task.updatedAt = now
                    task.downloadedBytes = computeDownloadedBytes(task)
                    changed = true
                }
            }
        }
        if (changed) {
            persistState(force = true)
        }
    }
}

internal data class BrowserDownloadSummary(
    val activeCount: Int = 0,
    val failedCount: Int = 0,
    val overallProgress: Float? = null,
    val latestCompletedFileName: String? = null
)

internal data class PendingBrowserDownloadRequest(
    val requestId: String,
    val sessionId: String,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val contentLength: Long,
    val headers: Map<String, String>,
    val engine: BrowserDownloadEngine,
    val destination: BrowserDownloadDestination = BrowserDownloadDestination.FollowSettings,
    val createdAt: Long = System.currentTimeMillis(),
)

internal class BrowserDownloadConfirmationQueue(
    private val capacity: Int = MAX_PENDING_BROWSER_DOWNLOAD_REQUESTS,
) {
    init {
        require(capacity > 0) { "Browser download confirmation queue capacity must be positive" }
    }

    private val lock = Any()
    private val requests = ArrayDeque<PendingBrowserDownloadRequest>()

    fun enqueue(request: PendingBrowserDownloadRequest): Boolean =
        synchronized(lock) {
            if (requests.size >= capacity) {
                false
            } else {
                requests.addLast(request)
                true
            }
        }

    fun peek(): PendingBrowserDownloadRequest? =
        synchronized(lock) {
            requests.peekFirst()
        }

    fun removeHead(requestId: String): PendingBrowserDownloadRequest? =
        synchronized(lock) {
            requests.peekFirst()
                ?.takeIf { request -> request.requestId == requestId }
                ?.also { requests.removeFirst() }
        }

    fun size(): Int = synchronized(lock) { requests.size }
}

internal fun browserDownloadSafeErrorMessage(
    error: Throwable,
    sensitiveValues: Collection<String> = emptyList(),
): String {
    var message =
        error.message
            ?.trim()
            ?.takeIf { value -> value.isNotBlank() }
            ?: error::class.java.simpleName
    sensitiveValues
        .asSequence()
        .map { value -> value.trim() }
        .filter { value -> value.length >= 4 }
        .distinct()
        .sortedByDescending { value -> value.length }
        .forEach { value ->
            message = message.replace(value, "[已隐藏]")
        }
    message = BROWSER_DOWNLOAD_ERROR_URL_PATTERN.replace(message, "[下载地址已隐藏]")
    return message.take(500)
}

private val BROWSER_DOWNLOAD_ERROR_URL_PATTERN =
    Regex("https?://\\S+", RegexOption.IGNORE_CASE)

internal fun browserDownloadDisplayUrl(url: String): String {
    if (!isBrowserDownloadNetworkUrl(url)) {
        return "[下载地址已隐藏]"
    }
    return runCatching {
        val parsed = URI(url)
        require(!parsed.host.isNullOrBlank()) { "Download URL host is blank" }
        URI(
            parsed.scheme,
            null,
            parsed.host,
            parsed.port,
            parsed.path,
            null,
            null,
        ).toString()
    }.getOrDefault("[下载地址已隐藏]")
}

internal fun isBrowserDownloadNetworkUrl(url: String): Boolean {
    val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.ROOT)
    return scheme == "http" || scheme == "https"
}

internal fun StandardBrowserSessionTools.browserDownloadManager(): BrowserDownloadManager =
    BrowserDownloadManager.getInstance(context.applicationContext)

internal fun StandardBrowserSessionTools.initializeBrowserDownloadSupport() {
    browserDownloadManager().setTaskListener { task, event ->
        task.sessionId?.let { sessionId ->
            sessionById(sessionId)?.let { session ->
                session.lastDownloadEvent = event
                session.lastDownloadEventAt = System.currentTimeMillis()
            }
        }
    }
    browserDownloadManager().setUiRefreshListener {
        StandardBrowserSessionTools.mainHandler.post {
            refreshSessionUiOnMain()
        }
    }
}

internal fun browserDownloadContentDeleteSucceeded(deletedRows: Int): Boolean = deletedRows > 0

internal fun StandardBrowserSessionTools.startBrowserManagedDownload(
    session: StandardBrowserSessionTools.WebSession,
    url: String,
    userAgent: String,
    contentDisposition: String?,
    mimeType: String?,
    contentLength: Long,
) {
    require(isBrowserDownloadNetworkUrl(url)) {
        "Browser downloads require an http or https URL: $url"
    }
    val fileName = sanitizeFileName(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType))
    val headers = linkedMapOf<String, String>()
    if (userAgent.isNotBlank()) {
        headers["User-Agent"] = userAgent
    }
    profileManager.cookieManagerFor(session.webView, session.profile)
        .getCookie(url)
        ?.takeIf { it.isNotBlank() }
        ?.let { cookie -> headers["Cookie"] = cookie }
    session.currentUrl.takeIf { it.isNotBlank() }?.let {
        headers["Referer"] = it
    }
    val settings = BrowserDownloadSettingsStore.getInstance(context).current
    val request =
        PendingBrowserDownloadRequest(
            requestId = UUID.randomUUID().toString(),
            sessionId = session.id,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            contentLength = contentLength,
            headers = headers.toMap(),
            engine = settings.defaultEngine,
        )
    if (settings.skipConfirmation) {
        dispatchBrowserDownloadRequest(request)
        return
    }
    runOnMainSync<Unit> {
        enqueueBrowserDownloadConfirmationOnMain(request)
    }
}

internal fun StandardBrowserSessionTools.startMediaCandidateDownload(
    session: StandardBrowserSessionTools.WebSession,
    candidate: BrowserMediaCandidate,
    destination: BrowserDownloadDestination = BrowserDownloadDestination.FollowSettings,
): Boolean {
    require(candidate.downloadReady && isBrowserDownloadNetworkUrl(candidate.url)) {
        "Media candidate is not an http or https download: ${candidate.url}"
    }
    val mimeType = candidate.displayMimeType
    val fileName = sanitizeFileName(android.webkit.URLUtil.guessFileName(candidate.url, null, mimeType))
    val settings = BrowserDownloadSettingsStore.getInstance(context).current
    val request =
        PendingBrowserDownloadRequest(
            requestId = UUID.randomUUID().toString(),
            sessionId = session.id,
            url = candidate.url,
            fileName = fileName,
            mimeType = mimeType,
            contentLength = -1L,
            // Candidate headers are the observed request identity. Rebuilding them here would
            // lose Origin/Range/Accept or widen the captured Cookie scope.
            headers = candidate.requestHeaders,
            engine =
                when (destination) {
                    BrowserDownloadDestination.FollowSettings -> settings.defaultEngine
                    is BrowserDownloadDestination.DocumentTree -> BrowserDownloadEngine.INTERNAL
                },
            destination = destination,
        )
    if (settings.skipConfirmation) {
        return dispatchBrowserDownloadRequest(request)
    }
    return runOnMainSync {
        enqueueBrowserDownloadConfirmationOnMain(request)
    }
}

internal fun StandardBrowserSessionTools.startManualBrowserDownload(
    url: String,
    requestedFileName: String,
    requestedSuffix: String,
    engine: BrowserDownloadEngine,
): Boolean {
    require(isBrowserDownloadNetworkUrl(url)) {
        "Manual browser downloads require an http or https URL: $url"
    }
    val session = getActiveSessionOnMain()
    val fileName =
        sanitizeFileName(
            resolveManualBrowserDownloadFileName(
                requestedFileName = requestedFileName,
                url = url,
                requestedSuffix = requestedSuffix,
            ),
        )
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val mimeType =
        extension.takeIf { value -> value.isNotBlank() }
            ?.let { value -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(value) }
    val headers = linkedMapOf<String, String>()
    session?.let { activeSession ->
        activeSession.webView.settings.userAgentString
            ?.takeIf { value -> value.isNotBlank() }
            ?.let { value -> headers["User-Agent"] = value }
        profileManager.cookieManagerFor(activeSession.webView, activeSession.profile)
            .getCookie(url)
            ?.takeIf { value -> value.isNotBlank() }
            ?.let { value -> headers["Cookie"] = value }
        activeSession.currentUrl.takeIf { value -> value.isNotBlank() }
            ?.let { value -> headers["Referer"] = value }
    }
    // The Minus-One drawer is a first-class manual entry and may exist before any WebSession.
    // Its stable request owner records that origin without creating a hidden tab or WebView.
    val requestSessionId = session?.id ?: BROWSER_STANDALONE_MANUAL_DOWNLOAD_SESSION_ID
    // The add-download dialog is the explicit user confirmation for this request. Dispatching
    // here prevents a second confirmation surface while preserving the selected engine owner.
    return dispatchBrowserDownloadRequest(
        PendingBrowserDownloadRequest(
            requestId = UUID.randomUUID().toString(),
            sessionId = requestSessionId,
            url = url.trim(),
            fileName = fileName,
            mimeType = mimeType,
            contentLength = -1L,
            headers = headers.toMap(),
            engine = engine,
        ),
    )
}

private const val BROWSER_STANDALONE_MANUAL_DOWNLOAD_SESSION_ID =
    "kiyori-standalone-manual-download"

internal fun StandardBrowserSessionTools.confirmBrowserDownloadRequest(requestId: String) {
    val request =
        StandardBrowserSessionTools.browserDownloadConfirmationQueue.removeHead(requestId)
            ?: return
    refreshSessionUiOnMain()
    dispatchBrowserDownloadRequest(request)
}

internal fun StandardBrowserSessionTools.cancelBrowserDownloadRequest(requestId: String) {
    StandardBrowserSessionTools.browserDownloadConfirmationQueue.removeHead(requestId)
        ?: return
    refreshSessionUiOnMain()
}

private fun StandardBrowserSessionTools.enqueueBrowserDownloadConfirmationOnMain(
    request: PendingBrowserDownloadRequest,
): Boolean {
    val accepted = StandardBrowserSessionTools.browserDownloadConfirmationQueue.enqueue(request)
    if (!accepted) {
        showToast(
            context.getString(
                com.ai.assistance.operit.R.string.web_session_download_confirmation_queue_full,
            ),
        )
        return false
    }
    refreshSessionUiOnMain()
    return true
}

private fun StandardBrowserSessionTools.dispatchBrowserDownloadRequest(
    request: PendingBrowserDownloadRequest,
): Boolean {
    val result = runCatching {
        when (request.engine) {
            BrowserDownloadEngine.INTERNAL -> {
                browserDownloadManager().startHttpDownload(
                    sessionId = request.sessionId,
                    url = request.url,
                    suggestedFileName = request.fileName,
                    mimeType = request.mimeType,
                    headers = request.headers,
                    destinationPolicy = request.destination,
                )
                showToast(context.getString(com.ai.assistance.operit.R.string.download_started, request.fileName))
            }
            BrowserDownloadEngine.SYSTEM -> {
                // Android DownloadManager owns this task end to end. Mirroring it into the Kiyori
                // JSON map would create a second, unsynchronized progress and lifecycle owner.
                enqueueSystemBrowserDownload(request)
                showToast(
                    context.getString(
                        com.ai.assistance.operit.R.string.web_session_system_download_enqueued,
                        request.fileName,
                    ),
                )
            }
        }
    }
    return result.fold(
        onSuccess = { true },
        onFailure = { error ->
            val safeMessage =
                browserDownloadSafeErrorMessage(
                    error = error,
                    sensitiveValues =
                        buildList {
                            add(request.url)
                            addAll(request.headers.values)
                        },
                )
            AppLogger.e(
                DOWNLOAD_SUPPORT_TAG,
                "Unable to dispatch ${request.engine.persistedId} browser download " +
                    "(${error::class.java.simpleName}): $safeMessage",
            )
            showToast(
                context.getString(
                    com.ai.assistance.operit.R.string.download_failed,
                    safeMessage,
                ),
            )
            false
        },
    )
}

private fun StandardBrowserSessionTools.enqueueSystemBrowserDownload(
    request: PendingBrowserDownloadRequest,
): Long {
    require(request.destination == BrowserDownloadDestination.FollowSettings) {
        "Android system downloads cannot target a SAF document tree"
    }
    require(isBrowserDownloadNetworkUrl(request.url)) {
        "System downloads require an http or https URL"
    }
    val settings = BrowserDownloadSettingsStore.getInstance(context).current
    val systemRequest =
        DownloadManager.Request(request.url.toUri()).apply {
            request.mimeType?.takeIf { value -> value.isNotBlank() }?.let(::setMimeType)
            setAllowedOverMetered(
                settings.networkPolicy == BrowserDownloadNetworkPolicy.ANY,
            )
            setAllowedOverRoaming(settings.allowRoaming)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setTitle(request.fileName)
            setDescription(context.getString(com.ai.assistance.operit.R.string.web_session_system_download_description))
            request.headers.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) {
                    addRequestHeader(name, value)
                }
            }
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "Kiyori/browser/downloads/${request.fileName}",
            )
        }
    val systemManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    return systemManager.enqueue(systemRequest)
}

internal fun PendingBrowserDownloadRequest.toUiState(): BrowserDownloadPromptState =
    BrowserDownloadPromptState(
        requestId = requestId,
        fileName = fileName,
        mimeType = mimeType?.takeIf { value -> value.isNotBlank() },
        contentLength = contentLength,
        engine = engine,
        destinationName =
            when (val currentDestination = destination) {
                BrowserDownloadDestination.FollowSettings -> null
                is BrowserDownloadDestination.DocumentTree -> currentDestination.displayName
            },
    )

internal fun StandardBrowserSessionTools.startInlineManagedDownload(
    session: StandardBrowserSessionTools.WebSession,
    type: String,
    base64Data: String,
    fileName: String,
    mimeType: String,
    sourceUrl: String? = null
) {
    val normalizedMimeType = mimeType.ifBlank { guessMimeTypeFromDataUrl(base64Data) }
    val resolvedFileName = resolveInlineDownloadFileName(fileName, normalizedMimeType)
    val bytes = decodeInlineDownloadBytes(base64Data)
    browserDownloadManager().startInlineDownload(
        sessionId = session.id,
        type = type,
        suggestedFileName = resolvedFileName,
        mimeType = normalizedMimeType,
        bytes = bytes,
        sourceUrl = sourceUrl
    )
    showToast(context.getString(com.ai.assistance.operit.R.string.download_started, resolvedFileName))
}

/**
 * Workspace WebView 不是第二下载系统。它只把用户动作投递给现有 Browser 下载 owner。
 */
internal fun enqueueWorkspaceWebViewDownload(
    context: Context,
    url: String,
    fileName: String,
    mimeType: String?,
    contentLength: Long,
    headers: Map<String, String>,
): Boolean {
    require(isBrowserDownloadNetworkUrl(url)) {
        "Workspace WebView downloads require an http or https URL"
    }
    val tools = ToolGetter.getBrowserSessionTools(context)
    val settings = BrowserDownloadSettingsStore.getInstance(context).current
    return tools.dispatchBrowserDownloadRequest(
        PendingBrowserDownloadRequest(
            requestId = UUID.randomUUID().toString(),
            sessionId = BROWSER_WORKSPACE_WEBVIEW_DOWNLOAD_SESSION_ID,
            url = url,
            fileName = tools.sanitizeFileName(fileName),
            mimeType = mimeType,
            contentLength = contentLength,
            headers = headers.filterKeys(String::isNotBlank),
            engine = settings.defaultEngine,
        ),
    )
}

internal fun enqueueWorkspaceWebViewInlineDownload(
    context: Context,
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
) {
    require(bytes.isNotEmpty()) { "Workspace WebView inline download is empty" }
    val tools = ToolGetter.getBrowserSessionTools(context)
    val resolvedFileName = tools.resolveInlineDownloadFileName(fileName, mimeType)
    tools.browserDownloadManager().startInlineDownload(
        sessionId = BROWSER_WORKSPACE_WEBVIEW_DOWNLOAD_SESSION_ID,
        type = "workspace_blob",
        suggestedFileName = resolvedFileName,
        mimeType = mimeType,
        bytes = bytes,
        sourceUrl = null,
    )
    tools.showToast(
        context.getString(
            com.ai.assistance.operit.R.string.download_started,
            resolvedFileName,
        ),
    )
}

private const val BROWSER_WORKSPACE_WEBVIEW_DOWNLOAD_SESSION_ID =
    "kiyori-workspace-webview-download"

internal fun StandardBrowserSessionTools.buildBrowserDownloadSummary(): BrowserDownloadSummary {
    val tasks = browserDownloadManager().snapshotTasks()
    val active = tasks.filter { it.activeOrPending() }
    val failed = tasks.count { it.status == BrowserDownloadStatus.FAILED }
    val latestCompleted =
        tasks.filter { it.status == BrowserDownloadStatus.COMPLETED && it.completedAt != null }
            .maxByOrNull { it.completedAt ?: 0L }
            ?.fileName
    val progress =
        if (active.isEmpty()) {
            null
        } else {
            val progressTasks = active.filter { it.totalBytes > 0L }
            if (progressTasks.isEmpty()) {
                null
            } else {
                val downloaded = progressTasks.sumOf { it.downloadedBytes.toDouble() }
                val total = progressTasks.sumOf { it.totalBytes.toDouble() }
                if (total > 0.0) {
                    (downloaded / total).toFloat()
                } else {
                    null
                }
            }
        }
    return BrowserDownloadSummary(
        activeCount = active.size,
        failedCount = failed,
        overallProgress = progress,
        latestCompletedFileName = latestCompleted
    )
}

internal fun buildBrowserDownloadUiState(
    tasks: List<BrowserDownloadTaskRecord>,
): BrowserDownloadUiState {
    val items =
        tasks.map { task ->
            BrowserDownloadItem(
                id = task.id,
                fileName = task.fileName,
                sourceUrl = task.sourceUrl,
                mimeType = task.mimeType,
                status = task.status.wireName,
                type = task.type,
                progress = task.progressOrNull(),
                downloadedBytes = task.downloadedBytes,
                totalBytes = task.totalBytes,
                speedBytesPerSecond = task.speedBytesPerSecond,
                destinationPath = browserDownloadSavedLocation(task),
                createdAt = task.createdAt,
                completedAt = task.completedAt,
                isM3u8Package = task.isM3u8Package,
                errorMessage = task.errorMessage,
                canPause =
                    task.status == BrowserDownloadStatus.QUEUED ||
                        task.status == BrowserDownloadStatus.CONNECTING ||
                        task.status == BrowserDownloadStatus.DOWNLOADING,
                canResume = task.supportsResumeAction(),
                canCancel =
                    task.status == BrowserDownloadStatus.QUEUED ||
                        task.status == BrowserDownloadStatus.CONNECTING ||
                        task.status == BrowserDownloadStatus.DOWNLOADING ||
                        task.status == BrowserDownloadStatus.PAUSED,
                canRetry = task.supportsRetry(),
                canDelete = true,
                canDeleteFile = browserDownloadSavedLocation(task).isNotBlank(),
                canOpenFile = task.status == BrowserDownloadStatus.COMPLETED,
                canOpenLocation = task.status == BrowserDownloadStatus.COMPLETED,
                canRedownload =
                    task.status == BrowserDownloadStatus.COMPLETED &&
                        task.sourceUrl?.let(::isBrowserDownloadNetworkUrl) == true,
                canMergeToMp4 =
                    task.status == BrowserDownloadStatus.COMPLETED &&
                        task.isM3u8Package &&
                        task.destinationUri.isNullOrBlank() &&
                        task.destinationPath.isNotBlank(),
            )
        }
    return BrowserDownloadUiState(tasks = items)
}

internal fun StandardBrowserSessionTools.buildBrowserDownloadUiState(): BrowserDownloadUiState =
    buildBrowserDownloadUiState(browserDownloadManager().snapshotTasks())

internal fun StandardBrowserSessionTools.renderManagedDownloads(
    session: StandardBrowserSessionTools.WebSession,
    marker: Long,
    includeAll: Boolean = false
): String? = browserDownloadManager().renderDownloads(marker, includeAll)

internal fun StandardBrowserSessionTools.latestBrowserDownloadEventAt(): Long =
    browserDownloadManager().latestEventAt()

internal fun StandardBrowserSessionTools.latestBrowserDownloadEventAfter(
    marker: Long
): WebDownloadEvent? = browserDownloadManager().latestEventAfter(marker)

internal fun StandardBrowserSessionTools.performBrowserDownloadAction(
    taskId: String,
    action: BrowserDownloadAction
) {
    browserDownloadManager().performAction(taskId, action)
}

internal fun StandardBrowserSessionTools.performBrowserDownloadDelete(
    taskId: String,
    deleteFile: Boolean
) {
    browserDownloadManager().performAction(
        taskId,
        if (deleteFile) BrowserDownloadAction.DELETE_WITH_FILE else BrowserDownloadAction.DELETE_RECORD
    )
}

internal fun StandardBrowserSessionTools.openDownloadedFile(taskId: String): Boolean {
    return browserDownloadManager().openDownloadedFile(taskId)
}

internal fun StandardBrowserSessionTools.openDownloadLocation(taskId: String? = null): Boolean {
    return browserDownloadManager().openDownloadLocation(taskId)
}

internal fun StandardBrowserSessionTools.launchBrowserExternalIntent(intent: Intent): Boolean {
    return launchBrowserExternalIntent(context, intent)
}

private fun launchBrowserExternalIntent(context: Context, intent: Intent): Boolean {
    val currentActivity = ActivityLifecycleManager.getCurrentActivity()
    return try {
        if (currentActivity != null && !currentActivity.isFinishing && !currentActivity.isDestroyed) {
            currentActivity.startActivity(Intent(intent))
        } else {
            context.applicationContext.startActivity(
                Intent(intent).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
        true
    } catch (e: ActivityNotFoundException) {
        AppLogger.w(DOWNLOAD_SUPPORT_TAG, "No activity found for browser intent: ${e.message}")
        false
    } catch (e: Exception) {
        AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Failed to launch browser intent: ${e.message}")
        false
    }
}

private fun reserveUniquePublicDestinationFile(suggestedFileName: String): File {
    val directory = KiyoriPaths.browserDownloadsDir()
    require(directory.isDirectory || directory.mkdirs()) {
        "Unable to create public browser download directory: ${directory.absolutePath}"
    }
    while (true) {
        val candidate =
            resolveUniqueBrowserDownloadFile(
                directory = directory,
                suggestedFileName = suggestedFileName,
            )
        if (candidate.createNewFile()) {
            return candidate
        }
    }
}

internal fun browserDownloadApplicationDirectory(context: Context): File {
    val directory = KiyoriPaths.browserApplicationDownloadsDir(context)
    require(directory.isDirectory || directory.mkdirs()) {
        "Unable to create application download directory: ${directory.absolutePath}"
    }
    return directory
}

internal fun browserDownloadPublicDirectory(): File = KiyoriPaths.browserDownloadsDir()

private fun resolveUniqueBrowserDownloadFile(
    directory: File,
    suggestedFileName: String,
    reservedPaths: Set<String> = emptySet(),
): File {
    require(directory.isDirectory || directory.mkdirs()) {
        "Unable to create browser download directory: ${directory.absolutePath}"
    }
    val availableName =
        resolveAvailableBrowserDownloadFileName(suggestedFileName) { candidateName ->
            val candidate = File(directory, candidateName)
            candidate.absolutePath in reservedPaths ||
                candidate.exists() ||
                directory.list()?.any { entryName ->
                    entryName == "$candidateName.part" ||
                        entryName.startsWith("$candidateName.part.")
                } == true
        }
    return File(directory, availableName)
}

private data class BrowserDownloadSavedLocation(
    val uri: Uri?,
    val absolutePath: String,
    val displayName: String,
    val fileSizeBytes: Long,
)

private fun browserDownloadSavedLocation(task: BrowserDownloadTaskRecord): String =
    resolveBrowserDownloadSavedLocation(task.destinationPath, task.destinationUri)

internal fun resolveBrowserDownloadSavedLocation(
    destinationPath: String,
    destinationUri: String?,
): String = destinationUri?.takeIf { it.isNotBlank() } ?: destinationPath

internal fun normalizeBrowserDownloadFileName(fileName: String): String {
    val sanitized =
        fileName
            .replace(Regex("[\\x00-\\x1F\\x7F\\\\/:*?\"<>|]"), "_")
            .trim()
            .trimEnd(' ', '.')
            .ifBlank { "download" }
    if (sanitized.length <= MAX_BROWSER_DOWNLOAD_FILE_NAME_LENGTH) {
        return sanitized
    }
    val extension =
        sanitized.substringAfterLast('.', "")
            .takeIf { value -> value.isNotBlank() }
            ?.let { value -> ".$value" }
            .orEmpty()
    val base = sanitized.removeSuffix(extension)
    val maximumBaseLength =
        (MAX_BROWSER_DOWNLOAD_FILE_NAME_LENGTH - extension.length).coerceAtLeast(1)
    return base.take(maximumBaseLength).trimEnd(' ', '.') + extension
}

internal fun resolveAvailableBrowserDownloadFileName(
    suggestedFileName: String,
    isUnavailable: (String) -> Boolean,
): String {
    val normalized = normalizeBrowserDownloadFileName(suggestedFileName)
    val dotIndex = normalized.lastIndexOf('.')
    val base = if (dotIndex > 0) normalized.substring(0, dotIndex) else normalized
    val extension = if (dotIndex > 0) normalized.substring(dotIndex) else ""
    var index = 0
    while (true) {
        val suffix = if (index == 0) "" else " ($index)"
        val maximumBaseLength =
            (MAX_BROWSER_DOWNLOAD_FILE_NAME_LENGTH - extension.length - suffix.length)
                .coerceAtLeast(1)
        val candidate =
            base.take(maximumBaseLength).trimEnd(' ', '.') + suffix + extension
        if (!isUnavailable(candidate)) {
            return candidate
        }
        index += 1
    }
}

private fun resolveAvailableDocumentName(directory: DocumentFile, displayName: String): String {
    val baseName = displayName.substringBeforeLast('.', displayName)
    val extension = displayName.substringAfterLast('.', "")
    var index = 0
    while (true) {
        val candidate =
            when {
                index == 0 -> displayName
                extension.isBlank() -> "$baseName ($index)"
                else -> "$baseName ($index).$extension"
            }
        if (directory.findFile(candidate) == null) {
            return candidate
        }
        index += 1
    }
}

private fun buildSinglePartPath(destination: File): File =
    File(destination.parentFile, "${destination.name}.part")

private fun buildSegmentPartPath(destination: File, index: Int): File =
    File(destination.parentFile, "${destination.name}.part.$index")

private fun decodeInlineDownloadBytes(rawData: String): ByteArray {
    if (rawData.startsWith("data:", ignoreCase = true)) {
        val metadata = rawData.substringBefore(',', "")
        val payload = rawData.substringAfter(',', "")
        return if (metadata.contains(";base64", ignoreCase = true)) {
            android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
        } else {
            android.net.Uri.decode(payload).toByteArray(Charsets.UTF_8)
        }
    }
    return android.util.Base64.decode(rawData.substringAfter(',', rawData), android.util.Base64.DEFAULT)
}

private fun BrowserDownloadTaskRecord.progressOrNull(): Float? =
    if (totalBytes > 0L) {
        (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        null
    }

private fun formatTaskProgress(task: BrowserDownloadTaskRecord): String {
    val progressText =
        task.progressOrNull()?.let {
            "${(it * 100f).toInt()}%"
        } ?: "Unknown"
    val sizeText =
        if (task.totalBytes > 0L) {
            "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
        } else {
            formatBytes(task.downloadedBytes)
        }
    return "$progressText ($sizeText)"
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) {
        return "$bytes B"
    }
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex + 1 < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return String.format(Locale.US, "%.1f %s", value, units[max(0, unitIndex)])
}
