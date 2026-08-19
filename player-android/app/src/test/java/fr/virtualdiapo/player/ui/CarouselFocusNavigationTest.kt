package fr.virtualdiapo.player.ui

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
}
