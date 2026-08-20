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
    fun settingsFocusLayout_shouldAdaptToAutoAdvanceAndServerMode() {
        val mdnsOff = SettingsFocusLayout(autoAdvanceEnabled = false, serverMode = ServerMode.MDNS)
        val manualOn = SettingsFocusLayout(autoAdvanceEnabled = true, serverMode = ServerMode.MANUAL)

        assertEquals(null, mdnsOff.durationIndex)
        assertEquals(3, mdnsOff.serverModeIndex)
        assertEquals(3, mdnsOff.maximumIndex)
        assertEquals(3, manualOn.durationIndex)
        assertEquals(4, manualOn.serverModeIndex)
        assertEquals(5, manualOn.manualAddressIndex)
        assertEquals(6, manualOn.manualTestIndex)
        assertEquals(6, manualOn.maximumIndex)
    }
}
