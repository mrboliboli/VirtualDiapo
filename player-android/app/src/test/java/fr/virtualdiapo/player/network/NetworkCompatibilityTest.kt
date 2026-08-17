package fr.virtualdiapo.player.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkCompatibilityTest {
    @Test
    fun `DNS-SD type accepts trailing local dot variations`() {
        assertTrue(DiscoveryProtocol.matchesServiceType("_virtualdiapo._tcp."))
        assertTrue(DiscoveryProtocol.matchesServiceType("_VIRTUALDIAPO._TCP"))
        assertFalse(DiscoveryProtocol.matchesServiceType("_http._tcp."))
    }

    @Test
    fun `resolved addresses support IPv4 and IPv6`() {
        assertEquals("192.168.1.42:8080", DiscoveryProtocol.formatAddress("192.168.1.42", 8080))
        assertEquals("[fe80::1234]:8080", DiscoveryProtocol.formatAddress("fe80::1234", 8080))
    }

    @Test
    fun `network failures have actionable French messages`() {
        assertTrue(connectionErrorMessage(SocketTimeoutException()).contains("ne répond pas"))
        assertTrue(connectionErrorMessage(ConnectException()).contains("VirtualDiapo"))
        assertTrue(connectionErrorMessage(UnknownHostException()).contains("introuvable"))
    }
}
