package fr.virtualdiapo.player.projection

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectionTransitionTest {
    @Test
    fun timeline_shouldMoveImmediatelyAndFinishAtFiveHundredTwentyMilliseconds() {
        assertEquals(0L, ProjectionTransition.MOVEMENT_START_MS)
        assertEquals(260L, ProjectionTransition.SOUND_CLICK_MS)
        assertEquals(520L, ProjectionTransition.TOTAL_DURATION_MS)
    }

    @Test
    fun outgoing_shouldTravelPastViewport_whenMovingForward() {
        // WHEN
        val transform = ProjectionTransition.outgoing(
            ProjectionTransition.TOTAL_DURATION_MS,
            direction = 1,
            fadeEnabled = true,
        )

        // THEN
        assertEquals(-1.08f, transform.translationXFactor, .002f)
        assertEquals(0f, transform.photoAlpha, .00001f)
    }

    @Test
    fun incoming_shouldReverseDirection_whenMovingBackward() {
        // WHEN
        val transform = ProjectionTransition.incoming(
            ProjectionTransition.MOVEMENT_START_MS,
            direction = -1,
            fadeEnabled = true,
        )

        // THEN
        assertEquals(-1.08f, transform.translationXFactor, .002f)
        assertEquals(0f, transform.photoAlpha, .00001f)
    }

    @Test
    fun transforms_shouldRemainOpaque_whenFadeIsDisabled() {
        assertEquals(1f, ProjectionTransition.outgoing(500L, 1, false).photoAlpha, 0f)
        assertEquals(1f, ProjectionTransition.incoming(20L, 1, false).photoAlpha, 0f)
    }

    @Test
    fun navigation_shouldAcceptOnlyInitialKeyDown() {
        assertEquals(true, ProjectionNavigation.acceptsKeyDown(isKeyDown = true, repeatCount = 0))
        assertEquals(false, ProjectionNavigation.acceptsKeyDown(isKeyDown = true, repeatCount = 1))
        assertEquals(false, ProjectionNavigation.acceptsKeyDown(isKeyDown = false, repeatCount = 0))
    }

    @Test
    fun plates_shouldExposeCanonicalGap_atSoundClick() {
        val outgoing = ProjectionTransition.outgoing(ProjectionTransition.SOUND_CLICK_MS, 1, false)
        val incoming = ProjectionTransition.incoming(ProjectionTransition.SOUND_CLICK_MS, 1, false)
        val gapWidthFactor = incoming.translationXFactor - outgoing.translationXFactor - 1f

        assertEquals(-.54f, outgoing.translationXFactor, .002f)
        assertEquals(.54f, incoming.translationXFactor, .002f)
        assertEquals(.08f, gapWidthFactor, .002f)
    }

    @Test
    fun beamRadius_shouldUseSeventyEightPercentOfViewportDiagonal() {
        assertEquals(1_560f, ProjectionBeamGeometry.radius(width = 1_600f, height = 1_200f), .01f)
    }

    @Test
    fun haloRadius_shouldUseTwentySixPercentOfSmallestViewportDimension() {
        assertEquals(280.8f, ProjectionBeamGeometry.haloRadius(width = 1_920f, height = 1_080f), .01f)
    }

    @Test
    fun dustCount_shouldScaleWithAreaAndRemainBounded() {
        assertEquals(14, ProjectionDust.count(width = 1_920f, height = 1_080f))
        assertEquals(10, ProjectionDust.count(width = 640f, height = 360f))
        assertEquals(20, ProjectionDust.count(width = 3_840f, height = 2_160f))
    }

    @Test
    fun dustGeneration_shouldBeDeterministicAndRespectVisualLimits() {
        val first = ProjectionDust.generate(width = 1_920f, height = 1_080f)
        val second = ProjectionDust.generate(width = 1_920f, height = 1_080f)

        assertEquals(first, second)
        assertEquals(14, first.size)
        assertEquals(true, first.all { it.x in 0f..1_920f && it.y in 0f..1_080f })
        assertEquals(true, first.all { it.radiusDp in .55f..1.6f })
        assertEquals(true, first.all { it.coreAlpha in .045f..085f })
        assertEquals(true, first.all { it.haloAlpha in .010f..018f })
        assertEquals(true, first.count { it.radiusDp == 1.6f } <= 1)
        assertEquals(
            true,
            first.drop(10).all { particle ->
                ProjectionDust.ellipseValue(particle.x, particle.y, 1_920f, 1_080f) >= 1f
            },
        )
    }

    @Test
    fun dustOffsets_shouldJoinContinuously_betweenEndAndStartOfCycle() {
        val particle = ProjectionDust.generate(width = 1_920f, height = 1_080f).first()

        assertEquals(
            ProjectionDust.offsetX(particle, phase = 0f),
            ProjectionDust.offsetX(particle, phase = 1f),
            .0001f,
        )
        assertEquals(
            ProjectionDust.offsetY(particle, phase = 0f),
            ProjectionDust.offsetY(particle, phase = 1f),
            .0001f,
        )
    }

    @Test
    fun indices_shouldKeepPreviousCurrentAndThreeFollowingSlides() {
        assertEquals(listOf(3, 4, 5, 6, 7), PreloadWindow.indices(currentIndex = 4, slideCount = 10))
        assertEquals(listOf(0, 1, 2), PreloadWindow.indices(currentIndex = 0, slideCount = 3))
        assertEquals(listOf(0, 1, 2, 3), PreloadWindow.initialIndices(slideCount = 8))
    }

    @Test
    fun initialPreload_shouldAllowProjection_whenFirstSlideSucceedsAndFutureSlideFails() {
        // GIVEN
        val requestedIndices = listOf(0, 1, 2, 3)

        // WHEN
        val result = InitialPreloadPolicy.evaluate(
            requestedIndices = requestedIndices,
            successfulIndices = setOf(0, 1, 3),
        )

        // THEN
        assertEquals(true, result.firstSlideReady)
        assertEquals(2, result.followingSlidesReady)
    }

    @Test
    fun initialPreload_shouldRejectProjection_whenFirstSlideFails() {
        val result = InitialPreloadPolicy.evaluate(
            requestedIndices = listOf(0, 1, 2, 3),
            successfulIndices = setOf(1, 2, 3),
        )

        assertEquals(false, result.firstSlideReady)
    }
}
