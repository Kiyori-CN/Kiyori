package com.ai.assistance.operit.api.chat.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MemoryAnalysisProtocolTest {
    @Test
    fun `parses named object protocol and validates values`() {
        val analysis = MemoryLibrary.parseAnalysisResult(
            """
            {
              "main":{"title":"Main event","content":"confirmed","tags":["project"],"folder_path":"work"},
              "new":[{"title":"New entity","content":"fact","tags":[],"folder_path":"work","alias_for":null}],
              "update":[{"title":"Existing","content":"new full content","reason":"confirmed","credibility":0.75,"importance":null}],
              "merge":[{"source_titles":["A","B"],"title":"Merged","content":"combined","tags":["merged"],"folder_path":"work","reason":"same fact"}],
              "links":[{"source":"Main event","target":"New entity","type":"INVOLVES","description":"explicit","weight":1.0}]
            }
            """.trimIndent()
        )

        assertEquals("Main event", analysis.mainProblem?.title)
        assertEquals("New entity", analysis.extractedEntities.single().title)
        assertEquals(0.75f, analysis.updatedEntities.single().newCredibility)
        assertEquals("Merged", analysis.mergedEntities.single().newTitle)
        assertEquals(1.0f, analysis.links.single().weight)
    }

    @Test
    fun `allows null main while retaining independent operations`() {
        val analysis = MemoryLibrary.parseAnalysisResult(
            """
            {
              "main":null,
              "new":[],
              "update":[{"title":"Existing","content":"updated","reason":"confirmed","credibility":null,"importance":0.5}],
              "merge":[],
              "links":[]
            }
            """.trimIndent()
        )

        assertEquals(null, analysis.mainProblem)
        assertEquals("Existing", analysis.updatedEntities.single().titleToUpdate)
        assertEquals(false, MemoryLibrary.hasNoMemoryOperations(analysis))
    }

    @Test
    fun `links remain an independent operation when main is null`() {
        val analysis = MemoryLibrary.parseAnalysisResult(
            """
            {
              "main":null,"new":[],"update":[],"merge":[],
              "links":[{"source":"A","target":"B","type":"RELATES","description":"explicit","weight":0.8}]
            }
            """.trimIndent()
        )

        assertEquals("A", analysis.links.single().sourceTitle)
        assertEquals(false, MemoryLibrary.hasNoMemoryOperations(analysis))
    }

    @Test
    fun `empty named analysis has no memory operations`() {
        val analysis = MemoryLibrary.parseAnalysisResult(
            """
            {"main":null,"new":[],"update":[],"merge":[],"links":[]}
            """.trimIndent()
        )

        assertEquals(true, MemoryLibrary.hasNoMemoryOperations(analysis))
    }

    @Test
    fun `rejects legacy positional arrays`() {
        assertThrows(Exception::class.java) {
            MemoryLibrary.parseAnalysisResult(
                """
                {
                  "main":["Title","Content",[],"folder"],
                  "new":[],"update":[],"merge":[],"links":[]
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `rejects missing required top level fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            MemoryLibrary.parseAnalysisResult("{\"main\":null,\"new\":[],\"update\":[],\"merge\":[]}")
        }
    }

    @Test
    fun `rejects missing required object fields`() {
        assertThrows(Exception::class.java) {
            MemoryLibrary.parseAnalysisResult(
                """
                {
                  "main":{"title":"Main","content":"fact","tags":[],"folder_path":""},
                  "new":[{"title":"Entity","content":"fact","tags":[],"folder_path":"folder"}],
                  "update":[],"merge":[],"links":[]
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `rejects out of range numeric values`() {
        assertThrows(IllegalArgumentException::class.java) {
            MemoryLibrary.parseAnalysisResult(
                """
                {
                  "main":null,"new":[],"update":[],"merge":[],
                  "links":[{"source":"A","target":"B","type":"RELATES","description":"explicit","weight":1.1}]
                }
                """.trimIndent()
            )
        }
    }
}
