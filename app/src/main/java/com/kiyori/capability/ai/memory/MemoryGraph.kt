package com.kiyori.capability.ai.memory

data class MemoryGraph(
    val nodes: List<MemoryGraphNode>,
    val edges: List<MemoryGraphEdge>
)

enum class MemoryGraphNodeType {
    DOCUMENT,
    PERSON,
    CONCEPT,
    OTHER
}

data class MemoryGraphNode(
    val id: String,
    val title: String,
    val type: MemoryGraphNodeType
)

data class MemoryGraphEdge(
    val id: Long,
    val sourceId: String,
    val targetId: String,
    val label: String? = null,
    val weight: Float = 1.0f,
    val metadata: Map<String, String> = emptyMap(),
    val isCrossFolderLink: Boolean = false
)
