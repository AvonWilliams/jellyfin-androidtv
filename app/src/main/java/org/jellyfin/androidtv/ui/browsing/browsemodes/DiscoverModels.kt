package org.jellyfin.androidtv.ui.browsing.browsemodes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * The ranked discover response returned by the Trending / Top Rated endpoints.
 *
 * The plugin narrows a ranked source list to the titles the library actually owns, writing each
 * item's 1-based source position into `IndexNumber`, and additionally returns external "missing"
 * titles not in the library. These stubs are never library items and never navigate to detail or
 * play; the client renders them as dimmed "coming soon" tiles.
 */
@Serializable
data class DiscoverRankedResult(
	@SerialName("Source") val source: String = "",
	@SerialName("Items") val items: List<BaseItemDto> = emptyList(),
	@SerialName("Missing") val missing: List<MissingTitleDto> = emptyList(),
)

/**
 * An external title surfaced inside a ranked discover response that is not in the library.
 */
@Serializable
data class MissingTitleDto(
	@SerialName("Source") val source: String = "",
	@SerialName("Rank") val rank: Int = 0,
	@SerialName("Title") val title: String = "",
	@SerialName("Year") val year: Int? = null,
	@SerialName("ProviderIds") val providerIds: Map<String, String> = emptyMap(),
	@SerialName("PosterUrl") val posterUrl: String = "",
)
