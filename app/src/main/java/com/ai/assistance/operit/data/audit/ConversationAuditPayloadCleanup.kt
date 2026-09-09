package com.ai.assistance.operit.data.audit

internal const val AUDIT_PAYLOAD_CLEANUP_BATCH_SIZE = 128

/**
 * 调用者持有 payload 生命周期锁，并在 IO 上执行。
 * 文件先删、元数据按批提交；失败批次保留无引用元数据，后续回收可继续处理已不存在的文件。
 */
internal suspend fun <T> cleanupAuditPayloadBatches(
    payloads: List<T>,
    deleteFile: (T) -> Unit,
    deleteMetadataBatch: suspend (List<T>) -> Unit,
): Int {
    payloads.chunked(AUDIT_PAYLOAD_CLEANUP_BATCH_SIZE).forEach { batch ->
        batch.forEach(deleteFile)
        deleteMetadataBatch(batch)
    }
    return payloads.size
}
