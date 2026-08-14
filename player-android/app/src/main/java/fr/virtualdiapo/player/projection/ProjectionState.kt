package fr.virtualdiapo.player.projection

data class ProjectionState(
    val slideCount: Int,
    val currentIndex: Int = 0,
    val black: Boolean = false,
    val transitioning: Boolean = false,
) {
    init {
        require(slideCount > 0)
        require(currentIndex in 0 until slideCount)
    }

    fun beginMove(delta: Int): ProjectionState? {
        if (transitioning) return null
        val target = currentIndex + delta
        if (target !in 0 until slideCount) return null
        return copy(currentIndex = target, black = true, transitioning = true)
    }

    fun reveal(): ProjectionState = copy(black = false, transitioning = false)
}

