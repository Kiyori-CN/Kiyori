package com.ai.assistance.operit.api.speech

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/** 语音识别服务工厂类 用于创建和管理不同类型的语音识别服务 */
object SpeechServiceFactory {
    private const val TAG = "SpeechServiceFactory"

    /** 语音识别服务类型 */
    enum class SpeechServiceType {
        /** 基于Sherpa-ncnn的本地识别实现 */
        SHERPA_NCNN,
        OPENAI_STT,
        DEEPGRAM_STT,
    }

    /**
     * 创建语音识别服务实例
     *
     * @param context 应用上下文
     * @return 对应类型的语音识别服务实例
     */
    fun createSpeechService(
        context: Context
    ): SpeechService {
        val profile = runBlocking { SpeechServiceProfilesPreferences(context).getCurrentSttProfile() }
        return createSpeechService(context, profile.serviceType, profile.httpConfig)
    }

    fun createWakeSpeechService(
        context: Context,
    ): SpeechService {
        val profile = runBlocking { SpeechServiceProfilesPreferences(context).getCurrentSttProfile() }
        val selectedType = profile.serviceType
        val effectiveType = when (selectedType) {
            SpeechServiceType.OPENAI_STT,
            SpeechServiceType.DEEPGRAM_STT,
            -> SpeechServiceType.SHERPA_NCNN
            else -> selectedType
        }
        return createSpeechService(context, effectiveType, profile.httpConfig)
    }

    fun createSpeechService(
        context: Context,
        type: SpeechServiceType,
    ): SpeechService {
        val profile = runBlocking { SpeechServiceProfilesPreferences(context).getCurrentSttProfile() }
        return createSpeechService(context, type, profile.httpConfig)
    }

    private fun createSpeechService(
        context: Context,
        type: SpeechServiceType,
        httpConfig: com.ai.assistance.operit.data.preferences.SpeechServicesPreferences.SttHttpConfig,
    ): SpeechService {
        return when (type) {
            SpeechServiceType.SHERPA_NCNN -> acquireLocalSpeechService(context, type)
            SpeechServiceType.OPENAI_STT -> {
                OpenAISttProvider(
                    context = context,
                    endpointUrl = httpConfig.endpointUrl,
                    apiKey = httpConfig.apiKey,
                    model = httpConfig.modelName,
                )
            }
            SpeechServiceType.DEEPGRAM_STT -> {
                DeepgramSttProvider(
                    context = context,
                    endpointUrl = httpConfig.endpointUrl,
                    apiKey = httpConfig.apiKey,
                    model = httpConfig.modelName,
                )
            }
        }
    }

    private class SpeechServiceLease(
        private val delegate: SpeechService,
        private val onRelease: () -> Unit,
    ) : SpeechService by delegate {
        private val released = AtomicBoolean(false)

        override fun shutdown() {
            if (released.compareAndSet(false, true)) {
                onRelease()
            }
        }
    }

    private data class LocalEntry(
        val type: SpeechServiceType,
        val service: SpeechService,
        var refCount: Int,
    )

    private val localLock = Any()
    private var localEntry: LocalEntry? = null

    private fun acquireLocalSpeechService(
        context: Context,
        type: SpeechServiceType,
    ): SpeechService {
        val appContext = context.applicationContext
        synchronized(localLock) {
            val existing = localEntry
            if (existing != null && existing.type != type) {
                throw IllegalStateException(
                    "Local SpeechService already active: ${existing.type}. " +
                        "Cannot create another local SpeechService of type $type before releasing the previous one."
                )
            }

            val entry =
                if (existing != null) {
                    existing.refCount += 1
                    existing
                } else {
                    val service =
                        when (type) {
                            SpeechServiceType.SHERPA_NCNN -> SherpaSpeechProvider(appContext)
                            else -> throw IllegalArgumentException("Not a local SpeechService type: $type")
                        }
                    LocalEntry(type = type, service = service, refCount = 1).also { localEntry = it }
                }

            return SpeechServiceLease(
                delegate = entry.service,
                onRelease = { releaseLocalSpeechService(entry.type) },
            )
        }
    }

    private fun releaseLocalSpeechService(type: SpeechServiceType) {
        val toShutdown: SpeechService?
        synchronized(localLock) {
            val entry = localEntry
            if (entry == null || entry.type != type) {
                return
            }

            entry.refCount -= 1
            if (entry.refCount > 0) {
                return
            }

            localEntry = null
            toShutdown = entry.service
        }

        try {
            toShutdown?.shutdown()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to shutdown local SpeechService", e)
        }
    }

    private data class ServiceConfiguration(
        val profileId: String,
        val type: SpeechServiceType,
        val httpConfig: SpeechServicesPreferences.SttHttpConfig,
    )

    private val serviceOwner = SpeechProfileServiceOwner<ServiceConfiguration, SpeechService> {
        it.shutdown()
    }

    /**
     * 获取语音识别服务单例实例
     *
     * @param context 应用上下文
     * @return 语音识别服务实例
     */
    fun getInstance(
        context: Context,
    ): SpeechService {
        try {
            return serviceOwner.get(
                readConfiguration = {
                    val profile = runBlocking {
                        SpeechServiceProfilesPreferences(context.applicationContext).getCurrentSttProfile()
                    }
                    ServiceConfiguration(profile.id, profile.serviceType, profile.httpConfig)
                },
                create = { configuration ->
                    createSpeechService(context.applicationContext, configuration.type, configuration.httpConfig)
                },
            )
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to obtain configured SpeechService", error)
            throw error
        }
    }

    /** 重置单例实例 在需要更改语音识别服务类型或释放资源时调用 */
    fun resetInstance() {
        try {
            serviceOwner.reset()
        } catch (error: Exception) {
            AppLogger.w(TAG, "Failed to shutdown SpeechService during reset", error)
            throw error
        }
    }
}
