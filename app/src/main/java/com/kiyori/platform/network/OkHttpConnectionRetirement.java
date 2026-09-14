package com.kiyori.platform.network;

import okhttp3.Connection;
import okhttp3.internal.connection.RealConnection;

/**
 * OkHttp 4.12.0 没有公开的“停止接收新流并排空旧流”接口；evictAll 只处理空闲连接。
 * 将这一处固定版本 ABI 依赖隔离在 Java 中，避免反射和物理 socket 关闭。
 * 升级 OkHttp 必须同时通过 NetworkRouteGuardTest 的真实 HTTP/2 并发与后续连接回归。
 */
final class OkHttpConnectionRetirement {
    private OkHttpConnectionRetirement() {}

    static void retire(Connection connection) {
        if (!(connection instanceof RealConnection)) {
            throw new IllegalStateException("Unsupported OkHttp connection implementation");
        }
        // 该方法内部同步设置 noNewExchanges；已创建的 exchange 继续，最后一条释放时关闭连接。
        ((RealConnection) connection).noNewExchanges$okhttp();
    }
}
