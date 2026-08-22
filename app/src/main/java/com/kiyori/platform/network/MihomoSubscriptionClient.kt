package com.kiyori.platform.network

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MihomoSubscriptionClient {
    suspend fun fetchAndSanitize(
        rawUrl: String,
        proxyEndpoint: KiyoriProxyEndpoint? = null,
    ): SanitizedMihomoSubscription =
        try {
            withContext(Dispatchers.IO) {
            val client = createClient(proxyEndpoint)
            var current = requireHttpUrl(rawUrl)
            repeat(MAX_REDIRECTS + 1) { redirectIndex ->
                val request =
                    Request.Builder()
                        .url(current.toString())
                        // Subscription servers commonly use this identity to choose Clash YAML
                        // instead of a Base64 URI list. Sending a generic Mihomo identity makes
                        // the same URL return an incompatible representation.
                        .header("User-Agent", CLASH_META_USER_AGENT)
                        .header("Accept", "application/yaml,text/yaml,text/plain,*/*")
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        in 200..299 -> {
                            val body =
                                response.body
                                    ?: subscriptionFailure(
                                        "The subscription response body is empty.",
                                    )
                            val declaredLength = body.contentLength()
                            if (declaredLength > MihomoConfigSanitizer.MAX_YAML_BYTES) {
                                subscriptionFailure("The subscription exceeds the 4 MiB limit.")
                            }
                            val bytes =
                                body.byteStream().use { input ->
                                    val output = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(32 * 1024)
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        if (
                                            output.size() + read >
                                                MihomoConfigSanitizer.MAX_YAML_BYTES
                                        ) {
                                            subscriptionFailure(
                                                "The subscription exceeds the 4 MiB limit.",
                                            )
                                        }
                                        output.write(buffer, 0, read)
                                    }
                                    output.toByteArray()
                                }
                            val sanitized =
                                MihomoConfigSanitizer.sanitize(
                                    rawYaml =
                                        MihomoConfigSanitizer.decodeUtf8(
                                            bytes,
                                            KiyoriNetworkErrorCode.SUBSCRIPTION_FAILED,
                                        ),
                                    rootErrorCode = KiyoriNetworkErrorCode.SUBSCRIPTION_FORMAT,
                                )
                            return@withContext sanitized.copy(
                                usage = parseSubscriptionUsage(response.header(SUBSCRIPTION_USERINFO)),
                            )
                        }
                        in 300..399 -> {
                            if (redirectIndex == MAX_REDIRECTS) {
                                subscriptionFailure(
                                    "The subscription exceeded the redirect limit.",
                                )
                            }
                            val location =
                                response.header("Location")
                                    ?: subscriptionFailure(
                                        "The subscription redirect has no target.",
                                    )
                            val next = requireHttpUrl(current.resolve(location).toString())
                            if (
                                current.scheme.equals("https", ignoreCase = true) &&
                                    next.scheme.equals("http", ignoreCase = true)
                            ) {
                                subscriptionFailure(
                                    "An HTTPS subscription cannot redirect to HTTP.",
                                )
                            }
                            current = next
                        }
                        else ->
                            subscriptionFailure(
                                "The subscription server returned HTTP ${response.code}.",
                            )
                    }
                }
            }
                subscriptionFailure("The subscription did not return a configuration.")
            }
        } catch (error: KiyoriNetworkException) {
            throw error
        } catch (error: Exception) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.SUBSCRIPTION_FAILED,
                "The subscription could not be downloaded.",
                error,
            )
        }

    private fun createClient(proxyEndpoint: KiyoriProxyEndpoint?): OkHttpClient {
        val builder =
            OkHttpClient.Builder()
                .followRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
        if (proxyEndpoint == null) {
            builder.proxy(Proxy.NO_PROXY)
        } else {
            builder.proxy(
                Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress.createUnresolved(proxyEndpoint.host, proxyEndpoint.port),
                ),
            )
        }
        return builder.build()
    }

    private fun requireHttpUrl(value: String): URI {
        val uri =
            runCatching { URI(value.trim()) }.getOrNull()
                ?: subscriptionFailure("The subscription URL is invalid.")
        if (uri.scheme?.lowercase() != "https" || uri.host.isNullOrBlank()) {
            subscriptionFailure("The subscription URL must be an absolute HTTPS URL.")
        }
        return uri
    }

    private fun parseSubscriptionUsage(rawHeader: String?): MihomoSubscriptionUsage? {
        if (rawHeader.isNullOrBlank()) return null
        val values =
            rawHeader.split(';').mapNotNull { field ->
                val separator = field.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val name = field.substring(0, separator).trim().lowercase()
                val value = field.substring(separator + 1).trim().toLongOrNull()
                if (value == null || value < 0L) null else name to value
            }.toMap()
        val usage =
            MihomoSubscriptionUsage(
                uploadBytes = values["upload"],
                downloadBytes = values["download"],
                totalBytes = values["total"],
                expiresAtEpochSeconds = values["expire"]?.takeIf { value -> value > 0L },
            )
        return usage.takeIf {
            it.uploadBytes != null ||
                it.downloadBytes != null ||
                it.totalBytes != null ||
                it.expiresAtEpochSeconds != null
        }
    }

    private fun subscriptionFailure(message: String): Nothing =
        throw KiyoriNetworkException(KiyoriNetworkErrorCode.SUBSCRIPTION_FAILED, message)

    private companion object {
        const val CLASH_META_USER_AGENT = "Clash.Meta"
        const val SUBSCRIPTION_USERINFO = "subscription-userinfo"
        const val MAX_REDIRECTS = 5
    }
}
