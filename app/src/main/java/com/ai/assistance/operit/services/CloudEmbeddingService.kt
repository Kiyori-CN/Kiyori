package com.ai.assistance.operit.services

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CloudEmbeddingConfig
import com.ai.assistance.operit.data.model.Embedding
import com.ai.assistance.operit.data.model.MemoryLibraryPolicy
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.platform.network.KiyoriNetworkModule
import com.kiyori.platform.network.applyKiyoriNetworkProxy
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class CloudEmbeddingService(
    private val context: Context
) {

    companion object {
        private const val TAG = "CloudEmbeddingService"
    }

    class CloudEmbeddingException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .applyKiyoriNetworkProxy(KiyoriNetworkModule.AI_SERVICES)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun generateEmbedding(config: CloudEmbeddingConfig, text: String): Embedding? = withContext(Dispatchers.IO) {
        val normalized = config.normalized()
        if (normalized.enabled && !normalized.isReady()) {
            throw CloudEmbeddingException(context.getString(R.string.memory_embedding_error_config_incomplete))
        }
        if (!normalized.enabled || text.isBlank()) {
            return@withContext null
        }

        // 已启用的语义请求失败必须传播，不能把网络错误伪装成没有相关记忆。
        requestEmbedding(normalized, text)
    }

    suspend fun generateEmbeddingOrThrow(config: CloudEmbeddingConfig, text: String): Embedding = withContext(Dispatchers.IO) {
        val normalized = config.normalized()
        if (!normalized.isReady()) {
            throw CloudEmbeddingException(context.getString(R.string.memory_embedding_error_config_incomplete))
        }
        if (text.isBlank()) {
            throw CloudEmbeddingException(context.getString(R.string.memory_embedding_error_input_empty))
        }

        requestEmbedding(normalized, text)
    }

    private fun requestEmbedding(config: CloudEmbeddingConfig, text: String): Embedding {
        val requestBodyJson = JSONObject()
            .put("model", config.model)
            .put("input", text)
            .toString()

        val request = Request.Builder()
            .url(completeEmbeddingsEndpoint(config.endpoint))
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = "嵌入服务请求失败，请检查模型与服务配置"
                val message = context.getString(R.string.memory_embedding_error_http, response.code, detail)
                AppLogger.w(TAG, "Embedding HTTP ${response.code}")
                throw CloudEmbeddingException(message)
            }

            return parseEmbedding(responseBody)
        }
    }

    private fun parseEmbedding(responseBody: String): Embedding {
        if (responseBody.isBlank()) {
            throw CloudEmbeddingException(context.getString(R.string.memory_embedding_error_empty_response))
        }

        return try {
            val root = JSONObject(responseBody)
            val data = root.optJSONArray("data")
                ?: throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_missing_data, "无效嵌入响应")
                )
            if (data.length() <= 0) {
                throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_empty_data, "无效嵌入响应")
                )
            }

            val first = data.optJSONObject(0)
                ?: throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_invalid_first_item, "无效嵌入响应")
                )
            val embeddingJson = first.optJSONArray("embedding")
                ?: throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_missing_embedding, "无效嵌入响应")
                )
            if (embeddingJson.length() <= 0) {
                throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_empty_embedding, "无效嵌入响应")
                )
            }

            val vector = FloatArray(embeddingJson.length()) { index ->
                embeddingJson.getDouble(index).toFloat()
            }
            require(MemoryLibraryPolicy.validVector(vector)) { "Invalid embedding vector" }
            Embedding(vector)
        } catch (e: CloudEmbeddingException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse embedding response", e)
            throw CloudEmbeddingException(
                context.getString(R.string.memory_embedding_error_parse_failed, "无效向量响应"),
                e
            )
        }
    }

    private fun completeEmbeddingsEndpoint(endpoint: String): String {
        val trimmed = endpoint.trim()
        if (trimmed.endsWith("#")) {
            return trimmed.removeSuffix("#")
        }

        val withoutSlash = trimmed.removeSuffix("/")

        return try {
            val path = URL(trimmed).path.removeSuffix("/")
            when {
                path.isEmpty() -> "$withoutSlash/v1/embeddings"
                path.endsWith("/v1", ignoreCase = true) -> "$withoutSlash/embeddings"
                else -> trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }
}
