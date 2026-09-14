package com.ai.assistance.operit.data.model

import java.security.MessageDigest

/** 人工整理和 Agent 检索共享语义，不能由 UI 各自解释类型或归档。 */
object MemoryLibraryPolicy {
    const val MEMORY = "memory"
    const val KNOWLEDGE = "knowledge"
    val categories = listOf("other", "preference", "fact", "decision", "experience", "event")
    const val MAX_DOCUMENT_CHARS = 2_000_000
    const val CHUNK_CHARS = 1800
    const val CHUNK_OVERLAP = 160

    fun kind(memory: Memory): String =
        if (memory.isDocumentNode || memory.libraryKind == KNOWLEDGE) KNOWLEDGE else MEMORY

    fun category(memory: Memory): String = memory.category?.takeIf { it.isNotBlank() } ?: "other"

    fun matches(memory: Memory, kind: String?, archived: Boolean = false): Boolean =
        memory.archived == archived && (kind == null || kind(memory) == kind) &&
            memory.title != ".folder_placeholder" && !memory.title.startsWith("__folder_placeholder__")

    fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** 不含凭据；更换模型或端点后，来源未知及旧模型的向量均不参与比较。 */
    fun modelKey(config: CloudEmbeddingConfig): String =
        digest("memory-v2\n${config.normalized().endpoint.trimEnd('/')}\n${config.model.trim()}")

    fun validVector(vector: FloatArray): Boolean =
        vector.isNotEmpty() && vector.all { it.isFinite() } && vector.any { it != 0f }

    fun compatible(embedding: Embedding?, key: String?, hash: String?, config: CloudEmbeddingConfig, text: String): Boolean =
        embedding != null && validVector(embedding.vector) && key == modelKey(config) && hash == digest(text)

    /** 有界重叠避免超长单段，也保证正文字符不被静默截断。 */
    fun chunks(text: String): List<String> {
        require(text.isNotBlank()) { "文档没有可读取的文字" }
        require(text.length <= MAX_DOCUMENT_CHARS) { "文档超过 200 万字符，请拆分后导入" }
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + CHUNK_CHARS, text.length)
            if (end < text.length) {
                val boundary = (end - 1 downTo start + CHUNK_CHARS / 2).firstOrNull {
                    text[it] == '\n' || text[it] in "。！？.!?"
                }
                if (boundary != null) end = boundary + 1
                if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
            }
            text.substring(start, end).takeIf { it.isNotBlank() }?.let(result::add)
            if (end == text.length) break
            start = maxOf(start + 1, end - CHUNK_OVERLAP)
            if (Character.isLowSurrogate(text[start])) start++
        }
        return result
    }
}
