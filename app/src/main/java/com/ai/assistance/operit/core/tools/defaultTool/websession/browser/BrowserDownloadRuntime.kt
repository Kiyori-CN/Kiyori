package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.application.ForegroundServiceCompat
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val DOWNLOAD_RUNTIME_TAG = "BrowserDownloadRuntime"
private const val BROWSER_DOWNLOAD_JOB_ID = 0x4B10
private const val BROWSER_DOWNLOAD_RUNTIME_NOTIFICATION_ID = 0x4B11
private const val BROWSER_DOWNLOAD_TERMINAL_NOTIFICATION_BASE = 0x4C00
private const val BROWSER_DOWNLOAD_RUNTIME_CHANNEL_ID = "kiyori_browser_download_runtime"
private const val BROWSER_DOWNLOAD_RESULT_CHANNEL_ID = "kiyori_browser_download_result"
private const val BROWSER_DOWNLOAD_NOTIFICATION_UPDATE_MILLIS = 500L

internal enum class BrowserDownloadRuntimeKind {
    USER_INITIATED_JOB,
    DATA_SYNC_FOREGROUND_SERVICE,
}

internal fun resolveBrowserDownloadRuntimeKind(sdkInt: Int): BrowserDownloadRuntimeKind =
    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        BrowserDownloadRuntimeKind.USER_INITIATED_JOB
    } else {
        BrowserDownloadRuntimeKind.DATA_SYNC_FOREGROUND_SERVICE
    }

internal fun isBrowserDownloadNetworkAllowed(
    policy: BrowserDownloadNetworkPolicy,
    allowRoaming: Boolean,
    isMetered: Boolean,
    isRoaming: Boolean,
): Boolean =
    (policy == BrowserDownloadNetworkPolicy.ANY || !isMetered) &&
        (allowRoaming || !isRoaming)

internal fun browserDownloadRuntimeRequiresUnmeteredNetwork(
    tasks: Collection<BrowserDownloadTaskRecord>,
): Boolean {
    val runnableNetworkTasks =
        tasks.filter { task ->
            task.type == BROWSER_DOWNLOAD_TYPE_HTTP && task.activeOrPending()
        }
    return runnableNetworkTasks.isNotEmpty() &&
        runnableNetworkTasks.all { task ->
            task.networkPolicy == BrowserDownloadNetworkPolicy.UNMETERED
        }
}

internal data class BrowserDownloadRuntimeSummary(
    val activeCount: Int,
    val queuedCount: Int,
    val totalCount: Int,
    val currentFileName: String?,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speedBytesPerSecond: Long,
) {
    val isIdle: Boolean
        get() = totalCount == 0

    val progressPercent: Int?
        get() =
            totalBytes
                ?.takeIf { total -> total > 0L }
                ?.let { total ->
                    ((downloadedBytes.coerceIn(0L, total).toDouble() / total.toDouble()) * 100.0)
                        .roundToInt()
                        .coerceIn(0, 100)
                }
}

internal fun buildBrowserDownloadRuntimeSummary(
    tasks: List<BrowserDownloadTaskRecord>,
): BrowserDownloadRuntimeSummary {
    val runnable =
        tasks.filter { task ->
            task.type == BROWSER_DOWNLOAD_TYPE_HTTP && task.activeOrPending()
        }
    val active =
        runnable.filter { task ->
            task.status == BrowserDownloadStatus.CONNECTING ||
                task.status == BrowserDownloadStatus.DOWNLOADING
        }
    val queued = runnable.count { task -> task.status == BrowserDownloadStatus.QUEUED }
    val knownTotals = runnable.map { task -> task.totalBytes }.filter { bytes -> bytes > 0L }
    val totalBytes =
        knownTotals
            .takeIf { totals -> totals.size == runnable.size && totals.isNotEmpty() }
            ?.fold(0L) { total, bytes ->
                if (Long.MAX_VALUE - total < bytes) Long.MAX_VALUE else total + bytes
            }
    val downloadedBytes =
        runnable.fold(0L) { total, task ->
            val bytes = task.downloadedBytes.coerceAtLeast(0L)
            if (Long.MAX_VALUE - total < bytes) Long.MAX_VALUE else total + bytes
        }
    val speedBytesPerSecond =
        active.fold(0L) { total, task ->
            val speed = task.speedBytesPerSecond.coerceAtLeast(0L)
            if (Long.MAX_VALUE - total < speed) Long.MAX_VALUE else total + speed
        }
    return BrowserDownloadRuntimeSummary(
        activeCount = active.size,
        queuedCount = queued,
        totalCount = runnable.size,
        currentFileName =
            active.firstOrNull()?.fileName
                ?: runnable.firstOrNull()?.fileName,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        speedBytesPerSecond = speedBytesPerSecond,
    )
}

internal object BrowserDownloadRuntimeCoordinator {
    private val runtimeProcessStart = AtomicBoolean(false)

    @Volatile
    private var legacyServiceActive = false

    @Volatile
    private var activeJobNetwork: Network? = null

    fun markRuntimeProcessStart() {
        runtimeProcessStart.set(true)
    }

    fun shouldResumeRestoredTasks(context: Context): Boolean {
        if (runtimeProcessStart.getAndSet(false)) {
            return true
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return legacyServiceActive
        }
        val scheduler = context.getSystemService(JobScheduler::class.java)
        return scheduler.getPendingJob(BROWSER_DOWNLOAD_JOB_ID) != null
    }

    fun markLegacyServiceActive(active: Boolean) {
        legacyServiceActive = active
    }

    fun updateJobNetwork(network: Network?) {
        activeJobNetwork = network
    }

    fun currentJobNetwork(): Network? = activeJobNetwork

    fun isNetworkAllowed(
        context: Context,
        policy: BrowserDownloadNetworkPolicy,
        allowRoaming: Boolean,
    ): Boolean {
        val connectivityManager =
            context.getSystemService(ConnectivityManager::class.java)
        val network = activeJobNetwork ?: connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(network)
                ?: return false
        val isMetered =
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        @Suppress("DEPRECATION")
        val isRoaming =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            } else {
                connectivityManager.activeNetworkInfo?.isRoaming == true
            }
        return isBrowserDownloadNetworkAllowed(
            policy = policy,
            allowRoaming = allowRoaming,
            isMetered = isMetered,
            isRoaming = isRoaming,
        )
    }

    fun requestExecution(
        context: Context,
        estimatedDownloadBytes: Long,
        runtimeExecutionEnabled: Boolean,
        requiresUnmeteredNetwork: Boolean,
    ): Boolean {
        if (runtimeExecutionEnabled) {
            return true
        }
        val appContext = context.applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requestUserInitiatedJob(
                context = appContext,
                estimatedDownloadBytes = estimatedDownloadBytes,
                requiresUnmeteredNetwork = requiresUnmeteredNetwork,
            )
        } else {
            requestLegacyForegroundService(appContext)
        }
    }

    fun resumeLegacyWorkAfterBoot(context: Context): Boolean =
        requestLegacyForegroundService(
            context = context.applicationContext,
        )

    fun createRuntimeNotification(
        context: Context,
        summary: BrowserDownloadRuntimeSummary,
    ): Notification {
        ensureNotificationChannels(context)
        val contentText =
            when {
                summary.totalCount == 0 ->
                    context.getString(R.string.browser_download_runtime_preparing)
                summary.totalCount == 1 ->
                    buildString {
                        append(summary.currentFileName.orEmpty())
                        if (summary.speedBytesPerSecond > 0L) {
                            append(" · ")
                            append(formatBrowserDownloadRuntimeBytes(summary.speedBytesPerSecond))
                            append("/s")
                        }
                    }
                else ->
                    context.getString(
                        R.string.browser_download_runtime_multiple,
                        summary.activeCount,
                        summary.queuedCount,
                    )
            }
        return NotificationCompat.Builder(context, BROWSER_DOWNLOAD_RUNTIME_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kiyori_notification)
            .setContentTitle(context.getString(R.string.browser_download_runtime_title))
            .setContentText(contentText)
            .setContentIntent(openDownloadsPendingIntent(context))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(
                100,
                summary.progressPercent ?: 0,
                summary.progressPercent == null,
            )
            .addAction(
                R.drawable.ic_kiyori_tool_download,
                context.getString(R.string.browser_download_runtime_pause_all),
                runtimeActionPendingIntent(
                    context = context,
                    action = BrowserDownloadRuntimeActionReceiver.ACTION_PAUSE_ALL,
                    requestCode = 1,
                ),
            )
            .build()
    }

    fun notifyTerminalTask(
        context: Context,
        task: BrowserDownloadTaskRecord,
    ) {
        if (!BrowserDownloadSettingsStore.getInstance(context).current.showResultNotifications) {
            return
        }
        if (
            task.status != BrowserDownloadStatus.COMPLETED &&
                task.status != BrowserDownloadStatus.FAILED
        ) {
            return
        }
        ensureNotificationChannels(context)
        val completed = task.status == BrowserDownloadStatus.COMPLETED
        val builder =
            NotificationCompat.Builder(context, BROWSER_DOWNLOAD_RESULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_kiyori_notification)
                .setContentTitle(
                    context.getString(
                        if (completed) {
                            R.string.browser_download_result_completed_title
                        } else {
                            R.string.browser_download_result_failed_title
                        },
                    ),
                )
                .setContentText(
                    if (completed) {
                        task.fileName
                    } else {
                        context.getString(
                            R.string.browser_download_result_failed_text,
                            task.fileName,
                            task.errorMessage.orEmpty(),
                        )
                    },
                )
                .setContentIntent(openDownloadsPendingIntent(context))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (completed) {
            builder.addAction(
                R.drawable.ic_kiyori_tool_download,
                context.getString(R.string.browser_download_result_open),
                openTaskPendingIntent(context, task),
            )
        }
        if (!canPostBrowserDownloadNotifications(context)) {
            AppLogger.w(
                DOWNLOAD_RUNTIME_TAG,
                "Browser download result notification skipped because notification permission is unavailable",
            )
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(
                terminalNotificationId(task.id),
                builder.build(),
            )
        } catch (error: SecurityException) {
            AppLogger.w(
                DOWNLOAD_RUNTIME_TAG,
                "Browser download result notification permission changed before publication: " +
                    browserDownloadSafeErrorMessage(error),
            )
        }
    }

    fun ensureNotificationChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    BROWSER_DOWNLOAD_RUNTIME_CHANNEL_ID,
                    context.getString(R.string.browser_download_runtime_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description =
                        context.getString(R.string.browser_download_runtime_channel_description)
                    setShowBadge(false)
                },
                NotificationChannel(
                    BROWSER_DOWNLOAD_RESULT_CHANNEL_ID,
                    context.getString(R.string.browser_download_result_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description =
                        context.getString(R.string.browser_download_result_channel_description)
                },
            ),
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestUserInitiatedJob(
        context: Context,
        estimatedDownloadBytes: Long,
        requiresUnmeteredNetwork: Boolean,
    ): Boolean {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val builder =
            JobInfo.Builder(
                BROWSER_DOWNLOAD_JOB_ID,
                ComponentName(context, BrowserDownloadJobService::class.java),
            )
                .setRequiredNetworkType(
                    if (requiresUnmeteredNetwork) {
                        JobInfo.NETWORK_TYPE_UNMETERED
                    } else {
                        JobInfo.NETWORK_TYPE_ANY
                    },
                )
                .setUserInitiated(true)
                .setPersisted(true)
        if (estimatedDownloadBytes >= 0L) {
            builder.setEstimatedNetworkBytes(
                JobInfo.NETWORK_BYTES_UNKNOWN.toLong(),
                estimatedDownloadBytes,
            )
        }
        return runCatching {
            scheduler.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
        }.onFailure { error ->
            AppLogger.w(
                DOWNLOAD_RUNTIME_TAG,
                "Unable to schedule user-initiated browser download job: " +
                    "${error::class.java.simpleName}: ${browserDownloadSafeErrorMessage(error)}",
            )
        }.getOrDefault(false)
    }

    private fun requestLegacyForegroundService(
        context: Context,
    ): Boolean {
        return runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BrowserDownloadForegroundService::class.java),
            )
            true
        }.onFailure { error ->
            AppLogger.w(
                DOWNLOAD_RUNTIME_TAG,
                "Unable to start browser download foreground service: " +
                    "${error::class.java.simpleName}: ${browserDownloadSafeErrorMessage(error)}",
            )
        }.getOrDefault(false)
    }

    private fun openDownloadsPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_KIYORI_DOWNLOADS
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun runtimeActionPendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, BrowserDownloadRuntimeActionReceiver::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openTaskPendingIntent(
        context: Context,
        task: BrowserDownloadTaskRecord,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            terminalNotificationId(task.id),
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_KIYORI_DOWNLOAD_TASK
                putExtra(MainActivity.EXTRA_KIYORI_DOWNLOAD_TASK_ID, task.id)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun terminalNotificationId(taskId: String): Int =
        BROWSER_DOWNLOAD_TERMINAL_NOTIFICATION_BASE + (taskId.hashCode() and 0x000F_FFFF)
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class BrowserDownloadJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observationJob: Job? = null
    private var currentParameters: JobParameters? = null
    private var manager: BrowserDownloadManager? = null

    override fun onStartJob(params: JobParameters): Boolean {
        BrowserDownloadRuntimeCoordinator.markRuntimeProcessStart()
        BrowserDownloadRuntimeCoordinator.updateJobNetwork(params.network)
        val downloadManager = BrowserDownloadManager.getInstance(applicationContext)
        manager = downloadManager
        currentParameters = params
        updateNotification(params, downloadManager)
        downloadManager.acquireRuntimeExecution()
        observationJob?.cancel()
        observationJob =
            serviceScope.launch {
                while (currentCoroutineContext().isActive && currentParameters === params) {
                    delay(BROWSER_DOWNLOAD_NOTIFICATION_UPDATE_MILLIS)
                    downloadManager.refreshRuntimeNetworkPolicy()
                    if (downloadManager.releaseRuntimeExecutionIfIdle()) {
                        currentParameters = null
                        BrowserDownloadRuntimeCoordinator.updateJobNetwork(null)
                        jobFinished(params, false)
                        break
                    }
                    updateNotification(params, downloadManager)
                }
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        if (currentParameters === params) {
            currentParameters = null
            observationJob?.cancel()
            observationJob = null
        }
        val shouldRetry = manager?.suspendRuntimeExecutionForSystemStop() == true
        BrowserDownloadRuntimeCoordinator.updateJobNetwork(null)
        AppLogger.w(
            DOWNLOAD_RUNTIME_TAG,
            "Browser download job stopped by the system: reason=${params.stopReason}, retry=$shouldRetry",
        )
        return shouldRetry
    }

    override fun onNetworkChanged(params: JobParameters) {
        if (currentParameters === params) {
            BrowserDownloadRuntimeCoordinator.updateJobNetwork(params.network)
            manager?.refreshRuntimeNetworkPolicy()
        }
    }

    override fun onDestroy() {
        observationJob?.cancel()
        BrowserDownloadRuntimeCoordinator.updateJobNetwork(null)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun updateNotification(
        params: JobParameters,
        manager: BrowserDownloadManager,
    ) {
        val summary = buildBrowserDownloadRuntimeSummary(manager.snapshotTasks())
        setNotification(
            params,
            BROWSER_DOWNLOAD_RUNTIME_NOTIFICATION_ID,
            BrowserDownloadRuntimeCoordinator.createRuntimeNotification(this, summary),
            JOB_END_NOTIFICATION_POLICY_REMOVE,
        )
    }
}

internal class BrowserDownloadForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observationJob: Job? = null
    private lateinit var manager: BrowserDownloadManager
    private var stoppingAfterIdle = false

    override fun onCreate() {
        super.onCreate()
        BrowserDownloadRuntimeCoordinator.markRuntimeProcessStart()
        BrowserDownloadRuntimeCoordinator.markLegacyServiceActive(true)
        BrowserDownloadRuntimeCoordinator.updateJobNetwork(null)
        manager = BrowserDownloadManager.getInstance(applicationContext)
        val summary = buildBrowserDownloadRuntimeSummary(manager.snapshotTasks())
        ForegroundServiceCompat.startForeground(
            service = this,
            notificationId = BROWSER_DOWNLOAD_RUNTIME_NOTIFICATION_ID,
            notification =
                BrowserDownloadRuntimeCoordinator.createRuntimeNotification(this, summary),
            types = ForegroundServiceCompat.buildTypes(dataSync = true),
        )
        manager.acquireRuntimeExecution()
        observeTasks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        manager.acquireRuntimeExecution()
        updateNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observationJob?.cancel()
        if (!stoppingAfterIdle) {
            manager.suspendRuntimeExecutionForSystemStop()
        }
        BrowserDownloadRuntimeCoordinator.markLegacyServiceActive(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        manager.suspendRuntimeExecutionForSystemStop()
        stoppingAfterIdle = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun observeTasks() {
        observationJob?.cancel()
        observationJob =
            serviceScope.launch {
                while (currentCoroutineContext().isActive) {
                    delay(BROWSER_DOWNLOAD_NOTIFICATION_UPDATE_MILLIS)
                    manager.refreshRuntimeNetworkPolicy()
                    if (manager.releaseRuntimeExecutionIfIdle()) {
                        stoppingAfterIdle = true
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        break
                    }
                    updateNotification()
                }
            }
    }

    private fun updateNotification() {
        if (!canPostBrowserDownloadNotifications(this)) {
            AppLogger.w(
                DOWNLOAD_RUNTIME_TAG,
                "Browser download runtime notification update skipped because notification permission is unavailable",
            )
            return
        }
        try {
            NotificationManagerCompat.from(this).notify(
                BROWSER_DOWNLOAD_RUNTIME_NOTIFICATION_ID,
                BrowserDownloadRuntimeCoordinator.createRuntimeNotification(
                    context = this,
                    summary = buildBrowserDownloadRuntimeSummary(manager.snapshotTasks()),
                ),
            )
        } catch (error: SecurityException) {
            AppLogger.w(
                DOWNLOAD_RUNTIME_TAG,
                "Browser download runtime notification permission changed before update: " +
                    browserDownloadSafeErrorMessage(error),
            )
        }
    }
}

private fun canPostBrowserDownloadNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

internal class BrowserDownloadRuntimeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PAUSE_ALL ->
                BrowserDownloadManager.getInstance(context.applicationContext).pauseAllForUser()
        }
    }

    companion object {
        internal const val ACTION_PAUSE_ALL =
            "com.kiyori.action.PAUSE_ALL_BROWSER_DOWNLOADS"
    }
}

internal class BrowserDownloadBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED ||
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            return
        }
        BrowserDownloadRuntimeCoordinator.markRuntimeProcessStart()
        val manager = BrowserDownloadManager.getInstance(context.applicationContext)
        if (manager.hasRunnableTasks()) {
            BrowserDownloadRuntimeCoordinator.resumeLegacyWorkAfterBoot(context)
        }
    }
}

private fun formatBrowserDownloadRuntimeBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = safeBytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${safeBytes}${units[unitIndex]}"
    } else {
        String.format(java.util.Locale.US, "%.1f%s", value, units[unitIndex])
    }
}
