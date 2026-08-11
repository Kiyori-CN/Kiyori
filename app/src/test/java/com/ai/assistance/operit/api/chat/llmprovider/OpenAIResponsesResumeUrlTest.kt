package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIResponsesResumeUrlTest {
    @Test
    fun build_appendsResponseIdentityAndLastCommittedSequence() {
        val url =
            OpenAIResponsesResumeUrl.build(
                responsesEndpoint = "https://api.openai.com/v1/responses",
                responseId = "resp_123",
                startingAfter = 42L,
            )

        assertEquals("/v1/responses/resp_123", url.encodedPath)
        assertEquals("true", url.queryParameter("stream"))
        assertEquals("42", url.queryParameter("starting_after"))
    }

    @Test
    fun build_rejectsCursorBeforeFirstCommittedResponsesEvent() {
        val error =
            runCatching {
                OpenAIResponsesResumeUrl.build(
                    responsesEndpoint = "https://api.openai.com/v1/responses",
                    responseId = "resp_123",
                    startingAfter = 0L,
                )
            }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
