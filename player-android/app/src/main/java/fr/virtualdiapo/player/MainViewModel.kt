package fr.virtualdiapo.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.virtualdiapo.player.model.SlideCollection
import fr.virtualdiapo.player.model.CollectionSummary
import fr.virtualdiapo.player.network.VirtualDiapoApiClient
import fr.virtualdiapo.player.network.connectionErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Setup : PlayerUiState
    data object Loading : PlayerUiState
    data class CollectionSelection(val address: String, val collections: List<CollectionSummary>) : PlayerUiState
    data class Ready(val collection: SlideCollection) : PlayerUiState
    data class Failure(val message: String, val address: String) : PlayerUiState
}

class MainViewModel(
    private val apiClient: VirtualDiapoApiClient = VirtualDiapoApiClient(),
) : ViewModel() {
    private var lastSelection: PlayerUiState.CollectionSelection? = null
    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Setup)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun connect(address: String) {
        _state.value = PlayerUiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { apiClient.loadCollections(address) }
                .fold(
                    onSuccess = { collections ->
                        if (collections.isEmpty()) PlayerUiState.Failure("Le serveur ne contient aucune collection", address)
                        else PlayerUiState.CollectionSelection(address, collections).also { lastSelection = it }
                    },
                    onFailure = { PlayerUiState.Failure(connectionErrorMessage(it), address) },
                )
        }
    }

    fun selectCollection(address: String, collectionId: String) {
        _state.value = PlayerUiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { apiClient.loadCollection(address, collectionId) }
                .fold(
                    onSuccess = PlayerUiState::Ready,
                    onFailure = { PlayerUiState.Failure(connectionErrorMessage(it), address) },
                )
        }
    }

    fun returnToSetup() {
        _state.value = PlayerUiState.Setup
    }

    fun returnToCollections() {
        _state.value = lastSelection ?: PlayerUiState.Setup
    }
}
