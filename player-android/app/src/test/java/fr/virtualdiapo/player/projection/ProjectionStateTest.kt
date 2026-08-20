package fr.virtualdiapo.player.projection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectionStateTest {
    @Test
    fun `initial load stays black until first slide is revealed`() {
        val initial = ProjectionState.initial(3)
        val preparing = initial.beginInitialLoad()!!
        val loading = preparing.beginMechanicalTransition()!!

        assertEquals(ProjectionState.Preparing(3, null, ProjectionState.Destination.Slide(0)), preparing)
        assertEquals(ProjectionState.Transition(3, null, ProjectionState.Destination.Slide(0), 1), loading)
        assertEquals(ProjectionState.Slide(3, 0), loading.reveal())
    }

    @Test
    fun `last slide advances to virtual black end`() {
        val preparing = ProjectionState.Slide(3, 2).beginMove(1)!!
        val transition = preparing.beginMechanicalTransition()!!

        assertEquals(ProjectionState.Slide(3, 2), preparing.cancelPreparation())
        assertEquals(
            ProjectionState.Transition(3, ProjectionState.Destination.Slide(2), ProjectionState.Destination.End, 1),
            transition,
        )
        assertEquals(ProjectionState.EndOfCarousel(3), transition.reveal())
    }

    @Test
    fun `penultimate slide advances to last real slide`() {
        val transition = ProjectionState.Slide(3, 1).beginMove(1)!!.beginMechanicalTransition()!!

        assertEquals(ProjectionState.Slide(3, 2), transition.reveal())
    }

    @Test
    fun preloadWindowIndex_shouldBecomeAvailableOnlyAfterTargetSlideIsRevealed() {
        // GIVEN
        val transition = ProjectionState.Slide(4, 1)
            .beginMove(1)!!
            .beginMechanicalTransition()!!

        // WHEN
        val revealed = transition.reveal()

        // THEN
        assertNull(transition.preloadWindowIndex())
        assertEquals(2, revealed.preloadWindowIndex())
    }

    @Test
    fun `previous from black end returns to last real slide`() {
        val transition = ProjectionState.EndOfCarousel(3).beginMove(-1)!!.beginMechanicalTransition()!!

        assertEquals(ProjectionState.Slide(3, 2), transition.reveal())
        assertEquals(-1, (transition as ProjectionState.Transition).direction)
    }

    @Test
    fun `next from black end and repeated input during transition are ignored`() {
        assertNull(ProjectionState.EndOfCarousel(3).beginMove(1))
        val preparing = ProjectionState.Slide(3, 1).beginMove(1)!!
        assertNull(preparing.beginMove(1))
        assertNull(preparing.beginMove(-1))
    }

    @Test
    fun `restoration returns a settled slide without initial loading`() {
        val restored = ProjectionState.restore(3, ProjectionState.Slide(3, 1).settledPosition())

        assertEquals(ProjectionState.Slide(3, 1), restored)
        assertNull(restored.beginInitialLoad())
    }

    @Test
    fun autoAdvance_shouldOnlyBeEligibleOnSettledSlideIncludingLastSlideBeforeEnd() {
        assertEquals(1, AutoAdvancePolicy.eligibleSlideIndex(ProjectionState.Slide(3, 1), enabled = true))
        assertEquals(2, AutoAdvancePolicy.eligibleSlideIndex(ProjectionState.Slide(3, 2), enabled = true))
        assertNull(AutoAdvancePolicy.eligibleSlideIndex(ProjectionState.EndOfCarousel(3), enabled = true))
        assertNull(AutoAdvancePolicy.eligibleSlideIndex(ProjectionState.Slide(3, 1), enabled = false))
        val transition = ProjectionState.Slide(3, 0).beginMove(1)!!.beginMechanicalTransition()!!
        assertNull(AutoAdvancePolicy.eligibleSlideIndex(transition, enabled = true))
    }

    @Test
    fun autoAdvanceDelay_shouldUseBackwardCompatibleDefaultAndClampRange() {
        assertEquals(10, ProjectionOptions().autoAdvanceDelaySeconds)
        assertEquals(false, ProjectionOptions().autoAdvanceEnabled)
        assertEquals(3, AutoAdvancePolicy.normalizeDelay(2))
        assertEquals(60, AutoAdvancePolicy.normalizeDelay(61))
        assertEquals(3, AutoAdvancePolicy.adjustDelay(3, -1))
        assertEquals(11, AutoAdvancePolicy.adjustDelay(10, 1))
        assertEquals(60, AutoAdvancePolicy.adjustDelay(60, 1))
    }
}
