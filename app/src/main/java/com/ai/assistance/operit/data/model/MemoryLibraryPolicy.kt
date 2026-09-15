package com.ai.assistance.operit.data.model

import java.security.MessageDigest

/** 人工整理和 Agent 检索共享语义，不能由 UI 各自解释类型或归档。 */
object MemoryLibraryPolicy {
    const val MEMORY = "memory"
    /** 工作过程记录；与长期记忆和参考知识共用同一事实库。 */
    const val DIARY = "diary"
    const val KNOWLEDGE = "knowledge"

    /** 界面分段与工具参数共用的内容类型表，顺序即展示顺序。 */
    val kinds = listOf(MEMORY, DIARY, KNOWLEDGE)
    val categories = listOf("other", "preference", "fact", "decision", "experience", "event")
    const val MAX_DOCUMENT_CHARS = 2_000_000
    const val CHUNK_CHARS = 1800
    const val CHUNK_OVERLAP = 160

    fun kind(memory: Memory): String =
        if (memory.isDocumentNode || memory.libraryKind == KNOWLEDGE) KNOWLEDGE
        else if (memory.libraryKind == DIARY) DIARY else MEMORY

    fun category(memory: Memory): String = memory.category?.takeIf { it.isNotBlank() } ?: "other"

    /**
     * 写入前的类型校验。导入的文档由 `isDocumentNode` 自己决定类型，
     * 这里只约束调用方能显式声明的三种内容类型，未知值必须失败而不是落成默认记忆。
     */
    fun requireWritableKind(kind: String): String {
        require(kind in kinds) { "library_kind 只能是 ${kinds.joinToString(", ")}" }
        return kind
    }

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
