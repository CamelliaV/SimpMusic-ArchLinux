package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.browse.artist.ArtistBrowseEndpoint
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.error

class ArtistVideosViewModel(
    private val artistRepository: ArtistRepository,
) : BaseViewModel() {
    private val _uiState = MutableStateFlow<ArtistVideosUIState>(ArtistVideosUIState.Loading)
    val uiState: StateFlow<ArtistVideosUIState> = _uiState

    fun getArtistVideos(
        browseId: String,
        params: String?,
    ) {
        viewModelScope.launch {
            _uiState.value = ArtistVideosUIState.Loading
            artistRepository
                .getArtistVideos(
                    ArtistBrowseEndpoint(
                        browseId = browseId,
                        params = params,
                    ),
                ).collect { result ->
                    val data = result.data
                    when (result) {
                        is Resource.Success if data != null && data.second.isNotEmpty() ->
                            _uiState.value =
                                ArtistVideosUIState.Success(
                                    title = data.first,
                                    tracks = data.second,
                                )

                        else ->
                            _uiState.value =
                                ArtistVideosUIState.Error(
                                    message = result.message ?: getString(Res.string.error),
                                )
                    }
                }
        }
    }
}

sealed class ArtistVideosUIState {
    data class Success(
        val title: String,
        val tracks: List<Track>,
    ) : ArtistVideosUIState()

    data class Error(
        val message: String,
    ) : ArtistVideosUIState()

    data object Loading : ArtistVideosUIState()
}
