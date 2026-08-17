package fr.virtualdiapo.player.projection

sealed interface ProjectionState {
    val slideCount: Int

    data class LoadingFirstSlide(override val slideCount: Int) : ProjectionState {
        init { require(slideCount > 0) }
    }

    data class Slide(override val slideCount: Int, val index: Int) : ProjectionState {
        init { require(slideCount > 0); require(index in 0 until slideCount) }
    }

    data class Preparing(
        override val slideCount: Int,
        val origin: Destination?,
        val destination: Destination,
    ) : ProjectionState {
        init {
            require(slideCount > 0)
            origin?.validate(slideCount)
            destination.validate(slideCount)
        }
    }

    data class Transition(override val slideCount: Int, val destination: Destination) : ProjectionState {
        init { require(slideCount > 0); destination.validate(slideCount) }
    }

    data class EndOfCarousel(override val slideCount: Int) : ProjectionState {
        init { require(slideCount > 0) }
    }

    sealed interface Destination {
        data class Slide(val index: Int) : Destination
        data object End : Destination

        fun validate(slideCount: Int) {
            if (this is Slide) require(index in 0 until slideCount)
        }
    }

    fun beginInitialLoad(): ProjectionState? = when (this) {
        is LoadingFirstSlide -> Preparing(slideCount, null, Destination.Slide(0))
        else -> null
    }

    fun beginMove(delta: Int): ProjectionState? {
        if (delta !in setOf(-1, 1)) return null
        return when (this) {
            is LoadingFirstSlide, is Preparing, is Transition -> null
            is Slide -> when {
                delta < 0 && index > 0 -> Preparing(slideCount, Destination.Slide(index), Destination.Slide(index - 1))
                delta > 0 && index < slideCount - 1 -> Preparing(slideCount, Destination.Slide(index), Destination.Slide(index + 1))
                delta > 0 && index == slideCount - 1 -> Preparing(slideCount, Destination.Slide(index), Destination.End)
                else -> null
            }
            is EndOfCarousel -> if (delta < 0) {
                Preparing(slideCount, Destination.End, Destination.Slide(slideCount - 1))
            } else null
        }
    }

    fun beginMechanicalTransition(): ProjectionState? = when (this) {
        is Preparing -> Transition(slideCount, destination)
        else -> null
    }

    fun cancelPreparation(): ProjectionState = when (this) {
        is Preparing -> when (val source = origin) {
            null -> LoadingFirstSlide(slideCount)
            is Destination.Slide -> Slide(slideCount, source.index)
            Destination.End -> EndOfCarousel(slideCount)
        }
        else -> this
    }

    fun visibleSlideIndex(): Int? = when (this) {
        is Slide -> index
        is Preparing -> (origin as? Destination.Slide)?.index
        else -> null
    }

    fun targetSlideIndex(): Int? = when (this) {
        is Preparing -> (destination as? Destination.Slide)?.index
        else -> null
    }

    fun reveal(): ProjectionState = when (this) {
        is Transition -> when (val target = destination) {
            is Destination.Slide -> Slide(slideCount, target.index)
            Destination.End -> EndOfCarousel(slideCount)
        }
        else -> this
    }

    fun settledPosition(): Int = when (this) {
        is LoadingFirstSlide -> -1
        is Slide -> index
        is Preparing -> when (val source = origin) {
            null -> -1
            is Destination.Slide -> source.index
            Destination.End -> slideCount
        }
        is Transition -> when (val target = destination) {
            is Destination.Slide -> target.index
            Destination.End -> slideCount
        }
        is EndOfCarousel -> slideCount
    }

    companion object {
        fun initial(slideCount: Int): ProjectionState = LoadingFirstSlide(slideCount)

        fun restore(slideCount: Int, position: Int): ProjectionState = when (position) {
            -1 -> LoadingFirstSlide(slideCount)
            slideCount -> EndOfCarousel(slideCount)
            else -> Slide(slideCount, position)
        }
    }
}
