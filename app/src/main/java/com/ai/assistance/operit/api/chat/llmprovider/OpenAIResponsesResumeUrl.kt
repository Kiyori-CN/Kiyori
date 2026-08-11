package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ProviderExecutionEntity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal object OpenAIResponsesResumeUrl {
    fun build(
        responsesEndpoint: String,
        responseId: String,
        startingAfter: Long,
    ): HttpUrl {
        require(responseId.isNotBlank()) { "responseId must not be blank" }
        require(startingAfter >= ProviderExecutionEntity.FIRST_EVENT_SEQUENCE) {
            "startingAfter must reference a committed Responses event"
        }
        return responsesEndpoint
            .toHttpUrl()
            .newBuilder()
            .addPathSegment(responseId)
            .addQueryParameter("stream", "true")
            .addQueryParameter("starting_after", startingAfter.toString())
            .build()
    }
}
