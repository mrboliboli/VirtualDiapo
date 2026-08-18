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
    data object Connecting : PlayerUiState
    data class LoadingCarousel(val title: String) : PlayerUiState
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
        if (_state.value == PlayerUiState.Connecting) return
        _state.value = PlayerUiState.Connecting
        viewModelScope.launch {
            runCatching { apiClient.loadCollections(address) }
                .onFailure { _state.value = PlayerUiState.Failure(connectionErrorMessage(it), address) }
                .onSuccess { collections ->
                    if (collections.isEmpty()) {
                        _state.value = PlayerUiState.Failure("Le serveur ne contient aucun carrousel", address)
                        return@onSuccess
                    }
                    updateSelection(PlayerUiState.CollectionSelection(address, collections))
                    collections.forEach { summary ->
                        val cover = runCatching {
                            apiClient.loadCollection(address, summary.id).slides.firstOrNull()?.imageUrl
                        }.getOrNull() ?: return@forEach
                        val current = _state.value as? PlayerUiState.CollectionSelection ?: return@forEach
                        updateSelection(current.copy(collections = current.collections.map {
                            if (it.id == summary.id) it.copy(coverImageUrl = cover) else it
                        }))
                    }
                }
        }
    }

    fun selectCollection(address: String, collection: CollectionSummary) {
        _state.value = PlayerUiState.LoadingCarousel(collection.title)
        viewModelScope.launch {
            _state.value = runCatching { apiClient.loadCollection(address, collection.id) }
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

    private fun updateSelection(selection: PlayerUiState.CollectionSelection) {
        lastSelection = selection
        _state.value = selection
    }
}
