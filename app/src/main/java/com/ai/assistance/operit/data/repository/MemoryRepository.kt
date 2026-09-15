package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.audit.ConversationAuditRedactor
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemoryLibraryPolicy
import com.ai.assistance.operit.data.model.MemoryLink
import com.ai.assistance.operit.data.model.MemoryProperty
import com.ai.assistance.operit.data.model.MemoryTag
import com.ai.assistance.operit.data.model.MemoryTag_
import com.ai.assistance.operit.data.model.Memory_
import com.ai.assistance.operit.data.model.DocumentChunk
import com.ai.assistance.operit.data.model.DocumentChunk_
import java.util.concurrent.ConcurrentHashMap
import com.ai.assistance.operit.data.model.Embedding
import com.ai.assistance.operit.data.model.CloudEmbeddingConfig
import com.ai.assistance.operit.data.model.DimensionCount
import com.ai.assistance.operit.data.model.EmbeddingDimensionUsage
import com.ai.assistance.operit.data.model.EmbeddingRebuildProgress
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.services.CloudEmbeddingService
import com.kiyori.capability.ai.memory.MemoryGraph
import com.kiyori.capability.ai.memory.MemoryGraphEdge
import com.kiyori.capability.ai.memory.MemoryGraphNode
import com.kiyori.capability.ai.memory.MemoryGraphNodeType
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.objectbox.query.QueryCondition
import java.util.UUID
import java.util.Date
import java.util.Locale
import com.ai.assistance.operit.data.model.MemoryExportData
import com.ai.assistance.operit.data.model.SerializableMemory
import com.ai.assistance.operit.data.model.SerializableLink
import com.ai.assistance.operit.data.model.ImportStrategy
import com.ai.assistance.operit.data.model.MemoryImportResult
import com.ai.assistance.operit.data.model.MemorySearchDebugCandidate
import com.ai.assistance.operit.data.model.MemorySearchDebugInfo
import com.ai.assistance.operit.data.model.MemoryScoreMode
import com.ai.assistance.operit.util.OperitPaths
import com.ai.assistance.operit.util.TextSegmenter
import com.ai.assistance.operit.util.vector.IndexItem
import com.ai.assistance.operit.util.vector.VectorIndexManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

/**
 * Repository for handling Memory data operations. It abstracts the data source (ObjectBox) from the
 * rest of the application.
 */
class MemoryRepository(private val context: Context, profileId: String) {

    companion object {
        /** Represents a strong link, e.g., "A is a B". */
        const val STRONG_LINK = 1.0f

        /** Represents a medium-strength link, e.g., "A is related to B". */
        const val MEDIUM_LINK = 0.7f

        /** Represents a weak link, e.g., "A is sometimes associated with B". */
        const val WEAK_LINK = 0.3f
        private val rebuildLocks = ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
        private val indexLocks = ConcurrentHashMap<String, Any>()
        private const val DANGLING_LINK_CLEANUP_INTERVAL_MS = 30_000L

        /**
         * 「未归类」在工具参数与结果渲染中的稳定标识。目录身份不能依赖界面语言：
         * 用本地化文案当路径会让同一条记忆在切换语言后落到另一个目录。
         */
        const val ROOT_FOLDER_TOKEN = "(root)"

        /** 目录占位记忆的稳定来源标识；标题可能来自旧版本的本地化文案。 */
        const val FOLDER_PLACEHOLDER_SOURCE = "folder_placeholder"
        private const val FOLDER_PLACEHOLDER_TITLE = ".folder_placeholder"
        private const val SEARCH_RRF_K = 60.0
        private const val SEARCH_KEYWORD_COVERAGE_BONUS = 0.6
        private const val SEARCH_RELEVANCE_THRESHOLD = 0.025
        private val INDEX_KEY_SANITIZE_REGEX = Regex("[^a-zA-Z0-9._-]")

        fun normalizeFolderPath(folderPath: String?): String? {
            val raw = folderPath?.trim() ?: return null
            if (raw.isBlank()) return null

            val parts = raw.replace('\\', '/')
                .split('/')
                .map { it.trim() }
                .filter { it.isNotBlank() }

            return parts.takeIf { it.isNotEmpty() }?.joinToString("/")
        }

        /** 记忆库页面渲染的实体；不含 MemoryAutoSaveCandidate。 */
        private val OBSERVED_ENTITIES = setOf<Class<*>>(
            Memory::class.java, MemoryLink::class.java, DocumentChunk::class.java,
            MemoryTag::class.java, MemoryProperty::class.java,
        )

        fun validateFolderPath(path: String) {
            require(path.none { it.isISOControl() } && path.replace('\\', '/').split('/').none { it.trim() in listOf(".", "..") }) {
                "文件夹路径不能包含控制字符、. 或 .."
            }
        }

        private fun sanitizeIndexKey(raw: String): String {
            val normalized = raw.trim().ifBlank { "default" }
            return MemoryLibraryPolicy.digest(normalized).take(24)
        }
    }

    private val rebuildMutex = rebuildLocks.computeIfAbsent(profileId) { kotlinx.coroutines.sync.Mutex() }
    private val indexLock = indexLocks.computeIfAbsent(profileId) { Any() }
    private val store = ObjectBoxManager.get(context, profileId)
    private val memoryBox: Box<Memory> = store.boxFor()
    private val tagBox = store.boxFor<MemoryTag>()
    private val linkBox = store.boxFor<MemoryLink>()
    private val chunkBox = store.boxFor<DocumentChunk>()

    private val searchSettingsPreferences = MemorySearchSettingsPreferences(context, profileId)
    private val cloudEmbeddingService = CloudEmbeddingService(context)
    private val sanitizedProfileKey = sanitizeIndexKey(profileId)
    @Volatile
    private var lastDanglingCleanupAtMs: Long = 0L

    /**
     * 直接观察同一 BoxStore，覆盖 UI、工具、导入和备份写入；取消收集即释放订阅。
     * 只转发记忆库自己渲染的实体：自动整理候选项在后台频繁写入，放行会让页面反复重查。
     */
    fun observeChanges(): Flow<Unit> = callbackFlow {
        val subscription = store.subscribe().onlyChanges().observer { changed ->
            if (changed in OBSERVED_ENTITIES) trySend(Unit)
        }
        awaitClose { subscription.cancel() }
    }.buffer(Channel.CONFLATED)

    private fun normalizeStoredFolderPath(path: String?): String? =
        if (path?.trim() == ROOT_FOLDER_TOKEN) null else normalizeFolderPath(path)

    private suspend fun generateEmbedding(text: String, config: CloudEmbeddingConfig): Embedding? {
        return cloudEmbeddingService.generateEmbedding(config, text)
    }

    private fun markEmbedding(memory: Memory, config: CloudEmbeddingConfig) {
        memory.embeddingModelKey = if (memory.embedding == null) "" else MemoryLibraryPolicy.modelKey(config)
        memory.embeddingContentHash = if (memory.embedding == null) "" else MemoryLibraryPolicy.digest(generateTextForEmbedding(memory))
    }

    private fun markEmbedding(chunk: DocumentChunk, config: CloudEmbeddingConfig) {
        chunk.embeddingModelKey = if (chunk.embedding == null) "" else MemoryLibraryPolicy.modelKey(config)
        chunk.embeddingContentHash = if (chunk.embedding == null) "" else MemoryLibraryPolicy.digest(chunk.content)
    }

    private fun hasCurrentEmbedding(memory: Memory, config: CloudEmbeddingConfig = loadCloudEmbeddingConfig()): Boolean =
        MemoryLibraryPolicy.compatible(memory.embedding, memory.embeddingModelKey, memory.embeddingContentHash, config, generateTextForEmbedding(memory))

    private fun hasCurrentEmbedding(chunk: DocumentChunk, config: CloudEmbeddingConfig = loadCloudEmbeddingConfig()): Boolean =
        MemoryLibraryPolicy.compatible(chunk.embedding, chunk.embeddingModelKey, chunk.embeddingContentHash, config, chunk.content)

    private fun cosineSimilarity(left: Embedding, right: Embedding): Float {
        val leftVector = left.vector
        val rightVector = right.vector

        if (leftVector.isEmpty() || rightVector.isEmpty() || leftVector.size != rightVector.size) {
            return 0f
        }

        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0

        for (index in leftVector.indices) {
            val leftValue = leftVector[index].toDouble()
            val rightValue = rightVector[index].toDouble()
            dot += leftValue * rightValue
            leftNorm += leftValue * leftValue
            rightNorm += rightValue * rightValue
        }

        if (leftNorm <= 0.0 || rightNorm <= 0.0) {
            return 0f
        }

        return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).toFloat()
    }

    /**
     * 对关键词做“切碎扩展”：
     * - 保留原词
     * - 使用 Jieba 分词补充分片
     *
     * 目的：在未开启语义检索时，提高中文长句与标题之间的匹配召回。
     */
    private fun expandKeywordToken(token: String): Set<String> {
        val normalized = token.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return emptySet()

        val expanded = linkedSetOf<String>()
        if (shouldKeepRawLexicalToken(normalized)) {
            expanded.add(normalized)
        }
        if (normalized.contains('*')) {
            return expanded
        }

        // 优先使用项目内已集成的 Jieba 分词，提升中文检索召回质量。
        val jiebaTokens = TextSegmenter.segment(normalized)
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { shouldKeepRawLexicalToken(it) }
        expanded.addAll(jiebaTokens)

        return expanded.filterTo(linkedSetOf()) { shouldKeepRawLexicalToken(it) }
    }

    private data class TitleMatchCandidate(
        val memory: Memory,
        val matchedTokenCount: Int
    )

    private data class SearchScoreParts(
        var matchedKeywordTokenCount: Int = 0,
        var keywordScore: Double = 0.0,
        var tagScore: Double = 0.0,
        var reverseContainmentScore: Double = 0.0,
        var semanticScore: Double = 0.0,
        var edgeScore: Double = 0.0
    )

    private data class SearchComputationResult(
        val memories: List<Memory>,
        val debug: MemorySearchDebugInfo
    )

    private data class ResolvedSearchWeights(
        val scoreMode: MemoryScoreMode,
        val effectiveKeywordWeight: Double,
        val effectiveTagWeight: Double,
        val effectiveSemanticWeight: Float,
        val effectiveEdgeWeight: Double,
        val semanticKeywordNormFactor: Double
    )

    private fun splitSearchKeywords(query: String): List<String> {
        return if (query.contains('|')) {
            query.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            query.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    private fun buildLexicalQueryTokens(query: String, keywords: List<String>): List<String> {
        val merged = linkedSetOf<String>()
        if (keywords.isNotEmpty()) {
            keywords.forEach { merged.addAll(expandKeywordToken(it)) }
        } else {
            merged.addAll(expandKeywordToken(query))
        }
        return merged
            .filter { shouldKeepRawLexicalToken(it) }
            .distinct()
            .sortedByDescending { it.length }
            .take(32)
    }

    private fun isFolderPlaceholderMemory(memory: Memory): Boolean {
        val title = memory.title.trim()
        return memory.source == FOLDER_PLACEHOLDER_SOURCE || title == FOLDER_PLACEHOLDER_TITLE ||
            title == context.getString(R.string.memory_repository_folder_description_title)
    }

    private fun resolveSearchWeights(
        scoreMode: MemoryScoreMode,
        keywordWeight: Float,
        tagWeight: Float,
        semanticWeight: Float,
        edgeWeight: Float,
        keywordCount: Int
    ): ResolvedSearchWeights {
        val normalizedKeywordWeight = keywordWeight.coerceAtLeast(0.0f).toDouble()
        val normalizedTagWeight = tagWeight.coerceAtLeast(0.0f).toDouble()
        val normalizedSemanticWeight = semanticWeight.coerceAtLeast(0.0f)
        val normalizedEdgeWeight = edgeWeight.coerceAtLeast(0.0f).toDouble()
        val (modeKeywordMultiplier, modeSemanticMultiplier, modeEdgeMultiplier) = when (scoreMode) {
            MemoryScoreMode.BALANCED -> Triple(1.0, 1.0, 1.0)
            MemoryScoreMode.KEYWORD_FIRST -> Triple(1.3, 0.8, 0.9)
            MemoryScoreMode.SEMANTIC_FIRST -> Triple(0.8, 1.3, 1.1)
        }
        val semanticKeywordNormFactor =
            if (keywordCount > 0) 1.0 / sqrt(keywordCount.toDouble()) else 1.0

        return ResolvedSearchWeights(
            scoreMode = scoreMode,
            effectiveKeywordWeight = normalizedKeywordWeight * modeKeywordMultiplier,
            effectiveTagWeight = normalizedTagWeight * modeKeywordMultiplier,
            effectiveSemanticWeight = normalizedSemanticWeight * modeSemanticMultiplier.toFloat(),
            effectiveEdgeWeight = normalizedEdgeWeight * modeEdgeMultiplier,
            semanticKeywordNormFactor = semanticKeywordNormFactor
        )
    }

    private fun computeRrfBaseScore(rank: Int): Double {
        return 1.0 / (SEARCH_RRF_K + rank)
    }

    private fun computeKeywordCoverageMultiplier(
        matchedTokenCount: Int,
        totalTokenCount: Int
    ): Double {
        if (matchedTokenCount <= 0 || totalTokenCount <= 0) return 1.0
        val coverageRatio = matchedTokenCount.toDouble() / totalTokenCount.toDouble()
        return 1.0 + (SEARCH_KEYWORD_COVERAGE_BONUS * coverageRatio)
    }

    private fun computeSemanticWeightedScore(
        rank: Int,
        baseImportance: Float,
        similarity: Float,
        resolvedWeights: ResolvedSearchWeights
    ): Double {
        val normalizedImportance = baseImportance.coerceAtLeast(0.0f).toDouble()
        val rankScore = computeRrfBaseScore(rank)
        val similarityScore = similarity * resolvedWeights.effectiveSemanticWeight
        return ((rankScore * sqrt(normalizedImportance)) + similarityScore) *
            resolvedWeights.semanticKeywordNormFactor
    }

    private fun vectorIndexDir(): File = OperitPaths.vectorIndexDir(context)

    private fun currentProfileMemoryIndexFiles(): List<File> {
        val prefix = "memory_hnsw_${sanitizedProfileKey}_"
        return vectorIndexDir()
            .listFiles()
            ?.filter { file -> file.name.startsWith(prefix) && file.name.endsWith(".idx") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    private fun currentProfileDocumentIndexFiles(): List<File> {
        val prefix = "doc_index_${sanitizedProfileKey}_"
        return vectorIndexDir()
            .listFiles()
            ?.filter { file -> file.name.startsWith(prefix) && file.name.endsWith(".hnsw") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    private fun memoryIndexFileForDimension(dimension: Int): File {
        return File(vectorIndexDir(), "memory_hnsw_${sanitizedProfileKey}_${MemoryLibraryPolicy.modelKey(loadCloudEmbeddingConfig()).take(16)}_${dimension}.idx")
    }

    private fun documentIndexFile(memoryId: Long, dimension: Int): File {
        return File(vectorIndexDir(), "doc_index_${sanitizedProfileKey}_${memoryId}_${MemoryLibraryPolicy.modelKey(loadCloudEmbeddingConfig()).take(16)}_${dimension}.hnsw")
    }

    private fun parseMemoryIndexDimension(file: File): Int? {
        val prefix = "memory_hnsw_${sanitizedProfileKey}_"
        val name = file.name
        if (!name.startsWith(prefix) || !name.endsWith(".idx")) return null
        return name
            .removePrefix(prefix)
            .removeSuffix(".idx")
            .substringAfterLast('_')
            .toIntOrNull()
    }

    private fun deleteIndexFileIfExists(file: File?) {
        if (file != null && file.exists()) {
            require(file.canonicalFile.parentFile == vectorIndexDir().canonicalFile) { "索引路径超出应用向量目录" }
            check(file.delete()) { "无法更新向量索引，请检查存储空间" }
        }
    }

    private fun loadChunksForDocument(memory: Memory): List<DocumentChunk> {
        memory.documentChunks.reset()
        return memory.documentChunks.sortedBy { it.chunkIndex }
    }

    private fun countDocumentChunkIndexableItems(memory: Memory): Int {
        if (!memory.isDocumentNode) return 0
        val chunks = loadChunksForDocument(memory)
        val targetDimension = resolveDocumentIndexDimension(chunks, forcedDimension = null) ?: return 0
        return chunks.count { chunk ->
            val vector = chunk.embedding?.vector
            vector != null && vector.isNotEmpty() && vector.size == targetDimension
        }
    }

    private fun createMemoryIndexItem(memory: Memory): IndexItem<Long, Long>? {
        if (!hasCurrentEmbedding(memory)) return null
        val embedding = memory.embedding ?: return null
        if (embedding.vector.isEmpty()) return null
        return IndexItem(
            id = memory.id,
            vector = embedding.vector,
            version = memory.updatedAt.time,
            value = memory.id
        )
    }

    private fun createChunkIndexItem(chunk: DocumentChunk): IndexItem<Long, Long>? {
        if (!hasCurrentEmbedding(chunk)) return null
        val embedding = chunk.embedding ?: return null
        if (embedding.vector.isEmpty()) return null
        return IndexItem(
            id = chunk.id,
            vector = embedding.vector,
            version = chunk.id,
            value = chunk.id
        )
    }

    // HNSW 的删除会保留 tombstone，直接增量 add 容易命中容量异常，因此按维度重建活跃记忆索引。
    private fun rebuildMemoryVectorIndexForDimension(
        dimension: Int,
        excludedMemoryId: Long? = null
    ): Int {
        return synchronized(indexLock) {
            if (dimension <= 0) return 0

            val items = memoryBox.all
                .asSequence()
                .filter { memory -> excludedMemoryId == null || memory.id != excludedMemoryId }
                .mapNotNull { memory ->
                    val itemDimension = memory.embedding?.vector?.size ?: return@mapNotNull null
                    if (itemDimension != dimension) return@mapNotNull null
                    createMemoryIndexItem(memory)
                }
                .toList()

            val indexFile = memoryIndexFileForDimension(dimension)
            deleteIndexFileIfExists(indexFile)
            if (items.isEmpty()) return 0

            val manager = VectorIndexManager<IndexItem<Long, Long>, Long>(
                dimensions = dimension,
                maxElements = items.size.coerceAtLeast(1),
                indexFile = indexFile
            )
            items.forEach { item ->
                manager.addItem(item)
            }
            manager.save()
            manager.close()
            return items.size
        }
    }

    private fun rebuildAffectedMemoryVectorIndices(
        dimensions: Collection<Int?>,
        excludedMemoryId: Long? = null
    ): Unit {
        return synchronized(indexLock) {
            dimensions
                .asSequence()
                .mapNotNull { it?.takeIf { dimension -> dimension > 0 } }
                .distinct()
                .forEach { dimension ->
                    rebuildMemoryVectorIndexForDimension(
                        dimension = dimension,
                        excludedMemoryId = excludedMemoryId
                    )
                }
        }
    }

    private fun addMemoryToIndexInternal(memory: Memory, previousDimension: Int? = null): Unit {
        return synchronized(indexLock) {
            // 仅使受影响派生索引失效，下一次查询重建一次；普通 CRUD 在同一锁内先失效后提交。
            // 批量写入不再对每条记录重建整库，避免 O(n²) 写放大。
            listOf(previousDimension, memory.embedding?.vector?.size).filterNotNull().distinct().forEach {
                deleteIndexFileIfExists(memoryIndexFileForDimension(it))
            }
            if (memory.isDocumentNode) {
                currentProfileDocumentIndexFiles().filter { it.name.startsWith("doc_index_${sanitizedProfileKey}_${memory.id}_") }
                    .forEach(::deleteIndexFileIfExists)
            }
        }
    }

    private fun removeMemoryFromIndexInternal(memory: Memory, memoryAlreadyRemoved: Boolean = false): Unit {
        return synchronized(indexLock) {
            rebuildAffectedMemoryVectorIndices(
                dimensions = listOf(memory.embedding?.vector?.size),
                excludedMemoryId = memory.id.takeUnless { memoryAlreadyRemoved }
            )
            if (!memory.isDocumentNode) return

            deleteIndexFileIfExists(memory.chunkIndexFilePath?.let(::File))
            val storedMemory = memoryBox.get(memory.id)
            if (storedMemory != null && storedMemory.chunkIndexFilePath != null) {
                storedMemory.chunkIndexFilePath = null
                memoryBox.put(storedMemory)
            }
        }
    }

    private fun rebuildAllMemoryVectorIndices(onItemIndexed: (() -> Unit)? = null): Int {
        return synchronized(indexLock) {
            val memoriesByDimension = memoryBox.all
                .mapNotNull { memory ->
                    val item = createMemoryIndexItem(memory) ?: return@mapNotNull null
                    val dimension = memory.embedding?.vector?.size ?: return@mapNotNull null
                    dimension to item
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second }
                )

            currentProfileMemoryIndexFiles().forEach { deleteIndexFileIfExists(it) }
            var indexedCount = 0

            memoriesByDimension.forEach { (dimension, items) ->
                val manager = VectorIndexManager<IndexItem<Long, Long>, Long>(
                    dimensions = dimension,
                    maxElements = items.size.coerceAtLeast(1),
                    indexFile = memoryIndexFileForDimension(dimension)
                )
                items.forEach { item ->
                    manager.addItem(item)
                    indexedCount += 1
                    onItemIndexed?.invoke()
                }
                manager.save()
                manager.close()
            }

            return indexedCount
        }
    }

    private fun resolveDocumentIndexDimension(chunks: List<DocumentChunk>, forcedDimension: Int?): Int? {
        if (forcedDimension != null) {
            return chunks
                .any { it.embedding?.vector?.size == forcedDimension }
                .takeIf { it }
                ?.let { forcedDimension }
        }

        return chunks
            .mapNotNull { it.embedding?.vector?.size?.takeIf { dimension -> dimension > 0 } }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    private fun rebuildDocumentChunkIndex(
        memory: Memory,
        forcedDimension: Int? = null,
        onItemIndexed: (() -> Unit)? = null
    ): Int {
        return synchronized(indexLock) {
            if (!memory.isDocumentNode) return 0

            val chunks = loadChunksForDocument(memory)
            val hasEmbeddings = chunks.any { chunk ->
                val vector = chunk.embedding?.vector
                vector != null && vector.isNotEmpty()
            }
            val targetDimension = resolveDocumentIndexDimension(chunks, forcedDimension)
            if (targetDimension == null) {
                if (forcedDimension != null && hasEmbeddings) {
                    return 0
                }
                deleteIndexFileIfExists(memory.chunkIndexFilePath?.let(::File))
                if (memory.chunkIndexFilePath != null) {
                    memory.chunkIndexFilePath = null
                    memoryBox.put(memory)
                }
                return 0
            }

            val compatibleItems = chunks
                .mapNotNull { chunk ->
                    val embedding = chunk.embedding ?: return@mapNotNull null
                    if (embedding.vector.size != targetDimension) return@mapNotNull null
                    createChunkIndexItem(chunk)
                }

            val targetFile = documentIndexFile(memory.id, targetDimension)
            if (compatibleItems.isEmpty()) {
                deleteIndexFileIfExists(memory.chunkIndexFilePath?.let(::File))
                if (memory.chunkIndexFilePath != null) {
                    memory.chunkIndexFilePath = null
                    memoryBox.put(memory)
                }
                return 0
            }

            val previousFile = memory.chunkIndexFilePath?.let(::File)
            if (previousFile?.absolutePath != targetFile.absolutePath) {
                deleteIndexFileIfExists(previousFile)
            }

            deleteIndexFileIfExists(targetFile)
            val manager = VectorIndexManager<IndexItem<Long, Long>, Long>(
                dimensions = targetDimension,
                maxElements = compatibleItems.size.coerceAtLeast(1),
                indexFile = targetFile
            )
            compatibleItems.forEach { item ->
                manager.addItem(item)
                onItemIndexed?.invoke()
            }
            manager.save()
            manager.close()

            if (memory.chunkIndexFilePath != targetFile.absolutePath) {
                memory.chunkIndexFilePath = targetFile.absolutePath
                memoryBox.put(memory)
            }

            return compatibleItems.size
        }
    }

    private fun rebuildAllDocumentChunkIndices(onItemIndexed: (() -> Unit)? = null): Int {
        return synchronized(indexLock) {
            currentProfileDocumentIndexFiles().forEach { deleteIndexFileIfExists(it) }
            var indexedCount = 0

            memoryBox.all
                .filter { it.isDocumentNode }
                .forEach { memory ->
                    indexedCount += rebuildDocumentChunkIndex(memory, onItemIndexed = onItemIndexed)
                }

            return indexedCount
        }
    }

    private fun ensureMemoryVectorIndex(dimension: Int): VectorIndexManager<IndexItem<Long, Long>, Long>? {
        return synchronized(indexLock) {
            val targetFile = memoryIndexFileForDimension(dimension)
            if (!targetFile.exists()) {
                rebuildAllMemoryVectorIndices()
            }
            if (!targetFile.exists()) return null
            return VectorIndexManager(
                dimensions = dimension,
                maxElements = 1,
                indexFile = targetFile
            )
        }
    }

    private fun ensureDocumentChunkIndex(memory: Memory, dimension: Int): VectorIndexManager<IndexItem<Long, Long>, Long>? {
        return synchronized(indexLock) {
            val targetFile = documentIndexFile(memory.id, dimension)
            if (memory.chunkIndexFilePath != targetFile.absolutePath || !targetFile.exists()) {
                rebuildDocumentChunkIndex(memory, forcedDimension = dimension)
            }
            if (!targetFile.exists()) return null
            return VectorIndexManager(
                dimensions = dimension,
                maxElements = 1,
                indexFile = targetFile
            )
        }
    }

    private fun getSemanticMemoryCandidatesFromIndex(queryEmbedding: Embedding): List<Pair<Memory, Float>> {
        return synchronized(indexLock) {
            val manager = ensureMemoryVectorIndex(queryEmbedding.vector.size) ?: return emptyList()
            val availableCount = manager.size()
            if (availableCount <= 0) {
                manager.close()
                return emptyList()
            }
            val nearest = manager.findNearest(
                queryEmbedding.vector,
                availableCount
            )
            manager.close()
            if (nearest.isEmpty()) return emptyList()

            val ids = nearest.map { it.value }.distinct()
            val memoryById = memoryBox.get(ids).filterNotNull().associateBy { it.id }
            return ids.mapNotNull { memoryId ->
                val memory = memoryById[memoryId] ?: return@mapNotNull null
                if (!hasCurrentEmbedding(memory)) return@mapNotNull null
                val memoryEmbedding = memory.embedding ?: return@mapNotNull null
                if (memoryEmbedding.vector.size != queryEmbedding.vector.size) return@mapNotNull null
                memory to cosineSimilarity(queryEmbedding, memoryEmbedding)
            }
        }
    }

    private fun getSemanticChunkCandidatesFromIndex(
        memory: Memory,
        queryEmbedding: Embedding
    ): List<Pair<DocumentChunk, Float>> {
        return synchronized(indexLock) {
            val manager = ensureDocumentChunkIndex(memory, queryEmbedding.vector.size) ?: return emptyList()
            val availableCount = manager.size()
            if (availableCount <= 0) {
                manager.close()
                return emptyList()
            }
            val nearest = manager.findNearest(
                queryEmbedding.vector,
                availableCount
            )
            manager.close()
            if (nearest.isEmpty()) return emptyList()

            val ids = nearest.map { it.value }.distinct()
            val chunkById = chunkBox.get(ids).filterNotNull().associateBy { it.id }
            return ids.mapNotNull { chunkId ->
                val chunk = chunkById[chunkId] ?: return@mapNotNull null
                if (!hasCurrentEmbedding(chunk)) return@mapNotNull null
                val chunkEmbedding = chunk.embedding ?: return@mapNotNull null
                if (chunkEmbedding.vector.size != queryEmbedding.vector.size) return@mapNotNull null
                chunk to cosineSimilarity(queryEmbedding, chunkEmbedding)
            }
        }
    }

    private fun shouldKeepRawLexicalToken(token: String): Boolean {
        val normalized = token.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return false

        return if (isWildcardLexicalToken(normalized)) {
            shouldKeepWildcardLexicalToken(normalized)
        } else {
            shouldKeepPlainLexicalToken(normalized)
        }
    }

    private fun isWildcardLexicalToken(token: String): Boolean {
        return token.contains('*') && token != "*"
    }

    private fun shouldKeepPlainLexicalToken(token: String): Boolean {
        return isSearchableLexicalBody(token)
    }

    private fun shouldKeepWildcardLexicalToken(token: String): Boolean {
        val literalBody = token.split('*').joinToString(separator = "") { it.trim() }
        if (literalBody.isBlank()) return false
        return isSearchableLexicalBody(literalBody)
    }

    private fun isSearchableLexicalBody(text: String): Boolean {
        if (text.length !in 2..24) return false
        return text.any { ch -> ch.isLetterOrDigit() || ch.code in 0x4E00..0x9FFF }
    }

    private fun buildWildcardRegex(token: String): Regex? {
        if (!token.contains('*') || token == "*") return null
        val parts = token
            .split('*')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val pattern = parts.joinToString(".*") { Regex.escape(it) }
        return Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }

    private fun textMatchesLexicalToken(text: String, token: String): Boolean {
        val normalizedToken = token.trim()
        if (normalizedToken.isBlank()) return false
        val wildcardRegex = buildWildcardRegex(normalizedToken)
        return if (wildcardRegex != null) {
            wildcardRegex.containsMatchIn(text)
        } else {
            text.contains(normalizedToken, ignoreCase = true)
        }
    }

    private fun queryTitleCandidatesByFragments(
        fragments: List<String>,
        scopedMemories: List<Memory>
    ): List<TitleMatchCandidate> {
        if (fragments.isEmpty() || scopedMemories.isEmpty()) return emptyList()

        val scopedMemoryIds = scopedMemories.map { it.id }.toHashSet()
        val scopedMemoriesById = scopedMemories.associateBy { it.id }

        val memoryById = mutableMapOf<Long, Memory>()
        val hitTokensByMemoryId = mutableMapOf<Long, MutableSet<String>>()

        fragments.forEach { fragment ->
            val matches = scopedMemories.filter { memory ->
                textMatchesLexicalToken(memory.title, fragment) ||
                    textMatchesLexicalToken(memory.content, fragment) ||
                    memory.tags.any { textMatchesLexicalToken(it.name, fragment) }
            }

            // DB 先用最长字面片段缩小候选，再验证通配顺序；不能把 a*b 当成 ab。
            val anchor = fragment.split('*').maxByOrNull { it.length }.orEmpty()
            val documentMatches = chunkBox.query(DocumentChunk_.content.contains(anchor, QueryBuilder.StringOrder.CASE_INSENSITIVE))
                .build().use { query -> query.find().asSequence()
                    .filter { it.memory.targetId in scopedMemoryIds && textMatchesLexicalToken(it.content, fragment) }
                    .map { it.memory.targetId }.toHashSet() }
            val combinedMatches = (matches + scopedMemories.filter { it.isDocumentNode && it.id in documentMatches }).distinctBy { it.id }
            combinedMatches.forEach { memory ->
                if (!scopedMemoryIds.contains(memory.id)) return@forEach
                val scopedMemory = scopedMemoriesById[memory.id] ?: memory
                memoryById.putIfAbsent(memory.id, scopedMemory)
                hitTokensByMemoryId.getOrPut(memory.id) { linkedSetOf() }.add(fragment)
            }
        }

        return memoryById.values
            .map { memory ->
                TitleMatchCandidate(
                    memory = memory,
                    matchedTokenCount = hitTokensByMemoryId[memory.id]?.size ?: 0
                )
            }
            .sortedWith(
                compareByDescending<TitleMatchCandidate> { it.matchedTokenCount }
                    .thenByDescending { it.memory.importance }
                    .thenByDescending { it.memory.updatedAt.time }
            )
    }

    private fun queryTagCandidatesByFragments(
        fragments: List<String>,
        scopedMemories: List<Memory>
    ): List<TitleMatchCandidate> {
        if (fragments.isEmpty() || scopedMemories.isEmpty()) return emptyList()

        return scopedMemories
            .mapNotNull { memory ->
                memory.tags.reset()
                val tagNames = memory.tags.map { it.name }
                if (tagNames.isEmpty()) return@mapNotNull null

                val matchedTokenCount = fragments.count { fragment ->
                    tagNames.any { tagName -> textMatchesLexicalToken(tagName, fragment) }
                }
                if (matchedTokenCount <= 0) return@mapNotNull null

                TitleMatchCandidate(
                    memory = memory,
                    matchedTokenCount = matchedTokenCount
                )
            }
            .sortedWith(
                compareByDescending<TitleMatchCandidate> { it.matchedTokenCount }
                    .thenByDescending { it.memory.importance }
                    .thenByDescending { it.memory.updatedAt.time }
            )
    }
    
    /**
     * 从外部文档创建记忆。
     * @param title 文档记忆的标题。
     * @param filePath 文档的路径。
     * @param fileContent 文档的文本内容。
     * @param folderPath 文件夹路径。
     * @return 创建的Memory对象。
     */
    suspend fun createMemoryFromDocument(documentName: String, originalPath: String, text: String, folderPath: String = ""): Memory = withContext(Dispatchers.IO) {
        validateFolderPath(folderPath)
        require(documentName.isNotBlank()) { "资料名称不能为空" }
        val safeText = ConversationAuditRedactor.redactText(text).value
        val parts = MemoryLibraryPolicy.chunks(safeText)
        fun existingImport(): Memory? = memoryBox.query(Memory_.documentPath.equal(originalPath)).build().use { query ->
            query.find().firstOrNull { existing -> existing.isDocumentNode && !existing.archived &&
                normalizeStoredFolderPath(existing.folderPath) == normalizeStoredFolderPath(folderPath) &&
                loadChunksForDocument(existing).sortedBy { it.chunkIndex }.map { it.content } == parts }
        }
        synchronized(indexLock) { existingImport()?.let { return@withContext it } }
        val cloudConfig = loadCloudEmbeddingConfig()
        val documentMemory = Memory(
            title = documentName.trim(),
            content = safeText.take(1000),
            source = "document_import",
            libraryKind = MemoryLibraryPolicy.KNOWLEDGE,
            isDocumentNode = true,
            documentPath = originalPath,
            folderPath = normalizeStoredFolderPath(folderPath)
        )
        documentMemory.embedding = generateEmbedding(generateTextForEmbedding(documentMemory), cloudConfig)
        markEmbedding(documentMemory, cloudConfig)
        val chunks = parts.mapIndexed { index, part ->
            DocumentChunk(content = part, chunkIndex = index).also { chunk ->
                chunk.embedding = generateEmbedding(part, cloudConfig)
                markEmbedding(chunk, cloudConfig)
            }
        }
        synchronized(indexLock) {
            existingImport()?.let { return@withContext it }
            addMemoryToIndexInternal(documentMemory)
            store.runInTx {
                memoryBox.put(documentMemory)
                chunks.forEach { it.memory.target = documentMemory }
                chunkBox.put(chunks)
            }
        }
        documentMemory
    }

    /**
     * 生成带有元数据（可信度、重要性）的文本，用于embedding。
     */
    private fun generateTextForEmbedding(memory: Memory): String {
        return if (isFolderPlaceholderMemory(memory)) "" else "${memory.title}\n${memory.content}"
    }

    // --- Memory CRUD Operations ---

    /**
     * Creates or updates a memory, automatically generating its embedding.
     * @param memory The memory object to be saved.
     * @return The ID of the saved memory.
     */
    suspend fun saveMemory(memory: Memory): Long = withContext(Dispatchers.IO){
        val cloudConfig = searchSettingsPreferences.loadCloudEmbedding()
        val previousDimension = memory.id.takeIf { it > 0L }
            ?.let { existingId -> memoryBox.get(existingId)?.embedding?.vector?.size }
        memory.title = ConversationAuditRedactor.redactText(memory.title).value
        memory.content = ConversationAuditRedactor.redactText(memory.content).value
        memory.source = ConversationAuditRedactor.redactText(memory.source).value
        memory.folderPath?.let(::validateFolderPath)
        memory.folderPath = normalizeStoredFolderPath(memory.folderPath)
        memory.credibility = memory.credibility.coerceIn(0.0f, 1.0f)
        memory.importance = memory.importance.coerceIn(0.0f, 1.0f)
        val textForEmbedding = generateTextForEmbedding(memory)
        if (textForEmbedding.isNotBlank()) {
            memory.embedding = generateEmbedding(textForEmbedding, cloudConfig)
        }
        markEmbedding(memory, cloudConfig)
        memory.updatedAt = Date()
        synchronized(indexLock) {
            // 缓存失效先于事实提交：磁盘错误不能让已保存条目表现为失败并诱发重复创建。
            addMemoryToIndexInternal(memory, previousDimension = previousDimension)
            memoryBox.put(memory)
        }
    }

    /**
     * Finds a memory by its ID.
     * @param id The ID of the memory to find.
     * @return The found Memory object, or null if not found.
     */
    suspend fun setArchived(id: Long, archived: Boolean) = withContext(Dispatchers.IO) {
        store.runInTx {
            val memory = requireNotNull(memoryBox.get(id)) { "条目已被删除" }
            memory.archived = archived
            memory.updatedAt = Date(maxOf(System.currentTimeMillis(), memory.updatedAt.time + 1))
            memoryBox.put(memory)
        }
    }

    suspend fun findMemoryById(id: Long): Memory? = withContext(Dispatchers.IO) {
        memoryBox.get(id)
    }

    /**
     * Finds a memory by its UUID.
     * @param uuid The UUID of the memory to find.
     * @return The found Memory object, or null if not found.
     */
    suspend fun findMemoryByUuid(uuid: String): Memory? = withContext(Dispatchers.IO) {
        memoryBox.query(Memory_.uuid.equal(uuid)).build().use { it.findFirst() }
    }

    /**
     * Finds a memory by its exact title.
     * @param title The title of the memory to find.
     * @return The found Memory object, or null if not found.
     */
    suspend fun findMemoryByTitle(title: String): Memory? = withContext(Dispatchers.IO) {
        findMemoriesByTitle(title).filter { !it.archived }.let { matches ->
            // 报错必须自带下一步：只说「请使用 UUID」而不给出 UUID，调用方无法脱困。
            require(matches.size <= 1) {
                "存在 ${matches.size} 条同名条目，请改用 uuid 参数定位：" +
                    matches.joinToString { "${it.uuid}（${it.folderPath ?: ROOT_FOLDER_TOKEN}）" }
            }
            matches.singleOrNull()
        }
    }

    /**
     * Finds all memories with the exact title (case-sensitive).
     * @param title The title of the memories to find.
     * @return A list of found Memory objects.
     */
    suspend fun findMemoriesByTitle(title: String): List<Memory> = withContext(Dispatchers.IO) {
        memoryBox.query(Memory_.title.equal(title)).build().find()
    }

    /**
     * Deletes a memory and all its links. This is a critical operation and should be handled with
     * care.
     * @param memoryId The ID of the memory to delete.
     * @return True if deletion was successful, false otherwise.
     */
    suspend fun deleteMemory(memoryId: Long): Boolean = withContext(Dispatchers.IO) {
        synchronized(indexLock) {
            val memory = memoryBox.get(memoryId) ?: return@withContext false
            // 先让派生缓存失效，再在一个事务内删除事实，异常不得留下孤儿块或半删除关系。
            removeMemoryFromIndexInternal(memory)
            try {
                store.callInTx<Boolean> {
                    val chunkIds = chunkBox.query(DocumentChunk_.memoryId.equal(memoryId)).build().use { it.findIds() }
                    if (chunkIds.isNotEmpty()) chunkBox.remove(*chunkIds)
                    val links = collectLinkIdsForDeletion(setOf(memoryId), includeDangling = false)
                    if (links.isNotEmpty()) linkBox.removeByIds(links)
                    memoryBox.remove(memoryId)
                }
            } catch (failure: Throwable) {
                // 事务回滚后条目仍然存在，索引必须跟着回滚，否则它会永久搜不到。
                runCatching { addMemoryToIndexInternal(memory) }
                throw failure
            }
        }
    }

    private fun collectLinkIdsForDeletion(
        memoryIdsToDelete: Set<Long>,
        includeDangling: Boolean = true
    ): Set<Long> {
        val existingMemoryIds =
            if (includeDangling) memoryBox.all.map { it.id }.toHashSet() else emptySet()
        return linkBox.all
            .asSequence()
            .filter { link ->
                val sourceId = link.source.targetId
                val targetId = link.target.targetId
                val linkedToDeletingMemories =
                    sourceId in memoryIdsToDelete || targetId in memoryIdsToDelete
                val danglingLink = if (includeDangling) {
                    sourceId <= 0L ||
                        targetId <= 0L ||
                        sourceId !in existingMemoryIds ||
                        targetId !in existingMemoryIds
                } else {
                    false
                }
                linkedToDeletingMemories || danglingLink
            }
            .map { it.id }
            .toSet()
    }

    private fun cleanupDanglingLinksIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastDanglingCleanupAtMs < DANGLING_LINK_CLEANUP_INTERVAL_MS) {
            return
        }
        val danglingLinkIds = collectLinkIdsForDeletion(emptySet())
        if (danglingLinkIds.isNotEmpty()) {
            linkBox.removeByIds(danglingLinkIds)
            com.ai.assistance.operit.util.AppLogger.w(
                "MemoryRepo",
                "Cleaned ${danglingLinkIds.size} dangling memory links."
            )
        }
        lastDanglingCleanupAtMs = now
    }

    suspend fun findLinkById(linkId: Long): MemoryLink? = withContext(Dispatchers.IO) {
        linkBox.get(linkId)
    }

    suspend fun updateLink(linkId: Long, type: String, weight: Float, description: String): MemoryLink? = withContext(Dispatchers.IO) {
        val link = findLinkById(linkId) ?: return@withContext null
        val sourceMemory = link.source.target

        link.type = type
        link.weight = weight
        link.description = description
        linkBox.put(link)

        // 在更新link后，同样put其所属的source memory。
        // 这是为了向ObjectBox明确指出，这个父实体的关系集合“脏了”，
        // 以此来避免后续查询时拿到缓存的旧数据。
        if (sourceMemory != null) {
            memoryBox.put(sourceMemory)
        }

        link
    }

    suspend fun deleteLink(linkId: Long): Boolean = withContext(Dispatchers.IO) {
        // 为了健壮性，在删除链接后，也更新其父实体。
        val link = findLinkById(linkId)
        val sourceMemory = link?.source?.target

        val wasRemoved = linkBox.remove(linkId)

        if (wasRemoved && sourceMemory != null) {
            // 通过put源实体，我们确保它的ToMany关系缓存在其他线程或未来的查询中得到更新。
            memoryBox.put(sourceMemory)
        }
        wasRemoved
    }

    // --- Tagging Operations ---

    /**
     * Adds a tag to a memory.
     * @param memory The memory to tag.
     * @param tagName The name of the tag.
     * @return The MemoryTag object.
     */
    suspend fun addTagToMemory(memory: Memory, tagName: String): MemoryTag = withContext(Dispatchers.IO) {
        // Find existing tag or create a new one
        val tag =
                tagBox.query()
                        .equal(MemoryTag_.name, tagName, QueryBuilder.StringOrder.CASE_SENSITIVE)
                        .build().use { it.findFirst() }
                        ?: MemoryTag(name = tagName)

        if (!memory.tags.any { it.id == tag.id }) {
            memory.tags.add(tag)
            memoryBox.put(memory)
        }
        tag
    }

    // --- Linking Operations ---

    /**
     * Creates a link between two memories.
     * @param source The source memory.
     * @param target The target memory.
     * @param type The type of the link (e.g., "causes", "explains").
     * @param weight The strength of the link, ideally between 0.0 and 1.0.
     *               It's recommended to use the predefined constants like [STRONG_LINK],
     *               [MEDIUM_LINK], or [WEAK_LINK] for consistency. The value will be
     *               automatically clamped to the [0.0, 1.0] range.
     * @param description A description of the link.
     */
    suspend fun linkMemories(
            source: Memory,
            target: Memory,
            type: String,
            weight: Float = MEDIUM_LINK,
            description: String = ""
    ) = withContext(Dispatchers.IO) {
        // 自环没有检索意义，还会让图谱扩展把同一条目当成邻居反复加权。
        require(source.id != target.id) { "不能把条目关联到它自己" }
        // 检查链接是否已存在
        val existingLink = source.links.find { link ->
            link.target.target?.id == target.id && 
            link.type == type
        }
        
        if (existingLink != null) {
            // 链接已存在，可以选择更新或直接返回
            // 这里我们选择直接返回，不创建重复链接
            com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Link already exists from memory ${source.id} to ${target.id} with type $type")
            return@withContext
        }
        
        // Coerce the weight to be within the valid range [0.0, 1.0] to ensure data integrity.
        val sanitizedWeight = weight.coerceIn(0.0f, 1.0f)
        val link = MemoryLink(type = type, weight = sanitizedWeight, description = description)
        link.source.target = source
        link.target.target = target

        source.links.add(link)
        memoryBox.put(source)
    }

    /** Gets all outgoing links from a memory. */
    suspend fun getOutgoingLinks(memoryId: Long): List<MemoryLink> = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId)
        memory?.links?.reset()
        memory?.links ?: emptyList()
    }

    /** Gets all incoming links to a memory. */
    suspend fun getIncomingLinks(memoryId: Long): List<MemoryLink> = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId)
        memory?.backlinks?.reset()
        memory?.backlinks ?: emptyList()
    }

    /**
     * Query memory links with optional filters.
     * If no filter is provided, returns recent links up to limit.
     */
    suspend fun queryMemoryLinks(
        linkId: Long? = null,
        sourceMemoryId: Long? = null,
        targetMemoryId: Long? = null,
        linkType: String? = null,
        limit: Int = 20
    ): List<MemoryLink> = withContext(Dispatchers.IO) {
        cleanupDanglingLinksIfNeeded()

        val normalizedType = linkType?.trim()?.takeIf { it.isNotEmpty() }
        val validLimit = limit.coerceIn(1, 200)

        val candidates = when {
            linkId != null -> listOfNotNull(linkBox.get(linkId))
            sourceMemoryId != null && targetMemoryId != null ->
                getOutgoingLinks(sourceMemoryId).filter { it.target.targetId == targetMemoryId }
            sourceMemoryId != null -> getOutgoingLinks(sourceMemoryId)
            targetMemoryId != null -> getIncomingLinks(targetMemoryId)
            else -> linkBox.all
        }

        candidates
            .asSequence()
            .filter { link ->
                val sourceId = link.source.targetId
                val targetId = link.target.targetId

                (sourceMemoryId == null || sourceId == sourceMemoryId) &&
                    (targetMemoryId == null || targetId == targetMemoryId) &&
                    (normalizedType == null || link.type == normalizedType)
            }
            .sortedByDescending { it.id }
            .take(validLimit)
            .toList()
    }

    // --- Complex Queries ---

    /**
     * Searches memories using semantic search if a query is provided, otherwise returns all
     * memories.
     * @param query The search query string. Keywords can be separated by '|' or spaces.
     * @param folderPath Optional path to a folder to limit the search.
     * @return A list of matching Memory objects, sorted by relevance.
     */
    suspend fun searchMemories(
        query: String,
        folderPath: String? = null,
        scoreMode: MemoryScoreMode = MemoryScoreMode.BALANCED,
        keywordWeight: Float = 10.0f,
        tagWeight: Float = 0.0f,
        semanticWeight: Float = 0.5f,
        edgeWeight: Float = 0.4f,
        relevanceThreshold: Double = SEARCH_RELEVANCE_THRESHOLD,
        createdAtStartMs: Long? = null,
        createdAtEndMs: Long? = null,
        libraryKind: String? = null,
        archived: Boolean = false
    ): List<Memory> {
        return runSearchMemoriesWithDebug(
            query = query,
            folderPath = folderPath,
            scoreMode = scoreMode,
            keywordWeight = keywordWeight,
            tagWeight = tagWeight,
            semanticWeight = semanticWeight,
            edgeWeight = edgeWeight,
            relevanceThreshold = relevanceThreshold,
            createdAtStartMs = createdAtStartMs,
            createdAtEndMs = createdAtEndMs,
            libraryKind = libraryKind,
            archived = archived
        ).memories
    }

    suspend fun searchMemoriesDebug(
        query: String,
        folderPath: String? = null,
        scoreMode: MemoryScoreMode = MemoryScoreMode.BALANCED,
        keywordWeight: Float = 10.0f,
        tagWeight: Float = 0.0f,
        semanticWeight: Float = 0.5f,
        edgeWeight: Float = 0.4f,
        relevanceThreshold: Double = SEARCH_RELEVANCE_THRESHOLD,
        createdAtStartMs: Long? = null,
        createdAtEndMs: Long? = null,
        libraryKind: String? = null,
        archived: Boolean = false
    ): MemorySearchDebugInfo {
        return runSearchMemoriesWithDebug(
            query = query,
            folderPath = folderPath,
            scoreMode = scoreMode,
            keywordWeight = keywordWeight,
            tagWeight = tagWeight,
            semanticWeight = semanticWeight,
            edgeWeight = edgeWeight,
            relevanceThreshold = relevanceThreshold,
            createdAtStartMs = createdAtStartMs,
            createdAtEndMs = createdAtEndMs,
            libraryKind = libraryKind,
            archived = archived
        ).debug
    }

    private suspend fun runSearchMemoriesWithDebug(
        query: String,
        folderPath: String? = null,
        scoreMode: MemoryScoreMode = MemoryScoreMode.BALANCED,
        keywordWeight: Float = 10.0f,
        tagWeight: Float = 0.0f,
        semanticWeight: Float = 0.5f,
        edgeWeight: Float = 0.4f,
        relevanceThreshold: Double = SEARCH_RELEVANCE_THRESHOLD,
        createdAtStartMs: Long? = null,
        createdAtEndMs: Long? = null,
        libraryKind: String? = null,
        archived: Boolean = false
    ): SearchComputationResult = withContext(Dispatchers.IO) {
        val normalizedFolderPath = normalizeStoredFolderPath(folderPath)

        val memoriesInScope = if (normalizedFolderPath == null) {
            // 空路径表示整个空间；`(root)` 明确表示只看未归类条目，两者不能混为一谈。
            if (folderPath?.trim() == ROOT_FOLDER_TOKEN) {
                memoryBox.all.filter { normalizeStoredFolderPath(it.folderPath) == null }
            } else {
                memoryBox.all
            }
        } else {
            getMemoriesByFolderPath(normalizedFolderPath)
        }

        val searchableMemoriesInScope = memoriesInScope.filterNot(::isFolderPlaceholderMemory).filter { MemoryLibraryPolicy.matches(it, libraryKind, archived) }

        val timeFilteredMemoriesInScope = if (createdAtStartMs == null && createdAtEndMs == null) {
            searchableMemoriesInScope
        } else {
            searchableMemoriesInScope.filter { memory ->
                val createdAtMs = memory.createdAt.time
                (createdAtStartMs == null || createdAtMs >= createdAtStartMs) &&
                    (createdAtEndMs == null || createdAtMs <= createdAtEndMs)
            }
        }

        val keywords = splitSearchKeywords(query)
        val resolvedWeights = resolveSearchWeights(
            scoreMode = scoreMode,
            keywordWeight = keywordWeight,
            tagWeight = tagWeight,
            semanticWeight = semanticWeight,
            edgeWeight = edgeWeight,
            keywordCount = 1
        )
        val effectiveKeywordWeight = resolvedWeights.effectiveKeywordWeight
        val effectiveTagWeight = resolvedWeights.effectiveTagWeight
        val effectiveSemanticWeight = resolvedWeights.effectiveSemanticWeight
        val effectiveEdgeWeight = resolvedWeights.effectiveEdgeWeight
        val semanticKeywordNormFactor = resolvedWeights.semanticKeywordNormFactor
        val effectiveRelevanceThreshold = relevanceThreshold.coerceAtLeast(0.0)

        fun buildDebug(
            debugKeywords: List<String> = keywords,
            lexicalTokens: List<String> = emptyList(),
            debugSemanticKeywordNormFactor: Double = resolvedWeights.semanticKeywordNormFactor,
            keywordMatchesCount: Int = 0,
            tagMatchesCount: Int = 0,
            reverseContainmentMatchesCount: Int = 0,
            semanticMatchesCount: Int = 0,
            graphEdgesTraversed: Int = 0,
            scoredCount: Int = 0,
            passedThresholdCount: Int = 0,
            candidates: List<MemorySearchDebugCandidate> = emptyList(),
            finalResultIds: List<Long> = emptyList()
        ): MemorySearchDebugInfo {
            return MemorySearchDebugInfo(
                query = query,
                keywords = debugKeywords,
                lexicalTokens = lexicalTokens,
                scoreMode = scoreMode,
                relevanceThreshold = effectiveRelevanceThreshold,
                effectiveKeywordWeight = effectiveKeywordWeight,
                effectiveTagWeight = effectiveTagWeight,
                effectiveSemanticWeight = effectiveSemanticWeight,
                semanticKeywordNormFactor = debugSemanticKeywordNormFactor,
                effectiveEdgeWeight = effectiveEdgeWeight,
                memoriesInScopeCount = timeFilteredMemoriesInScope.size,
                keywordMatchesCount = keywordMatchesCount,
                tagMatchesCount = tagMatchesCount,
                reverseContainmentMatchesCount = reverseContainmentMatchesCount,
                semanticMatchesCount = semanticMatchesCount,
                graphEdgesTraversed = graphEdgesTraversed,
                scoredCount = scoredCount,
                passedThresholdCount = passedThresholdCount,
                candidates = candidates,
                finalResultIds = finalResultIds
            )
        }

        // 支持通配符搜索：如果查询是 "*"，返回所有记忆（在文件夹过滤后）
        if (query.trim() == "*") {
            return@withContext SearchComputationResult(
                memories = timeFilteredMemoriesInScope,
                debug = buildDebug(finalResultIds = timeFilteredMemoriesInScope.map { it.id })
            )
        }

        if (query.isBlank()) {
            return@withContext SearchComputationResult(
                memories = timeFilteredMemoriesInScope,
                debug = buildDebug(finalResultIds = timeFilteredMemoriesInScope.map { it.id })
            )
        }

        if (keywords.isEmpty()) {
            return@withContext SearchComputationResult(
                memories = emptyList(),
                debug = buildDebug(debugKeywords = emptyList())
            )
        }
        val keywordTokensForLexicalMatch = buildLexicalQueryTokens(query, keywords)

        val scores = mutableMapOf<Long, Double>()
        val scorePartsByMemoryId = mutableMapOf<Long, SearchScoreParts>()
        fun getScoreParts(memoryId: Long): SearchScoreParts {
            return scorePartsByMemoryId.getOrPut(memoryId) { SearchScoreParts() }
        }
        com.ai.assistance.operit.util.AppLogger.d(
            "MemoryRepo",
            "search settings => mode=${resolvedWeights.scoreMode}, keyword=${String.format(java.util.Locale.getDefault(), "%.2f", effectiveKeywordWeight)}, " +
                "tag=${String.format(java.util.Locale.getDefault(), "%.2f", effectiveTagWeight)}, " +
                "semantic=${String.format(java.util.Locale.getDefault(), "%.2f", effectiveSemanticWeight)}, " +
                "semanticNorm=${String.format(java.util.Locale.getDefault(), "%.4f", semanticKeywordNormFactor)}, edge=${String.format(java.util.Locale.getDefault(), "%.2f", effectiveEdgeWeight)}"
        )
        com.ai.assistance.operit.util.AppLogger.d(
            "MemoryRepo",
            "keyword fragments: raw=${keywords.size}, lexical=${keywordTokensForLexicalMatch.size}"
        )
        if (keywordTokensForLexicalMatch.isNotEmpty()) {
            com.ai.assistance.operit.util.AppLogger.d(
                "MemoryRepo",
                "keyword fragments preview: ${
                    keywordTokensForLexicalMatch.take(12).joinToString(" | ")
                }"
            )
        }

        // --- PRE-FILTERING BY FOLDER ---
        // If a folder path is provided, all subsequent searches will be performed on this subset.
        // Otherwise, search all memories.
        val memoriesToSearch = timeFilteredMemoriesInScope

        if (memoriesToSearch.isEmpty()) {
            com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "No memories found in folder '$folderPath' to search.")
            return@withContext SearchComputationResult(
                memories = emptyList(),
                debug = buildDebug(
                    debugKeywords = keywords,
                    lexicalTokens = keywordTokensForLexicalMatch,
                    debugSemanticKeywordNormFactor = semanticKeywordNormFactor
                )
            )
        }


        // 1. Keyword-based search (DB title contains any fragment from the query)
        val keywordResults = queryTitleCandidatesByFragments(
            fragments = keywordTokensForLexicalMatch,
            scopedMemories = memoriesToSearch
        )

        if (keywordResults.isNotEmpty()) {
            com.ai.assistance.operit.util.AppLogger.d(
                "MemoryRepo",
                "Keyword search (title fragments): ${keywordResults.size} matches"
            )
        }
        if (effectiveKeywordWeight > 0.0) {
            keywordResults.forEachIndexed { index, candidate ->
                val memory = candidate.memory
                val rank = index + 1
                val baseScore = computeRrfBaseScore(rank)
                val coverageMultiplier = computeKeywordCoverageMultiplier(
                    matchedTokenCount = candidate.matchedTokenCount,
                    totalTokenCount = keywordTokensForLexicalMatch.size
                )
                val weightedScore = baseScore * memory.importance * effectiveKeywordWeight * coverageMultiplier
                scores[memory.id] = scores.getOrDefault(memory.id, 0.0) + weightedScore
                val parts = getScoreParts(memory.id)
                parts.keywordScore += weightedScore
                parts.matchedKeywordTokenCount = maxOf(parts.matchedKeywordTokenCount, candidate.matchedTokenCount)
            }
        }

        val tagResults = queryTagCandidatesByFragments(
            fragments = keywordTokensForLexicalMatch,
            scopedMemories = memoriesToSearch
        )

        if (tagResults.isNotEmpty()) {
            com.ai.assistance.operit.util.AppLogger.d(
                "MemoryRepo",
                "Tag search: ${tagResults.size} matches"
            )
        }
        if (effectiveTagWeight > 0.0) {
            tagResults.forEachIndexed { index, candidate ->
                val memory = candidate.memory
                val rank = index + 1
                val baseScore = computeRrfBaseScore(rank)
                val coverageMultiplier = computeKeywordCoverageMultiplier(
                    matchedTokenCount = candidate.matchedTokenCount,
                    totalTokenCount = keywordTokensForLexicalMatch.size
                )
                val weightedScore = baseScore * memory.importance * effectiveTagWeight * coverageMultiplier
                scores[memory.id] = scores.getOrDefault(memory.id, 0.0) + weightedScore
                val parts = getScoreParts(memory.id)
                parts.tagScore += weightedScore
                parts.matchedKeywordTokenCount = maxOf(parts.matchedKeywordTokenCount, candidate.matchedTokenCount)
            }
        }

        // 2. Reverse Containment Search (Query contains Memory Title)
        // This is crucial for finding "长安大学" within the query "长安大学在西安".
        val reverseContainmentResults =
                memoriesToSearch.filter { memory -> textMatchesLexicalToken(query, memory.title) }
        
        if (reverseContainmentResults.isNotEmpty()) {
            com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Reverse containment: ${reverseContainmentResults.size} matches")
        }
        if (effectiveKeywordWeight > 0.0) {
            reverseContainmentResults.forEachIndexed { index, memory ->
                val rank = index + 1
                // Use the same RRF formula to add to the score
                val baseScore = computeRrfBaseScore(rank)
                val weightedScore = baseScore * memory.importance * effectiveKeywordWeight
                scores[memory.id] = scores.getOrDefault(memory.id, 0.0) + weightedScore
                getScoreParts(memory.id).reverseContainmentScore += weightedScore
            }
        }

        // 3. Semantic search (for conceptual matches)
        val allMemoriesWithEmbedding = memoriesToSearch.filter { it.embedding != null }
        val cloudConfig = searchSettingsPreferences.loadCloudEmbedding()
        val semanticMatchedIds = mutableSetOf<Long>()
        val scopedMemoryIds = allMemoriesWithEmbedding.map { it.id }.toHashSet()

        if (effectiveSemanticWeight > 0.0f && cloudConfig.enabled) {
            check(cloudConfig.isReady()) { "云端嵌入配置不完整" }
            com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "--- Starting Semantic Search for ${keywords.size} keywords ---")
            listOf(query.trim()).forEach { keyword ->
                val queryEmbedding = generateEmbedding(keyword, cloudConfig)
                if (queryEmbedding == null) {
                    com.ai.assistance.operit.util.AppLogger.w("MemoryRepo", "Failed to generate embedding for: '$keyword'")
                    return@forEach
                }

                val semanticResultsWithScores = getSemanticMemoryCandidatesFromIndex(queryEmbedding)
                    .asSequence()
                    .filter { (memory, _) -> scopedMemoryIds.contains(memory.id) }
                    .sortedByDescending { it.second }
                    .toList()

                if (semanticResultsWithScores.isEmpty()) {
                    com.ai.assistance.operit.util.AppLogger.d(
                        "MemoryRepo",
                        "Keyword '$keyword': semantic index returned no compatible memory candidates"
                    )
                } else {
                    com.ai.assistance.operit.util.AppLogger.d(
                        "MemoryRepo",
                        "Keyword '$keyword': ${semanticResultsWithScores.size} indexed matches (top: ${String.format(java.util.Locale.getDefault(), "%.2f", semanticResultsWithScores.first().second)})"
                    )
                }

                semanticResultsWithScores.forEachIndexed { index, (memory, similarity) ->
                    val rank = index + 1
                    val weightedScore = computeSemanticWeightedScore(
                        rank = rank,
                        baseImportance = memory.importance,
                        similarity = similarity,
                        resolvedWeights = resolvedWeights
                    )
                    scores[memory.id] = scores.getOrDefault(memory.id, 0.0) + weightedScore
                    getScoreParts(memory.id).semanticScore += weightedScore
                    semanticMatchedIds.add(memory.id)
                }
            }
            com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "--- Semantic Search Completed ---")
        }

        // 4. Graph-based expansion: Boost scores of connected memories based on edge weights
        // Take top-scoring memories as "seed nodes" and propagate scores through edges
        val topMemoriesForExpansion = if (effectiveEdgeWeight > 0.0) {
            scores.entries.sortedByDescending { it.value }.take(10)
        } else {
            emptyList()
        }

        var edgesTraversed = 0
        val graphPropagationWeight = effectiveEdgeWeight
        val basePropagationScore = 0.03 * effectiveEdgeWeight // Give a minimum score boost for any connection

        topMemoriesForExpansion.forEach { (sourceId, _) ->
            val sourceMemory = memoriesToSearch.find { it.id == sourceId } ?: return@forEach
            val sourceScore = scores[sourceId] ?: 0.0
            
            // 重置关系缓存以获取最新连接
            sourceMemory.links.reset()
            sourceMemory.backlinks.reset()
            
            // Propagate score through outgoing links
            sourceMemory.links.forEach { link ->
                val targetMemory = link.target.target
                if (targetMemory != null && memoriesToSearch.any { it.id == targetMemory.id }) {
                    // 边权重越高，传播的分数越多
                    val propagatedScore = (sourceScore * link.weight * graphPropagationWeight) + basePropagationScore
                    scores[targetMemory.id] = scores.getOrDefault(targetMemory.id, 0.0) + propagatedScore
                    getScoreParts(targetMemory.id).edgeScore += propagatedScore
                    edgesTraversed++
                }
            }
            
            // Propagate score through incoming links (backlinks)
            sourceMemory.backlinks.forEach { link ->
                val targetMemory = link.source.target
                if (targetMemory != null && memoriesToSearch.any { it.id == targetMemory.id }) {
                    // 边权重越高，传播的分数越多
                    val propagatedScore = (sourceScore * link.weight * graphPropagationWeight) + basePropagationScore
                    scores[targetMemory.id] = scores.getOrDefault(targetMemory.id, 0.0) + propagatedScore
                    getScoreParts(targetMemory.id).edgeScore += propagatedScore
                    edgesTraversed++
                }
            }
        }
        if (edgesTraversed > 0) {
            com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Graph expansion: ${edgesTraversed} edges traversed")
        }

        // 5. Fuse results using RRF and return sorted list
        if (scores.isEmpty()) {
            return@withContext SearchComputationResult(
                memories = emptyList(),
                debug = buildDebug(
                    debugKeywords = keywords,
                    lexicalTokens = keywordTokensForLexicalMatch,
                    debugSemanticKeywordNormFactor = semanticKeywordNormFactor,
                    keywordMatchesCount = keywordResults.size,
                    tagMatchesCount = tagResults.size,
                    reverseContainmentMatchesCount = reverseContainmentResults.size,
                    semanticMatchesCount = semanticMatchedIds.size,
                    graphEdgesTraversed = edgesTraversed
                )
            )
        }

        // 添加相关性阈值过滤，避免返回不相关的记忆
        val filteredScores = scores.entries.filter { it.value >= effectiveRelevanceThreshold }

        com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Final results: ${filteredScores.size}/${scores.size} above threshold")

        // 只显示前3个结果的分数
        val sortedScoresForLogging = scores.entries.sortedByDescending { it.value }
        val scoredMemoryMap = memoryBox.get(scores.keys.toList()).filterNotNull().associateBy { it.id }
        sortedScoresForLogging.take(3).forEach { (id, score) ->
            val memory = scoredMemoryMap[id] ?: memoriesToSearch.find { it.id == id }
            if (memory != null) {
                com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "  Top: [${memory.title}] = ${String.format(java.util.Locale.getDefault(), "%.4f", score)}")
            }
        }

        val candidateRows = sortedScoresForLogging.map { (memoryId, totalScore) ->
            val memory = scoredMemoryMap[memoryId]
            val parts = scorePartsByMemoryId[memoryId] ?: SearchScoreParts()
            MemorySearchDebugCandidate(
                memoryId = memoryId,
                title = memory?.title ?: "#$memoryId",
                folderPath = memory?.folderPath,
                matchedKeywordTokenCount = parts.matchedKeywordTokenCount,
                keywordScore = parts.keywordScore,
                tagScore = parts.tagScore,
                reverseContainmentScore = parts.reverseContainmentScore,
                semanticScore = parts.semanticScore,
                edgeScore = parts.edgeScore,
                totalScore = totalScore,
                passedThreshold = totalScore >= effectiveRelevanceThreshold
            )
        }

        val debugInfo = buildDebug(
            debugKeywords = keywords,
            lexicalTokens = keywordTokensForLexicalMatch,
            debugSemanticKeywordNormFactor = semanticKeywordNormFactor,
            keywordMatchesCount = keywordResults.size,
            tagMatchesCount = tagResults.size,
            reverseContainmentMatchesCount = reverseContainmentResults.size,
            semanticMatchesCount = semanticMatchedIds.size,
            graphEdgesTraversed = edgesTraversed,
            scoredCount = scores.size,
            passedThresholdCount = filteredScores.size,
            candidates = candidateRows,
            finalResultIds = filteredScores.sortedByDescending { it.value }.map { it.key }
        )

        if (filteredScores.isEmpty()) {
            com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "No memories above relevance threshold")
            return@withContext SearchComputationResult(
                memories = emptyList(),
                debug = debugInfo
            )
        }

        val sortedMemoryIds = filteredScores.sortedByDescending { it.value }.map { it.key }

        // Fetch the sorted entities from the database
        val sortedMemories = memoryBox.get(sortedMemoryIds).filterNotNull()

        // 7. Semantic Deduplication
        // deduplicateBySemantics(sortedMemories)
        SearchComputationResult(
            memories = sortedMemories,
            debug = debugInfo
        )
    }

    /**
     * 获取指定记忆的所有文档区块。
     * @param memoryId 父记忆的ID。
     * @return 该记忆关联的DocumentChunk列表。
     */
    suspend fun getChunksForMemory(memoryId: Long): List<DocumentChunk> = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId)
        // 从数据库关系中获取，并按原始顺序排序
        memory?.documentChunks?.sortedBy { it.chunkIndex } ?: emptyList()
    }

    /**
     * 根据索引获取单个文档区块。
     * @param memoryId 父记忆的ID。
     * @param chunkIndex 区块索引（0-based）。
     * @return 对应的DocumentChunk，如果不存在则返回null。
     */
    suspend fun getChunkByIndex(memoryId: Long, chunkIndex: Int): DocumentChunk? = withContext(Dispatchers.IO) {
        val chunks = getChunksForMemory(memoryId)
        chunks.firstOrNull { it.chunkIndex == chunkIndex }
    }

    /**
     * 获取指定范围内的文档区块。
     * @param memoryId 父记忆的ID。
     * @param startIndex 起始索引（0-based，包含）。
     * @param endIndex 结束索引（0-based，包含）。
     * @return 指定范围内的DocumentChunk列表。
     */
    suspend fun getChunksByRange(memoryId: Long, startIndex: Int, endIndex: Int): List<DocumentChunk> = withContext(Dispatchers.IO) {
        val chunks = getChunksForMemory(memoryId)
        chunks.filter { it.chunkIndex in startIndex..endIndex }
    }

    /**
     * 获取文档的总区块数。
     * @param memoryId 父记忆的ID。
     * @return 总区块数。
     */
    suspend fun getTotalChunkCount(memoryId: Long): Int = withContext(Dispatchers.IO) {
        getChunksForMemory(memoryId).size
    }

    /**
     * 在指定文档的区块内进行搜索。
     * @param memoryId 父记忆的ID。
     * @param query 搜索查询。
     * @return 匹配的DocumentChunk列表。
     */
    suspend fun searchChunksInDocument(memoryId: Long, query: String, limit: Int = 20): List<DocumentChunk> = withContext(Dispatchers.IO) {
        com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "--- Starting search in document (Memory ID: $memoryId) for query: '$query' ---")
        val memory = findMemoryById(memoryId) ?: return@withContext emptyList<DocumentChunk>().also {
            com.ai.assistance.operit.util.AppLogger.w("MemoryRepo", "Document with ID $memoryId not found.")
        }
        if (!memory.isDocumentNode) {
            return@withContext emptyList()
        }
        val validLimit = limit.coerceAtLeast(1)

        if (query.isBlank()) {
            com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Query is blank, returning up to $validLimit chunks sorted by index.")
            return@withContext getChunksForMemory(memoryId).take(validLimit)
        }

        val keywords = splitSearchKeywords(query)
        if (keywords.isEmpty()) {
            return@withContext getChunksForMemory(memoryId).take(validLimit)
        }

        val allChunks = getChunksForMemory(memoryId)
        if (allChunks.isEmpty()) {
            return@withContext emptyList()
        }

        val searchConfig = searchSettingsPreferences.load()
        val resolvedWeights = resolveSearchWeights(
            scoreMode = searchConfig.scoreMode,
            keywordWeight = searchConfig.keywordWeight,
            tagWeight = searchConfig.tagWeight,
            semanticWeight = searchConfig.vectorWeight,
            edgeWeight = searchConfig.edgeWeight,
            keywordCount = 1
        )
        val effectiveKeywordWeight = resolvedWeights.effectiveKeywordWeight
        val effectiveTagWeight = resolvedWeights.effectiveTagWeight
        val effectiveSemanticWeight = resolvedWeights.effectiveSemanticWeight
        val keywordTokensForLexicalMatch = buildLexicalQueryTokens(query, keywords)
        val scores = mutableMapOf<Long, Double>()

        com.ai.assistance.operit.util.AppLogger.d(
            "MemoryRepo",
            "Document chunk search settings => mode=${resolvedWeights.scoreMode}, " +
                "keyword=${String.format(java.util.Locale.getDefault(), "%.2f", effectiveKeywordWeight)}, " +
                "tag=${String.format(java.util.Locale.getDefault(), "%.2f", effectiveTagWeight)} (ignored for chunks), " +
                "semantic=${String.format(java.util.Locale.getDefault(), "%.2f", effectiveSemanticWeight)}, " +
                "semanticNorm=${String.format(java.util.Locale.getDefault(), "%.4f", resolvedWeights.semanticKeywordNormFactor)}, " +
                "edge=${String.format(java.util.Locale.getDefault(), "%.2f", resolvedWeights.effectiveEdgeWeight)} (ignored for chunks)"
        )

        val keywordResults = allChunks
            .mapNotNull { chunk ->
                val matchedTokens = keywordTokensForLexicalMatch.count { token ->
                    textMatchesLexicalToken(chunk.content, token)
                }
                if (matchedTokens > 0) {
                    Pair(chunk, matchedTokens)
                } else {
                    null
                }
            }
            .sortedWith(
                compareByDescending<Pair<DocumentChunk, Int>> { it.second }
                    .thenBy { it.first.chunkIndex }
            )

        if (keywordResults.isNotEmpty()) {
            com.ai.assistance.operit.util.AppLogger.d(
                "MemoryRepo",
                "Document keyword search: ${keywordResults.size} chunk matches"
            )
        }

        if (effectiveKeywordWeight > 0.0) {
            keywordResults.forEachIndexed { index, (chunk, matchedTokenCount) ->
                val rank = index + 1
                val baseScore = computeRrfBaseScore(rank)
                val coverageMultiplier = computeKeywordCoverageMultiplier(
                    matchedTokenCount = matchedTokenCount,
                    totalTokenCount = keywordTokensForLexicalMatch.size
                )
                val weightedScore = baseScore * effectiveKeywordWeight * coverageMultiplier
                scores[chunk.id] = scores.getOrDefault(chunk.id, 0.0) + weightedScore
            }
        }

        val cloudConfig = searchSettingsPreferences.loadCloudEmbedding()
        if (effectiveSemanticWeight > 0.0f && cloudConfig.enabled) {
            check(cloudConfig.isReady()) { "云端嵌入配置不完整" }
            listOf(query.trim()).forEach { keyword ->
                val queryEmbedding = generateEmbedding(keyword, cloudConfig)
                if (queryEmbedding == null) {
                    com.ai.assistance.operit.util.AppLogger.w("MemoryRepo", "Failed to generate chunk embedding query for: '$keyword'")
                    return@forEach
                }

                val semanticResultsWithScores = getSemanticChunkCandidatesFromIndex(
                    memory = memory,
                    queryEmbedding = queryEmbedding
                )
                    .sortedByDescending { it.second }

                if (semanticResultsWithScores.isNotEmpty()) {
                    com.ai.assistance.operit.util.AppLogger.d(
                        "MemoryRepo",
                        "Document semantic search for '$keyword': ${semanticResultsWithScores.size} indexed chunk matches"
                    )
                } else {
                    com.ai.assistance.operit.util.AppLogger.d(
                        "MemoryRepo",
                        "Document semantic search for '$keyword': no indexed chunk matches"
                    )
                }

                semanticResultsWithScores.forEachIndexed { index, (chunk, similarity) ->
                    val rank = index + 1
                    val weightedScore = computeSemanticWeightedScore(
                        rank = rank,
                        baseImportance = 1.0f,
                        similarity = similarity,
                        resolvedWeights = resolvedWeights
                    )
                    scores[chunk.id] = scores.getOrDefault(chunk.id, 0.0) + weightedScore
                }
            }
        }

        if (scores.isEmpty()) {
            com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "No matching chunks found after weighted ranking.")
            return@withContext emptyList()
        }

        val chunkById = allChunks.associateBy { it.id }
        val combinedResults = scores.entries
            .sortedWith(
                compareByDescending<Map.Entry<Long, Double>> { it.value }
                    .thenBy { chunkById[it.key]?.chunkIndex ?: Int.MAX_VALUE }
            )
            .mapNotNull { entry -> chunkById[entry.key] }
            .take(validLimit)
        com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Weighted document chunk results count: ${combinedResults.size}. Limit=$validLimit.")
        com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "--- Search in document finished ---")

        combinedResults
    }

    /**
     * 更新单个文档区块的内容。
     * @param chunkId 要更新的区块ID。
     * @param newContent 新的文本内容。
     */
    suspend fun updateDocument(memory: Memory, title: String, edits: Map<Long, String>) = withContext(Dispatchers.IO) {
        require(title.isNotBlank()) { "标题不能为空" }
        val original = requireNotNull(memoryBox.get(memory.id)) { "文档已被删除" }
        check(original.updatedAt == memory.updatedAt) { "文档已修改，请刷新后重试" }
        val oldChunks = loadChunksForDocument(original)
        require(edits.keys.all { id -> oldChunks.any { it.id == id } }) { "区块不属于当前文档" }
        val config = loadCloudEmbeddingConfig()
        val changed = oldChunks.filter { edits[it.id] != null && edits[it.id] != it.content }
        val oldContents = changed.associate { it.id to it.content }
        changed.forEach { chunk ->
            val content = requireNotNull(edits[chunk.id])
            require(content.isNotBlank() && content.length <= MemoryLibraryPolicy.CHUNK_CHARS) { "区块需要 1 至 1800 个字符" }
            chunk.content = ConversationAuditRedactor.redactText(content).value
            chunk.embedding = generateEmbedding(chunk.content, config)
            markEmbedding(chunk, config)
        }
        val expectedTime = original.updatedAt.time
        original.title = title.trim()
        original.content = oldChunks.firstOrNull()?.content?.take(1000) ?: original.content
        if (!hasCurrentEmbedding(original, config)) {
            original.embedding = generateEmbedding(generateTextForEmbedding(original), config)
            markEmbedding(original, config)
        }
        synchronized(indexLock) {
            addMemoryToIndexInternal(original)
            store.runInTx {
            check(memoryBox.get(original.id)?.updatedAt?.time == expectedTime &&
                oldContents.all { (id, content) -> chunkBox.get(id)?.content == content }) { "文档已修改，请刷新后重试" }
            original.updatedAt = Date(maxOf(System.currentTimeMillis(), expectedTime + 1))
            memoryBox.put(original)
            chunkBox.put(changed)
            }
        }
    }

    suspend fun updateChunk(chunkId: Long, newContent: String) = withContext(Dispatchers.IO) {
        val chunk = requireNotNull(chunkBox.get(chunkId)) { "区块已被删除" }
        val owner = requireNotNull(chunk.memory.target) { "文档已被删除" }
        updateDocument(owner, owner.title, mapOf(chunkId to newContent))
    }

    suspend fun addMemoryToIndex(memory: Memory) = withContext(Dispatchers.IO) {
        addMemoryToIndexInternal(memory)
    }

    suspend fun removeMemoryFromIndex(memory: Memory) = withContext(Dispatchers.IO) {
        removeMemoryFromIndexInternal(memory)
    }

    fun loadCloudEmbeddingConfig(): CloudEmbeddingConfig {
        return searchSettingsPreferences.loadCloudEmbedding()
    }

    fun saveCloudEmbeddingConfig(config: CloudEmbeddingConfig) {
        searchSettingsPreferences.saveCloudEmbedding(config.normalized())
    }

    suspend fun getEmbeddingDimensionUsage(): EmbeddingDimensionUsage = withContext(Dispatchers.IO) {
        // 统计不同时持有全库正文/向量；配置在本次统计内保持一致，避免逐条读偏好并计算模型指纹。
        val modelKey = MemoryLibraryPolicy.modelKey(loadCloudEmbeddingConfig())
        var memoryTotal = 0
        var memoryMissing = 0
        var chunkTotal = 0
        var chunkMissing = 0
        val memoryDimensions = mutableMapOf<Int, Int>()
        val chunkDimensions = mutableMapOf<Int, Int>()
        fun current(embedding: Embedding?, key: String?, hash: String?, text: () -> String): Boolean =
            embedding != null && key == modelKey && MemoryLibraryPolicy.validVector(embedding.vector) &&
                hash == MemoryLibraryPolicy.digest(text())
        memoryBox.query().build().use { query ->
            var offset = 0L
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val page = query.find(offset, 128)
                if (page.isEmpty()) break
                page.filterNot(::isFolderPlaceholderMemory).forEach { memory ->
                    memoryTotal++
                    memory.embedding?.vector?.size?.takeIf { it > 0 }?.let {
                        memoryDimensions[it] = (memoryDimensions[it] ?: 0) + 1
                    }
                    if (!current(memory.embedding, memory.embeddingModelKey, memory.embeddingContentHash) {
                            generateTextForEmbedding(memory)
                        }) memoryMissing++
                }
                offset += page.size
            }
        }
        chunkBox.query().build().use { query ->
            var offset = 0L
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val page = query.find(offset, 128)
                if (page.isEmpty()) break
                page.forEach { chunk ->
                    chunkTotal++
                    chunk.embedding?.vector?.size?.takeIf { it > 0 }?.let {
                        chunkDimensions[it] = (chunkDimensions[it] ?: 0) + 1
                    }
                    if (!current(chunk.embedding, chunk.embeddingModelKey, chunk.embeddingContentHash) {
                            chunk.content
                        }) chunkMissing++
                }
                offset += page.size
            }
        }
        fun counts(values: Map<Int, Int>) = values.entries.sortedByDescending { it.value }
            .map { DimensionCount(dimension = it.key, count = it.value) }
        EmbeddingDimensionUsage(memoryTotal = memoryTotal, memoryMissing = memoryMissing,
            memoryDimensions = counts(memoryDimensions), chunkTotal = chunkTotal,
            chunkMissing = chunkMissing, chunkDimensions = counts(chunkDimensions))
    }

    suspend fun rebuildVectorIndices(onProgress: (EmbeddingRebuildProgress) -> Unit): EmbeddingRebuildProgress = withContext(Dispatchers.IO) {
        check(rebuildMutex.tryLock()) { "当前空间正在构建索引，请等待完成" }
        try {
            val cloudConfig = searchSettingsPreferences.loadCloudEmbedding()
            if (!cloudConfig.isReady()) {
                throw IllegalStateException(context.getString(R.string.memory_embedding_rebuild_requires_config))
            }

            val memories = memoryBox.all
            val documentChunksByMemoryId = memories
                .filter { it.isDocumentNode }
                .associate { memory -> memory.id to loadChunksForDocument(memory) }
            val probeMemory = memories.firstOrNull { generateTextForEmbedding(it).isNotBlank() }
            val probeChunk = if (probeMemory == null) {
                documentChunksByMemoryId.values.asSequence()
                    .flatten()
                    .firstOrNull { it.content.isNotBlank() }
            } else {
                null
            }
            val probeText = probeMemory
                ?.let(::generateTextForEmbedding)
                ?: probeChunk?.content

            if (probeText.isNullOrBlank()) {
                onProgress(
                    EmbeddingRebuildProgress(
                        total = 0,
                        processed = 0,
                        failed = 0,
                        currentStage = "done"
                    )
                )
                return@withContext EmbeddingRebuildProgress(
                    total = 0,
                    processed = 0,
                    failed = 0,
                    currentStage = "done"
                )
            }

            val probeEmbedding = try {
                cloudEmbeddingService.generateEmbeddingOrThrow(cloudConfig, probeText)
            } catch (e: CloudEmbeddingService.CloudEmbeddingException) {
                throw IllegalStateException(e.message ?: context.getString(R.string.memory_embedding_rebuild_probe_failed), e)
            }
            val targetDimension = probeEmbedding.vector.size
            if (targetDimension <= 0) {
                throw IllegalStateException(context.getString(R.string.memory_embedding_rebuild_probe_failed))
            }

            memories.forEach { memory ->
                val textForEmbedding = generateTextForEmbedding(memory)
                if (textForEmbedding.isBlank() && memory.embedding != null) {
                    memory.embedding = null
                    memoryBox.put(memory)
                }
            }
            documentChunksByMemoryId.values.forEach { chunks ->
                val updatedBlankChunks = chunks.filter { chunk ->
                    chunk.content.isBlank() && chunk.embedding != null
                }
                if (updatedBlankChunks.isNotEmpty()) {
                    updatedBlankChunks.forEach { it.embedding = null }
                    chunkBox.put(updatedBlankChunks)
                }
            }

            val memoriesNeedingEmbedding = memories.filter { memory ->
                val textForEmbedding = generateTextForEmbedding(memory)
                if (textForEmbedding.isBlank()) {
                    false
                } else {
                    val vector = memory.embedding?.vector
                    !hasCurrentEmbedding(memory, cloudConfig) || vector?.size != targetDimension
                }
            }
            val chunksNeedingEmbedding = documentChunksByMemoryId.values
                .asSequence()
                .flatten()
                .filter { chunk ->
                    val textForEmbedding = chunk.content
                    if (textForEmbedding.isBlank()) {
                        false
                    } else {
                        val vector = chunk.embedding?.vector
                        !hasCurrentEmbedding(chunk, cloudConfig) || vector?.size != targetDimension
                    }
                }
                .toList()

            var total = memoriesNeedingEmbedding.size + chunksNeedingEmbedding.size
            var processed = 0
            var failed = 0

            fun report(stage: String) {
                onProgress(
                    EmbeddingRebuildProgress(
                        total = total,
                        processed = processed,
                        failed = failed,
                        currentStage = stage
                    )
                )
            }

            report("preparing")

            report("memory_embedding")
            memoriesNeedingEmbedding.forEach { memory ->
                val textForEmbedding = generateTextForEmbedding(memory)
                val embedding = if (probeMemory?.id == memory.id) {
                    probeEmbedding
                } else {
                    generateEmbedding(textForEmbedding, cloudConfig)
                }
                if (embedding == null) {
                    failed += 1
                }
                check(MemoryLibraryPolicy.modelKey(loadCloudEmbeddingConfig()) == MemoryLibraryPolicy.modelKey(cloudConfig)) { "嵌入配置已改变，请重新构建" }
                var saved: Memory? = null
                store.runInTx {
                    val current = memoryBox.get(memory.id)
                    if (current != null && generateTextForEmbedding(current) == generateTextForEmbedding(memory)) {
                        current.embedding = embedding
                        markEmbedding(current, cloudConfig)
                        memoryBox.put(current)
                        saved = current
                    } else failed += 1
                }
                saved?.let { addMemoryToIndexInternal(it) }

                processed += 1
                report("memory_embedding")
            }

            report("chunk_embedding")
            val chunksNeedingEmbeddingByMemoryId = chunksNeedingEmbedding.groupBy { it.memory.targetId }
            chunksNeedingEmbeddingByMemoryId.forEach { (_, chunks) ->
                val updatedChunks = chunks.map { chunk ->
                    val textForEmbedding = chunk.content
                    val embedding = if (probeChunk?.id == chunk.id) {
                        probeEmbedding
                    } else {
                        generateEmbedding(textForEmbedding, cloudConfig)
                    }
                    if (embedding == null) {
                        failed += 1
                    }
                    check(MemoryLibraryPolicy.modelKey(loadCloudEmbeddingConfig()) == MemoryLibraryPolicy.modelKey(cloudConfig)) { "嵌入配置已改变，请重新构建" }
                    store.runInTx {
                        val current = chunkBox.get(chunk.id)
                        if (current != null && current.content == chunk.content && memoryBox.get(current.memory.targetId) != null) {
                            current.embedding = embedding
                            markEmbedding(current, cloudConfig)
                            chunkBox.put(current)
                        } else failed += 1
                    }
                    chunk.memory.target?.let { addMemoryToIndexInternal(it) }

                    processed += 1
                    report("chunk_embedding")
                    chunk
                }

            }

            val indexedMemories = memoryBox.all
            total += indexedMemories.count { createMemoryIndexItem(it) != null } +
                indexedMemories.filter { it.isDocumentNode }.sumOf { memory ->
                    countDocumentChunkIndexableItems(memory)
                }

            report("memory_index")
            rebuildAllMemoryVectorIndices {
                processed += 1
                report("memory_index")
            }

            report("chunk_index")
            rebuildAllDocumentChunkIndices {
                processed += 1
                report("chunk_index")
            }

            processed = total
            report("done")
            EmbeddingRebuildProgress(
                total = total,
                processed = processed,
                failed = failed,
                currentStage = "done"
            )
        } finally {
            rebuildMutex.unlock()
        }
    }

    /**
     * Builds a memory graph from a given list of memories. This is used to display a subset of the
     * entire memory graph, e.g., after a search.
     * @param memories The list of memories to include in the graph.
     * @return A graph of memory facts, independent of presentation.
     */
    suspend fun getGraphForMemories(memories: List<Memory>): MemoryGraph = withContext(Dispatchers.IO) {
        // 调用者已经限定类型、归档、目录与数量，图谱不能再次扩大集合。
        buildGraphFromMemories(memories)

    }

    /** Retrieves a single memory by its UUID. */
    suspend fun getMemoryByUuid(uuid: String): Memory? =
            withContext(Dispatchers.IO) {
                memoryBox.query(Memory_.uuid.equal(uuid)).build().findUnique()
            }

    /**
     * 获取所有唯一的文件夹路径。
     * @return 所有唯一的文件夹路径列表。
     */
    suspend fun getAllFolderPaths(): List<String> = withContext(Dispatchers.IO) {
        val allMemories = memoryBox.all
        com.ai.assistance.operit.util.AppLogger.d("MemoryRepository", "getAllFolderPaths: Total memories: ${allMemories.size}")
        // 只返回真实目录：根（未归类）不是一个可重命名或删除的目录，由调用方用空路径表达，
        // 否则本地化文案会作为路径进入树、下拉框和工具参数。
        // 同时补全中间层级，"工作/项目A" 必然意味着存在 "工作"，逐级浏览不能少一层。
        val folderPaths = allMemories
            .mapNotNull { normalizeStoredFolderPath(it.folderPath) }
            .flatMap { path ->
                path.split('/').runningReduce { parent, name -> "$parent/$name" }
            }
            .distinct()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        com.ai.assistance.operit.util.AppLogger.d("MemoryRepository", "getAllFolderPaths: Unique folders: ${folderPaths.size}")
        folderPaths
    }

    /**
     * 按文件夹路径获取记忆（包括所有子文件夹）。
     * @param folderPath 文件夹路径。
     * @return 该文件夹及其所有子文件夹下的记忆列表。
     */
    suspend fun getMemoriesByFolderPath(folderPath: String): List<Memory> = withContext(Dispatchers.IO) {
        val normalizedTarget = normalizeStoredFolderPath(folderPath)
        if (normalizedTarget == null) {
            memoryBox.all.filter { normalizeStoredFolderPath(it.folderPath) == null }
        } else {
            memoryBox.all.filter { memory ->
                val path = normalizeStoredFolderPath(memory.folderPath) ?: return@filter false
                path == normalizedTarget || path.startsWith("$normalizedTarget/")
            }
        }
    }

    /**
     * 获取指定文件夹的图谱（包括跨文件夹的边）。
     * @param folderPath 文件夹路径。
     * @return 该文件夹的图谱对象。
     */
    suspend fun getGraphForFolder(folderPath: String): MemoryGraph = withContext(Dispatchers.IO) {
        val memories = getMemoriesByFolderPath(folderPath)
        buildGraphFromMemories(memories)
    }

    /**
     * 重命名文件夹（更新该文件夹下所有记忆的 folderPath）。
     * @param oldPath 旧的文件夹路径。
     * @param newPath 新的文件夹路径。
     * @return 是否成功。
     */
    suspend fun renameFolder(oldPath: String, newPath: String): Boolean = withContext(Dispatchers.IO) {
        validateFolderPath(oldPath)
        validateFolderPath(newPath)
        val old = normalizeStoredFolderPath(oldPath) ?: return@withContext false
        val target = normalizeStoredFolderPath(newPath) ?: return@withContext false
        if (old == target) return@withContext true
        require(!target.startsWith("$old/")) { "不能移动到自身的子文件夹" }
        store.callInTx<Boolean> {
            val all = memoryBox.all
            require(all.none { normalizeStoredFolderPath(it.folderPath)?.let { path -> path == target || path.startsWith("$target/") } == true }) { "目标文件夹已存在" }
            val records = all.filter { normalizeStoredFolderPath(it.folderPath)?.let { path -> path == old || path.startsWith("$old/") } == true }
            require(records.isNotEmpty()) { "原文件夹已不存在" }
            records.forEach { memory ->
                memory.folderPath = target + requireNotNull(normalizeStoredFolderPath(memory.folderPath)).removePrefix(old)
                memory.updatedAt = Date(maxOf(System.currentTimeMillis(), memory.updatedAt.time + 1))
            }
            memoryBox.put(records)
            true
        }
    }

    /**
     * 移动记忆到新文件夹。
     * @param memoryIds 要移动的记忆ID列表。
     * @param targetFolderPath 目标文件夹路径。
     * @return 是否成功。
     */
    suspend fun moveMemoriesToFolder(memoryIds: List<Long>, targetFolderPath: String): Boolean = withContext(Dispatchers.IO) {
        validateFolderPath(targetFolderPath)
        require(memoryIds.isNotEmpty()) { "请选择要移动的条目" }
        val target = normalizeStoredFolderPath(targetFolderPath)
        store.callInTx<Boolean> {
            val records = memoryIds.distinct().map { requireNotNull(memoryBox.get(it)) { "条目已被删除，请刷新后重试" } }
            records.forEach {
                it.folderPath = target
                it.updatedAt = Date(maxOf(System.currentTimeMillis(), it.updatedAt.time + 1))
            }
            memoryBox.put(records)
            true
        }
    }

    /**
     * 创建新文件夹（实际上是通过在该路径下创建一个占位记忆来实现）。
     * @param folderPath 新文件夹的路径。
     * @return 是否成功。
     */
    suspend fun createFolder(folderPath: String): Boolean = withContext(Dispatchers.IO) {
        validateFolderPath(folderPath)
        // 失败原因必须能传到调用方：吞掉异常只会留下一句「无法创建文件夹」。
        val normalizedFolderPath = requireNotNull(normalizeStoredFolderPath(folderPath)) { "请输入文件夹名称" }
        // 已存在的路径不是一次成功创建；沿用旧的静默返回会让重名提交看起来生效。
        require(memoryBox.all.none {
            val path = normalizeStoredFolderPath(it.folderPath)
            path == normalizedFolderPath || path?.startsWith("$normalizedFolderPath/") == true
        }) { "目标文件夹已存在" }

        // 创建一个占位记忆
        val placeholder = Memory(
            title = FOLDER_PLACEHOLDER_TITLE,
            content = context.getString(R.string.memory_repository_folder_description_content, normalizedFolderPath),
            source = FOLDER_PLACEHOLDER_SOURCE,
            uuid = UUID.randomUUID().toString(),
            folderPath = normalizedFolderPath
        )
        memoryBox.put(placeholder)
        addMemoryToIndexInternal(placeholder)
        true
    }

    /**
     * 创建新记忆并自动生成embedding，保存到数据库并同步索引。
     */
    suspend fun createMemory(
        title: String,
        content: String,
        contentType: String = "text/plain",
        source: String = "user_input",
        folderPath: String = "",
        tags: List<String>? = null,
        credibility: Float = 0.8f,
        importance: Float = 0.5f,
        libraryKind: String = MemoryLibraryPolicy.MEMORY,
        category: String = "other"
    ): Memory? = withContext(Dispatchers.IO) {
        require(title.isNotBlank() && content.isNotBlank()) { "标题和正文不能为空" }
        require(libraryKind in listOf(MemoryLibraryPolicy.MEMORY, MemoryLibraryPolicy.KNOWLEDGE))
        require(category in MemoryLibraryPolicy.categories)
        val normalizedTags = tags
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
        // 同一目录内标题即身份：允许重名会让按标题定位的更新、移动和删除全部失去唯一目标。
        val targetFolder = normalizeStoredFolderPath(folderPath)
        findMemoriesByTitle(title.trim()).firstOrNull { existing ->
            !existing.archived && normalizeStoredFolderPath(existing.folderPath) == targetFolder &&
                MemoryLibraryPolicy.kind(existing) == libraryKind
        }?.let { existing ->
            throw IllegalArgumentException(
                "同目录已存在同名条目（uuid: ${existing.uuid}）。请改用 update_memory(uuid=…) 更新，或换一个标题。"
            )
        }

        val memory = Memory(
            title = title,
            content = content,
            contentType = contentType,
            source = source,
            credibility = credibility.coerceIn(0f, 1f),
            importance = importance.coerceIn(0f, 1f),
            libraryKind = libraryKind,
            category = category,
            folderPath = normalizeStoredFolderPath(folderPath)
        )
        if (!normalizedTags.isNullOrEmpty()) {
            normalizedTags.forEach { tagName ->
                val tag =
                    tagBox.query(MemoryTag_.name.equal(tagName, QueryBuilder.StringOrder.CASE_SENSITIVE))
                        .build().use { it.findFirst() }
                        ?: MemoryTag(name = tagName)
                memory.tags.add(tag)
            }
        }
        // ToMany 随主体 put 事务一起保存，不在正文成功之后执行第二次标签写入。
        saveMemory(memory)
        memory
    }

    /**
     * 更新已有记忆内容（title/content等），自动更新embedding和索引。
     */
    suspend fun updateMemory(
        memory: Memory,
        newTitle: String,
        newContent: String,
        newContentType: String = memory.contentType,
        newSource: String = memory.source,
        newCredibility: Float = memory.credibility,
        newImportance: Float = memory.importance,
        newFolderPath: String? = memory.folderPath,
        newTags: List<String>? = null,
        newLibraryKind: String = MemoryLibraryPolicy.kind(memory),
        newCategory: String = MemoryLibraryPolicy.category(memory)
    ): Memory? = withContext(Dispatchers.IO) {
        newFolderPath?.let(::validateFolderPath)
        require(!memory.isDocumentNode || newContent == memory.content) { "文档正文请按区块编辑，不能只覆盖摘要" }
        val expectedUpdatedAt = memory.updatedAt.time
        val draft = requireNotNull(memoryBox.get(memory.id)) { "条目已被删除" }
        check(draft.updatedAt.time == expectedUpdatedAt) { "条目已被其他操作修改，请刷新后重试" }
        require(newLibraryKind in listOf(MemoryLibraryPolicy.MEMORY, MemoryLibraryPolicy.KNOWLEDGE))
        require(newCategory == MemoryLibraryPolicy.category(memory) || newCategory.isBlank() || newCategory in MemoryLibraryPolicy.categories)
        val cloudConfig = searchSettingsPreferences.loadCloudEmbedding()
        val previousDimension = draft.embedding?.vector?.size
        val sanitizedCredibility = newCredibility.coerceIn(0.0f, 1.0f)
        val sanitizedImportance = newImportance.coerceIn(0.0f, 1.0f)
        require(newTitle.isNotBlank() && newContent.isNotBlank()) { "标题和正文不能为空" }
        val titleChanged = draft.title != newTitle
        val contentChanged = draft.content != newContent
        // 重命名或移动都可能撞上同目录同名条目；写入前拒绝，避免事后无法按标题定位。
        val nextFolder = normalizeStoredFolderPath(newFolderPath)
        if (titleChanged || nextFolder != normalizeStoredFolderPath(draft.folderPath)) {
            findMemoriesByTitle(newTitle.trim()).firstOrNull { existing ->
                existing.id != draft.id && !existing.archived &&
                    normalizeStoredFolderPath(existing.folderPath) == nextFolder
            }?.let { existing ->
                throw IllegalArgumentException("同目录已存在同名条目（uuid: ${existing.uuid}），请换一个标题或目录。")
            }
        }

        val needsReEmbedding =
            contentChanged || titleChanged || !hasCurrentEmbedding(draft, cloudConfig)

        // 更新记忆属性
        draft.apply {
            title = ConversationAuditRedactor.redactText(newTitle).value
            content = ConversationAuditRedactor.redactText(newContent).value
            contentType = newContentType
            source = ConversationAuditRedactor.redactText(newSource).value
            libraryKind = newLibraryKind
            category = newCategory.ifBlank { "other" }
            credibility = sanitizedCredibility
            importance = sanitizedImportance
            folderPath = normalizeStoredFolderPath(newFolderPath)
        }

        val newEmbedding = if (needsReEmbedding) {
            val textForEmbedding = generateTextForEmbedding(draft)
            generateEmbedding(textForEmbedding, cloudConfig)
        } else {
            draft.embedding
        }
        draft.embedding = newEmbedding
        markEmbedding(draft, cloudConfig)

        // 更新标签
        if (newTags != null) {
            draft.tags.clear() // 清除旧标签
            newTags.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { tagName ->
                // Find existing tag or create a new one
                val tag = tagBox.query(MemoryTag_.name.equal(tagName, QueryBuilder.StringOrder.CASE_SENSITIVE))
                    .build().use { it.findFirst() } ?: MemoryTag(name = tagName)
                draft.tags.add(tag)
            }
        }

        // 更新记忆属性
        draft.apply {
            this.updatedAt = Date(maxOf(System.currentTimeMillis(), expectedUpdatedAt + 1))
        }

        // 这里不再需要调用 saveMemory，因为 memory 对象已经被修改，
        // 最后的 memoryBox.put(memory) 会保存所有更改。
        synchronized(indexLock) {
            if (needsReEmbedding) addMemoryToIndexInternal(draft, previousDimension = previousDimension)
            store.runInTx {
                check(memoryBox.get(draft.id)?.updatedAt?.time == expectedUpdatedAt) { "条目已被其他操作修改，请刷新后重试" }
                memoryBox.put(draft)
            }
        }
        draft
    }

    /**
     * Merges multiple source memories into a single new memory, redirecting all links.
     */
    suspend fun mergeMemories(
        sourceTitles: List<String>,
        newTitle: String,
        newContent: String,
        newTags: List<String>,
        folderPath: String
    ): Memory? = withContext(Dispatchers.IO) {
        // Step 1: Find all unique source memories from the given titles.
        // Using a Set ensures that we handle each memory object only once, even if titles are duplicated.
        val sourceMemories = mutableSetOf<Memory>()
        for (title in sourceTitles.distinct()) {
            val matches = findMemoriesByTitle(title).filter { !it.archived }
            require(matches.size <= 1) { "同名条目不能自动合并，请先核查 UUID" }
            sourceMemories.addAll(matches)
        }

        // After finding all memories, check if we have enough to merge.
        if (sourceMemories.size < 2) {
            com.ai.assistance.operit.util.AppLogger.w("MemoryRepo", "Merge requires at least two unique source memories to be found. Found: ${sourceMemories.size} from titles: ${sourceTitles.joinToString()}.")
            return@withContext null
        }

        var newMemory: Memory? = null
        try {
            store.runInTx {
                // 2. Create the new merged memory (without embedding yet)
                val mergedMemory = Memory(
                    title = newTitle,
                    content = newContent,
                    folderPath = normalizeStoredFolderPath(folderPath),
                    source = "merged_from_memory"
                )
                memoryBox.put(mergedMemory) // Save to get an ID

                // 3. Add tags to the new memory
                newTags.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { tagName ->
                    val tag = tagBox.query(MemoryTag_.name.equal(tagName, QueryBuilder.StringOrder.CASE_SENSITIVE))
                        .build().use { it.findFirst() } ?: MemoryTag(name = tagName)
                    mergedMemory.tags.add(tag)
                }
                memoryBox.put(mergedMemory)

                // 4. Collect all unique links and redirect them
                val allLinksToProcess = mutableSetOf<MemoryLink>()
                val sourceIdsSet = sourceMemories.map { it.id }.toSet()

                sourceMemories.forEach {
                    it.links.reset()
                    it.backlinks.reset()
                    allLinksToProcess.addAll(it.links)
                    allLinksToProcess.addAll(it.backlinks)
                }

                allLinksToProcess.forEach { link ->
                    if (link.source.targetId in sourceIdsSet) {
                        link.source.target = mergedMemory
                    }
                    if (link.target.targetId in sourceIdsSet) {
                        link.target.target = mergedMemory
                    }
                }
                linkBox.put(allLinksToProcess.toList())

                // 自动整理只退役原条目，保留正文与来源供用户核查或恢复。
                sourceMemories.forEach { it.archived = true; it.updatedAt = Date() }
                memoryBox.put(sourceMemories)

                newMemory = mergedMemory
            }

            // After the transaction, handle non-transactional parts
            newMemory?.let { memory ->
                val cloudConfig = searchSettingsPreferences.loadCloudEmbedding()
                // Generate and save embedding for the new memory
                val textForEmbedding = generateTextForEmbedding(memory)
                memory.embedding = generateEmbedding(textForEmbedding, cloudConfig)
                markEmbedding(memory, cloudConfig)
                memoryBox.put(memory)

                sourceMemories.forEach(::removeMemoryFromIndexInternal)
                addMemoryToIndexInternal(memory)
            }
        } catch (e: Exception) {
            com.ai.assistance.operit.util.AppLogger.e("MemoryRepo", "Error during memory merge transaction.", e)
            return@withContext null
        }

        newMemory
    }

    /**
     * 根据UUID批量删除记忆及其所有关联。
     * @param uuids 要删除的记忆的UUID集合。
     * @return 如果操作成功，返回true。
     */
    suspend fun deleteMemoriesByUuids(uuids: Set<String>): Boolean = withContext(Dispatchers.IO) {
        com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Attempting to delete memories with UUIDs: $uuids")
        if (uuids.isEmpty()) {
            com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "UUID set is empty, nothing to delete.")
            return@withContext true
        }

        // 使用QueryBuilder动态构建OR查询
        val builder = memoryBox.query()
        // ObjectBox的QueryBuilder.equal()不支持字符串，我们必须从Property本身开始构建条件
        if (uuids.isNotEmpty()) {
            var finalCondition: QueryCondition<Memory> = Memory_.uuid.equal(uuids.first())
            uuids.drop(1).forEach { uuid ->
                finalCondition = finalCondition.or(Memory_.uuid.equal(uuid))
            }
            builder.apply(finalCondition)
        }
        val memoriesToDelete = builder.build().find()

        com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Found ${memoriesToDelete.size} memories to delete.")
        if (memoriesToDelete.isEmpty()) {
            return@withContext true
        }

        // 在一个事务中执行所有数据库写入操作
        try {
            store.runInTx {
                // 1. 收集所有相关链接和区块的ID
                val memoryIdsToDelete = memoriesToDelete.map { it.id }.toSet()
                val linkIdsToDelete =
                    collectLinkIdsForDeletion(memoryIdsToDelete, includeDangling = false)
                        .toMutableSet()
                val chunkIdsToDelete = mutableSetOf<Long>()
                for (memory in memoriesToDelete) {
                    if (memory.isDocumentNode) {
                        memory.documentChunks.reset()
                        memory.documentChunks.forEach { chunk -> chunkIdsToDelete.add(chunk.id) }
                    }
                }
                com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Found ${linkIdsToDelete.size} unique links and ${chunkIdsToDelete.size} chunks to delete.")

                // 2. 批量删除链接和区块
                if (linkIdsToDelete.isNotEmpty()) {
                    linkBox.removeByIds(linkIdsToDelete)
                    com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Bulk-deleted ${linkIdsToDelete.size} links.")
                }
                if (chunkIdsToDelete.isNotEmpty()) {
                    chunkBox.removeByIds(chunkIdsToDelete)
                    com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Bulk-deleted ${chunkIdsToDelete.size} chunks.")
                }

                // 3. 批量删除记忆本身
                memoryBox.removeByIds(memoryIdsToDelete.toList())
                com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Bulk-deleted ${memoriesToDelete.size} memories.")
            }
            com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Transaction completed successfully.")
        } catch (e: Exception) {
            com.ai.assistance.operit.util.AppLogger.e("MemoryRepo", "Error during bulk delete transaction.", e)
            return@withContext false
        }

        // 4. 条目已从库中移除，受影响维度只需重建一次；逐条重建会在批量删除时放大成 O(n²) 写入。
        synchronized(indexLock) {
            rebuildAffectedMemoryVectorIndices(memoriesToDelete.map { it.embedding?.vector?.size })
            memoriesToDelete.filter { it.isDocumentNode }
                .forEach { memory -> deleteIndexFileIfExists(memory.chunkIndexFilePath?.let(::File)) }
        }
        com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Removed deleted memories from vector indices and cleaned up chunk index files.")

        return@withContext true
    }

    // --- Graph Export ---

    /** Fetches all memories and their links without constructing UI presentation objects. */
    suspend fun getMemoryGraph(): MemoryGraph = withContext(Dispatchers.IO) {
        cleanupDanglingLinksIfNeeded()
        buildGraphFromMemories(memoryBox.all)
    }

    /**
     * Private helper to construct a graph from a specific list of memories. Ensures that edges are
     * only created if both source and target nodes are in the list.
     * @param memories 要构建图谱的记忆列表
     */
    private fun buildGraphFromMemories(memories: List<Memory>): MemoryGraph {
        val memoryUuids = memories.map { it.uuid }.toSet()

        val nodes =
                memories.map { memory ->
                    MemoryGraphNode(
                            id = memory.uuid,
                            title = memory.title,
                            type =
                                    if (memory.isDocumentNode) {
                                        MemoryGraphNodeType.DOCUMENT
                                    } else {
                                        when (memory.tags.firstOrNull()?.name) {
                                            "Person" -> MemoryGraphNodeType.PERSON
                                            "Concept" -> MemoryGraphNodeType.CONCEPT
                                            else -> MemoryGraphNodeType.OTHER
                                        }
                                    }
                    )
                }

        val edges = mutableListOf<MemoryGraphEdge>()
        memories.forEach { memory ->
            // 关键：重置关系缓存，确保获取最新的连接信息
            memory.links.reset()
            memory.links.forEach { link ->
                val sourceMemory = link.source.target
                val targetMemory = link.target.target
                val sourceId = sourceMemory?.uuid
                val targetId = targetMemory?.uuid
                
                // Only add edges if both source and target are in the filtered list
                if (sourceId != null &&
                    targetId != null &&
                    sourceId in memoryUuids &&
                    targetId in memoryUuids
                ) {
                    // 检测是否为跨文件夹连接
                    // 始终检测跨文件夹连接，无论是否选择了特定文件夹
                    val sourcePath = normalizeStoredFolderPath(sourceMemory.folderPath) ?: ROOT_FOLDER_TOKEN
                    val targetPath = normalizeStoredFolderPath(targetMemory.folderPath) ?: ROOT_FOLDER_TOKEN
                    val isCrossFolder = sourcePath != targetPath
                    
                    edges.add(
                        MemoryGraphEdge(
                            id = link.id,
                            sourceId = sourceId,
                            targetId = targetId,
                            label = link.type,
                            weight = link.weight,
                            isCrossFolderLink = isCrossFolder
                        )
                    )
                }
            }
        }
        val distinctEdges = edges.distinct()
        com.ai.assistance.operit.util.AppLogger.d(
                "MemoryRepo",
                "Built graph with ${nodes.size} nodes and ${distinctEdges.size} edges."
        )
        return MemoryGraph(nodes = nodes, edges = distinctEdges)
    }

    /**
     * 删除文件夹：将所有属于 folderPath 的记忆移动到"未分类"
     */
    suspend fun deleteFolder(folderPath: String) {
        withContext(Dispatchers.IO) {
            // 根不是目录：删除根会把「解除归属」伪装成一次成功的目录删除。
            val normalizedTarget = requireNotNull(normalizeStoredFolderPath(folderPath)) { "根目录不能删除" }
            val memories = memoryBox.all.filter {
                val path = normalizeStoredFolderPath(it.folderPath)
                path == normalizedTarget || path?.startsWith("$normalizedTarget/") == true
            }
            // 删除目录结构，保留所有正文、文档块和关系；子目录必须一起解除归属，
            // 否则虚拟树会立即从子路径重新生成刚刚删除的父目录。
            store.runInTx {
                memories.forEach { memory ->
                    memory.folderPath = null
                    memory.updatedAt = Date(maxOf(System.currentTimeMillis(), memory.updatedAt.time + 1))
                }
                memoryBox.put(memories)
            }
            com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Deleted folder '$folderPath', moved ${memories.size} memories to uncategorized")
        }
    }

    /**
     * 导出所有记忆（不包括文档节点）为 JSON 字符串
     * @return JSON 格式的记忆库数据
     */
    suspend fun exportMemoriesToJson(): String = withContext(Dispatchers.IO) {
        // 获取所有非文档节点的记忆
        val memories = memoryBox.all
        
        // 转换为可序列化格式
        val serializableMemories = memories.map { memory ->
            // 获取标签名称
            memory.tags.reset()
            val tagNames = memory.tags.map { it.name }
            
            SerializableMemory(
                uuid = memory.uuid,
                title = memory.title,
                content = memory.content,
                contentType = memory.contentType,
                source = memory.source,
                credibility = memory.credibility,
                importance = memory.importance,
                folderPath = memory.folderPath,
                createdAt = memory.createdAt,
                updatedAt = memory.updatedAt,
                tagNames = tagNames,
                libraryKind = MemoryLibraryPolicy.kind(memory),
                category = MemoryLibraryPolicy.category(memory),
                archived = memory.archived,
                isDocumentNode = memory.isDocumentNode,
                documentPath = memory.documentPath,
                chunks = if (memory.isDocumentNode) loadChunksForDocument(memory).map { it.content } else emptyList()
            )
        }
        
        // 获取所有链接关系（只包含非文档节点之间的链接）
        val memoryUuids = memories.map { it.uuid }.toSet()
        val serializableLinks = mutableListOf<SerializableLink>()
        
        memories.forEach { memory ->
            memory.links.reset()
            memory.links.forEach { link ->
                val sourceUuid = link.source.target?.uuid
                val targetUuid = link.target.target?.uuid
                
                // 只导出两端都是非文档节点的链接
                if (sourceUuid != null && targetUuid != null && 
                    sourceUuid in memoryUuids && targetUuid in memoryUuids) {
                    serializableLinks.add(
                        SerializableLink(
                            sourceUuid = sourceUuid,
                            targetUuid = targetUuid,
                            type = link.type,
                            weight = link.weight,
                            description = link.description
                        )
                    )
                }
            }
        }
        
        // 创建导出数据
        val exportData = MemoryExportData(
            memories = serializableMemories,
            links = serializableLinks.distinct(), // 去重
            exportDate = Date(),
            version = "2.0"
        )
        
        // 序列化为 JSON
        val json = Json { 
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        json.encodeToString(exportData)
    }
    
    /**
     * 从 JSON 字符串导入记忆
     * @param jsonString JSON 格式的记忆库数据
     * @param strategy 导入策略（遇到重复记忆时的处理方式）
     * @return 导入结果统计
     */
    suspend fun importMemoriesFromJson(
        jsonString: String,
        strategy: ImportStrategy = ImportStrategy.SKIP
    ): MemoryImportResult = withContext(Dispatchers.IO) {
        val json = Json { 
            ignoreUnknownKeys = true
        }
        
        try {
            val exportData = json.decodeFromString<MemoryExportData>(jsonString)
            
            require(exportData.version in listOf("1.0", "2.0")) { "不支持的记忆备份版本" }
            require(exportData.memories.map { it.uuid }.distinct().size == exportData.memories.size) { "备份包含重复 UUID" }
            exportData.memories.forEach { item ->
                require(item.uuid.isNotBlank() && item.title.isNotBlank()) { "备份条目标识无效" }
                require(item.credibility.isFinite() && item.importance.isFinite()) { "备份评分无效" }
                require(item.content.length <= MemoryLibraryPolicy.MAX_DOCUMENT_CHARS && item.chunks.sumOf { it.length.toLong() } <= 3_000_000L) { "备份条目过大" }
            }
            synchronized(indexLock) {
                // 缓存失效失败必须发生在事务前，否则调用者重试 CREATE_NEW 会重复导入。
                (currentProfileMemoryIndexFiles() + currentProfileDocumentIndexFiles()).forEach(::deleteIndexFileIfExists)
                store.callInTx(java.util.concurrent.Callable {
                var newCount = 0
                var updatedCount = 0
                var skippedCount = 0
                val uuidMap = mutableMapOf<String, Memory>() // 旧UUID -> 新Memory对象
            
                // 导入记忆
                exportData.memories.forEach { serializableMemory ->
                    val existingMemory = memoryBox.query(Memory_.uuid.equal(serializableMemory.uuid))
                        .build().findFirst()
                
                    when {
                        existingMemory != null && strategy == ImportStrategy.SKIP -> {
                            skippedCount++
                            uuidMap[serializableMemory.uuid] = existingMemory
                        }
                    
                        existingMemory != null && strategy == ImportStrategy.UPDATE -> {
                            // 更新现有记忆
                            existingMemory.apply {
                                title = ConversationAuditRedactor.redactText(serializableMemory.title).value
                                content = ConversationAuditRedactor.redactText(serializableMemory.content).value
                                contentType = serializableMemory.contentType
                                source = serializableMemory.source
                                credibility = serializableMemory.credibility
                                importance = serializableMemory.importance
                                folderPath = normalizeStoredFolderPath(serializableMemory.folderPath)
                                updatedAt = Date()
                                libraryKind = serializableMemory.libraryKind
                                category = serializableMemory.category
                                archived = serializableMemory.archived
                                isDocumentNode = serializableMemory.isDocumentNode
                                documentPath = serializableMemory.documentPath
                                embedding = null
                                embeddingModelKey = ""
                                embeddingContentHash = ""
                                chunkIndexFilePath = null
                            }
                            memoryBox.put(existingMemory)
                            replaceImportedChunks(existingMemory, serializableMemory.chunks)
                            updatedCount++
                            uuidMap[serializableMemory.uuid] = existingMemory

                            // 更新标签
                            updateMemoryTags(existingMemory, serializableMemory.tagNames)
                        }
                        
                        else -> {
                            // 创建新记忆
                            val newMemory = createMemoryFromSerializable(
                                serializableMemory,
                                strategy == ImportStrategy.CREATE_NEW
                            )
                            newCount++
                            uuidMap[serializableMemory.uuid] = newMemory
                        }
                    }
                }
            
                // 导入链接关系
                var newLinksCount = 0
                exportData.links.forEach { serializableLink ->
                    val sourceMemory = uuidMap[serializableLink.sourceUuid]
                    val targetMemory = uuidMap[serializableLink.targetUuid]
                
                    if (sourceMemory != null && targetMemory != null) {
                        // 检查链接是否已存在 - 查询所有链接并手动过滤
                        val existingLink = sourceMemory.links.find { link ->
                            link.target.target?.id == targetMemory.id &&
                            link.type == serializableLink.type
                        }
                    
                        if (existingLink == null) {
                            val newLink = MemoryLink(
                                type = serializableLink.type,
                                weight = serializableLink.weight,
                                description = serializableLink.description
                            )
                            newLink.source.target = sourceMemory
                            newLink.target.target = targetMemory
                            // 将链接添加到源记忆的 links 集合中，并保存源记忆
                            // 这与 linkMemories 方法保持一致
                            sourceMemory.links.add(newLink)
                            memoryBox.put(sourceMemory)
                            newLinksCount++
                        }
                    }
                }

                com.ai.assistance.operit.util.AppLogger.d("MemoryRepo", "Import completed: $newCount new, $updatedCount updated, $skippedCount skipped, $newLinksCount links")

                MemoryImportResult(
                    newMemories = newCount,
                    updatedMemories = updatedCount,
                    skippedMemories = skippedCount,
                    newLinks = newLinksCount
                )

                })
            }
        } catch (e: Exception) {
            com.ai.assistance.operit.util.AppLogger.e("MemoryRepo", "Failed to import memories", e)
            throw e
        }
    }
    
    /**
     * 从可序列化的记忆数据创建 Memory 对象
     * @param serializable 可序列化的记忆数据
     * @param forceNewUuid 是否强制生成新的 UUID
     * @return 创建的 Memory 对象
     */
    private fun createMemoryFromSerializable(
        serializable: SerializableMemory,
        forceNewUuid: Boolean
    ): Memory {
        val memory = Memory(
            uuid = if (forceNewUuid) UUID.randomUUID().toString() else serializable.uuid,
            title = ConversationAuditRedactor.redactText(serializable.title).value,
            content = ConversationAuditRedactor.redactText(serializable.content).value,
            contentType = serializable.contentType,
            source = serializable.source,
            credibility = serializable.credibility,
            importance = serializable.importance,
            folderPath = normalizeStoredFolderPath(serializable.folderPath),
            createdAt = serializable.createdAt,
            updatedAt = serializable.updatedAt,
            libraryKind = serializable.libraryKind,
            category = serializable.category,
            archived = serializable.archived,
            isDocumentNode = serializable.isDocumentNode,
            documentPath = serializable.documentPath
        )
        
        memoryBox.put(memory)
        
        // 添加标签
        updateMemoryTags(memory, serializable.tagNames)
        replaceImportedChunks(memory, serializable.chunks)
        
        return memory
    }
    
    /**
     * 更新记忆的标签
     * @param memory 要更新的记忆
     * @param tagNames 标签名称列表
     */
    private fun replaceImportedChunks(memory: Memory, parts: List<String>) {
        store.runInTx {
            val oldIds = loadChunksForDocument(memory).map { it.id }
            chunkBox.removeByIds(oldIds)
            val chunks = if (memory.isDocumentNode) parts.mapIndexed { index, text ->
                DocumentChunk(content = ConversationAuditRedactor.redactText(text).value, chunkIndex = index).also { it.memory.target = memory }
            } else emptyList()
            chunkBox.put(chunks)
        }
    }

    private fun updateMemoryTags(memory: Memory, tagNames: List<String>) {
        memory.tags.clear()
        
        tagNames.forEach { tagName ->
            val tag = tagBox.query(MemoryTag_.name.equal(tagName)).build().findFirst()
                ?: MemoryTag(name = tagName).also { tagBox.put(it) }
            memory.tags.add(tag)
        }
        
        memoryBox.put(memory)
    }

}
