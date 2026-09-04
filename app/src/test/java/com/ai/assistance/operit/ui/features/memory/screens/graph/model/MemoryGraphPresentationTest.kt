package com.ai.assistance.operit.ui.features.memory.screens.graph.model

import androidx.compose.ui.graphics.Color
import com.kiyori.capability.ai.memory.MemoryGraph
import com.kiyori.capability.ai.memory.MemoryGraphEdge
import com.kiyori.capability.ai.memory.MemoryGraphNode
import com.kiyori.capability.ai.memory.MemoryGraphNodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGraphPresentationTest {
    @Test
    fun `presentation preserves node identity order and established colors`() {
        val facts = MemoryGraph(
            nodes = listOf(
                MemoryGraphNode("document-id", "Document", MemoryGraphNodeType.DOCUMENT),
                MemoryGraphNode("person-id", "Person", MemoryGraphNodeType.PERSON),
                MemoryGraphNode("concept-id", "Concept", MemoryGraphNodeType.CONCEPT),
                MemoryGraphNode("other-id", "Other", MemoryGraphNodeType.OTHER),
            ),
            edges = emptyList(),
        )

        val graph = facts.toPresentationGraph()

        assertEquals(listOf("document-id", "person-id", "concept-id", "other-id"), graph.nodes.map { it.id })
        assertEquals(listOf("Document", "Person", "Concept", "Other"), graph.nodes.map { it.label })
        assertEquals(
            listOf(Color(0xFF9575CD), Color(0xFF81C784), Color(0xFF64B5F6), Color.LightGray),
            graph.nodes.map { it.color },
        )
        assertTrue(graph.nodes.all { it.metadata.isEmpty() })
    }

    @Test
    fun `presentation shares edge facts including edit identity and cross-folder marker`() {
        val edges = listOf(
            MemoryGraphEdge(
                id = 73L,
                sourceId = "from",
                targetId = "to",
                label = "related",
                weight = 0.7f,
                metadata = mapOf("description" to "detail"),
                isCrossFolderLink = true,
            ),
            MemoryGraphEdge(id = 74L, sourceId = "from", targetId = "from"),
        )
        val facts = MemoryGraph(emptyList(), edges)

        val graph = facts.toPresentationGraph()

        assertSame(edges, graph.edges)
        assertSame(edges[0], graph.edges[0])
        assertEquals(73L, graph.edges[0].id)
        assertEquals("from", graph.edges[0].sourceId)
        assertEquals("to", graph.edges[0].targetId)
        assertEquals("related", graph.edges[0].label)
        assertEquals(0.7f, graph.edges[0].weight, 0.0f)
        assertEquals("detail", graph.edges[0].metadata["description"])
        assertTrue(graph.edges[0].isCrossFolderLink)
    }

    @Test
    fun `empty graph stays empty`() {
        assertEquals(Graph(emptyList(), emptyList()), MemoryGraph(emptyList(), emptyList()).toPresentationGraph())
    }
}
