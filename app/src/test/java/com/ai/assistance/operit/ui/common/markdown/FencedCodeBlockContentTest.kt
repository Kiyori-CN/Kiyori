package com.ai.assistance.operit.ui.common.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class FencedCodeBlockContentTest {
    @Test
    fun `extracts language and preserves code whitespace`() {
        val raw = "```python extra\n  value = \"`\"  \n\n```\n"

        val payload = extractFencedCodeBlockPayload(raw)

        assertEquals("python", payload.language)
        assertEquals("  value = \"`\"  \n", payload.code)
    }

    @Test
    fun `requires matching fence length and trailing whitespace only`() {
        val raw = "````mermaid\ngraph TD\n``` not-a-close\n  ````\t\ntext"

        val payload = extractFencedCodeBlockPayload(raw)

        assertEquals("mermaid", payload.language)
        assertEquals("graph TD\n``` not-a-close", payload.code)
    }

    @Test
    fun `unfinished block removes only opening line`() {
        val raw = "  ```bash\r\necho ${'$'}${'$'}\r\n  "

        val payload = extractFencedCodeBlockPayload(raw)

        assertEquals("bash", payload.language)
        assertEquals("echo ${'$'}${'$'}\r\n  ", payload.code)
    }

    @Test
    fun `malformed raw content remains observable`() {
        val raw = "plain text ``` inline"

        val payload = extractFencedCodeBlockPayload(raw)

        assertEquals("", payload.language)
        assertEquals(raw, payload.code)
    }
}
