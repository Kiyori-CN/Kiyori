package com.ai.assistance.operit.util

internal data class TextSegmenterDiagnosticsSnapshot(
    val initializeCallCount: Int,
    val initializationCompletionCount: Int,
    val lastInitializationQueueWaitMs: Long?,
    val lastInitializationDurationMs: Long?,
    val lastInitializationThreadName: String?,
    val dictionaryLookupDurationMs: Long?,
    val prewarmDurationMs: Long?,
    val prewarmCompleted: Boolean,
    val searchCount: Int,
    val firstSearchDurationMs: Long?,
    val firstSearchThreadName: String?,
    val firstSearchBeforePrewarm: Boolean?,
) {
    init {
        require(initializeCallCount >= 0) {
            "initializeCallCount must not be negative"
        }
        require(initializationCompletionCount >= 0) {
            "initializationCompletionCount must not be negative"
        }
        require(searchCount >= 0) { "searchCount must not be negative" }
        require(lastInitializationQueueWaitMs == null || lastInitializationQueueWaitMs >= 0L) {
            "lastInitializationQueueWaitMs must not be negative"
        }
        require(lastInitializationDurationMs == null || lastInitializationDurationMs >= 0L) {
            "lastInitializationDurationMs must not be negative"
        }
        require(dictionaryLookupDurationMs == null || dictionaryLookupDurationMs >= 0L) {
            "dictionaryLookupDurationMs must not be negative"
        }
        require(prewarmDurationMs == null || prewarmDurationMs >= 0L) {
            "prewarmDurationMs must not be negative"
        }
        require(firstSearchDurationMs == null || firstSearchDurationMs >= 0L) {
            "firstSearchDurationMs must not be negative"
        }
    }

    fun summary(): String =
        "initializeCallCount=$initializeCallCount, " +
            "initializationCompletionCount=$initializationCompletionCount, " +
            "queueWaitMs=${lastInitializationQueueWaitMs ?: "none"}, " +
            "initializationDurationMs=${lastInitializationDurationMs ?: "none"}, " +
            "initializationThread=${lastInitializationThreadName ?: "none"}, " +
            "dictionaryLookupDurationMs=${dictionaryLookupDurationMs ?: "none"}, " +
            "prewarmDurationMs=${prewarmDurationMs ?: "none"}, " +
            "prewarmCompleted=$prewarmCompleted, " +
            "searchCount=$searchCount, " +
            "firstSearchDurationMs=${firstSearchDurationMs ?: "none"}, " +
            "firstSearchThread=${firstSearchThreadName ?: "none"}, " +
            "firstSearchBeforePrewarm=${firstSearchBeforePrewarm ?: "unknown"}"
}

/**
 * TextSegmenter 的初始化与首次真实搜索观测 owner。
 *
 * 这里不改变分词执行顺序，只记录锁等待、词典访问、预热和首次真实搜索的时间关系。
 */
internal class TextSegmenterDiagnostics {
    private var initializeCallCount = 0
    private var initializationCompletionCount = 0
    private var lastInitializationQueueWaitMs: Long? = null
    private var lastInitializationDurationMs: Long? = null
    private var lastInitializationThreadName: String? = null
    private var dictionaryLookupDurationMs: Long? = null
    private var prewarmDurationMs: Long? = null
    private var prewarmCompleted = false
    private var searchCount = 0
    private var firstSearchDurationMs: Long? = null
    private var firstSearchThreadName: String? = null
    private var firstSearchBeforePrewarm: Boolean? = null

    @Synchronized
    fun recordInitializeInvocation(): Long {
        initializeCallCount += 1
        return System.nanoTime()
    }

    @Synchronized
    fun recordInitializationLockAcquired(invocationStartNanos: Long) {
        lastInitializationQueueWaitMs =
            elapsedMillis(invocationStartNanos, System.nanoTime())
    }

    @Synchronized
    fun recordDictionaryLookup(durationNanos: Long) {
        dictionaryLookupDurationMs = nanosToMillis(durationNanos)
    }

    @Synchronized
    fun recordPrewarmCompleted(durationNanos: Long) {
        prewarmDurationMs = nanosToMillis(durationNanos)
        prewarmCompleted = true
    }

    @Synchronized
    fun recordInitializationCompleted(
        invocationStartNanos: Long,
        threadName: String,
    ) {
        initializationCompletionCount += 1
        lastInitializationDurationMs =
            elapsedMillis(invocationStartNanos, System.nanoTime())
        lastInitializationThreadName = threadName
    }

    @Synchronized
    fun recordSearchStarted(): Long {
        searchCount += 1
        if (firstSearchBeforePrewarm == null) {
            firstSearchBeforePrewarm = !prewarmCompleted
        }
        return System.nanoTime()
    }

    @Synchronized
    fun recordSearchCompleted(
        searchStartNanos: Long,
        threadName: String,
    ) {
        if (firstSearchDurationMs == null) {
            firstSearchDurationMs =
                elapsedMillis(searchStartNanos, System.nanoTime())
            firstSearchThreadName = threadName
        }
    }

    @Synchronized
    fun snapshot(): TextSegmenterDiagnosticsSnapshot =
        TextSegmenterDiagnosticsSnapshot(
            initializeCallCount = initializeCallCount,
            initializationCompletionCount = initializationCompletionCount,
            lastInitializationQueueWaitMs = lastInitializationQueueWaitMs,
            lastInitializationDurationMs = lastInitializationDurationMs,
            lastInitializationThreadName = lastInitializationThreadName,
            dictionaryLookupDurationMs = dictionaryLookupDurationMs,
            prewarmDurationMs = prewarmDurationMs,
            prewarmCompleted = prewarmCompleted,
            searchCount = searchCount,
            firstSearchDurationMs = firstSearchDurationMs,
            firstSearchThreadName = firstSearchThreadName,
            firstSearchBeforePrewarm = firstSearchBeforePrewarm,
        )

    private fun elapsedMillis(
        startNanos: Long,
        endNanos: Long,
    ): Long = nanosToMillis((endNanos - startNanos).coerceAtLeast(0L))

    private fun nanosToMillis(durationNanos: Long): Long =
        durationNanos.coerceAtLeast(0L) / 1_000_000L
}
