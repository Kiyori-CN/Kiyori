package com.ai.assistance.operit.ui.common.markdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidPreviewHtmlTest {
    @Test
    fun `source is encoded as javascript text instead of executable html`() {
        val source = """flowchart LR
            A["</script><script>alert('x')</script>"] --> B
        """.trimIndent()

        val html = buildMermaidPreviewHtml(source)

        assertFalse(html.contains("</script><script>alert('x')</script>"))
        assertTrue(html.contains("\\u003c/script\\u003e"))
        assertTrue(html.contains("diagram.textContent = diagramSource"))
    }

    @Test
    fun `render waits for mermaid output before measuring the final svg`() {
        val html = buildMermaidPreviewHtml("sequenceDiagram\nA->>B: hello")

        assertTrue(html.contains("startOnLoad: false"))
        assertTrue(html.contains("mermaid.run({ nodes: [diagram] })"))
        assertTrue(html.contains("diagram.querySelector('svg')"))
        assertTrue(html.contains("svg.getBBox()"))
        assertTrue(html.contains("svg.style.maxWidth = 'none'"))
    }

    @Test
    fun `preview owns an internal two dimensional scroll viewport`() {
        val html = buildMermaidPreviewHtml("flowchart LR\nA --> B")

        assertTrue(html.contains("#diagram-viewport"))
        assertTrue(html.contains("overflow: auto"))
        assertTrue(html.contains("touch-action: pan-x pan-y"))
        assertTrue(html.contains("wrapper.style.width"))
        assertTrue(html.contains("wrapper.style.height"))
    }
}
