package com.ai.assistance.operit.core.player.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * Passive, request-scoped network evidence for the native player process.
 *
 * It intentionally records only transport/capability booleans and counts. DNS names, proxy
 * addresses, IP addresses, URLs and network handles never enter player diagnostics.
 */
internal data class PlayerNetworkSnapshot(
    val activeNetworkPresent: Boolean,
    val processBoundNetworkPresent: Boolean,
    val processBoundMatchesActive: Boolean,
    val effectiveNetworkSource: String,
    val activeTransports: Set<String>,
    val effectiveTransports: Set<String>,
    val internet: Boolean,
    val validated: Boolean,
    val notSuspended: Boolean,
    val captivePortal: Boolean,
    val metered: Boolean?,
    val restrictedBackground: Boolean,
    val privateDnsActive: Boolean,
    val privateDnsServerPresent: Boolean,
    val ipv4DnsCount: Int,
    val ipv6DnsCount: Int,
    val ipv4DefaultRouteCount: Int,
    val ipv6DefaultRouteCount: Int,
    val proxyType: String,
) {
    fun diagnosticSummary(): String =
        "active=$activeNetworkPresent bound=$processBoundNetworkPresent " +
            "boundMatchesActive=$processBoundMatchesActive " +
            "effective=$effectiveNetworkSource " +
            "activeTransports=${activeTransports.sorted().joinToString("|").ifBlank { "none" }} " +
            "effectiveTransports=${effectiveTransports.sorted().joinToString("|").ifBlank { "none" }} " +
            "internet=$internet validated=$validated notSuspended=$notSuspended " +
            "captivePortal=$captivePortal metered=${metered ?: "unknown"} " +
            "backgroundRestricted=$restrictedBackground " +
            "privateDns=$privateDnsActive privateDnsServer=$privateDnsServerPresent " +
            "dns4=$ipv4DnsCount dns6=$ipv6DnsCount " +
            "defaultRoute4=$ipv4DefaultRouteCount defaultRoute6=$ipv6DefaultRouteCount " +
            "proxy=$proxyType"
}

internal fun capturePlayerNetworkSnapshot(context: Context): PlayerNetworkSnapshot {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork
    val boundNetwork = connectivityManager.boundNetworkForProcess
    // A process-bound network owns native sockets even when Android reports another active
    // network. Using the active network here would hide VPN bypass or an unexpected binding.
    val effectiveNetwork = boundNetwork ?: activeNetwork
    val activeCapabilities = activeNetwork?.let(connectivityManager::getNetworkCapabilities)
    val capabilities = effectiveNetwork?.let(connectivityManager::getNetworkCapabilities)
    val linkProperties = effectiveNetwork?.let(connectivityManager::getLinkProperties)
    val dnsServers = linkProperties?.dnsServers.orEmpty()
    val defaultRoutes = linkProperties?.routes.orEmpty().filter { route -> route.isDefaultRoute }
    val proxy = linkProperties?.httpProxy
    val proxyType =
        when {
            proxy == null -> "absent"
            proxy.pacFileUrl?.toString().orEmpty().isNotBlank() -> "pac"
            else -> "static"
        }
    val restrictedBackground =
        connectivityManager.restrictBackgroundStatus !=
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
    return PlayerNetworkSnapshot(
        activeNetworkPresent = activeNetwork != null,
        processBoundNetworkPresent = boundNetwork != null,
        processBoundMatchesActive =
            activeNetwork != null && boundNetwork != null && sameNetwork(activeNetwork, boundNetwork),
        effectiveNetworkSource =
            when {
                boundNetwork != null -> "bound"
                activeNetwork != null -> "active"
                else -> "absent"
            },
        activeTransports = resolveNetworkTransports(activeCapabilities),
        effectiveTransports = resolveNetworkTransports(capabilities),
        internet =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
        validated =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
        notSuspended =
            capabilities != null &&
                (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                        capabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED,
                        )
                ),
        captivePortal =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true,
        metered =
            capabilities?.let { current ->
                !current.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            },
        restrictedBackground = restrictedBackground,
        privateDnsActive =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                linkProperties?.isPrivateDnsActive == true,
        privateDnsServerPresent =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                !linkProperties?.privateDnsServerName.isNullOrBlank(),
        ipv4DnsCount = dnsServers.count { address -> address is Inet4Address },
        ipv6DnsCount = dnsServers.count { address -> address is Inet6Address },
        ipv4DefaultRouteCount =
            defaultRoutes.count { route -> route.destination.address is Inet4Address },
        ipv6DefaultRouteCount =
            defaultRoutes.count { route -> route.destination.address is Inet6Address },
        proxyType = proxyType,
    )
}

internal fun shouldPublishPlayerNetworkSnapshot(
    previous: PlayerNetworkSnapshot?,
    current: PlayerNetworkSnapshot,
): Boolean = previous != current

/**
 * Observes Android's effective network facts without opening sockets or changing process binding.
 *
 * ConnectivityManager can emit several callbacks for one transition. Coalescing them on the
 * player runtime Handler keeps diagnostics bounded while preserving every visible fact change.
 */
internal class PlayerNetworkSnapshotObserver(
    context: Context,
    private val handler: Handler,
    private val onSnapshotChanged: (reason: String, snapshot: PlayerNetworkSnapshot) -> Unit,
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var registered = false
    private var lastSnapshot: PlayerNetworkSnapshot? = null
    private val pendingReasons = linkedSetOf<String>()
    private val publishRunnable =
        Runnable {
            val reason = pendingReasons.sorted().joinToString("+").ifBlank { "changed" }
            pendingReasons.clear()
            publishSnapshot(reason)
        }
    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scheduleSnapshot("available")
            }

            override fun onLost(network: Network) {
                scheduleSnapshot("lost")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                scheduleSnapshot("capabilities")
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties,
            ) {
                scheduleSnapshot("link-properties")
            }
        }

    fun start() {
        check(!registered) { "Player network observer is already registered" }
        connectivityManager.registerDefaultNetworkCallback(callback, handler)
        registered = true
        publishSnapshot("initial")
    }

    fun stop() {
        if (!registered) return
        handler.removeCallbacks(publishRunnable)
        pendingReasons.clear()
        connectivityManager.unregisterNetworkCallback(callback)
        registered = false
        lastSnapshot = null
    }

    private fun scheduleSnapshot(reason: String) {
        pendingReasons += reason
        handler.removeCallbacks(publishRunnable)
        handler.postDelayed(publishRunnable, PLAYER_NETWORK_SNAPSHOT_COALESCE_MILLIS)
    }

    private fun publishSnapshot(reason: String) {
        val snapshot = capturePlayerNetworkSnapshot(appContext)
        if (!shouldPublishPlayerNetworkSnapshot(lastSnapshot, snapshot)) return
        lastSnapshot = snapshot
        onSnapshotChanged(reason, snapshot)
    }
}

private fun sameNetwork(first: Network, second: Network): Boolean = first == second

private fun resolveNetworkTransports(capabilities: NetworkCapabilities?): Set<String> =
    buildSet {
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("wifi")
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("cellular")
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) add("ethernet")
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("vpn")
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true) add("bluetooth")
    }

private const val PLAYER_NETWORK_SNAPSHOT_COALESCE_MILLIS = 250L
