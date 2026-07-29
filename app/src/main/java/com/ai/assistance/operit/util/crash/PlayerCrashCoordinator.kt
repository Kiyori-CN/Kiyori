package com.ai.assistance.operit.util.crash

import android.app.Activity
import android.content.Context
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.ui.error.CrashReportActivity
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object PlayerCrashCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handledRuntimeGeneration = AtomicLong(0L)
    @Volatile private var launchingReportId: String? = null

    fun onRuntimeDeath(
        context: Context,
        runtimeGeneration: Long,
        processId: Int,
    ) {
        if (runtimeGeneration <= 0L || processId <= 0) return
        while (true) {
            val handled = handledRuntimeGeneration.get()
            if (runtimeGeneration <= handled) return
            if (handledRuntimeGeneration.compareAndSet(handled, runtimeGeneration)) break
        }
        val appContext = context.applicationContext
        scope.launch {
            delay(EXIT_INFO_SETTLE_MS)
            val evidence = PlayerExitInfoCollector.collect(appContext, processId)
            val journal = PlayerCrashJournal.snapshot(appContext)
            val recentExisting =
                CrashReportStore.listReports(appContext)
                    .firstOrNull { report ->
                        report.type == CrashReportType.PLAYER_RUNTIME_FATAL &&
                            report.processId == processId &&
                            report.status == CrashReportStatus.PENDING &&
                            System.currentTimeMillis() - report.createdAtEpochMs <=
                                EXISTING_REPORT_WINDOW_MS
                    }
            val report =
                if (recentExisting != null) {
                    requireNotNull(
                        CrashReportStore.updateReport(
                            appContext,
                            recentExisting.reportId,
                        ) { existing ->
                            existing.copy(
                                runtimeGeneration = runtimeGeneration,
                                exitReason = evidence.reason,
                                exitStatus = evidence.status,
                                exitDescription = evidence.description,
                                processImportance = evidence.importance,
                                processStateSummary = evidence.processStateSummary,
                                playerJournal = journal,
                            )
                        },
                    )
                } else {
                    CrashReportStore.createReport(
                    context = appContext,
                    draft =
                        CrashReportDraft(
                            type = CrashReportType.PLAYER_RUNTIME_FATAL,
                            processName = "${appContext.packageName}:player",
                            processId = processId,
                            runtimeGeneration = runtimeGeneration,
                            exitReason = evidence.reason,
                            exitStatus = evidence.status,
                            exitDescription = evidence.description,
                            processImportance = evidence.importance,
                            processStateSummary = evidence.processStateSummary,
                            playerJournal = journal,
                        ),
                    )
                }
            withContext(Dispatchers.Main.immediate) {
                ActivityLifecycleManager.getCurrentActivity()?.let { activity ->
                    launchReport(activity, report.reportId)
                }
            }
        }
    }

    fun onActivityResumed(activity: Activity) {
        val appContext = activity.applicationContext
        scope.launch {
            launchingReportId?.let { reportId ->
                val activeReport = CrashReportStore.readReport(appContext, reportId)
                if (activeReport?.status != CrashReportStatus.RESOLVED) {
                    return@launch
                }
                launchingReportId = null
            }
            val pending =
                CrashReportStore.listReports(appContext)
                    .firstOrNull { report ->
                        report.type == CrashReportType.PLAYER_RUNTIME_FATAL &&
                            report.status == CrashReportStatus.PENDING
                    } ?: return@launch
            withContext(Dispatchers.Main.immediate) {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    launchReport(activity, pending.reportId)
                }
            }
        }
    }

    private fun launchReport(activity: Activity, reportId: String) {
        if (launchingReportId == reportId) return
        launchingReportId = reportId
        activity.startActivity(
            CrashReportActivity.createIntent(
                context = activity,
                reportId = reportId,
                clearTask = false,
            ),
        )
    }

    private const val EXIT_INFO_SETTLE_MS = 300L
    private const val EXISTING_REPORT_WINDOW_MS = 10_000L
}
