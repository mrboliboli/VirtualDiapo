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

data class SettingsFocusLayout(
    val autoAdvanceEnabled: Boolean,
    val serverMode: ServerMode,
) {
    val autoAdvanceIndex = 2
    val durationIndex: Int? = if (autoAdvanceEnabled) 3 else null
    val serverModeIndex = if (autoAdvanceEnabled) 4 else 3
    val manualAddressIndex: Int? = if (serverMode == ServerMode.MANUAL) serverModeIndex + 1 else null
    val manualTestIndex: Int? = if (serverMode == ServerMode.MANUAL) serverModeIndex + 2 else null
    val maximumIndex = manualTestIndex ?: serverModeIndex
}
