package com.ai.assistance.operit.core.tools.system.shell

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.ShellIdentity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import moe.shizuku.server.IRemoteProcess
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.IOException
import kotlinx.coroutines.isActive

private const val DEBUGGER_SHELL_TAG = "DebuggerShellExecutor"

/** 基于Shizuku的Shell命令执行器 实现DEBUGGER权限级别的命令执行 */
class DebuggerShellExecutor(private val context: Context) : ShellExecutor {
    companion object {
        private const val TAG = "DebuggerShellExecutor"
        private val serviceCache = ConcurrentHashMap<Int, IShizukuService>()

        /** 添加状态变更监听器 */
        fun addStateChangeListener(listener: () -> Unit) {
            ShizukuAuthorizer.addStateChangeListener(listener)
        }

        /** 移除状态变更监听器 */
        fun removeStateChangeListener(listener: () -> Unit) {
            ShizukuAuthorizer.removeStateChangeListener(listener)
        }

        /** 获取Shizuku启动说明 */
        fun getShizukuStartupInstructions(context: Context): String {
            return ShizukuAuthorizer.getShizukuStartupInstructions(context)
        }
    }

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.DEBUGGER

    override fun isAvailable(): Boolean {
        return ShizukuAuthorizer.isShizukuServiceRunning()
    }

    override fun hasPermission(): ShellExecutor.PermissionStatus {
        val hasPermission = ShizukuAuthorizer.hasShizukuPermission()
        return if (hasPermission) {
            ShellExecutor.PermissionStatus.granted()
        } else {
            ShellExecutor.PermissionStatus.denied(ShizukuAuthorizer.getPermissionErrorMessage())
        }
    }

    override fun initialize() {
        ShizukuAuthorizer.initialize()
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        ShizukuAuthorizer.requestShizukuPermission(onResult)
    }

    /**
     * 检查Shizuku是否已安装
     * @return 是否已安装Shizuku
     */
    fun isShizukuInstalled(): Boolean {
        return ShizukuAuthorizer.isShizukuInstalled(context)
    }

    override suspend fun executeCommand(
        command: String,
        identity: ShellIdentity
    ): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                val permStatus = hasPermission()
                if (!permStatus.granted) {
                    return@withContext ShellExecutor.CommandResult(false, "", permStatus.reason)
                }

                // 此接口接收 Shell 源码而非 argv。赋值、内建命令、引号和转义必须由同一个
                // 系统 Shell 解释；猜测“普通命令”会把 LC_ALL=C 当成可执行文件。
                executeWithShell(command)
            }

    /** 通过系统 Shell 执行命令；注入服务仅用于验证真实进程边界，不改变外层权限检查。 */
    internal suspend fun executeWithShell(
        command: String,
        service: IShizukuService? = getShizukuService(),
    ): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                var process: IRemoteProcess? = null
                var inputStream: ParcelFileDescriptor? = null
                var errorStream: ParcelFileDescriptor? = null
                var processCompleted = false
                var backgroundProcess = false
                try {
                    if (service == null) return@withContext ShellExecutor.CommandResult(
                                            false,
                                            "",
                                            "Shizuku service not available"
                                    )

                    // 如果命令以单个'&'结尾（后台运行），我们只负责启动，不阻塞等待
                    val trimmedForBg = command.trimEnd()
                    val isBackground =
                            trimmedForBg.endsWith("&") && !trimmedForBg.endsWith("&&")

                    val shellArgs = debuggerShellArguments(command)

                    // 创建进程
                    val remoteProcess = service.newProcess(shellArgs, null, null)
                    process = remoteProcess
                    if (remoteProcess == null) {
                        return@withContext ShellExecutor.CommandResult(false, "", "Failed to create process")
                    }
                    // 处理输入输出流
                    inputStream = remoteProcess.getInputStream()
                    errorStream = remoteProcess.getErrorStream()

                    if (isBackground) {
                        backgroundProcess = true
                        AppLogger.d(TAG, "Detected background shell command (ending with '&'), not waiting for process")
                        // 对于后台命令，我们不读取输出，也不等待退出，只要进程创建成功就视为成功
                        try {
                            inputStream?.close()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error closing input stream for background shell command", e)
                        }

                        try {
                            errorStream?.close()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error closing error stream for background shell command", e)
                        }
                        inputStream = null
                        errorStream = null
                        processCompleted = true

                        return@withContext ShellExecutor.CommandResult(
                                true,
                                "",
                                "",
                                0
                        )
                    }

                    // Drain both pipes together to avoid a child process blocking on a full
                    // stderr pipe while stdout is being consumed.
                    val (stdout, stderr) = readProcessStreams(inputStream, errorStream)
                    inputStream = null
                    errorStream = null
                    val exitCode = remoteProcess.waitFor()
                    processCompleted = true

                    // 确定命令是否成功
                    val success =
                            when {
                                // 如果命令包含grep，即使没有找到匹配也认为成功
                                command.contains("grep") -> exitCode == 0 || exitCode == 1

                                // 对其他命令，只有exitCode=0才算成功
                                else -> exitCode == 0
                            }

                    return@withContext ShellExecutor.CommandResult(
                            success,
                            stdout,
                            stderr,
                            exitCode
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error executing shell command", e)
                    return@withContext ShellExecutor.CommandResult(false, "", "Error: ${e.message}")
                } finally {
                    try {
                        inputStream?.close()
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error closing input stream in shell cleanup", e)
                    }
                    try {
                        errorStream?.close()
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error closing error stream in shell cleanup", e)
                    }
                    if (!processCompleted && !backgroundProcess) {
                        runCatching { process?.destroy() }
                            .onFailure { error ->
                                AppLogger.e(TAG, "Error destroying failed Shizuku shell process", error)
                            }
                    }
                }
            }

    private suspend fun readProcessStreams(
        inputStream: ParcelFileDescriptor?,
        errorStream: ParcelFileDescriptor?,
    ): Pair<String, String> = coroutineScope {
        val stdout = async(Dispatchers.IO) { readProcessStream(inputStream) }
        val stderr = async(Dispatchers.IO) { readProcessStream(errorStream) }
        stdout.await() to stderr.await()
    }

    private fun readProcessStream(descriptor: ParcelFileDescriptor?): String {
        if (descriptor == null) {
            return ""
        }
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.readText()
            }
        }
    }

    /** 获取Shizuku服务 */
    private fun getShizukuService(): IShizukuService? {
        try {
            val connection = ShizukuAuthorizer.getOrResolveShizukuConnection() ?: return null

            // 检查缓存的服务是否可用
            val cached = serviceCache[connection.uid]
            if (cached != null) {
                val isCachedAlive =
                        try {
                            cached.asBinder().pingBinder()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error pinging cached binder", e)
                            false
                        }

                if (isCachedAlive) {
                    return cached
                } else {
                    AppLogger.d(TAG, "Cached Shizuku service is dead, removing from cache")
                    serviceCache.remove(connection.uid)
                }
            }

            val service = IShizukuService.Stub.asInterface(connection.binder)
            if (service == null) {
                AppLogger.d(TAG, "Failed to create Shizuku service interface")
                return null
            }

            AppLogger.d(TAG, "Creating new Shizuku service interface")
            serviceCache[connection.uid] = service
            return service
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting Shizuku service", e)
            return null
        }
    }

    override suspend fun startProcess(command: String): ShellProcess {
        if (!hasPermission().granted) {
            throw SecurityException("Shizuku permission not granted.")
        }
        val service = getShizukuService() ?: throw IOException("Shizuku service not available")
        return ShizukuShellProcess(service, command)
    }
}

/**
 * 使用 Shizuku 实现的 ShellProcess。
 */
private class ShizukuShellProcess(
    private val service: IShizukuService,
    private val command: String
) : ShellProcess {
    private val process: IRemoteProcess

    init {
        val shellArgs = debuggerShellArguments(command)
        process = service.newProcess(shellArgs, null, null)
            ?: throw IOException("Failed to create Shizuku process")
    }

    private val inputStream: ParcelFileDescriptor by lazy {
        process.getInputStream()
    }

    private val errorStream: ParcelFileDescriptor by lazy {
        process.getErrorStream()
    }

    override val stdout: Flow<String> by lazy {
        flowFromStream(ParcelFileDescriptor.AutoCloseInputStream(inputStream))
    }

    override val stderr: Flow<String> by lazy {
        flowFromStream(ParcelFileDescriptor.AutoCloseInputStream(errorStream))
    }

    override val isAlive: Boolean
        get() = try {
            process.alive()
        } catch (error: Exception) {
            AppLogger.w(DEBUGGER_SHELL_TAG, "Unable to query Shizuku process state", error)
            false
        }

    override fun destroy() {
        try {
            process.destroy()
        } catch (error: Exception) {
            AppLogger.e(DEBUGGER_SHELL_TAG, "Unable to destroy Shizuku process", error)
        } finally {
            runCatching { inputStream.close() }
                .onFailure {
                    error -> AppLogger.e(DEBUGGER_SHELL_TAG, "Error closing process stdout", error)
                }
            runCatching { errorStream.close() }
                .onFailure {
                    error -> AppLogger.e(DEBUGGER_SHELL_TAG, "Error closing process stderr", error)
                }
        }
    }

    override suspend fun waitFor(): Int = withContext(Dispatchers.IO) {
        process.waitFor()
    }
}

private fun flowFromStream(inputStream: InputStream): Flow<String> = callbackFlow {
    val job = launch(Dispatchers.IO) {
        try {
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    if (isActive) {
                        trySend(line)
                    }
                }
            }
        } catch (e: IOException) {
            // This is expected when the process is destroyed
        } finally {
            close()
        }
    }
    awaitClose { job.cancel() }
}

/** 保留命令原文作为单个参数，避免重解析路径中的空格、引号、反斜杠或环境变量赋值。 */
internal fun debuggerShellArguments(command: String): Array<String> =
    arrayOf("/system/bin/sh", "-c", command)
