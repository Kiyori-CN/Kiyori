package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import org.junit.Assert.*
import org.junit.Test

class ChatCallCodecTest {
    @Test fun `structured turns preserve metadata and tool names`() {
        val turns = ChatCallCodec.parseChatCallPromptTurns("""[{"kind":"tool_result","content":"ok","toolName":"read_file","metadata":{"count":2,"nested":[null,true]}}]""")
        assertEquals(PromptTurnKind.TOOL_RESULT, turns.single().kind)
        assertEquals("read_file", turns.single().toolName)
        assertEquals(2, turns.single().metadata["count"])
    }

    @Test fun `invalid and trailing prompt data fail before submitting a model request`() {
        listOf("[]", "{}", "[{\"kind\":\"invalid\",\"content\":\"x\"}]", "[{\"kind\":\"user\",\"content\":3}]",
            "[{\"kind\":\"user\",\"content\":\"x\"}] {}").forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { ChatCallCodec.parseChatCallPromptTurns(input) }
        }
    }

    @Test fun `text and tool output preserve ordering without executing tools`() {
        val output = ChatCallCodec.parseChatCallOutput("Before <tool name=\"read_file\"><param name=\"path\">a</param></tool> After")
        assertEquals(listOf("ASSISTANT", "TOOL_CALL", "ASSISTANT"), output.turns.map { it.kind })
        assertEquals("tool_call", output.finishReason)
        assertEquals("read_file", output.turns[1].toolName)
        assertEquals("Before  After", output.text)
    }
}
