package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
import kotlinx.coroutines.delay
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
private const val DOWNLOAD_TYPE_HTTP = "http"
internal const val MIN_BROWSER_DOWNLOAD_SEGMENT_BYTES = 1024L * 1024L
internal const val BROWSER_DOWNLOAD_APK_AUTO_CLEAN_DELAY_MILLIS = 90_000L

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
    var threadCount: Int,
    val m3u8ThreadCount: Int = DEFAULT_BROWSER_DOWNLOAD_M3U8_THREAD_COUNT,
    val autoMergeM3u8: Boolean = false,
    val autoTransferToPublicDirectory: Boolean = false,
    val chunkSizeKb: Int = DEFAULT_BROWSER_DOWNLOAD_CHUNK_SIZE_KB,
    val enableHttp2: Boolean = true,
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
            .put("thread_count", threadCount)
            .put("m3u8_thread_count", m3u8ThreadCount)
            .put("auto_merge_m3u8", autoMergeM3u8)
            .put("auto_transfer_to_public_directory", autoTransferToPublicDirectory)
            .put("chunk_size_kb", chunkSizeKb)
            .put("enable_http2", enableHttp2)
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
                type = json.optString("type", DOWNLOAD_TYPE_HTTP),
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
                autoMergeM3u8 = json.optBoolean("auto_merge_m3u8", false),
                autoTransferToPublicDirectory =
                    json.optBoolean("auto_transfer_to_public_directory", false),
                chunkSizeKb =
                    json.optInt(
                        "chunk_size_kb",
                        DEFAULT_BROWSER_DOWNLOAD_CHUNK_SIZE_KB,
                    ),
                enableHttp2 = json.optBoolean("enable_http2", true),
                isM3u8Package = json.optBoolean("is_m3u8_package", false),
                errorMessage = json.optString("error_message").ifBlank { null },
                completedAt = json.optLong("completed_at").takeIf { it > 0L },
                segments = segments
            )
        }
    }
}

private data class BrowserDownloadActiveControl(
    val job: Job,
    @Volatile var stopAction: BrowserDownloadAction? = null
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
    private val tasks = LinkedHashMap<String, BrowserDownloadTaskRecord>()
    private val activeControls = ConcurrentHashMap<String, BrowserDownloadActiveControl>()
    private val inlinePayloads = ConcurrentHashMap<String, BrowserDownloadInlinePayload>()
    private val stateFile = File(appContext.filesDir, BROWSER_DOWNLOAD_STATE_FILE)
    private val _taskSnapshots = MutableStateFlow<List<BrowserDownloadTaskRecord>>(emptyList())

    val taskSnapshots: StateFlow<List<BrowserDownloadTaskRecord>> = _taskSnapshots.asStateFlow()

    @Volatile private var taskListener: ((BrowserDownloadTaskRecord, WebDownloadEvent) -> Unit)? = null
    @Volatile private var uiRefreshListener: (() -> Unit)? = null
    @Volatile private var lastUiDispatchAt: Long = 0L
    @Volatile private var lastPersistAt: Long = 0L
    @Volatile private var lastEventAt: Long = 0L
    @Volatile private var lastEvent: WebDownloadEvent? = null

    init {
        loadState()
        normalizeRestoredTasks()
        publishTaskSnapshots()
        scope.launch {
            settingsStore.state.collect {
                scheduleQueuedTasks()
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

    fun startHttpDownload(
        sessionId: String?,
        url: String,
        suggestedFileName: String,
        mimeType: String?,
        headers: Map<String, String>
    ): BrowserDownloadTaskRecord {
        val settings = settingsStore.current
        val destination = resolveUniqueApplicationDestinationFile(appContext, suggestedFileName)
        val now = System.currentTimeMillis()
        val task =
            BrowserDownloadTaskRecord(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                type = DOWNLOAD_TYPE_HTTP,
                sourceUrl = url,
                destinationPath = destination.absolutePath,
                targetDirectoryUri = settings.customDirectoryUri.takeIf { it.isNotBlank() },
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
                autoMergeM3u8 = settings.autoMergeM3u8,
                autoTransferToPublicDirectory = settings.autoTransferToPublicDirectory,
                chunkSizeKb = settings.chunkSizeKb,
                enableHttp2 = settings.enableHttp2,
                errorMessage = null,
                completedAt = null
            )
        synchronized(tasks) {
            tasks[task.id] = task
        }
        persistState(force = true)
        notifyTaskChanged(task, buildTaskEvent(task, "started"), forceUi = true)
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
        val destination = resolveUniqueApplicationDestinationFile(appContext, suggestedFileName)
        val now = System.currentTimeMillis()
        val task =
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
                autoMergeM3u8 = settings.autoMergeM3u8,
                autoTransferToPublicDirectory = settings.autoTransferToPublicDirectory,
                chunkSizeKb = settings.chunkSizeKb,
                enableHttp2 = settings.enableHttp2,
                errorMessage = null,
                completedAt = null,
                segments =
                    mutableListOf(
                        BrowserDownloadSegmentRecord(
                            index = 0,
                            startInclusive = 0L,
                            endInclusive = bytes.size.toLong() - 1L,
                            tempPath = buildSinglePartPath(destination).absolutePath
                        )
                    )
            )
        inlinePayloads[task.id] = BrowserDownloadInlinePayload(bytes)
        synchronized(tasks) {
            tasks[task.id] = task
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
                    appendLine("- URL: ${task.sourceUrl}")
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
        val uri = resolveTaskOpenUri(task) ?: return false
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, resolveTaskMimeType(task))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val launched = launchBrowserExternalIntent(appContext, intent)
        if (
            launched &&
            isBrowserDownloadApkPackage(
                mimeType = resolveTaskMimeType(task),
                fileName = task.fileName,
            )
        ) {
            scheduleAutoCleanApk(task.id)
        }
        return launched
    }

    private fun scheduleAutoCleanApk(taskId: String) {
        if (!settingsStore.current.autoCleanApk) {
            return
        }
        scope.launch {
            delay(BROWSER_DOWNLOAD_APK_AUTO_CLEAN_DELAY_MILLIS)
            deleteTask(taskId, deleteFile = true)
        }
    }

    fun copyDownloadUrl(taskId: String): String? =
        snapshotTasks()
            .firstOrNull { task -> task.id == taskId }
            ?.sourceUrl
            ?.takeIf { url -> url.isNotBlank() }

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
            AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Failed to share browser download: ${error.message}")
            false
        }
    }

    suspend fun renameDownloadedFile(taskId: String, targetFileName: String): Result<BrowserDownloadTaskRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
                val task = requireCompletedTask(taskId)
                require(targetFileName.trim().isNotBlank()) { "文件名不能为空" }
                val safeFileName = sanitizeBrowserDownloadFileName(targetFileName)
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
                AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Failed to rename browser download: ${error.message}")
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
                            AppLogger.w(
                                DOWNLOAD_SUPPORT_TAG,
                                "Failed to remove copied download after source deletion failed: ${cleanupError.message}",
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
                AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Failed to move browser download: ${error.message}")
            }
        }

    suspend fun transferDownloadedFileToPublicDirectory(
        taskId: String,
    ): Result<BrowserDownloadTaskRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
                val task = requireCompletedTask(taskId)
                require(!task.isM3u8Package) { "M3U8离线包需要保留在应用下载目录" }
                val publicDirectory = OperitPaths.browserDownloadsDir()
                require(
                    task.destinationPath.isBlank() ||
                        File(task.destinationPath).parentFile?.canonicalFile != publicDirectory.canonicalFile,
                ) { "当前文件已位于公开下载目录" }
                val targetFile = resolveUniquePublicDestinationFile(task.fileName)
                openTaskInputStream(task)?.use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("当前文件不存在")
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
                AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Failed to transfer browser download: ${error.message}")
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
        mutateTask(taskId, forcePersist = true, forceUi = true) { task ->
            task.fileName = fileName
            task.destinationPath = destinationPath
            task.destinationUri = destinationUri
            task.targetDirectoryUri = targetDirectoryUri
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
                    require(deleted >= 0) { "无法删除当前 SAF 文件" }
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
            control.stopAction = BrowserDownloadAction.PAUSE
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
            control.stopAction = BrowserDownloadAction.CANCEL
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
        scheduleQueuedTasks()
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
        scheduleQueuedTasks()
    }

    private fun deleteTask(taskId: String, deleteFile: Boolean) {
        val control = activeControls[taskId]
        if (control != null) {
            control.stopAction =
                if (deleteFile) {
                    BrowserDownloadAction.DELETE_WITH_FILE
                } else {
                    BrowserDownloadAction.DELETE_RECORD
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
                        tasks.values.map { task ->
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
                                DOWNLOAD_TYPE_HTTP -> runHttpTask(taskId)
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
                val supportsResume = probe.acceptsRanges && probe.contentLength > 0L
                configureTaskSegments(taskId, probe.contentLength, supportsResume)
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
        val autoMerge = task.autoMergeM3u8
        configureTaskForM3u8(taskId, autoMerge)
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
                    autoMerge = autoMerge,
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

    private fun configureTaskForM3u8(taskId: String, autoMerge: Boolean) {
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
            current.isM3u8Package = autoMerge
            if (autoMerge) {
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

    private fun configureTaskSegments(taskId: String, totalBytes: Long, supportsResume: Boolean) {
        mutateTask(taskId, forcePersist = true, forceUi = true) { task ->
            task.totalBytes = totalBytes
            task.supportsResume = supportsResume
            task.errorMessage = null
            task.completedAt = null
            task.isM3u8Package = false
            val destination = File(task.destinationPath)
            if (!supportsResume || totalBytes <= 0L) {
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

            if (task.segments.isNotEmpty()) {
                require(isCompleteBrowserDownloadSegmentPlan(task.segments, totalBytes)) {
                    "Stored download segment plan no longer matches the remote content length."
                }
                return@mutateTask
            }

            task.segments.clear()
            buildBrowserDownloadRangePlan(totalBytes, task.chunkSizeKb).forEach { plan ->
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
        val targetFile = resolveUniquePublicDestinationFile(sourceLocation.displayName)
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

    private fun failTask(taskId: String, error: Throwable) {
        AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Browser download failed: ${error.message}")
        mutateTask(taskId, eventStatus = "failed") { task ->
            task.status = BrowserDownloadStatus.FAILED
            task.errorMessage = error.message ?: error::class.java.simpleName
            task.speedBytesPerSecond = 0L
        }
    }

    private suspend fun handleTaskError(taskId: String, error: Throwable) {
        if (error is CancellationException) {
            handleTaskCancellation(taskId, error)
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

    private fun handleTaskCancellation(taskId: String, error: CancellationException) {
        when (activeControls[taskId]?.stopAction) {
            BrowserDownloadAction.PAUSE ->
                mutateTask(taskId, eventStatus = "paused") { task ->
                    task.status = BrowserDownloadStatus.PAUSED
                    task.errorMessage = null
                    task.speedBytesPerSecond = 0L
                }

            BrowserDownloadAction.CANCEL ->
                mutateTask(taskId, eventStatus = "canceled") { task ->
                    task.status = BrowserDownloadStatus.CANCELED
                    task.errorMessage = null
                    task.speedBytesPerSecond = 0L
                }

            BrowserDownloadAction.DELETE_RECORD,
            BrowserDownloadAction.DELETE_WITH_FILE -> Unit
            BrowserDownloadAction.RESUME,
            BrowserDownloadAction.RETRY -> Unit
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
                AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Failed to delete browser download file: ${error.message}")
            }
        }
        if (task.isM3u8Package && (includeDestinationFile || task.status != BrowserDownloadStatus.COMPLETED)) {
            runCatching {
                deleteBrowserM3u8PackageDirectory(browserM3u8PackageDirectoryFor(File(task.destinationPath)))
            }.onFailure { error ->
                AppLogger.w(
                    DOWNLOAD_SUPPORT_TAG,
                    "Failed to delete browser M3U8 package directory: ${error.message}",
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
        if (task.type != DOWNLOAD_TYPE_HTTP) {
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
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < 500L) {
            return
        }
        lastPersistAt = now
        val payload =
            JSONArray().also { array ->
                synchronized(tasks) {
                    tasks.values.forEach { task ->
                        array.put(task.toJson())
                    }
                }
            }
        runCatching {
            stateFile.writeText(payload.toString())
        }.onFailure {
            AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Failed to persist browser downloads: ${it.message}")
        }
    }

    private fun loadState() {
        if (!stateFile.exists()) {
            return
        }
        runCatching {
            val raw = stateFile.readText()
            if (raw.isBlank()) {
                return@runCatching
            }
            val array = JSONArray(raw)
            synchronized(tasks) {
                tasks.clear()
                for (index in 0 until array.length()) {
                    val task = BrowserDownloadTaskRecord.fromJson(array.getJSONObject(index))
                    tasks[task.id] = task
                }
            }
        }.onFailure {
            AppLogger.w(DOWNLOAD_SUPPORT_TAG, "Failed to load browser downloads: ${it.message}")
        }
    }

    private fun normalizeRestoredTasks() {
        val now = System.currentTimeMillis()
        var changed = false
        synchronized(tasks) {
            tasks.values.forEach { task ->
                if (task.type != DOWNLOAD_TYPE_HTTP && task.status != BrowserDownloadStatus.COMPLETED) {
                    task.status = BrowserDownloadStatus.FAILED
                    task.errorMessage = "Inline download could not be resumed after app restart."
                    task.updatedAt = now
                    task.speedBytesPerSecond = 0L
                    changed = true
                    return@forEach
                }
                if (task.activeOrPending()) {
                    task.status = BrowserDownloadStatus.PAUSED
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

internal data class PendingExternalOpenRequest(
    val requestId: String,
    val intent: Intent,
    val title: String,
    val target: String,
    val createdAt: Long = System.currentTimeMillis()
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
    val createdAt: Long = System.currentTimeMillis(),
)

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
        if (BrowserDownloadSettingsStore.getInstance(context).current.showCompletionTip) {
            when (event.status) {
                "completed" -> showToast(context.getString(com.ai.assistance.operit.R.string.download_success, task.fileName))
                "failed" ->
                    showToast(
                        context.getString(
                            com.ai.assistance.operit.R.string.download_failed,
                            task.errorMessage ?: task.fileName,
                        ),
                    )
            }
        }
    }
    browserDownloadManager().setUiRefreshListener {
        StandardBrowserSessionTools.mainHandler.post {
            refreshSessionUiOnMain()
        }
    }
}

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
        check(StandardBrowserSessionTools.pendingBrowserDownloadRequest == null) {
            "A browser download request is already awaiting confirmation."
        }
        StandardBrowserSessionTools.pendingBrowserDownloadRequest = request
        refreshSessionUiOnMain()
    }
}

internal fun StandardBrowserSessionTools.confirmBrowserDownloadRequest(requestId: String) {
    val request = StandardBrowserSessionTools.pendingBrowserDownloadRequest ?: return
    if (request.requestId != requestId) {
        return
    }
    StandardBrowserSessionTools.pendingBrowserDownloadRequest = null
    refreshSessionUiOnMain()
    dispatchBrowserDownloadRequest(request)
}

internal fun StandardBrowserSessionTools.cancelBrowserDownloadRequest(requestId: String) {
    val request = StandardBrowserSessionTools.pendingBrowserDownloadRequest ?: return
    if (request.requestId != requestId) {
        return
    }
    StandardBrowserSessionTools.pendingBrowserDownloadRequest = null
    refreshSessionUiOnMain()
}

private fun StandardBrowserSessionTools.dispatchBrowserDownloadRequest(
    request: PendingBrowserDownloadRequest,
) {
    runCatching {
        when (request.engine) {
            BrowserDownloadEngine.INTERNAL -> {
                browserDownloadManager().startHttpDownload(
                    sessionId = request.sessionId,
                    url = request.url,
                    suggestedFileName = request.fileName,
                    mimeType = request.mimeType,
                    headers = request.headers,
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
    }.onFailure { error ->
        AppLogger.e(
            DOWNLOAD_SUPPORT_TAG,
            "Unable to dispatch ${request.engine.persistedId} browser download: ${error.message}",
            error,
        )
        showToast(
            context.getString(
                com.ai.assistance.operit.R.string.download_failed,
                error.message ?: request.fileName,
            ),
        )
    }
}

private fun StandardBrowserSessionTools.enqueueSystemBrowserDownload(
    request: PendingBrowserDownloadRequest,
): Long {
    require(isBrowserDownloadNetworkUrl(request.url)) {
        "System downloads require an http or https URL: ${request.url}"
    }
    val systemRequest =
        DownloadManager.Request(Uri.parse(request.url)).apply {
            request.mimeType?.takeIf { value -> value.isNotBlank() }?.let(::setMimeType)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
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
    selectedFilter: BrowserDownloadFilter = BrowserDownloadFilter.IN_PROGRESS,
): BrowserDownloadUiState {
    val items =
        tasks.map { task ->
            BrowserDownloadItem(
                id = task.id,
                fileName = task.fileName,
                status = task.status.wireName,
                type = task.type,
                progress = task.progressOrNull(),
                downloadedBytes = task.downloadedBytes,
                totalBytes = task.totalBytes,
                speedBytesPerSecond = task.speedBytesPerSecond,
                destinationPath = browserDownloadSavedLocation(task),
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
                canOpenLocation = task.status == BrowserDownloadStatus.COMPLETED
            )
        }
    return BrowserDownloadUiState(tasks = items, selectedFilter = selectedFilter)
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

private fun resolveUniqueApplicationDestinationFile(
    context: Context,
    suggestedFileName: String,
): File =
    resolveUniqueBrowserDownloadFile(
        directory = browserDownloadApplicationDirectory(context),
        suggestedFileName = suggestedFileName,
    )

private fun resolveUniquePublicDestinationFile(suggestedFileName: String): File =
    resolveUniqueBrowserDownloadFile(
        directory = OperitPaths.browserDownloadsDir(),
        suggestedFileName = suggestedFileName,
    )

private fun browserDownloadApplicationDirectory(context: Context): File {
    val externalDownloads =
        requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) {
            "Application download directory is unavailable"
        }
    val directory = File(externalDownloads, "Kiyori/browser/downloads")
    require(directory.isDirectory || directory.mkdirs()) {
        "Unable to create application download directory: ${directory.absolutePath}"
    }
    return directory
}

private fun resolveUniqueBrowserDownloadFile(
    directory: File,
    suggestedFileName: String,
): File {
    val sanitized = suggestedFileName.trim().ifBlank { "download" }
    val dotIndex = sanitized.lastIndexOf('.')
    val base = if (dotIndex > 0) sanitized.substring(0, dotIndex) else sanitized
    val ext = if (dotIndex > 0) sanitized.substring(dotIndex) else ""
    var candidate = File(directory, sanitized)
    var index = 1
    while (
        candidate.exists() ||
            File(candidate.absolutePath + ".part").exists() ||
            File(candidate.absolutePath + ".part.0").exists()
    ) {
        candidate = File(directory, "$base ($index)$ext")
        index += 1
    }
    return candidate
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

private fun sanitizeBrowserDownloadFileName(fileName: String): String =
    fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

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
