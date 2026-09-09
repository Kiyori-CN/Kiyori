package com.ai.assistance.operit.data.audit

import java.io.File
import java.nio.file.Files

/** 完整写入同目录临时文件后再发布，失败不留下可被误认为导出结果的半成品。 */
internal fun writeConversationAuditExportFile(target: File, write: (File) -> Unit) {
    val absoluteTarget = target.absoluteFile
    val parent = checkNotNull(absoluteTarget.parentFile) { "Export file must have a parent directory" }
    val temporary = Files.createTempFile(parent.toPath(), ".audit-export-", ".tmp")
    var failure: Throwable? = null
    try {
        write(temporary.toFile())
        // 不覆盖已经存在的导出结果；调用者持有现有 exportMutex。
        Files.move(temporary, absoluteTarget.toPath())
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        try {
            Files.deleteIfExists(temporary)
        } catch (cleanupError: Exception) {
            val writeFailure = failure
            if (writeFailure != null) writeFailure.addSuppressed(cleanupError) else throw cleanupError
        }
    }
}
