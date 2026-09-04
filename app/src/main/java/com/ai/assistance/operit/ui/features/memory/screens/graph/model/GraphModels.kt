package com.ai.assistance.operit.ui.features.memory.screens.graph.model

import androidx.compose.ui.graphics.Color
import com.kiyori.capability.ai.memory.MemoryGraph
import com.kiyori.capability.ai.memory.MemoryGraphEdge
import com.kiyori.capability.ai.memory.MemoryGraphNodeType

data class Graph(
    val nodes: List<Node>,
    val edges: List<MemoryGraphEdge>
)

data class Node(
    val id: String,
    val label: String,
    val color: Color = Color.LightGray,
    val metadata: Map<String, String> = emptyMap()
)

// 颜色只属于展示层；边直接引用事实快照，避免备份统计也构造 Compose 对象。
fun MemoryGraph.toPresentationGraph(): Graph = Graph(
    nodes = nodes.map { node ->
        Node(
            id = node.id,
            label = node.title,
            color = when (node.type) {
                MemoryGraphNodeType.DOCUMENT -> Color(0xFF9575CD)
                MemoryGraphNodeType.PERSON -> Color(0xFF81C784)
                MemoryGraphNodeType.CONCEPT -> Color(0xFF64B5F6)
                MemoryGraphNodeType.OTHER -> Color.LightGray
            }
        )
    },
    edges = edges
)
