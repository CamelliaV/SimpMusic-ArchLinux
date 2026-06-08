package com.maxrave.simpmusic.ui.screen.other

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.common.Config
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.home.Content
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.HomeItemVideo
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.ArtistVideosUIState
import com.maxrave.simpmusic.viewModel.ArtistVideosViewModel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import simpmusic.composeapp.generated.resources.videos

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun ArtistVideosScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    browseId: String,
    params: String?,
    viewModel: ArtistVideosViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()

    LaunchedEffect(browseId, params) {
        viewModel.getArtistVideos(
            browseId = browseId,
            params = params,
        )
    }

    Crossfade(targetState = uiState) { state ->
        when (state) {
            is ArtistVideosUIState.Success -> {
                val title = state.title.ifBlank { stringResource(Res.string.videos) }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(300.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .hazeSource(state = hazeState),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Spacer(
                            Modifier.size(
                                innerPadding.calculateTopPadding() + 64.dp,
                            ),
                        )
                    }
                    items(state.tracks) { track ->
                        HomeItemVideo(
                            onClick = {
                                viewModel.setQueueData(
                                    QueueData.Data(
                                        listTracks = state.tracks.toCollection(arrayListOf<Track>()),
                                        firstPlayedTrack = track,
                                        playlistId = "RDAMVM${track.videoId}",
                                        playlistName = title,
                                        playlistType = PlaylistType.RADIO,
                                        continuation = null,
                                    ),
                                )
                                viewModel.loadMediaItem(
                                    track,
                                    Config.VIDEO_CLICK,
                                    state.tracks.indexOf(track),
                                )
                            },
                            onLongClick = {},
                            data =
                                Content(
                                    album = null,
                                    artists = track.artists,
                                    description = null,
                                    isExplicit = track.isExplicit,
                                    playlistId = null,
                                    browseId = null,
                                    thumbnails = track.thumbnails ?: emptyList(),
                                    title = track.title,
                                    videoId = track.videoId,
                                    views = track.duration?.takeIf { it != "null" && it.isNotBlank() },
                                    durationSeconds = track.durationSeconds,
                                ),
                        )
                    }
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        EndOfPage()
                    }
                }
                TopAppBar(
                    modifier =
                        Modifier
                            .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                                blurEnabled = true
                            },
                    title = {
                        Text(
                            text = title,
                            style = typo().titleMedium,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(
                                        align = Alignment.CenterVertically,
                                    ).basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        animationMode = MarqueeAnimationMode.Immediately,
                                    ).focusable(),
                        )
                    },
                    navigationIcon = {
                        Box(Modifier.padding(horizontal = 5.dp)) {
                            RippleIconButton(
                                Res.drawable.baseline_arrow_back_ios_new_24,
                                Modifier.size(32.dp),
                                true,
                            ) {
                                navController.navigateUp()
                            }
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            Color.Transparent,
                            Color.Unspecified,
                            Color.Unspecified,
                            Color.Unspecified,
                            Color.Unspecified,
                        ),
                )
            }

            is ArtistVideosUIState.Error -> {
                viewModel.makeToast(state.message)
                CenterLoadingBox(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(15.dp),
                )
            }

            ArtistVideosUIState.Loading -> {
                CenterLoadingBox(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(15.dp),
                )
            }
        }
    }
}
