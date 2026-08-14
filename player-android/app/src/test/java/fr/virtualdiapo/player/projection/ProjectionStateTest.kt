package fr.virtualdiapo.player.projection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionStateTest {
    @Test
    fun `a valid move immediately enters black state`() {
        val moving = ProjectionState(slideCount = 3).beginMove(1)!!

        assertEquals(1, moving.currentIndex)
        assertTrue(moving.black)
        assertTrue(moving.transitioning)
    }

    @Test
    fun `reveal ends the transition`() {
        val revealed = ProjectionState(3).beginMove(1)!!.reveal()

        assertFalse(revealed.black)
        assertFalse(revealed.transitioning)
    }

    @Test
    fun `boundaries and repeated input are ignored`() {
        assertNull(ProjectionState(3).beginMove(-1))
        assertNull(ProjectionState(3, currentIndex = 2).beginMove(1))
        assertNull(ProjectionState(3).beginMove(1)!!.beginMove(1))
    }
}
