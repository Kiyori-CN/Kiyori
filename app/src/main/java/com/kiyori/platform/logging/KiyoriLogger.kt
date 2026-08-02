package com.kiyori.platform.logging

import android.content.Context
import android.util.Log
import com.kiyori.platform.android.ApplicationContextAccess
import com.kiyori.platform.lifecycle.ApplicationStartupTime
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.regex.Pattern

/**
 * Kiyori 进程内唯一的系统日志、应用日志文件与 ToolPkg package log 写入 owner。
 *
 * 写入状态集中在此对象，旧 Operit FQCN 只保留无状态委派，以免同一条日志进入两套 executor
 * 或两个文件引用。Provider 早于 Application 初始化时可先绑定内部日志目录；公开目录的计算
 * 仍由现有路径 owner 提供，M-05B 不复制目录规则，也不持有第二份 Android Context。
 */
object KiyoriLogger {

    const val VERBOSE: Int = Log.VERBOSE
    const val DEBUG: Int = Log.DEBUG
    const val INFO: Int = Log.INFO
    const val WARN: Int = Log.WARN
    const val ERROR: Int = Log.ERROR
    const val ASSERT: Int = Log.ASSERT

    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "operit.log"
    private const val PACKAGE_LOG_DIR_NAME = "packageLogs"
    private const val TOOLPKG_LOG_TAG = "ToolPkg"
    private const val MAX_LOG_MESSAGE_CHARS = 12_000
    private const val MAX_LOG_THROWABLE_CHARS = 24_000

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val startupFileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    private val packageIdRegexes =
        listOf(
            Pattern.compile("""\btoolPkgId=([A-Za-z0-9._:-]+)\b"""),
            Pattern.compile("""\bpackage(?:/subpackage)?=([A-Za-z0-9._:-]+)\b"""),
            Pattern.compile("""\bcontainer=([A-Za-z0-9._:-]+)\b"""),
            Pattern.compile("""\btarget=([A-Za-z0-9._:-]+)\b"""),
        )
    private val scriptRegexes =
        listOf(
            Pattern.compile("""\bscript=([^\s,]+)"""),
            Pattern.compile("""\bpath=([^\s,]+)"""),
            Pattern.compile("""\bscreen=([^\s,]+)"""),
            Pattern.compile("""\bfunction=([A-Za-z0-9_.$:-]+)\b"""),
        )
    private val pluginRegexes =
        listOf(
            Pattern.compile("""\bplugin=([A-Za-z0-9._:-]+)\b"""),
            Pattern.compile("""\bpluginId=([A-Za-z0-9._:-]+)\b"""),
            Pattern.compile("""\bhookId=([A-Za-z0-9._:-]+)\b"""),
        )

    /**
     * 关闭时只停止文件写入；所有系统 [Log] 调用与返回值保持不变。
     */
    @Volatile
    var enableFileLogging: Boolean = true

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var packageLogFile: File? = null

    @Volatile
    private var boundFilesDir: File? = null

    @Volatile
    private var packageLogRootProvider: (() -> File)? = null

    private val fileLogExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "OperitAppLogger").apply {
                isDaemon = true
            }
        }

    @JvmStatic
    fun bindContext(
        context: Context,
        packageLogRootProvider: () -> File,
    ) {
        if (boundFilesDir == null) {
            boundFilesDir = context.applicationContext.filesDir
        }
        if (this.packageLogRootProvider == null) {
            this.packageLogRootProvider = packageLogRootProvider
        }
    }

    @JvmStatic
    fun v(tag: String, msg: String): Int {
        writeToFile(VERBOSE, tag, msg, null)
        return Log.v(tag, msg)
    }

    @JvmStatic
    fun v(tag: String, msg: String, tr: Throwable): Int {
        writeToFile(VERBOSE, tag, msg, tr)
        return Log.v(tag, msg, tr)
    }

    @JvmStatic
    fun d(tag: String, msg: String): Int {
        writeToFile(DEBUG, tag, msg, null)
        return Log.d(tag, msg)
    }

    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable): Int {
        writeToFile(DEBUG, tag, msg, tr)
        return Log.d(tag, msg, tr)
    }

    @JvmStatic
    fun i(tag: String, msg: String): Int {
        writeToFile(INFO, tag, msg, null)
        return Log.i(tag, msg)
    }

    @JvmStatic
    fun i(tag: String, msg: String, tr: Throwable): Int {
        writeToFile(INFO, tag, msg, tr)
        return Log.i(tag, msg, tr)
    }

    @JvmStatic
    fun w(tag: String, msg: String): Int {
        writeToFile(WARN, tag, msg, null)
        return Log.w(tag, msg)
    }

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable): Int {
        writeToFile(WARN, tag, msg, tr)
        return Log.w(tag, msg, tr)
    }

    @JvmStatic
    fun w(tag: String, tr: Throwable): Int {
        writeToFile(WARN, tag, "", tr)
        return Log.w(tag, tr)
    }

    @JvmStatic
    fun e(tag: String, msg: String): Int {
        writeToFile(ERROR, tag, msg, null)
        return Log.e(tag, msg)
    }

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable): Int {
        writeToFile(ERROR, tag, msg, tr)
        return Log.e(tag, msg, tr)
    }

    @JvmStatic
    fun wtf(tag: String, msg: String): Int {
        writeToFile(ASSERT, tag, msg, null)
        return Log.wtf(tag, msg)
    }

    @JvmStatic
    fun wtf(tag: String, msg: String, tr: Throwable): Int {
        writeToFile(ASSERT, tag, msg, tr)
        return Log.wtf(tag, msg, tr)
    }

    @JvmStatic
    fun wtf(tag: String, tr: Throwable): Int {
        writeToFile(ASSERT, tag, "", tr)
        return Log.wtf(tag, tr)
    }

    @JvmStatic
    fun isLoggable(tag: String, level: Int): Boolean = Log.isLoggable(tag, level)

    @JvmStatic
    fun println(priority: Int, tag: String, msg: String): Int {
        writeToFile(priority, tag, msg, null)
        return Log.println(priority, tag, msg)
    }

    @JvmStatic
    fun getStackTraceString(tr: Throwable): String =
        KiyoriLogTextFormatter.format(tr, MAX_LOG_THROWABLE_CHARS)

    @JvmStatic
    fun getLogFile(): File? = resolveLogFile()

    @JvmStatic
    fun resetLogFile() {
        try {
            val appContext: Context = ApplicationContextAccess.current
            val dir = File(appContext.filesDir, LOG_DIR_NAME)
            val file = File(dir, LOG_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
            logFile = null
            packageLogFile = null
        } catch (_: Throwable) {
            // 启动阶段的日志清理不得阻断 Application 初始化。
        }
    }

    private fun resolveLogFile(): File? {
        val existing = logFile
        if (existing != null) {
            return existing
        }

        return try {
            val filesDir = resolveFilesDir() ?: return null
            val dir = File(filesDir, LOG_DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            File(dir, LOG_FILE_NAME).also { file ->
                logFile = file
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolvePackageLogFile(): File? {
        val existing = packageLogFile
        if (existing != null) {
            return existing
        }

        return try {
            resolveFilesDir() ?: return null
            val rootProvider = packageLogRootProvider ?: return null
            val dir = File(rootProvider(), PACKAGE_LOG_DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val startupMs =
                ApplicationStartupTime.epochMillis.takeIf { it > 0L }
                    ?: System.currentTimeMillis()
            val fileName = startupFileDateFormat.format(Date(startupMs)) + ".log"
            File(dir, fileName).also { file ->
                packageLogFile = file
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveFilesDir(): File? =
        try {
            ApplicationContextAccess.current.filesDir
        } catch (_: Throwable) {
            boundFilesDir
        }

    private fun writeToFile(
        priority: Int,
        tag: String,
        msg: String,
        tr: Throwable?,
    ) {
        if (!enableFileLogging) {
            return
        }
        try {
            fileLogExecutor.execute {
                writeToFileSync(priority, tag, msg, tr)
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun writeToFileSync(
        priority: Int,
        tag: String,
        msg: String,
        tr: Throwable?,
    ) {
        if (!enableFileLogging) {
            return
        }
        val file = resolveLogFile() ?: return

        val time = dateFormat.format(Date())
        val normalizedMessage = normalizeLogMessage(msg)
        val throwableText =
            tr?.let {
                KiyoriLogTextFormatter.format(it, MAX_LOG_THROWABLE_CHARS)
            }
        val levelChar =
            when (priority) {
                VERBOSE -> 'V'
                DEBUG -> 'D'
                INFO -> 'I'
                WARN -> 'W'
                ERROR -> 'E'
                ASSERT -> 'A'
                else -> '?'
            }

        val builder =
            StringBuilder()
                .append(time)
                .append(" ")
                .append(levelChar)
                .append("/")
                .append(tag)
                .append(": ")
                .append(normalizedMessage)

        if (throwableText != null) {
            builder.append("\n").append(throwableText)
        }
        builder.append('\n')

        try {
            FileWriter(file, true).use { writer ->
                writer.write(builder.toString())
            }
        } catch (_: IOException) {
            // 文件写入失败不能递归调用 logger，否则会形成无限日志链。
        }

        writeToPackageLogIfNeeded(
            tag = tag,
            msg = normalizedMessage,
            throwableText = throwableText,
            time = time,
            levelChar = levelChar,
        )
    }

    private fun writeToPackageLogIfNeeded(
        tag: String,
        msg: String,
        throwableText: String?,
        time: String,
        levelChar: Char,
    ) {
        if (!shouldMirrorToPackageLog(tag, msg)) {
            return
        }
        val file = resolvePackageLogFile() ?: return
        val packageId = extractFirstMatch(msg, packageIdRegexes)
        val scriptId = extractFirstMatch(msg, scriptRegexes)
        val pluginId = extractFirstMatch(msg, pluginRegexes)

        val builder =
            StringBuilder()
                .append(time)
                .append(" ")
                .append(levelChar)
                .append("/")
                .append(TOOLPKG_LOG_TAG)
                .append(" ")

        if (!packageId.isNullOrBlank()) {
            builder.append("[PKG:").append(packageId).append("]")
        }
        if (!scriptId.isNullOrBlank()) {
            builder.append("[SCRIPT:").append(scriptId).append("]")
        }
        if (!pluginId.isNullOrBlank()) {
            builder.append("[PLUGIN:").append(pluginId).append("]")
        }
        if (builder.isNotEmpty() && builder[builder.length - 1] != ' ') {
            builder.append(" ")
        }
        builder.append(msg)

        if (throwableText != null) {
            builder.append("\n").append(throwableText)
        }
        builder.append('\n')

        try {
            FileWriter(file, true).use { writer ->
                writer.write(builder.toString())
            }
        } catch (_: IOException) {
            // package log 与主日志共用同一 executor；失败时不产生第二写入路径。
        }
    }

    private fun shouldMirrorToPackageLog(
        tag: String,
        @Suppress("UNUSED_PARAMETER") msg: String,
    ): Boolean = tag.equals(TOOLPKG_LOG_TAG, ignoreCase = true)

    private fun normalizeLogMessage(msg: String): String =
        KiyoriLogTextFormatter.truncateText(msg, MAX_LOG_MESSAGE_CHARS)

    private fun extractFirstMatch(
        text: String,
        patterns: List<Pattern>,
    ): String? {
        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val value = matcher.group(1)?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    return value
                }
            }
        }
        return null
    }
}
