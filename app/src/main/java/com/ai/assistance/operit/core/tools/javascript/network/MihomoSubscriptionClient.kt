package com.ai.assistance.operit.core.tools.javascript.network

import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MihomoSubscriptionClient {
    private val client =
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

    suspend fun fetchAndSanitize(rawUrl: String): SanitizedMihomoSubscription =
        withContext(Dispatchers.IO) {
            var current = requireHttpUrl(rawUrl)
            repeat(6) { redirectIndex ->
                val request =
                    Request.Builder()
                        .url(current.toString())
                        .header("User-Agent", "mihomo/1.19.30 Kiyori")
                        .header("Accept", "application/yaml,text/yaml,text/plain,*/*")
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        in 200..299 -> {
                            val body = response.body
                                ?: subscriptionFailure("The subscription response body is empty.")
                            val declaredLength = body.contentLength()
                            if (declaredLength > MihomoConfigSanitizer.MAX_YAML_BYTES) {
                                subscriptionFailure("The subscription exceeds the 4 MiB limit.")
                            }
                            val bytes = body.byteStream().use { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (output.size() + read > MihomoConfigSanitizer.MAX_YAML_BYTES) {
                                        subscriptionFailure("The subscription exceeds the 4 MiB limit.")
                                    }
                                    output.write(buffer, 0, read)
                                }
                                output.toByteArray()
                            }
                            return@withContext MihomoConfigSanitizer.sanitize(
                                MihomoConfigSanitizer.decodeUtf8(
                                    bytes,
                                    ScriptNetworkErrorCode.SUBSCRIPTION_FAILED,
                                ),
                            )
                        }
                        in 300..399 -> {
                            val location = response.header("Location")
                                ?: subscriptionFailure("The subscription redirect has no target.")
                            val next = requireHttpUrl(current.resolve(location).toString())
                            if (current.scheme.equals("https", ignoreCase = true) &&
                                next.scheme.equals("http", ignoreCase = true)
                            ) {
                                subscriptionFailure("An HTTPS subscription cannot redirect to HTTP.")
                            }
                            current = next
                        }
                        else -> subscriptionFailure("The subscription server returned HTTP ${response.code}.")
                    }
                }
                if (redirectIndex == 5) {
                    subscriptionFailure("The subscription exceeded the redirect limit.")
                }
            }
            subscriptionFailure("The subscription did not return a configuration.")
        }

    private fun requireHttpUrl(value: String): URI {
        val uri = runCatching { URI(value.trim()) }.getOrNull()
            ?: subscriptionFailure("The subscription URL is invalid.")
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            subscriptionFailure("The subscription URL must be an absolute HTTP(S) URL.")
        }
        return uri
    }

    private fun subscriptionFailure(message: String): Nothing =
        throw ScriptNetworkException(ScriptNetworkErrorCode.SUBSCRIPTION_FAILED, message)
}
