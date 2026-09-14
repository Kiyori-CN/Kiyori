package com.ai.assistance.operit.api.chat.llmprovider

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal object GeminiRequestUrl {
    fun build(endpoint: String, model: String, streaming: Boolean): HttpUrl {
        val url = endpoint.trim().removeSuffix("#").toHttpUrl()
        val path = url.encodedPath.trimEnd('/').substringBefore("/models/")
        val versioned = if (path.endsWith("/v1beta") || path.endsWith("/v1")) path else "$path/v1beta"
        // 保留中转路径、版本与查询参数，仅替换请求模型/操作；密钥不进入 URL。
        return url.newBuilder().encodedPath(versioned)
            .addPathSegment("models")
            .addPathSegment(model.removePrefix("models/") + if (streaming) ":streamGenerateContent" else ":generateContent")
            .removeAllQueryParameters("key")
            .removeAllQueryParameters("alt")
            .apply { if (streaming) addQueryParameter("alt", "sse") }
            .build()
    }
}
