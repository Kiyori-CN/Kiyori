package com.ai.assistance.operit.util

import android.content.Context
import com.kiyori.platform.logging.KiyoriLogger
import com.kiyori.platform.storage.KiyoriPaths
import java.io.File

/**
 * Operit 源码、上游补丁与现有测试使用的稳定日志入口。
 *
 * 该对象不持有 executor、Context 或文件状态；全部行为委派给唯一 [KiyoriLogger] owner。
 */
object AppLogger {

    const val VERBOSE: Int = KiyoriLogger.VERBOSE
    const val DEBUG: Int = KiyoriLogger.DEBUG
    const val INFO: Int = KiyoriLogger.INFO
    const val WARN: Int = KiyoriLogger.WARN
    const val ERROR: Int = KiyoriLogger.ERROR
    const val ASSERT: Int = KiyoriLogger.ASSERT

    var enableFileLogging: Boolean
        get() = KiyoriLogger.enableFileLogging
        set(value) {
            KiyoriLogger.enableFileLogging = value
        }

    @JvmStatic
    fun bindContext(context: Context) {
        KiyoriLogger.bindContext(context, KiyoriPaths::kiyoriRootDir)
    }

    @JvmStatic
    fun v(tag: String, msg: String): Int = KiyoriLogger.v(tag, msg)

    @JvmStatic
    fun v(tag: String, msg: String, tr: Throwable): Int =
        KiyoriLogger.v(tag, msg, tr)

    @JvmStatic
    fun d(tag: String, msg: String): Int = KiyoriLogger.d(tag, msg)

    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable): Int =
        KiyoriLogger.d(tag, msg, tr)

    @JvmStatic
    fun i(tag: String, msg: String): Int = KiyoriLogger.i(tag, msg)

    @JvmStatic
    fun i(tag: String, msg: String, tr: Throwable): Int =
        KiyoriLogger.i(tag, msg, tr)

    @JvmStatic
    fun w(tag: String, msg: String): Int = KiyoriLogger.w(tag, msg)

    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable): Int =
        KiyoriLogger.w(tag, msg, tr)

    @JvmStatic
    fun w(tag: String, tr: Throwable): Int = KiyoriLogger.w(tag, tr)

    @JvmStatic
    fun e(tag: String, msg: String): Int = KiyoriLogger.e(tag, msg)

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable): Int =
        KiyoriLogger.e(tag, msg, tr)

    @JvmStatic
    fun wtf(tag: String, msg: String): Int = KiyoriLogger.wtf(tag, msg)

    @JvmStatic
    fun wtf(tag: String, msg: String, tr: Throwable): Int =
        KiyoriLogger.wtf(tag, msg, tr)

    @JvmStatic
    fun wtf(tag: String, tr: Throwable): Int = KiyoriLogger.wtf(tag, tr)

    @JvmStatic
    fun isLoggable(tag: String, level: Int): Boolean =
        KiyoriLogger.isLoggable(tag, level)

    @JvmStatic
    fun println(priority: Int, tag: String, msg: String): Int =
        KiyoriLogger.println(priority, tag, msg)

    @JvmStatic
    fun getStackTraceString(tr: Throwable): String =
        KiyoriLogger.getStackTraceString(tr)

    @JvmStatic
    fun getLogFile(): File? = KiyoriLogger.getLogFile()

    @JvmStatic
    fun resetLogFile() = KiyoriLogger.resetLogFile()
}
