package com.kiyori.platform.serialization

import kotlinx.serialization.json.Json

/** 当前 Android 进程由 Application 配置并安装的唯一全局 Json。 */
object ApplicationJson {
    lateinit var current: Json
        private set

    internal fun installForProcess(json: Json) {
        current = json
    }
}
