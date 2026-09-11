package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertSame
import java.io.BufferedReader
import java.io.EOFException
import java.io.StringReader

class ServerSentEventReaderTest {
    @Test fun interruptedTransportDeliversBufferedDataThenTheOriginalFailure() {
        val failure = EOFException("missing HTTP chunk terminator")
        val transport = object : BufferedReader(StringReader("")) {
            private var first = true
            override fun readLine(): String {
                if (first) {
                    first = false
                    return "data: {\"type\":\"response.output_text.delta\",\"delta\":\"last text\"}"
                }
                throw failure
            }
        }
        val reader = ServerSentEventReader(transport)
        assertEquals("{\"type\":\"response.output_text.delta\",\"delta\":\"last text\"}", reader.readData())
        assertSame(failure, runCatching { reader.readData() }.exceptionOrNull())
        assertSame(failure, runCatching { reader.readData() }.exceptionOrNull())
    }

    @Test fun joinsDataLinesAndIgnoresTransportFields() {
        val reader = ServerSentEventReader(("\uFEFF: heartbeat\r\nevent: response.completed\r\nid: 3\r\n" +
            "data: {\r\ndata:   \"type\": \"response.completed\"\r\ndata: }\r\n\r\n" +
            ": ping\n\ndata: [DONE]\n\n").reader().buffered())
        assertEquals("{\n  \"type\": \"response.completed\"\n}", reader.readData())
        assertEquals("[DONE]", reader.readData())
        assertNull(reader.readData())
    }

    @Test fun eofDoesNotInventATerminalEvent() {
        val reader = ServerSentEventReader("data: partial".reader().buffered())
        assertEquals("partial", reader.readData())
        assertNull(reader.readData())
    }

    @Test fun emptyDataAndCommentsAreDistinctFromEof() {
        val reader = ServerSentEventReader(": ping\n\ndata\n\ndata: next\n\n".reader().buffered())
        assertEquals("", reader.readData())
        assertEquals("next", reader.readData())
        assertNull(reader.readData())
    }
}
