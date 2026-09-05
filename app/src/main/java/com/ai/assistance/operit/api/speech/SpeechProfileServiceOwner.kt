package com.ai.assistance.operit.api.speech

/** STT/TTS 工厂的配置与实例必须成对发布；失败后不能再次交出已经关闭的服务。 */
internal class SpeechProfileServiceOwner<Configuration : Any, Service : Any>(
    private val shutdown: (Service) -> Unit,
) {
    private data class Entry<Configuration, Service>(
        val configuration: Configuration,
        val service: Service,
    )

    private val lock = Any()
    private var entry: Entry<Configuration, Service>? = null

    fun get(
        readConfiguration: () -> Configuration,
        create: (Configuration) -> Service,
    ): Service = synchronized(lock) {
        // 读取也属于本次切换，不能先读旧配置、等另一线程发布新配置后再覆盖它。
        val configuration = readConfiguration()
        val current = entry
        if (current != null && current.configuration == configuration) {
            return@synchronized current.service
        }
        entry = null
        current?.let { shutdown(it.service) }
        val service = create(configuration)
        entry = Entry(configuration, service)
        service
    }

    fun reset() = synchronized(lock) {
        val current = entry
        // 即使 shutdown 抛错也不保留失效实例；错误交给调用方处理。
        entry = null
        current?.let { shutdown(it.service) }
        Unit
    }
}
