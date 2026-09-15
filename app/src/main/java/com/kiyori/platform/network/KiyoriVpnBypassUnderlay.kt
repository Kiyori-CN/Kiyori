package com.kiyori.platform.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * 系统 VPN（例如外部 Clash/Surfboard 的 TUN）会把本应用的全部出站套接字接管进隧道，
 * 包括内嵌 Mihomo 与自身节点服务器之间的连接。两层代理互相嵌套后，出站握手要么被外层
 * 规则再次代理、要么落进外层的 fake-ip 段，表现就是"核心健康、端口在听、请求却超时"。
 *
 * 这个回环 SOCKS5 中继是内嵌核心的出站底座：核心把节点地址原样交给它（域名不在核心内解析），
 * 中继把真实套接字绑定到"非 VPN"的物理 Network 后再连接，于是内嵌核心与系统 VPN 各走各的路，
 * 不再互相嵌套。没有可用物理网络时退回不绑定连接，行为与未启用该模式时一致。
 *
 * 只有握手通过随机口令的客户端才能使用；回环端口对同设备其它应用同样可见，凭据是这里唯一的边界。
 */
internal class KiyoriVpnBypassUnderlay private constructor(
    private val networks: KiyoriUnderlayNetworkSource,
    private val serverSocket: ServerSocket,
    private val username: String,
    private val password: String,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val activeSessions = AtomicInteger(0)
    private val rejectedSessions = AtomicLong(0L)
    private val failureReportedAtEpochMillis = AtomicLong(0L)
    private val usernameBytes = username.toByteArray(Charsets.UTF_8)
    private val passwordBytes = password.toByteArray(Charsets.UTF_8)

    val port: Int
        get() = serverSocket.localPort

    val binding: KiyoriVpnBypassBinding
        get() = KiyoriVpnBypassBinding(port = port, username = username, password = password)

    private fun startAcceptLoop() {
        thread(name = "Kiyori-Underlay-Accept", isDaemon = true) {
            acceptUntilClosed()
            // 循环结束就意味着不再有人接受连接。此时必须关闭监听，让核心立即得到明确拒绝，
            // 而不是把连接挂在内核 backlog 里表现成无法解释的超时。
            if (!closed.get()) {
                KiyoriNetworkProxyLogStore.error("VPN 绕行", "回环出站中继停止接受连接 port=$port")
                close()
            }
        }
    }

    private fun acceptUntilClosed() {
        while (!closed.get()) {
            val client =
                try {
                    serverSocket.accept()
                } catch (error: IOException) {
                    if (closed.get() || serverSocket.isClosed) break
                    // 单次 accept 失败不代表监听失效。这个监听同时承载核心的 DNS 解析，
                    // 因为一次瞬时错误退出循环会让后续连接排队等待而不是明确失败。
                    reportFailure("接受连接失败 type=${error::class.java.simpleName}")
                    runCatching { Thread.sleep(ACCEPT_RETRY_DELAY_MILLIS) }
                    continue
                }
            if (activeSessions.get() >= MAX_CONCURRENT_SESSIONS) {
                rejectedSessions.incrementAndGet()
                closeQuietly(client)
                reportFailure("并发会话已达上限 limit=$MAX_CONCURRENT_SESSIONS rejected=${rejectedSessions.get()}")
                continue
            }
            activeSessions.incrementAndGet()
            val started =
                runCatching {
                    thread(name = "Kiyori-Underlay-Session", isDaemon = true) {
                        try {
                            serveSession(client)
                        } finally {
                            activeSessions.decrementAndGet()
                            closeQuietly(client)
                        }
                    }
                }.isSuccess
            if (!started) {
                activeSessions.decrementAndGet()
                closeQuietly(client)
                reportFailure("无法为新会话创建线程")
            }
        }
    }

    private fun serveSession(client: Socket) {
        client.tcpNoDelay = true
        client.soTimeout = HANDSHAKE_TIMEOUT_MILLIS
        val input = client.getInputStream()
        val output = client.getOutputStream()
        if (!negotiateAuthentication(input, output)) return
        val request = readKiyoriSocksRequest(input) ?: return
        when (request.command) {
            KIYORI_SOCKS_COMMAND_CONNECT -> serveConnect(client, output, request)
            KIYORI_SOCKS_COMMAND_UDP_ASSOCIATE -> serveUdpAssociate(client, input, output)
            else -> writeReply(output, KIYORI_SOCKS_REPLY_COMMAND_UNSUPPORTED)
        }
    }

    private fun negotiateAuthentication(
        input: InputStream,
        output: OutputStream,
    ): Boolean {
        val greeting = readExactly(input, 2) ?: return false
        if (greeting[0] != KIYORI_SOCKS_VERSION) return false
        val methodCount = greeting[1].toInt() and 0xff
        if (methodCount <= 0) return false
        val methods = readExactly(input, methodCount) ?: return false
        if (methods.none { method -> method.toInt() and 0xff == KIYORI_SOCKS_AUTH_USER_PASSWORD }) {
            // 回环端口对同设备其它应用可见。不接受匿名方法是这里唯一能守住的边界。
            output.write(byteArrayOf(KIYORI_SOCKS_VERSION, 0xFF.toByte()))
            output.flush()
            return false
        }
        output.write(byteArrayOf(KIYORI_SOCKS_VERSION, KIYORI_SOCKS_AUTH_USER_PASSWORD.toByte()))
        output.flush()

        val authHeader = readExactly(input, 2) ?: return false
        if (authHeader[0].toInt() != 0x01) return false
        val candidateUsername = readExactly(input, authHeader[1].toInt() and 0xff) ?: return false
        val passwordLength = readExactly(input, 1) ?: return false
        val candidatePassword = readExactly(input, passwordLength[0].toInt() and 0xff) ?: return false
        val accepted =
            MessageDigest.isEqual(candidateUsername, usernameBytes) &&
                MessageDigest.isEqual(candidatePassword, passwordBytes)
        output.write(byteArrayOf(0x01, if (accepted) 0x00 else 0x01))
        output.flush()
        if (!accepted) reportFailure("拒绝了一个凭据不匹配的回环客户端")
        return accepted
    }

    private fun serveConnect(
        client: Socket,
        output: OutputStream,
        request: KiyoriSocksRequest,
    ) {
        val upstream =
            try {
                openUpstreamStream(request.host, request.port)
            } catch (error: IOException) {
                writeReply(output, replyCodeFor(error))
                reportFailure(
                    "出站连接失败 type=${error::class.java.simpleName} " +
                        "network=${networks.describeCurrent()}",
                )
                return
            }
        try {
            writeReply(output, KIYORI_SOCKS_REPLY_SUCCEEDED, upstream.localBindAddress())
            client.soTimeout = 0
            upstream.soTimeout = 0
            relayStreams(client, upstream)
        } finally {
            closeQuietly(upstream)
        }
    }

    /**
     * 基于 UDP 传输的节点（Hysteria2、TUIC、WireGuard 等）在 dialer 上申请 UDP ASSOCIATE。
     * 缺少这一支时这些订阅会在并存模式下整体不可用，所以这里与 CONNECT 一样落到物理网络上。
     */
    private fun serveUdpAssociate(
        client: Socket,
        input: InputStream,
        output: OutputStream,
    ) {
        val clientFacing =
            try {
                DatagramSocket(0, LOOPBACK_ADDRESS)
            } catch (error: IOException) {
                writeReply(output, KIYORI_SOCKS_REPLY_GENERAL_FAILURE)
                reportFailure("无法创建 UDP 关联端口 type=${error::class.java.simpleName}")
                return
            }
        val upstream =
            try {
                openUpstreamDatagram()
            } catch (error: IOException) {
                closeQuietly(clientFacing)
                writeReply(output, KIYORI_SOCKS_REPLY_GENERAL_FAILURE)
                reportFailure("无法绑定 UDP 出站套接字 type=${error::class.java.simpleName}")
                return
            }
        val association = KiyoriUnderlayUdpAssociation(clientFacing, upstream)
        try {
            writeReply(
                output,
                KIYORI_SOCKS_REPLY_SUCCEEDED,
                InetSocketAddress(LOOPBACK_ADDRESS, clientFacing.localPort),
            )
            client.soTimeout = 0
            val downstream =
                thread(name = "Kiyori-Underlay-Udp-Down", isDaemon = true) {
                    pumpUdpDownstream(association)
                }
            val upstreamPump =
                thread(name = "Kiyori-Underlay-Udp-Up", isDaemon = true) {
                    pumpUdpUpstream(association)
                }
            // 控制连接的生命周期就是关联的生命周期：对端关闭即拆除，避免悬空的 UDP 端口。
            drainUntilClosed(input)
            association.close()
            downstream.join(THREAD_JOIN_TIMEOUT_MILLIS)
            upstreamPump.join(THREAD_JOIN_TIMEOUT_MILLIS)
        } finally {
            association.close()
        }
    }

    private fun pumpUdpUpstream(association: KiyoriUnderlayUdpAssociation) {
        val buffer = ByteArray(MAX_UDP_DATAGRAM_BYTES)
        while (!association.closed()) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                association.clientFacing.receive(packet)
            } catch (error: IOException) {
                break
            }
            association.rememberClient(InetSocketAddress(packet.address, packet.port))
            val datagram = parseKiyoriSocksUdpDatagram(packet.data, packet.offset, packet.length) ?: continue
            val target =
                try {
                    resolveTarget(datagram.host, datagram.port)
                } catch (error: IOException) {
                    reportFailure("UDP 目标解析失败 type=${error::class.java.simpleName}")
                    continue
                }
            try {
                association.upstream.send(
                    DatagramPacket(packet.data, datagram.payloadOffset, datagram.payloadLength, target),
                )
            } catch (error: IOException) {
                if (error is SocketException && association.closed()) break
                reportFailure("UDP 发送失败 type=${error::class.java.simpleName}")
            }
        }
    }

    private fun pumpUdpDownstream(association: KiyoriUnderlayUdpAssociation) {
        val buffer = ByteArray(MAX_UDP_DATAGRAM_BYTES)
        while (!association.closed()) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                association.upstream.receive(packet)
            } catch (error: PortUnreachableException) {
                continue
            } catch (error: IOException) {
                break
            }
            val client = association.currentClient() ?: continue
            val encoded =
                buildKiyoriSocksUdpDatagram(
                    address = packet.address,
                    port = packet.port,
                    payload = packet.data,
                    offset = packet.offset,
                    length = packet.length,
                ) ?: continue
            try {
                association.clientFacing.send(DatagramPacket(encoded, encoded.size, client))
            } catch (error: IOException) {
                if (error is SocketException && association.closed()) break
            }
        }
    }

    private fun drainUntilClosed(input: InputStream) {
        val buffer = ByteArray(256)
        while (true) {
            val read =
                try {
                    input.read(buffer)
                } catch (error: IOException) {
                    return
                }
            if (read < 0) return
        }
    }

    private fun relayStreams(
        client: Socket,
        upstream: Socket,
    ) {
        val downstream =
            thread(name = "Kiyori-Underlay-Down", isDaemon = true) {
                copyStream(upstream, client)
            }
        copyStream(client, upstream)
        // 上传方向先结束是常态（请求体发完即半关闭）。这里必须等下行复制自然结束，
        // 否则流式响应会在会话线程退出、套接字被关闭时被截断。
        downstream.join()
    }

    private fun copyStream(
        from: Socket,
        to: Socket,
    ) {
        val buffer = ByteArray(RELAY_BUFFER_BYTES)
        try {
            val input = from.getInputStream()
            val output = to.getOutputStream()
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                output.write(buffer, 0, read)
                output.flush()
            }
            // 半关闭必须传递下去：上传方向结束不能连带切断仍在下发的响应体（SSE、长回包）。
            runCatching { to.shutdownOutput() }
        } catch (error: IOException) {
            closeQuietly(from)
            closeQuietly(to)
        }
    }

    private fun openUpstreamStream(
        host: String,
        port: Int,
    ): Socket {
        val network = networks.current()
        if (network != null) {
            try {
                return connectUpstream(network, host, port)
            } catch (error: IOException) {
                // 系统 VPN 开启"阻止不使用 VPN 的连接"时，绑定后的连接会被内核直接拒绝。
                // 那种设备上绕行不可能成立，退回默认路由至少保留原有的嵌套链路。
                reportFailure(
                    "绑定物理网络的出站失败，回退系统默认路由 " +
                        "type=${error::class.java.simpleName} network=${networks.describeCurrent()}",
                )
            }
        }
        return connectUpstream(null, host, port)
    }

    private fun connectUpstream(
        network: Network?,
        host: String,
        port: Int,
    ): Socket {
        val addresses = resolveAll(network, host)
        var lastError: IOException? = null
        addresses.forEach { address ->
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                if (network != null) {
                    // bindSocket 需要已存在的文件描述符；先绑定本地通配地址再交给系统打标记。
                    socket.bind(InetSocketAddress(0))
                    network.bindSocket(socket)
                }
                socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MILLIS)
                return socket
            } catch (error: IOException) {
                closeQuietly(socket)
                lastError = error
            }
        }
        throw lastError ?: UnknownHostException(KIYORI_UNDERLAY_UNRESOLVED_MESSAGE)
    }

    private fun openUpstreamDatagram(): DatagramSocket {
        val socket = DatagramSocket(null as SocketAddress?)
        return try {
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(0))
            val network = networks.current()
            if (network != null && !bindDatagramToNetwork(network, socket)) {
                reportFailure("UDP 套接字无法绑定物理网络，回退至系统默认路由")
            }
            socket
        } catch (error: IOException) {
            closeQuietly(socket)
            throw error
        }
    }

    private fun bindDatagramToNetwork(
        network: Network,
        socket: DatagramSocket,
    ): Boolean =
        runCatching { network.bindSocket(socket) }.isSuccess

    private fun resolveTarget(
        host: String,
        port: Int,
    ): InetSocketAddress {
        val address =
            resolveAll(networks.current(), host).firstOrNull()
                ?: throw UnknownHostException(KIYORI_UNDERLAY_UNRESOLVED_MESSAGE)
        return InetSocketAddress(address, port)
    }

    /**
     * 域名由这里解析而不是由核心解析：系统 VPN 往往同时劫持 DNS，核心在隧道内查到的可能是
     * 外层的 fake-ip。绑定网络后的解析走物理链路的 DNS，拿到的是可直接连接的真实地址。
     */
    private fun resolveAll(
        network: Network?,
        host: String,
    ): List<InetAddress> {
        // 先判断字面量再解析：对非数字主机名调用 getByName 会触发一次隧道内的系统解析。
        if (isNumericHost(host)) {
            runCatching { InetAddress.getByName(host) }.getOrNull()?.let { literal ->
                return listOf(literal)
            }
        }
        val resolved =
            runCatching {
                if (network != null) {
                    network.getAllByName(host).toList()
                } else {
                    InetAddress.getAllByName(host).toList()
                }
            }.getOrElse { emptyList() }
        if (resolved.isNotEmpty()) return orderUpstreamAddresses(resolved)
        // 绑定网络上的解析失败时仍尝试系统解析器，避免单一路径故障直接断掉全部出站。
        return orderUpstreamAddresses(
            runCatching { InetAddress.getAllByName(host).toList() }.getOrElse { emptyList() },
        )
    }

    /**
     * 先试 IPv4：节点地址绝大多数是 IPv4，而移动网络上不可用的 IPv6 记录会让每次拨号
     * 先耗光一次连接超时，核心那边早已判定失败。
     */
    private fun orderUpstreamAddresses(addresses: List<InetAddress>): List<InetAddress> =
        addresses
            .sortedBy { address -> if (address is Inet4Address) 0 else 1 }
            .take(MAX_UPSTREAM_ADDRESS_ATTEMPTS)

    private fun writeReply(
        output: OutputStream,
        replyCode: Int,
        boundAddress: InetSocketAddress? = null,
    ) {
        runCatching {
            output.write(buildKiyoriSocksReply(replyCode, boundAddress))
            output.flush()
        }
    }

    private fun replyCodeFor(error: IOException): Int =
        when (error) {
            is UnknownHostException -> KIYORI_SOCKS_REPLY_HOST_UNREACHABLE
            is NoRouteToHostException -> KIYORI_SOCKS_REPLY_NETWORK_UNREACHABLE
            is SocketTimeoutException -> KIYORI_SOCKS_REPLY_HOST_UNREACHABLE
            else -> KIYORI_SOCKS_REPLY_GENERAL_FAILURE
        }

    private fun reportFailure(message: String) {
        val now = System.currentTimeMillis()
        val previous = failureReportedAtEpochMillis.get()
        if (now - previous < FAILURE_REPORT_INTERVAL_MILLIS) return
        if (!failureReportedAtEpochMillis.compareAndSet(previous, now)) return
        KiyoriNetworkProxyLogStore.warning("VPN 绕行", message)
    }

    /**
     * 核心的解析与出站全部依赖这个监听端口，它不可达时整个应用会表现为“解析失败”而不是
     * “中继故障”。启动阶段先自己握手一次，把这类问题挡在核心启动之前。
     */
    private fun requireReachableFromLoopback() {
        try {
            Socket().use { probe ->
                probe.connect(InetSocketAddress(LOOPBACK_ADDRESS, port), SELF_CHECK_TIMEOUT_MILLIS)
                probe.soTimeout = SELF_CHECK_TIMEOUT_MILLIS
                val output = probe.getOutputStream()
                output.write(
                    byteArrayOf(
                        KIYORI_SOCKS_VERSION,
                        1,
                        KIYORI_SOCKS_AUTH_USER_PASSWORD.toByte(),
                    ),
                )
                output.flush()
                val selection = readExactly(probe.getInputStream(), 2)
                if (
                    selection == null ||
                        selection[0] != KIYORI_SOCKS_VERSION ||
                        selection[1].toInt() and 0xff != KIYORI_SOCKS_AUTH_USER_PASSWORD
                ) {
                    throw IOException("unexpected method selection")
                }
            }
        } catch (error: IOException) {
            close()
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CORE_START_FAILED,
                "The VPN bypass underlay is not reachable on its own loopback port.",
                error,
            )
        }
    }

    fun describeState(): String =
        "port=$port sessions=${activeSessions.get()} rejected=${rejectedSessions.get()} " +
            "network=${networks.describeCurrent()}"

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { serverSocket.close() }
        networks.close()
        KiyoriNetworkProxyLogStore.info(
            "VPN 绕行",
            "回环出站中继已停止 port=$port rejected=${rejectedSessions.get()}",
        )
    }

    companion object {
        private const val MAX_CONCURRENT_SESSIONS = 192
        private const val HANDSHAKE_TIMEOUT_MILLIS = 10_000
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val MAX_UPSTREAM_ADDRESS_ATTEMPTS = 4
        private const val RELAY_BUFFER_BYTES = 32 * 1024
        private const val MAX_UDP_DATAGRAM_BYTES = 65_535
        private const val THREAD_JOIN_TIMEOUT_MILLIS = 1_000L
        private const val FAILURE_REPORT_INTERVAL_MILLIS = 15_000L
        private const val ACCEPT_BACKLOG = 64
        private const val SELF_CHECK_TIMEOUT_MILLIS = 3_000
        private const val ACCEPT_RETRY_DELAY_MILLIS = 50L

        /**
         * 监听地址必须与运行配置里写给核心的 `127.0.0.1` 完全一致。
         * Android 的 `InetAddress.getLoopbackAddress()` 返回 IPv6 的 `::1`，绑在那里的监听
         * 不接受 IPv4 回环连接：核心会得到 connection refused，而端口本身看起来是正常分配的。
         */
        val LOOPBACK_ADDRESS: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

        fun start(
            context: Context,
            credential: KiyoriUnderlayCredential,
        ): KiyoriVpnBypassUnderlay {
            val tracker =
                try {
                    KiyoriUnderlayNetworkTracker.install(context)
                } catch (error: Exception) {
                    throw KiyoriNetworkException(
                        KiyoriNetworkErrorCode.CORE_START_FAILED,
                        "The VPN bypass underlay could not observe the device networks.",
                        error,
                    )
                }
            val underlay = startWithNetworkSource(tracker, credential)
            KiyoriNetworkProxyLogStore.info(
                "VPN 绕行",
                "回环出站中继已启动 ${underlay.describeState()}",
            )
            return underlay
        }

        fun startWithNetworkSource(
            networks: KiyoriUnderlayNetworkSource,
            credential: KiyoriUnderlayCredential,
        ): KiyoriVpnBypassUnderlay {
            val serverSocket =
                try {
                    ServerSocket(0, ACCEPT_BACKLOG, LOOPBACK_ADDRESS)
                } catch (error: IOException) {
                    networks.close()
                    throw KiyoriNetworkException(
                        KiyoriNetworkErrorCode.CORE_START_FAILED,
                        "The VPN bypass underlay could not open its loopback listener.",
                        error,
                    )
                }
            val underlay =
                KiyoriVpnBypassUnderlay(
                    networks = networks,
                    serverSocket = serverSocket,
                    username = credential.username,
                    password = credential.password,
                )
            underlay.startAcceptLoop()
            underlay.requireReachableFromLoopback()
            return underlay
        }

        private fun closeQuietly(socket: Socket) {
            runCatching { socket.close() }
        }

        private fun closeQuietly(socket: DatagramSocket) {
            runCatching { socket.close() }
        }
    }
}

internal data class KiyoriUnderlayCredential(
    val username: String,
    val password: String,
)

private class KiyoriUnderlayUdpAssociation(
    val clientFacing: DatagramSocket,
    val upstream: DatagramSocket,
) {
    private val closed = AtomicBoolean(false)

    @Volatile
    private var client: InetSocketAddress? = null

    fun rememberClient(address: InetSocketAddress) {
        client = address
    }

    fun currentClient(): InetSocketAddress? = client

    fun closed(): Boolean = closed.get()

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { clientFacing.close() }
        runCatching { upstream.close() }
    }
}

/**
 * 追踪"不是 VPN"的可用网络。系统在 VPN 生效期间仍把物理网络登记在册，
 * 只是默认网络指向 TUN；这里显式选出物理网络，供出站套接字绑定。
 */
/** 出站套接字要绑定的网络来源。抽出接口后中继本身可以脱离 Android 框架被完整验证。 */
internal interface KiyoriUnderlayNetworkSource : Closeable {
    fun current(): Network?

    fun describeCurrent(): String
}

internal class KiyoriUnderlayNetworkTracker private constructor(
    private val connectivity: ConnectivityManager,
    private val callback: ConnectivityManager.NetworkCallback,
) : KiyoriUnderlayNetworkSource {
    private val lock = Any()
    private val candidates = linkedMapOf<Network, KiyoriUnderlayNetworkCandidate>()
    private val sequence = AtomicLong(0L)

    @Volatile
    private var selected: Network? = null

    override fun current(): Network? = selected

    override fun describeCurrent(): String =
        synchronized(lock) {
            val network = selected ?: return@synchronized "none"
            candidates[network]?.let { candidate ->
                "${candidate.label}${if (candidate.validated) "" else "(unvalidated)"}"
            } ?: "available"
        }

    private fun onNetworkChanged(
        network: Network,
        capabilities: NetworkCapabilities?,
    ) {
        synchronized(lock) {
            if (capabilities == null) {
                candidates.remove(network)
            } else {
                val existing = candidates[network]
                candidates[network] =
                    KiyoriUnderlayNetworkCandidate(
                        order = existing?.order ?: sequence.incrementAndGet(),
                        validated =
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                        transportRank = kiyoriUnderlayTransportRank(capabilities),
                        label = kiyoriUnderlayTransportLabel(capabilities),
                    )
            }
            val best = selectKiyoriUnderlayNetwork(candidates)
            if (best != selected) {
                selected = best
                KiyoriNetworkProxyLogStore.info(
                    "VPN 绕行",
                    "出站物理网络已更新 network=${describeCurrentLocked()} candidates=${candidates.size}",
                )
            }
        }
    }

    private fun describeCurrentLocked(): String {
        val network = selected ?: return "none"
        return candidates[network]?.label ?: "available"
    }

    override fun close() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        synchronized(lock) {
            candidates.clear()
            selected = null
        }
    }

    companion object {
        fun install(context: Context): KiyoriUnderlayNetworkTracker {
            val connectivity =
                context.applicationContext.getSystemService(ConnectivityManager::class.java)
                    ?: throw IllegalStateException("ConnectivityManager is unavailable")
            lateinit var tracker: KiyoriUnderlayNetworkTracker
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        tracker.onNetworkChanged(
                            network,
                            connectivity.getNetworkCapabilities(network),
                        )
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        tracker.onNetworkChanged(network, capabilities)
                    }

                    override fun onLost(network: Network) {
                        tracker.onNetworkChanged(network, null)
                    }
                }
            tracker = KiyoriUnderlayNetworkTracker(connectivity, callback)
            // NetworkRequest.Builder 默认带 NET_CAPABILITY_NOT_VPN，这里得到的都是物理网络。
            val request =
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
            connectivity.registerNetworkCallback(request, callback)
            return tracker
        }
    }
}

internal data class KiyoriUnderlayNetworkCandidate(
    val order: Long,
    val validated: Boolean,
    val transportRank: Int,
    val label: String,
)

/**
 * 已验证优先、其次按传输类型（以太网 > Wi-Fi > 蜂窝 > 其它），同级取最近出现的一个。
 */
internal fun <T> selectKiyoriUnderlayNetwork(
    candidates: Map<T, KiyoriUnderlayNetworkCandidate>,
): T? =
    candidates.entries
        .minWithOrNull(
            compareBy<Map.Entry<T, KiyoriUnderlayNetworkCandidate>> { entry ->
                if (entry.value.validated) 0 else 1
            }.thenBy { entry -> entry.value.transportRank }
                .thenByDescending { entry -> entry.value.order },
        )?.key

internal fun kiyoriUnderlayTransportRank(capabilities: NetworkCapabilities): Int =
    when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 0
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 2
        else -> 3
    }

internal fun kiyoriUnderlayTransportLabel(capabilities: NetworkCapabilities): String =
    when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        else -> "other"
    }

internal const val KIYORI_SOCKS_VERSION: Byte = 0x05
internal const val KIYORI_SOCKS_AUTH_USER_PASSWORD = 0x02
internal const val KIYORI_SOCKS_COMMAND_CONNECT = 0x01
internal const val KIYORI_SOCKS_COMMAND_UDP_ASSOCIATE = 0x03
internal const val KIYORI_SOCKS_REPLY_SUCCEEDED = 0x00
internal const val KIYORI_SOCKS_REPLY_GENERAL_FAILURE = 0x01
internal const val KIYORI_SOCKS_REPLY_NETWORK_UNREACHABLE = 0x03
internal const val KIYORI_SOCKS_REPLY_HOST_UNREACHABLE = 0x04
internal const val KIYORI_SOCKS_REPLY_COMMAND_UNSUPPORTED = 0x07
private const val KIYORI_SOCKS_ADDRESS_IPV4 = 0x01
private const val KIYORI_SOCKS_ADDRESS_DOMAIN = 0x03
private const val KIYORI_SOCKS_ADDRESS_IPV6 = 0x04
private const val KIYORI_UNDERLAY_UNRESOLVED_MESSAGE = "underlay target is unresolvable"

internal data class KiyoriSocksRequest(
    val command: Int,
    val host: String,
    val port: Int,
)

internal data class KiyoriSocksUdpDatagram(
    val host: String,
    val port: Int,
    val payloadOffset: Int,
    val payloadLength: Int,
)

private fun readKiyoriSocksRequest(input: InputStream): KiyoriSocksRequest? {
    val header = readExactly(input, 4) ?: return null
    if (header[0] != KIYORI_SOCKS_VERSION) return null
    val command = header[1].toInt() and 0xff
    val host =
        when (header[3].toInt() and 0xff) {
            KIYORI_SOCKS_ADDRESS_IPV4 -> readExactly(input, 4)?.let(::formatNumericAddress)
            KIYORI_SOCKS_ADDRESS_IPV6 -> readExactly(input, 16)?.let(::formatNumericAddress)
            KIYORI_SOCKS_ADDRESS_DOMAIN -> {
                val length = readExactly(input, 1)?.get(0)?.toInt()?.and(0xff) ?: return null
                readExactly(input, length)?.toString(Charsets.UTF_8)
            }
            else -> null
        } ?: return null
    val portBytes = readExactly(input, 2) ?: return null
    val port = ((portBytes[0].toInt() and 0xff) shl 8) or (portBytes[1].toInt() and 0xff)
    if (port == 0 && command == KIYORI_SOCKS_COMMAND_CONNECT) return null
    return KiyoriSocksRequest(command = command, host = host, port = port)
}

internal fun parseKiyoriSocksUdpDatagram(
    data: ByteArray,
    offset: Int,
    length: Int,
): KiyoriSocksUdpDatagram? {
    if (length < 10) return null
    val end = offset + length
    // RSV(2) 必须为 0，FRAG(1) 非 0 表示分片，这里不支持分片重组。
    if (data[offset].toInt() != 0 || data[offset + 1].toInt() != 0) return null
    if (data[offset + 2].toInt() != 0) return null
    var cursor = offset + 3
    val addressType = data[cursor].toInt() and 0xff
    cursor += 1
    val host =
        when (addressType) {
            KIYORI_SOCKS_ADDRESS_IPV4 -> {
                if (cursor + 4 > end) return null
                formatNumericAddress(data.copyOfRange(cursor, cursor + 4)).also { cursor += 4 }
            }
            KIYORI_SOCKS_ADDRESS_IPV6 -> {
                if (cursor + 16 > end) return null
                formatNumericAddress(data.copyOfRange(cursor, cursor + 16)).also { cursor += 16 }
            }
            KIYORI_SOCKS_ADDRESS_DOMAIN -> {
                if (cursor + 1 > end) return null
                val domainLength = data[cursor].toInt() and 0xff
                cursor += 1
                if (domainLength == 0 || cursor + domainLength > end) return null
                String(data, cursor, domainLength, Charsets.UTF_8).also { cursor += domainLength }
            }
            else -> return null
        } ?: return null
    if (cursor + 2 > end) return null
    val port = ((data[cursor].toInt() and 0xff) shl 8) or (data[cursor + 1].toInt() and 0xff)
    cursor += 2
    if (port == 0) return null
    return KiyoriSocksUdpDatagram(
        host = host,
        port = port,
        payloadOffset = cursor,
        payloadLength = end - cursor,
    )
}

internal fun buildKiyoriSocksUdpDatagram(
    address: InetAddress?,
    port: Int,
    payload: ByteArray,
    offset: Int,
    length: Int,
): ByteArray? {
    val raw = address?.address ?: return null
    val addressType =
        when (address) {
            is Inet4Address -> KIYORI_SOCKS_ADDRESS_IPV4
            is Inet6Address -> KIYORI_SOCKS_ADDRESS_IPV6
            else -> return null
        }
    val encoded = ByteArray(3 + 1 + raw.size + 2 + length)
    encoded[3] = addressType.toByte()
    raw.copyInto(encoded, destinationOffset = 4)
    encoded[4 + raw.size] = ((port shr 8) and 0xff).toByte()
    encoded[5 + raw.size] = (port and 0xff).toByte()
    payload.copyInto(encoded, destinationOffset = 6 + raw.size, startIndex = offset, endIndex = offset + length)
    return encoded
}

internal fun buildKiyoriSocksReply(
    replyCode: Int,
    boundAddress: InetSocketAddress?,
): ByteArray {
    val address = boundAddress?.address
    val raw =
        when (address) {
            is Inet4Address, is Inet6Address -> address.address
            else -> ByteArray(4)
        }
    val addressType = if (raw.size == 16) KIYORI_SOCKS_ADDRESS_IPV6 else KIYORI_SOCKS_ADDRESS_IPV4
    val port = if (address == null) 0 else boundAddress.port
    val reply = ByteArray(4 + raw.size + 2)
    reply[0] = KIYORI_SOCKS_VERSION
    reply[1] = replyCode.toByte()
    reply[3] = addressType.toByte()
    raw.copyInto(reply, destinationOffset = 4)
    reply[4 + raw.size] = ((port shr 8) and 0xff).toByte()
    reply[5 + raw.size] = (port and 0xff).toByte()
    return reply
}

private fun readExactly(
    input: InputStream,
    length: Int,
): ByteArray? {
    if (length <= 0) return ByteArray(0)
    val buffer = ByteArray(length)
    var filled = 0
    while (filled < length) {
        val read =
            try {
                input.read(buffer, filled, length - filled)
            } catch (error: IOException) {
                return null
            }
        if (read < 0) return null
        filled += read
    }
    return buffer
}

private fun formatNumericAddress(raw: ByteArray): String? =
    runCatching { InetAddress.getByAddress(raw).hostAddress }.getOrNull()

private fun isNumericHost(host: String): Boolean =
    host.isNotEmpty() &&
        (host.contains(':') || host.all { character -> character.isDigit() || character == '.' })

private fun Socket.localBindAddress(): InetSocketAddress? =
    getLocalSocketAddress() as? InetSocketAddress
