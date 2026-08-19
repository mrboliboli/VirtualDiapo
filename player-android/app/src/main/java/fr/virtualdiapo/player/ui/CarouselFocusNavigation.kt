package fr.virtualdiapo.player.ui

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
