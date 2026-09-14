package com.ai.assistance.operit.util.vector

import com.github.jelmerk.hnswlib.core.DistanceFunctions
import com.github.jelmerk.hnswlib.core.Item
import com.github.jelmerk.hnswlib.core.hnsw.HnswIndex
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 精简的HNSW向量索引管理器，支持初始化、添加、查询、保存、加载。
 */
class VectorIndexManager<T : Item<Id, FloatArray>, Id : Any>(
    private val dimensions: Int,
    private val maxElements: Int,
    private val indexFile: File? = null
) {
    private var index: HnswIndex<Id, FloatArray, T, Float>? = null

    init {
        initIndex()
    }

    /** 初始化索引（新建或加载） */
    fun initIndex() {
        index = if (indexFile != null && indexFile.exists()) {
            // 损坏必须被调用者看到；空索引不能伪装成成功加载。
            readPersistedIndex(indexFile)
        } else {
            HnswIndex
                .newBuilder(dimensions, DistanceFunctions.FLOAT_COSINE_DISTANCE, maxElements)
                .withRemoveEnabled()
                .build()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readPersistedIndex(file: File): HnswIndex<Id, FloatArray, T, Float> {
        return ObjectInputStream(file.inputStream()).use { stream ->
            val restored = stream.readObject()
            require(restored is HnswIndex<*, *, *, *>) {
                "Persisted vector index has unexpected type: ${restored.javaClass.name}"
            }
            // Java serialization erases generic arguments; the manager's file ownership fixes them by construction.
            restored as HnswIndex<Id, FloatArray, T, Float>
        }
    }

    /** 添加一个向量项 */
    fun addItem(item: T) {
        ensureCapacity(size() + 1)
        index?.add(item)
    }

    /** 删除一个向量项。 */
    fun removeItem(id: Id, version: Long = Long.MAX_VALUE): Boolean {
        return index?.remove(id, version) ?: false
    }

    /** 查询最近的K个邻居 */
    fun findNearest(query: FloatArray, k: Int): List<T> {
        return index?.findNearest(query, k)?.map { it.item() } ?: emptyList()
    }

    fun size(): Int = index?.size() ?: 0

    fun maxItemCount(): Int = index?.maxItemCount ?: maxElements

    fun ensureCapacity(minCapacity: Int) {
        val current = index ?: return
        if (minCapacity > current.maxItemCount) {
            current.resize(minCapacity)
        }
    }

    /** 保存索引到文件 */
    fun save() {
        if (indexFile != null && index != null) {
            indexFile.parentFile?.mkdirs()
            val temporary = File.createTempFile("vector-", ".tmp", indexFile.parentFile)
            try {
                ObjectOutputStream(temporary.outputStream()).use { it.writeObject(index) }
                Files.move(temporary.toPath(), indexFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }
    }

    /** 关闭索引（可选） */
    fun close() {
        index = null
    }
}
