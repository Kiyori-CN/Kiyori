package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.system.OsConstants
import java.nio.file.Path

/** 最小文件系统原语；不支持时明确失败，禁止退回可能覆盖目标的 rename/move。 */
internal object NativeNoReplaceCommit {
    init { System.loadLibrary("kiyori_fileops") }

    private external fun renameNoReplace(sourceUtf8: ByteArray, destinationUtf8: ByteArray): Int

    fun commit(source: Path, destination: Path) {
        val sourceText = source.toString()
        val destinationText = destination.toString()
        require('\u0000' !in sourceText && '\u0000' !in destinationText)
        // JNI Modified UTF-8 不能保真 emoji 路径，显式传递文件系统 UTF-8 字节。
        val error = renameNoReplace(sourceText.toByteArray(Charsets.UTF_8), destinationText.toByteArray(Charsets.UTF_8))
        if (error == 0) return
        val code = when (error) {
            OsConstants.EEXIST, OsConstants.ENOTEMPTY -> FileCopyErrorCode.CONFLICT
            OsConstants.ENOSYS, OsConstants.EINVAL, OsConstants.EOPNOTSUPP, OsConstants.EXDEV -> FileCopyErrorCode.UNSUPPORTED
            else -> FileCopyErrorCode.FAILED
        }
        val message = when (code) {
            FileCopyErrorCode.CONFLICT -> "目标已存在"
            FileCopyErrorCode.UNSUPPORTED -> if (error == OsConstants.EXDEV) "跨文件系统移动尚未支持，源项目保留；可先复制并核验" else "当前文件系统不支持原子不覆盖提交"
            else -> "文件提交失败（errno=$error），源项目保留"
        }
        throw LocalCopyException(code, message)
    }
}
