package fr.virtualdiapo.player.ui

import fr.virtualdiapo.player.network.ServerMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CarouselFocusNavigationTest {
    @Test
    fun moveCarouselFocus_shouldMoveFromCarouselToSettings_whenPressingUp() {
        assertEquals(
            CarouselFocusZone.SETTINGS,
            moveCarouselFocus(CarouselFocusZone.CAROUSEL, CarouselFocusDirection.UP),
        )
    }

    @Test
    fun moveCarouselFocus_shouldReturnToCarousel_whenPressingDownOnSettings() {
        assertEquals(
            CarouselFocusZone.CAROUSEL,
            moveCarouselFocus(CarouselFocusZone.SETTINGS, CarouselFocusDirection.DOWN),
        )
    }

    @Test
    fun maximumSettingsIndex_shouldAdaptToServerMode() {
        assertEquals(2, maximumSettingsIndex(ServerMode.MDNS))
        assertEquals(4, maximumSettingsIndex(ServerMode.MANUAL))
    }
}
