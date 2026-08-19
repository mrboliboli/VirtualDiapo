package fr.virtualdiapo.player.projection

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectionTransitionTest {
    @Test
    fun outgoing_shouldUsePrescribedFinalTransform_whenMovingForward() {
        // WHEN
        val transform = ProjectionTransition.outgoing(140L, direction = 1, fadeEnabled = true)

        // THEN
        assertEquals(-12f, transform.translationXFactor, .02f)
        assertEquals(8f, transform.translationYFactor, .02f)
        assertEquals(-.16f, transform.rotationZ, .002f)
        assertEquals(.992f, transform.scale, .0002f)
        assertEquals(0f, transform.alpha, 0f)
    }

    @Test
    fun incoming_shouldReverseDirection_whenMovingBackward() {
        // WHEN
        val transform = ProjectionTransition.incoming(ProjectionTransition.ENTRY_START_MS, -1, true)

        // THEN
        assertEquals(-12f, transform.translationXFactor, .02f)
        assertEquals(-8f, transform.translationYFactor, .02f)
        assertEquals(-.16f, transform.rotationZ, .002f)
        assertEquals(0f, transform.alpha, 0f)
    }

    @Test
    fun transforms_shouldRemainOpaque_whenFadeIsDisabled() {
        assertEquals(1f, ProjectionTransition.outgoing(130L, 1, false).alpha, 0f)
        assertEquals(1f, ProjectionTransition.incoming(1_650L, 1, false).alpha, 0f)
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
