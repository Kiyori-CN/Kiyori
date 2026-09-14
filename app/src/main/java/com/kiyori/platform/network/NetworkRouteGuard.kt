package com.kiyori.platform.network

import java.io.IOException
import java.net.Proxy
import okhttp3.Interceptor
import okhttp3.Request

/** 在发送请求前复核池化连接的路由；不预读正文，也不重试请求。 */
internal fun networkRouteGuard(expectedProxy: (Request) -> Proxy): Interceptor = Interceptor { chain ->
    val expected = expectedProxy(chain.request())
    val connection = chain.connection()
    if (connection != null && connection.route().proxy != expected) {
        // 关闭物理 socket 会同时截断这个 HTTP/2 连接上的其他 AI 流；
        // 先阻止后续复用，再拒绝当前 exchange，由 OkHttp 排空其他流后释放连接。
        OkHttpConnectionRetirement.retire(connection)
        throw IOException("The application proxy route changed; this connection is no longer valid.")
    }
    chain.proceed(chain.request())
}
