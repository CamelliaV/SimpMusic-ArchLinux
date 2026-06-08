package com.maxrave.simpmusic.ui.navigation.destination.list

import kotlinx.serialization.Serializable

@Serializable
data class ArtistVideosDestination(
    val browseId: String,
    val params: String? = null,
)
