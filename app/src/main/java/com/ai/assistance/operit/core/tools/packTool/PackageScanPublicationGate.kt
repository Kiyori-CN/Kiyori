package com.ai.assistance.operit.core.tools.packTool

/** 包扫描在锁外执行；请求登记和结果发布共用 registry 锁，避免检查后被新请求穿透。 */
internal class PackageScanPublicationGate(private val registryLock: Any) {
    private var generation = 0L

    fun begin(): Long = synchronized(registryLock) {
        ++generation
    }

    fun publish(request: Long, publication: () -> Unit): Boolean = synchronized(registryLock) {
        if (request != generation) return@synchronized false
        publication()
        true
    }

    fun invalidate(mutation: () -> Unit) = synchronized(registryLock) {
        ++generation
        mutation()
    }
}
