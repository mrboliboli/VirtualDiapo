package fr.virtualdiapo.player.network

import android.content.Context
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

enum class ServerMode { MDNS, MANUAL }

data class ServerConfiguration(
    val mode: ServerMode = ServerMode.MDNS,
    val manualAddress: String = "",
)

class ServerPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): ServerConfiguration = ServerConfiguration(
        mode = runCatching {
            ServerMode.valueOf(preferences.getString(MODE, ServerMode.MDNS.name)!!)
        }.getOrDefault(ServerMode.MDNS),
        manualAddress = preferences.getString(MANUAL_ADDRESS, "").orEmpty(),
    )

    fun save(configuration: ServerConfiguration) {
        preferences.edit()
            .putString(MODE, configuration.mode.name)
            .putString(MANUAL_ADDRESS, configuration.manualAddress)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "server_preferences"
        const val MODE = "server_mode"
        const val MANUAL_ADDRESS = "manual_server_address"
    }
}

object ServerAddressValidator {
    private val hostnameLabel = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
    private val ipv4 = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")

    fun normalize(value: String): String? {
        val trimmed = value.trim()
        val host: String
        val portText: String
        if (trimmed.startsWith("[")) {
            val closing = trimmed.indexOf(']')
            if (closing <= 1 || closing + 1 >= trimmed.length || trimmed[closing + 1] != ':') return null
            host = trimmed.substring(1, closing)
            portText = trimmed.substring(closing + 2)
            if (!validIpv6(host)) return null
        } else {
            val separator = trimmed.lastIndexOf(':')
            if (separator <= 0 || separator != trimmed.indexOf(':')) return null
            host = trimmed.substring(0, separator)
            portText = trimmed.substring(separator + 1)
            if (ipv4.matches(host)) {
                if (!validIpv4(host)) return null
            } else if (!validHostname(host)) {
                return null
            }
        }
        val port = portText.toIntOrNull() ?: return null
        if (port !in 1..65_535) return null
        return if (host.contains(':')) "[$host]:$port" else "$host:$port"
    }

    private fun validIpv4(value: String): Boolean =
        ipv4.matches(value) && value.split('.').all { it.toInt() in 0..255 }

    private fun validHostname(value: String): Boolean =
        value.length <= 253 &&
            !value.contains("..") &&
            value.split('.').all { label -> label.length in 1..63 && hostnameLabel.matches(label) }

    private fun validIpv6(value: String): Boolean = try {
        value.contains(':') && InetAddress.getByName(value) is Inet6Address
    } catch (_: UnknownHostException) {
        false
    }
}

fun preferredServerAddress(
    configuration: ServerConfiguration,
    discoveredAddress: String?,
): String? = when (configuration.mode) {
    ServerMode.MDNS -> discoveredAddress
    ServerMode.MANUAL -> ServerAddressValidator.normalize(configuration.manualAddress)
}

data class AvailabilityRequestSnapshot(
    val generation: Long,
    val mode: ServerMode,
    val address: String,
)

object AvailabilityRequestPolicy {
    fun isCurrent(
        snapshot: AvailabilityRequestSnapshot,
        generation: Long,
        mode: ServerMode,
        address: String?,
    ): Boolean = snapshot.generation == generation &&
        snapshot.mode == mode &&
        snapshot.address == address
}
