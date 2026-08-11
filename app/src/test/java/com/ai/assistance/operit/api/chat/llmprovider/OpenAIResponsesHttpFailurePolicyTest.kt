package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAIResponsesHttpFailurePolicyTest {
    @Test
    fun submission_retriesOnlyExplicitRateLimitRejection() {
        assertEquals(
            OpenAIResponsesHttpFailurePolicy.SubmissionAction.RETRY_EXPLICIT_REJECTION,
            OpenAIResponsesHttpFailurePolicy.classifySubmission(429),
        )
        listOf(408, 409, 500, 502, 503, 504).forEach { statusCode ->
            assertEquals(
                OpenAIResponsesHttpFailurePolicy.SubmissionAction.SUBMISSION_UNKNOWN,
                OpenAIResponsesHttpFailurePolicy.classifySubmission(statusCode),
            )
        }
        listOf(400, 401, 403, 404, 422).forEach { statusCode ->
            assertEquals(
                OpenAIResponsesHttpFailurePolicy.SubmissionAction.FAIL,
                OpenAIResponsesHttpFailurePolicy.classifySubmission(statusCode),
            )
        }
    }

    @Test
    fun resume_retriesSameResponseAndExpiresMissingRemoteState() {
        listOf(408, 409, 429, 500, 502, 503, 504).forEach { statusCode ->
            assertEquals(
                OpenAIResponsesHttpFailurePolicy.ResumeAction.RETRY_SAME_RESPONSE,
                OpenAIResponsesHttpFailurePolicy.classifyResume(statusCode),
            )
        }
        listOf(404, 410).forEach { statusCode ->
            assertEquals(
                OpenAIResponsesHttpFailurePolicy.ResumeAction.EXPIRE,
                OpenAIResponsesHttpFailurePolicy.classifyResume(statusCode),
            )
        }
        listOf(400, 401, 403, 422).forEach { statusCode ->
            assertEquals(
                OpenAIResponsesHttpFailurePolicy.ResumeAction.FAIL,
                OpenAIResponsesHttpFailurePolicy.classifyResume(statusCode),
            )
        }
    }
}
