package fr.virtualdiapo.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException

class MainViewModelTest {
    @Test
    fun `projection failure exposes a recoverable connection error`() {
        val viewModel = MainViewModel()

        viewModel.reportProjectionFailure("192.168.1.42:8080", ConnectException())

        val failure = viewModel.state.value
        assertTrue(failure is PlayerUiState.Failure)
        failure as PlayerUiState.Failure
        assertEquals("192.168.1.42:8080", failure.address)
        assertTrue(failure.message.contains("Connexion perdue"))
        assertTrue(failure.message.contains("VirtualDiapo"))
    }
}
