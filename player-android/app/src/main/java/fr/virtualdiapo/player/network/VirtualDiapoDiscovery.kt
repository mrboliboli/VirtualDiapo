package fr.virtualdiapo.player.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredServer(val name: String, val address: String)

class VirtualDiapoDiscovery(context: Context) {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _servers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val servers: StateFlow<List<DiscoveredServer>> = _servers.asStateFlow()
    private var started = false

    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) { started = true }
        override fun onDiscoveryStopped(type: String) { started = false }
        override fun onStartDiscoveryFailed(type: String, code: Int) { started = false }
        override fun onStopDiscoveryFailed(type: String, code: Int) { started = false }
        override fun onServiceLost(service: NsdServiceInfo) {
            _servers.value = _servers.value.filterNot { it.name == service.serviceName }
        }
        @Suppress("DEPRECATION")
        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceType != SERVICE_TYPE) return
            manager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) = Unit
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress ?: return
                    val formatted = if (host.contains(':')) "[$host]" else host
                    val server = DiscoveredServer(info.serviceName, "$formatted:${info.port}")
                    _servers.value = (_servers.value.filterNot { it.name == server.name } + server)
                        .sortedBy { it.name }
                }
            })
        }
    }

    fun start() {
        if (!started) manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }
    fun stop() {
        if (started) runCatching { manager.stopServiceDiscovery(listener) }
        started = false
    }

    companion object { const val SERVICE_TYPE = "_virtualdiapo._tcp." }
}
