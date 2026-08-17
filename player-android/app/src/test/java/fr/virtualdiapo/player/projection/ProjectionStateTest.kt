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
        assertEquals(ProjectionState.Transition(3, ProjectionState.Destination.Slide(0)), loading)
        assertEquals(ProjectionState.Slide(3, 0), loading.reveal())
    }

    @Test
    fun `last slide advances to virtual black end`() {
        val preparing = ProjectionState.Slide(3, 2).beginMove(1)!!
        val transition = preparing.beginMechanicalTransition()!!

        assertEquals(ProjectionState.Slide(3, 2), preparing.cancelPreparation())
        assertEquals(ProjectionState.Transition(3, ProjectionState.Destination.End), transition)
        assertEquals(ProjectionState.EndOfCarousel(3), transition.reveal())
    }

    @Test
    fun `penultimate slide advances to last real slide`() {
        val transition = ProjectionState.Slide(3, 1).beginMove(1)!!.beginMechanicalTransition()!!

        assertEquals(ProjectionState.Slide(3, 2), transition.reveal())
    }

    @Test
    fun `previous from black end returns to last real slide`() {
        val transition = ProjectionState.EndOfCarousel(3).beginMove(-1)!!.beginMechanicalTransition()!!

        assertEquals(ProjectionState.Slide(3, 2), transition.reveal())
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
}
