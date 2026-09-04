package com.ai.assistance.operit.core.tools.system

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.shell.ShellExecutor
import com.ai.assistance.operit.core.tools.system.shell.ShellExecutorFactory
import com.ai.assistance.operit.core.tools.system.shell.ShellProcess
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences

internal enum class PrivilegedShellRoute {
    DEBUGGER,
    ROOT,
}

internal data class PrivilegedShellRouteSelection(
    val route: PrivilegedShellRoute?,
    val reason: String,
)

/** 向后兼容的Shell命令执行工具类 通过权限级别委托到相应的Shell执行器 */
class AndroidShellExecutor {
    companion object {
        private const val TAG = "AndroidShellExecutor"
        @Volatile private var context: Context? = null
        private val preferredPermissionLevelCacheLock = Any()
        @Volatile private var hasCachedPreferredPermissionLevel = false
        @Volatile private var cachedPreferredPermissionLevel: AndroidPermissionLevel? = null

        /**
         * 设置全局上下文引用
         * @param appContext 应用上下文
         */
        fun setContext(appContext: Context) {
            val applicationContext = appContext.applicationContext
            val contextChanged = context !== applicationContext
            context = applicationContext
            if (contextChanged) {
                clearPreferredPermissionLevelCache()
                ShellExecutorFactory.clearCache()
            }
            // Bind Shizuku before any tool is registered so a live service is observable
            // even when the user has not selected a preferred Android permission level.
            ShizukuAuthorizer.initialize()
        }

        fun clearPreferredPermissionLevelCache() {
            synchronized(preferredPermissionLevelCacheLock) {
                cachedPreferredPermissionLevel = null
                hasCachedPreferredPermissionLevel = false
            }
            ShellExecutorFactory.clearCache()
        }

        private fun readPreferredPermissionLevel(): AndroidPermissionLevel? =
            androidPermissionPreferences.getPreferredPermissionLevel()

        private fun getPreferredPermissionLevelCached(): AndroidPermissionLevel? {
            if (hasCachedPreferredPermissionLevel) {
                return cachedPreferredPermissionLevel
            }

            synchronized(preferredPermissionLevelCacheLock) {
                if (!hasCachedPreferredPermissionLevel) {
                    cachedPreferredPermissionLevel = readPreferredPermissionLevel()
                    hasCachedPreferredPermissionLevel = true
                }
                return cachedPreferredPermissionLevel
            }
        }

        private fun getPermissionLevelLabel(level: AndroidPermissionLevel): String {
            return when (level) {
                AndroidPermissionLevel.STANDARD -> "STANDARD"
                AndroidPermissionLevel.ACCESSIBILITY -> "ACCESSIBILITY"
                AndroidPermissionLevel.DEBUGGER -> "DEBUGGER"
                AndroidPermissionLevel.ADMIN -> "ADMIN"
                AndroidPermissionLevel.ROOT -> "ROOT"
            }
        }

        private fun buildStrictUnavailableReason(
            level: AndroidPermissionLevel,
            executorAvailable: Boolean,
            permStatus: ShellExecutor.PermissionStatus
        ): String {
            val reasons = mutableListOf<String>()

            if (!executorAvailable) {
                reasons += "executor unavailable"
            }
            if (!permStatus.granted) {
                reasons += permStatus.reason.trim().ifEmpty { "permission not granted" }
            }

            val reasonText = reasons.distinct().joinToString("; ").ifBlank { "unknown reason" }
            return "Current ${getPermissionLevelLabel(level)} unavailable: $reasonText"
        }

        /**
         * Resolves the only routes that are allowed to satisfy a privileged Android shell call.
         * A configured non-privileged level is an explicit choice and must not silently change
         * the caller's identity. When no level has been configured, a live authorized Shizuku
         * service is the single implicit route permitted for this explicit privileged entry.
         */
        internal fun selectPrivilegedShellRoute(
            configuredLevel: AndroidPermissionLevel?,
            shizukuServiceRunning: Boolean,
            shizukuPermissionGranted: Boolean,
            shizukuBinderAlive: Boolean = true,
        ): PrivilegedShellRouteSelection {
            return when (configuredLevel) {
                AndroidPermissionLevel.ROOT ->
                    PrivilegedShellRouteSelection(PrivilegedShellRoute.ROOT, "configured ROOT")
                AndroidPermissionLevel.DEBUGGER ->
                    PrivilegedShellRouteSelection(
                        PrivilegedShellRoute.DEBUGGER,
                        "configured DEBUGGER",
                    )
                null ->
                    if (
                        shizukuServiceRunning &&
                            shizukuBinderAlive &&
                            shizukuPermissionGranted
                    ) {
                        PrivilegedShellRouteSelection(
                            PrivilegedShellRoute.DEBUGGER,
                            "live authorized Shizuku",
                        )
                    } else {
                        val reason = when {
                            !shizukuServiceRunning -> "Shizuku service is not running"
                            !shizukuBinderAlive -> "Shizuku binder is not alive"
                            else -> "Shizuku permission is not granted"
                        }
                        PrivilegedShellRouteSelection(null, reason)
                    }
                AndroidPermissionLevel.STANDARD,
                AndroidPermissionLevel.ACCESSIBILITY,
                AndroidPermissionLevel.ADMIN,
                ->
                    PrivilegedShellRouteSelection(
                        null,
                        "configured ${getPermissionLevelLabel(configuredLevel)} is not a privileged route",
                    )
            }
        }

        /** True when intent/broadcast tools must use the explicit privileged route. */
        fun isPrivilegedExecutionConfiguredOrAvailable(): Boolean {
            val configuredLevel = getPreferredPermissionLevelCached()
            if (
                configuredLevel == AndroidPermissionLevel.ROOT ||
                    configuredLevel == AndroidPermissionLevel.DEBUGGER
            ) {
                return true
            }
            if (configuredLevel != null) {
                return false
            }
            val status = ShizukuAuthorizer.getStatusSnapshot()
            return status.serviceRunning && status.binderAlive && status.permissionGranted
        }

        private fun buildPrivilegedDiagnostic(
            configuredLevel: AndroidPermissionLevel?,
            route: PrivilegedShellRoute?,
            status: ShizukuStatusSnapshot,
            executorAvailable: Boolean?,
            permissionStatus: ShellExecutor.PermissionStatus?,
            failureReason: String?,
        ): String {
            val permissionResult = status.permissionResult?.toString() ?: "unknown"
            val executorLevel = permissionStatus?.let { permissionStatusValue ->
                if (permissionStatusValue.granted) "granted" else "denied"
            } ?: "not-selected"
            return buildString {
                append("configuredLevel=")
                append(configuredLevel?.name ?: "UNCONFIGURED")
                append(", selectedRoute=")
                append(route?.name ?: "NONE")
                append(", shizukuServiceRunning=")
                append(status.serviceRunning)
                append(", binderAlive=")
                append(status.binderAlive)
                append(", shizukuUid=")
                append(status.uid?.toString() ?: "unknown")
                append(", checkSelfPermission=")
                append(permissionResult)
                append(", shizukuPermissionGranted=")
                append(status.permissionGranted)
                append(", executorAvailable=")
                append(executorAvailable?.toString() ?: "not-selected")
                append(", executorPermission=")
                append(executorLevel)
                status.serviceError
                    .takeIf(String::isNotBlank)
                    ?.let { append(", serviceError=").append(it.take(256)) }
                status.permissionError
                    .takeIf(String::isNotBlank)
                    ?.let { append(", permissionError=").append(it.take(256)) }
                failureReason
                    ?.takeIf(String::isNotBlank)
                    ?.let { append(", reason=").append(it.take(512)) }
            }
        }

        /**
         * Executes an Android shell command through an explicitly selected Root or Shizuku
         * process. Ordinary executeShellCommand remains configuration-driven for Ubuntu and
         * historical callers; this method is the contract used by super_admin:shell and protected
         * system operations.
         */
        suspend fun executePrivilegedShellCommand(command: String): CommandResult {
            val ctx = context
                ?: return CommandResult(false, "", "Context not initialized", -1)

            val configuredLevel = getPreferredPermissionLevelCached()
            val status = ShizukuAuthorizer.getStatusSnapshot()
            val selection =
                selectPrivilegedShellRoute(
                    configuredLevel = configuredLevel,
                    shizukuServiceRunning = status.serviceRunning,
                    shizukuPermissionGranted = status.permissionGranted,
                    shizukuBinderAlive = status.binderAlive,
                )
            val permissionLevel =
                when (selection.route) {
                    PrivilegedShellRoute.ROOT -> AndroidPermissionLevel.ROOT
                    PrivilegedShellRoute.DEBUGGER -> AndroidPermissionLevel.DEBUGGER
                    null -> null
                }
            if (permissionLevel == null) {
                val diagnostic =
                    buildPrivilegedDiagnostic(
                        configuredLevel = configuredLevel,
                        route = selection.route,
                        status = status,
                        executorAvailable = null,
                        permissionStatus = null,
                        failureReason = selection.reason,
                    )
                AppLogger.e(TAG, "Privileged shell route rejected: $diagnostic")
                return CommandResult(false, "", diagnostic, -1)
            }

            val executor = ShellExecutorFactory.getExecutor(ctx, permissionLevel)
            val permissionStatus = executor.hasPermission()
            val executorAvailable = executor.isAvailable()
            if (!executorAvailable || !permissionStatus.granted) {
                val diagnostic =
                    buildPrivilegedDiagnostic(
                        configuredLevel = configuredLevel,
                        route = selection.route,
                        status = status,
                        executorAvailable = executorAvailable,
                        permissionStatus = permissionStatus,
                        failureReason = buildStrictUnavailableReason(
                            permissionLevel,
                            executorAvailable,
                            permissionStatus,
                        ),
                    )
                AppLogger.e(TAG, "Privileged shell executor unavailable: $diagnostic")
                return CommandResult(false, "", diagnostic, -1)
            }

            val result = executor.executeCommand(command, ShellIdentity.DEFAULT)
            // Arbitrary super_admin:shell output is user data. Text such as "Error:" can be a
            // legitimate grep/cat/echo result, so only the process result belongs in this generic
            // owner. Protocol-specific output checks (notably `am`) stay with that protocol.
            val success = result.success && result.exitCode == 0
            val diagnostic =
                buildPrivilegedDiagnostic(
                    configuredLevel = configuredLevel,
                    route = selection.route,
                    status = status,
                    executorAvailable = executorAvailable,
                    permissionStatus = permissionStatus,
                    failureReason = if (success) null else "command failed",
                )
            AppLogger.d(TAG, "Privileged shell route=${selection.route?.name ?: "NONE"}, success=$success, exitCode=${result.exitCode}")
            val stderr =
                if (success) {
                    result.stderr
                } else {
                    listOf(diagnostic, result.stderr, result.stdout)
                        .filter(String::isNotBlank)
                        .joinToString("\n")
                }
            return CommandResult(success, result.stdout, stderr, result.exitCode)
        }

        suspend fun startPrivilegedShellProcess(command: String): ShellProcess {
            val ctx = context ?: throw IllegalStateException("Context not initialized")
            val configuredLevel = getPreferredPermissionLevelCached()
            val status = ShizukuAuthorizer.getStatusSnapshot()
            val selection =
                selectPrivilegedShellRoute(
                    configuredLevel = configuredLevel,
                    shizukuServiceRunning = status.serviceRunning,
                    shizukuPermissionGranted = status.permissionGranted,
                    shizukuBinderAlive = status.binderAlive,
                )
            val permissionLevel =
                when (selection.route) {
                    PrivilegedShellRoute.ROOT -> AndroidPermissionLevel.ROOT
                    PrivilegedShellRoute.DEBUGGER -> AndroidPermissionLevel.DEBUGGER
                    null -> null
                }
            if (permissionLevel == null) {
                throw SecurityException(
                    buildPrivilegedDiagnostic(
                        configuredLevel,
                        selection.route,
                        status,
                        null,
                        null,
                        selection.reason,
                    ),
                )
            }
            val executor = ShellExecutorFactory.getExecutor(ctx, permissionLevel)
            val permissionStatus = executor.hasPermission()
            val executorAvailable = executor.isAvailable()
            if (!executorAvailable || !permissionStatus.granted) {
                throw SecurityException(
                    buildPrivilegedDiagnostic(
                        configuredLevel,
                        selection.route,
                        status,
                        executorAvailable,
                        permissionStatus,
                        buildStrictUnavailableReason(
                            permissionLevel,
                            executorAvailable,
                            permissionStatus,
                        ),
                    ),
                )
            }
            AppLogger.d(TAG, "Starting privileged shell route=${selection.route?.name ?: "NONE"}")
            return executor.startProcess(command)
        }

        /**
         * 封装执行命令的函数
         * @param command 要执行的命令
         * @return 命令执行结果
         */
        suspend fun executeShellCommand(command: String): CommandResult {
            return executeShellCommand(command, null)
        }

        suspend fun executeShellCommand(command: String, identityOverride: ShellIdentity?): CommandResult {
            val ctx = context ?: return CommandResult(false, "", "Context not initialized")

            // 如果调用方显式指定了身份，就直接向下传递；否则使用默认身份
            val identity = identityOverride ?: ShellIdentity.DEFAULT

            val preferredLevel = getPreferredPermissionLevelCached()
            val actualLevel = preferredLevel ?: AndroidPermissionLevel.STANDARD

            val preferredExecutor = ShellExecutorFactory.getExecutor(ctx, actualLevel)
            val permStatus = preferredExecutor.hasPermission()
            val executorAvailable = preferredExecutor.isAvailable()

            if (executorAvailable && permStatus.granted) {
                val result = preferredExecutor.executeCommand(command, identity)
                return CommandResult(result.success, result.stdout, result.stderr, result.exitCode)
            }

            val reason = buildStrictUnavailableReason(actualLevel, executorAvailable, permStatus)

            AppLogger.d(TAG, "Strict permission mode enabled. $reason")
            return CommandResult(false, "", reason, -1)
        }

        suspend fun startShellProcess(command: String): ShellProcess {
            val ctx = context ?: throw IllegalStateException("Context not initialized")

            val preferredLevel = getPreferredPermissionLevelCached()
            val actualLevel = preferredLevel ?: AndroidPermissionLevel.STANDARD
            val preferredExecutor = ShellExecutorFactory.getExecutor(ctx, actualLevel)
            val permStatus = preferredExecutor.hasPermission()
            val executorAvailable = preferredExecutor.isAvailable()

            if (executorAvailable && permStatus.granted) {
                return preferredExecutor.startProcess(command)
            }

            val reason = buildStrictUnavailableReason(actualLevel, executorAvailable, permStatus)

            AppLogger.d(TAG, "Strict permission mode enabled. $reason")
            throw SecurityException(reason)
        }
    }

    /** 命令执行结果数据类 */
    data class CommandResult(
            val success: Boolean,
            val stdout: String,
            val stderr: String = "",
            val exitCode: Int = -1
    )
}

enum class ShellIdentity {
    DEFAULT,
    APP,
    ROOT,
    SHELL
}
