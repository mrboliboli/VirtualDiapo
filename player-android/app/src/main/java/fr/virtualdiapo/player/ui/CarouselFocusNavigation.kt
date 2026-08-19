package fr.virtualdiapo.player.ui

import fr.virtualdiapo.player.network.ServerMode

enum class CarouselFocusZone {
    CAROUSEL,
    SETTINGS,
}

enum class CarouselFocusDirection {
    UP,
    DOWN,
}

fun moveCarouselFocus(
    current: CarouselFocusZone,
    direction: CarouselFocusDirection,
): CarouselFocusZone = when (current) {
    CarouselFocusZone.CAROUSEL -> when (direction) {
        CarouselFocusDirection.UP -> CarouselFocusZone.SETTINGS
        CarouselFocusDirection.DOWN -> CarouselFocusZone.CAROUSEL
    }
    CarouselFocusZone.SETTINGS -> when (direction) {
        CarouselFocusDirection.UP -> CarouselFocusZone.SETTINGS
        CarouselFocusDirection.DOWN -> CarouselFocusZone.CAROUSEL
    }
}

fun maximumSettingsIndex(mode: ServerMode): Int =
    if (mode == ServerMode.MANUAL) 4 else 2
