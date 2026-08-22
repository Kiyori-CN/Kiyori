package com.ai.assistance.operit.core.tools.javascript.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient

class ScriptNetworkHttpClientFactory private constructor(context: Context) {
    companion object {
        private const val SETTINGS_TEST_PACKAGE = "__kiyori_script_network_settings_test__"

        @Volatile
        private var instance: ScriptNetworkHttpClientFactory? = null

        fun getInstance(context: Context): ScriptNetworkHttpClientFactory =
            instance
                ?: synchronized(this) {
                    instance
                        ?: ScriptNetworkHttpClientFactory(context.applicationContext).also {
                            instance = it
                        }
                }
    }

    private val appContext = context.applicationContext
    private val configStore = ScriptNetworkConfigStore.getInstance(appContext)
    private val runtime = ScriptProxyRuntime.getInstance(appContext)

    suspend fun applyScriptRoute(
        builder: OkHttpClient.Builder,
        packageName: String,
    ): ScriptNetworkRoute =
        applyResolvedRoute(
            builder = builder,
            config = configStore.currentConfig(),
            packageName = packageName,
        )

    suspend fun applyGlobalRouteForTest(
        builder: OkHttpClient.Builder,
        config: ScriptNetworkConfig,
    ): ScriptNetworkRoute =
        applyResolvedRoute(
            builder = builder,
            config = config,
            packageName = SETTINGS_TEST_PACKAGE,
        )

    suspend fun discoverEmbeddedProxyNames(config: ScriptNetworkConfig): List<String> {
        validateEmbeddedRuntimeAccess(config, requireNode = false)
        return runtime.discoverProxyNames(config.embeddedProxy)
    }

    suspend fun selectEmbeddedProxy(
        config: ScriptNetworkConfig,
        proxyName: String,
    ): ScriptProxyEndpoint {
        validateEmbeddedRuntimeAccess(config, requireNode = false)
        return runtime.selectProxy(proxyName)
    }

    suspend fun stopEmbeddedRuntime() {
        runtime.stop()
    }

    private suspend fun applyResolvedRoute(
        builder: OkHttpClient.Builder,
        config: ScriptNetworkConfig,
        packageName: String,
    ): ScriptNetworkRoute {
        val route =
            try {
                ScriptNetworkPolicy.resolve(
                    config = config,
                    packageName = packageName,
                    isSystemVpnActive = isSystemVpnActive(),
                )
            } catch (error: ScriptNetworkException) {
                if (error.code == ScriptNetworkErrorCode.VPN_CONFLICT) runtime.stop()
                throw error
            }
        when (route) {
            ScriptNetworkRoute.Direct -> builder.proxy(Proxy.NO_PROXY)
            is ScriptNetworkRoute.ExternalProxy ->
                applyHttpProxy(
                    builder = builder,
                    host = route.host,
                    port = route.port,
                    username = route.username,
                    password = route.password,
                    proxyPrivateNetworks = config.proxyPrivateNetworks,
                )
            is ScriptNetworkRoute.EmbeddedSubscription -> {
                val endpoint = runtime.ensureReady(config.embeddedProxy)
                applyHttpProxy(
                    builder = builder,
                    host = endpoint.host,
                    port = endpoint.port,
                    username = endpoint.username,
                    password = endpoint.password,
                    proxyPrivateNetworks = config.proxyPrivateNetworks,
                )
            }
        }
        installFailureMapping(builder, route)
        return route
    }

    private suspend fun validateEmbeddedRuntimeAccess(
        config: ScriptNetworkConfig,
        requireNode: Boolean,
    ) {
        try {
            ScriptNetworkPolicy.validateEmbeddedStart(
                config = config,
                isSystemVpnActive = isSystemVpnActive(),
                requireNode = requireNode,
            )
        } catch (error: ScriptNetworkException) {
            if (error.code == ScriptNetworkErrorCode.VPN_CONFLICT) runtime.stop()
            throw error
        }
    }

    fun isSystemVpnActive(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun applyHttpProxy(
        builder: OkHttpClient.Builder,
        host: String,
        port: Int,
        username: String?,
        password: String?,
        proxyPrivateNetworks: Boolean,
    ) {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port))
        builder.proxySelector(ScopedScriptProxySelector(proxy, proxyPrivateNetworks))
        if (username != null && password != null) {
            val credential = Credentials.basic(username, password)
            builder.proxyAuthenticator(
                Authenticator { _, response ->
                    if (response.request.header("Proxy-Authorization") != null) {
                        null
                    } else {
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                },
            )
        }
    }

    private fun installFailureMapping(
        builder: OkHttpClient.Builder,
        route: ScriptNetworkRoute,
    ) {
        builder.addInterceptor { chain ->
            try {
                val response = chain.proceed(chain.request())
                if (route !is ScriptNetworkRoute.Direct && response.code == 407) {
                    response.close()
                    throw ScriptNetworkException(
                        ScriptNetworkErrorCode.PROXY_CONNECT_FAILED,
                        "The configured script proxy rejected authentication.",
                    )
                }
                response
            } catch (error: ScriptNetworkException) {
                throw error
            } catch (error: IOException) {
                val isProxyRoute = route !is ScriptNetworkRoute.Direct
                throw ScriptNetworkException(
                    if (isProxyRoute) {
                        ScriptNetworkErrorCode.PROXY_CONNECT_FAILED
                    } else {
                        ScriptNetworkErrorCode.HTTP_FAILED
                    },
                    if (isProxyRoute) {
                        "The configured script proxy could not connect."
                    } else {
                        "The script direct route could not connect."
                    },
                    error,
                )
            }
        }
    }

}

internal class ScopedScriptProxySelector(
    private val proxy: Proxy,
    private val proxyPrivateNetworks: Boolean,
) : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        val host = uri?.host?.trim()?.lowercase().orEmpty()
        if (host.isEmpty() || shouldUseDirectConnection(host, proxyPrivateNetworks)) {
            return listOf(Proxy.NO_PROXY)
        }
        return listOf(proxy)
    }

    override fun connectFailed(uri: URI?, socketAddress: java.net.SocketAddress?, error: java.io.IOException?) {
        // OkHttp reports the actual connection exception to the owning script request.
    }
}

internal fun shouldUseDirectConnection(
    rawHost: String,
    proxyPrivateNetworks: Boolean,
): Boolean {
    val host = rawHost.removePrefix("[").removeSuffix("]").lowercase()
    if (host == "localhost" || host.endsWith(".localhost")) return true
    val address = parseNumericAddress(host) ?: return false
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isMulticastAddress) return true
    if (proxyPrivateNetworks) return false
    if (address.isSiteLocalAddress || address.isLinkLocalAddress) return true
    if (address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc) return true
    return false
}

private fun parseNumericAddress(host: String): InetAddress? {
    val couldBeNumeric = ':' in host || host.all { character -> character.isDigit() || character == '.' }
    if (!couldBeNumeric) return null
    return runCatching { InetAddress.getByName(host) }.getOrNull()
}
