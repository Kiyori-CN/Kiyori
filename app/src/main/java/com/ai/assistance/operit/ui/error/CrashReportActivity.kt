package com.ai.assistance.operit.ui.error

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.OperitUtilityTheme
import com.ai.assistance.operit.ui.features.toolbox.screens.logcat.LogcatExportHelper
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.util.OperitPaths
import com.ai.assistance.operit.util.crash.CrashReportFormatter
import com.ai.assistance.operit.util.crash.CrashReportPrimaryAction
import com.ai.assistance.operit.util.crash.CrashReportRecord
import com.ai.assistance.operit.util.crash.CrashReportStore
import com.ai.assistance.operit.util.crash.CrashReportType
import com.ai.assistance.operit.util.crash.selectCrashReportPrimaryAction
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class CrashReportActivity : ComponentActivity() {
    private var reportId: String? = null
    private var reportType: CrashReportType? = null
    private var runtimeGeneration: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reportId = intent.getStringExtra(EXTRA_REPORT_ID)
        val report = loadAndMarkDisplayed(reportId)
        reportType = report?.type
        runtimeGeneration = report?.runtimeGeneration
        val reportText =
            report?.let(CrashReportFormatter::format)
                ?: getString(R.string.crash_report_missing)

        if (reportType == CrashReportType.PLAYER_RUNTIME_FATAL) {
            onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        resolveAndFinish()
                    }
                },
            )
        }

        setContent {
            OperitUtilityTheme {
                CrashReportScreen(
                    reportType = reportType,
                    reportText = reportText,
                    onRestartApp = { restartApp(this, reportId) },
                    onRestartPlayer = ::restartPlayer,
                    onReturnToKiyori = ::resolveAndFinish,
                )
            }
        }
    }

    private fun loadAndMarkDisplayed(reportId: String?): CrashReportRecord? {
        if (reportId == null) return null
        val report =
            runCatching { CrashReportStore.readReport(this, reportId) }
                .onFailure { error -> Log.e(TAG, "Unable to read crash report", error) }
                .getOrNull()
                ?: return null
        return runCatching { CrashReportStore.markDisplayed(this, reportId) }
            .onFailure { error -> Log.e(TAG, "Unable to mark crash report displayed", error) }
            .getOrNull()
            ?: report
    }

    private fun resolveAndFinish() {
        reportId?.let { id ->
            runCatching { CrashReportStore.markResolved(this, id) }
                .onFailure { error -> Log.e(TAG, "Unable to resolve crash report", error) }
        }
        finish()
    }

    private fun restartPlayer() {
        val generation = runtimeGeneration ?: return
        reportId?.let { id ->
            runCatching { CrashReportStore.markResolved(this, id) }
                .onFailure { error -> Log.e(TAG, "Unable to resolve player crash report", error) }
        }
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_RESTART_PLAYER_AFTER_CRASH
                putExtra(MainActivity.EXTRA_PLAYER_RUNTIME_GENERATION, generation)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        finish()
    }

    companion object {
        private const val TAG = "CrashReportActivity"
        private const val EXTRA_REPORT_ID = "extra_crash_report_id"

        fun createIntent(
            context: Context,
            reportId: String?,
            clearTask: Boolean,
        ): Intent =
            Intent(context, CrashReportActivity::class.java).apply {
                reportId?.let { putExtra(EXTRA_REPORT_ID, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (clearTask) {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CrashReportScreen(
    reportType: CrashReportType?,
    reportText: String,
    onRestartApp: () -> Unit,
    onRestartPlayer: () -> Unit,
    onReturnToKiyori: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExportingLogs by remember { mutableStateOf(false) }
    var logExportMessage by remember { mutableStateOf<String?>(null) }
    var logExportSuccess by remember { mutableStateOf<Boolean?>(null) }
    val isPlayerRuntimeReport = reportType == CrashReportType.PLAYER_RUNTIME_FATAL
    val primaryAction = selectCrashReportPrimaryAction(reportType)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.title_activity_crash_report)) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        titleContentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text =
                    stringResource(
                        id =
                            if (isPlayerRuntimeReport) {
                                R.string.crash_report_player_header
                            } else {
                                R.string.crash_report_header
                            },
                    ),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    when (primaryAction) {
                        CrashReportPrimaryAction.RESTART_APP -> onRestartApp()
                        CrashReportPrimaryAction.RESTART_PLAYER -> onRestartPlayer()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    stringResource(
                        id =
                            when (primaryAction) {
                                CrashReportPrimaryAction.RESTART_APP ->
                                    R.string.crash_report_restart_button
                                CrashReportPrimaryAction.RESTART_PLAYER ->
                                    R.string.crash_report_restart_player_button
                            },
                    ),
                )
            }

            if (isPlayerRuntimeReport) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onReturnToKiyori,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(id = R.string.crash_report_return_button))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(
                    onClick = { copyToClipboard(context, reportText) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(id = R.string.crash_report_copy_button))
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            exportToFile(context, reportText)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(id = R.string.crash_report_export_button))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        isExportingLogs = true
                        logExportMessage = null
                        logExportSuccess = null
                        try {
                            val result = LogcatExportHelper.exportLogs(context)
                            logExportMessage = result.message
                            logExportSuccess = result.success
                        } finally {
                            isExportingLogs = false
                        }
                    }
                },
                enabled = !isExportingLogs,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isExportingLogs) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(id = R.string.crash_report_export_logs_button))
            }

            logExportMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (logExportSuccess == true) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                        ),
                ) {
                    SelectionContainer {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                            color =
                                if (logExportSuccess == true) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(id = R.string.crash_report_details_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                SelectionContainer {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                    ) {
                        Text(
                            text = reportText,
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("error_report", text))
    Toast.makeText(context, R.string.crash_report_copy_success, Toast.LENGTH_SHORT).show()
}

private fun exportToFile(context: Context, text: String) {
    try {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(OperitPaths.errorDir(), "error-report-$timestamp.log")
        FileOutputStream(file).use { output ->
            output.write(text.toByteArray(StandardCharsets.UTF_8))
        }
        Toast.makeText(
            context,
            context.getString(R.string.crash_report_export_success, file.absolutePath),
            Toast.LENGTH_LONG,
        ).show()
    } catch (error: Exception) {
        Toast.makeText(
            context,
            context.getString(
                R.string.crash_report_export_failed,
                error.localizedMessage ?: error.javaClass.simpleName,
            ),
            Toast.LENGTH_LONG,
        ).show()
        Log.e("CrashReportActivity", "Failed to export crash report", error)
    }
}

private fun restartApp(
    context: Context,
    reportId: String?,
) {
    reportId?.let { id ->
        runCatching { CrashReportStore.markResolved(context, id) }
            .onFailure { error -> Log.e("CrashReportActivity", "Unable to resolve crash report", error) }
    }
    val intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    val flags =
        PendingIntent.FLAG_CANCEL_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            })
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.set(
        AlarmManager.RTC,
        System.currentTimeMillis() + 200,
        pendingIntent,
    )

    (context as? Activity)?.finishAffinity()
    android.os.Process.killProcess(android.os.Process.myPid())
    kotlin.system.exitProcess(0)
}
