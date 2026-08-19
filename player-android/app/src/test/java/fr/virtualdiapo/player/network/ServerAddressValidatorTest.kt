package fr.virtualdiapo.player.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressValidatorTest {
    @Test fun acceptsSupportedAddresses() {
        assertEquals("192.168.1.20:8080", ServerAddressValidator.normalize(" 192.168.1.20:8080 "))
        assertEquals("virtualdiapo.local:8080", ServerAddressValidator.normalize("virtualdiapo.local:8080"))
        assertEquals("[fe80::1]:8080", ServerAddressValidator.normalize("[fe80::1]:8080"))
    }

    @Test fun rejectsMissingAndInvalidPorts() {
        assertNull(ServerAddressValidator.normalize("virtualdiapo.local"))
        assertNull(ServerAddressValidator.normalize("192.168.1.20:0"))
        assertNull(ServerAddressValidator.normalize("192.168.1.20:65536"))
        assertNull(ServerAddressValidator.normalize("999.1.1.1:8080"))
        assertNull(ServerAddressValidator.normalize("foo..bar:8080"))
        assertNull(ServerAddressValidator.normalize("-foo.local:8080"))
        assertNull(ServerAddressValidator.normalize("foo-.local:8080"))
        assertNull(ServerAddressValidator.normalize("${"a".repeat(64)}.local:8080"))
        assertNull(ServerAddressValidator.normalize("[:::]:8080"))
        assertNull(ServerAddressValidator.normalize("[fe80:: 1]:8080"))
    }

    @Test fun manualModeNeverUsesDiscoveredServer() {
        val configuration = ServerConfiguration(
            mode = ServerMode.MANUAL,
            manualAddress = "manual.local:9090",
        )

        assertEquals(
            "manual.local:9090",
            preferredServerAddress(configuration, discoveredAddress = "mdns.local:8080"),
        )
    }

    @Test fun staleAvailabilityRequestCannotPublishOrConnect() {
        val snapshot = AvailabilityRequestSnapshot(3L, ServerMode.MANUAL, "old.local:8080")

        assertEquals(
            false,
            AvailabilityRequestPolicy.isCurrent(
                snapshot,
                generation = 4L,
                mode = ServerMode.MANUAL,
                address = "new.local:8080",
            ),
        )
    }
}
