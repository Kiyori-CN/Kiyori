package com.ai.assistance.operit.api.chat.llmprovider

internal object OpenAIHostedWebSearchTestFixtures {
    fun binding(
        providerContract: OpenAIHostedWebSearchProviderContract =
            OpenAIHostedWebSearchProviderContract.RESPONSES_HOSTED_OFFICIAL,
        endpoint: String = "https://api.openai.com/v1/responses",
        modelName: String = "gpt-5.6-luna",
        apiKey: String = "test-key",
        authHeaderName: String = "Authorization",
        authScheme: String = "Bearer",
        extraHeaders: Map<String, String> = emptyMap(),
        reasoningEffort: OpenAIHostedWebSearchReasoningEffort =
            OpenAIHostedWebSearchReasoningEffort.MEDIUM,
        maxOutputTokens: Int? = null,
        returnTokenBudget: OpenAIHostedWebSearchReturnTokenBudget =
            OpenAIHostedWebSearchReturnTokenBudget.DEFAULT,
        additionalInstructions: String = "",
        mode: OpenAIHostedWebSearchMode = OpenAIHostedWebSearchMode.LIVE,
        contextSize: OpenAIHostedWebSearchContextSize =
            OpenAIHostedWebSearchContextSize.MEDIUM,
        allowedDomains: List<String> = emptyList(),
        blockedDomains: List<String> = emptyList(),
        location: OpenAIHostedWebSearchApproximateLocation? = null,
        queueTimeoutSeconds: Int = 60,
        timeoutSeconds: Int = 300,
        maxConcurrentRequests: Int = 1,
        requestsPerMinute: Int = 0,
    ): OpenAIHostedWebSearchBinding =
        OpenAIHostedWebSearchBinding(
            toolPkgId = OpenAIHostedWebSearchContract.TOOLPKG_ID,
            providerContract = providerContract,
            endpoint = endpoint,
            modelName = modelName,
            apiKey = apiKey,
            authHeaderName = authHeaderName,
            authScheme = authScheme,
            extraHeaders = extraHeaders,
            reasoningEffort = reasoningEffort,
            maxOutputTokens = maxOutputTokens,
            returnTokenBudget = returnTokenBudget,
            additionalInstructions = additionalInstructions,
            mode = mode,
            contextSize = contextSize,
            allowedDomains = allowedDomains,
            blockedDomains = blockedDomains,
            location = location,
            queueTimeoutSeconds = queueTimeoutSeconds,
            timeoutSeconds = timeoutSeconds,
            maxConcurrentRequests = maxConcurrentRequests,
            requestsPerMinute = requestsPerMinute,
        )

    fun effectiveRequest(
        requestId: String = "ows_test",
        query: String = "latest Kiyori release",
        contextSize: OpenAIHostedWebSearchContextSize =
            OpenAIHostedWebSearchContextSize.MEDIUM,
        allowedDomains: List<String> = emptyList(),
        blockedDomains: List<String> = emptyList(),
        location: OpenAIHostedWebSearchApproximateLocation? = null,
        locationRequested: Boolean = location != null,
        locationConfigured: Boolean = location != null,
        locationPrecision: String =
            openAIHostedWebSearchLocationPrecision(location),
    ): OpenAIHostedWebSearchEffectiveRequest =
        OpenAIHostedWebSearchEffectiveRequest(
            requestId = requestId,
            query = query,
            contextSize = contextSize,
            allowedDomains = allowedDomains,
            blockedDomains = blockedDomains,
            location = location,
            locationRequested = locationRequested,
            locationConfigured = locationConfigured,
            locationPrecision = locationPrecision,
        )
}
