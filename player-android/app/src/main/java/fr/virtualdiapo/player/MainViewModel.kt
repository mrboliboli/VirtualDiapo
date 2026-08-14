package fr.virtualdiapo.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.virtualdiapo.player.model.SlideCollection
import fr.virtualdiapo.player.network.VirtualDiapoApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Setup : PlayerUiState
    data object Loading : PlayerUiState
    data class Ready(val collection: SlideCollection) : PlayerUiState
    data class Failure(val message: String) : PlayerUiState
}

class MainViewModel(
    private val apiClient: VirtualDiapoApiClient = VirtualDiapoApiClient(),
) : ViewModel() {
    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Setup)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun connect(address: String) {
        _state.value = PlayerUiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { apiClient.loadFirstCollection(address) }
                .fold(
                    onSuccess = PlayerUiState::Ready,
                    onFailure = { PlayerUiState.Failure(it.message ?: "Connexion impossible") },
                )
        }
    }

    fun returnToSetup() {
        _state.value = PlayerUiState.Setup
    }
}

