package fr.virtualdiapo.player.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredServer(val name: String, val address: String)

enum class DiscoveryStatus { STOPPED, SEARCHING, AVAILABLE, UNAVAILABLE }

class VirtualDiapoDiscovery(context: Context) {
    private enum class Lifecycle { STOPPED, STARTING, STARTED, STOPPING }

    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val lock = Any()
    private val pendingResolutions = ArrayDeque<NsdServiceInfo>()
    private val foundNames = mutableSetOf<String>()
    private var lifecycle = Lifecycle.STOPPED
    private var stopWhenStarted = false
    private var resolving = false

    private val _servers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val servers: StateFlow<List<DiscoveredServer>> = _servers.asStateFlow()
    private val _status = MutableStateFlow(DiscoveryStatus.STOPPED)
    val status: StateFlow<DiscoveryStatus> = _status.asStateFlow()

    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) {
            val mustStop = synchronized(lock) {
                lifecycle = Lifecycle.STARTED
                _status.value = DiscoveryStatus.SEARCHING
                stopWhenStarted
            }
            if (mustStop) stop()
        }

        override fun onDiscoveryStopped(type: String) {
            synchronized(lock) { resetStopped() }
        }

        override fun onStartDiscoveryFailed(type: String, code: Int) {
            synchronized(lock) {
                resetStopped()
                _status.value = DiscoveryStatus.UNAVAILABLE
            }
        }

        override fun onStopDiscoveryFailed(type: String, code: Int) {
            synchronized(lock) {
                lifecycle = Lifecycle.STARTED
                stopWhenStarted = false
                _status.value = DiscoveryStatus.UNAVAILABLE
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            synchronized(lock) {
                foundNames.remove(service.serviceName)
                pendingResolutions.removeAll { it.serviceName == service.serviceName }
                _servers.value = _servers.value.filterNot { it.name == service.serviceName }
                if (_servers.value.isEmpty() && lifecycle == Lifecycle.STARTED) {
                    _status.value = DiscoveryStatus.SEARCHING
                }
            }
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            if (!DiscoveryProtocol.matchesServiceType(service.serviceType)) return
            synchronized(lock) {
                if (lifecycle != Lifecycle.STARTED || !foundNames.add(service.serviceName)) return
                pendingResolutions.addLast(service)
            }
            resolveNext()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val service = synchronized(lock) {
            if (lifecycle != Lifecycle.STARTED || resolving || pendingResolutions.isEmpty()) return
            resolving = true
            pendingResolutions.removeFirst()
        }
        try {
            manager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) = finishResolution(null)
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress
                    finishResolution(host?.let {
                        DiscoveredServer(info.serviceName, DiscoveryProtocol.formatAddress(it, info.port))
                    })
                }
            })
        } catch (_: RuntimeException) {
            finishResolution(null)
        }
    }

    private fun finishResolution(server: DiscoveredServer?) {
        synchronized(lock) {
            if (server != null && lifecycle == Lifecycle.STARTED && server.name in foundNames) {
                _servers.value = (_servers.value.filterNot { it.name == server.name } + server).sortedBy { it.name }
                _status.value = DiscoveryStatus.AVAILABLE
            }
            resolving = false
        }
        resolveNext()
    }

    fun start() {
        synchronized(lock) {
            if (lifecycle != Lifecycle.STOPPED) return
            lifecycle = Lifecycle.STARTING
            stopWhenStarted = false
            _status.value = DiscoveryStatus.SEARCHING
        }
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: RuntimeException) {
            synchronized(lock) {
                resetStopped()
                _status.value = DiscoveryStatus.UNAVAILABLE
            }
        }
    }

    fun stop() {
        val stopNow = synchronized(lock) {
            when (lifecycle) {
                Lifecycle.STARTING -> { stopWhenStarted = true; false }
                Lifecycle.STARTED -> { lifecycle = Lifecycle.STOPPING; true }
                else -> false
            }
        }
        if (stopNow) {
            runCatching { manager.stopServiceDiscovery(listener) }
                .onFailure { synchronized(lock) { resetStopped() } }
        }
    }

    private fun resetStopped() {
        lifecycle = Lifecycle.STOPPED
        stopWhenStarted = false
        resolving = false
        pendingResolutions.clear()
        foundNames.clear()
        _servers.value = emptyList()
        _status.value = DiscoveryStatus.STOPPED
    }

    companion object { const val SERVICE_TYPE = "_virtualdiapo._tcp." }
}

internal object DiscoveryProtocol {
    fun matchesServiceType(type: String): Boolean =
        type.trim().trimEnd('.').equals(VirtualDiapoDiscovery.SERVICE_TYPE.trimEnd('.'), ignoreCase = true)

    fun formatAddress(host: String, port: Int): String =
        if (host.contains(':')) "[$host]:$port" else "$host:$port"
}
