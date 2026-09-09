package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerSentEventReaderTest {
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
