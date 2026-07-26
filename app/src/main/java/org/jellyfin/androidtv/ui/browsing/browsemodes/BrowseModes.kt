package org.jellyfin.androidtv.ui.browsing.browsemodes

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

/**
 * The ways a library can be browsed, offered as a grid of tiles when the library is opened.
 *
 * The key is used to scope the display preferences of each mode, so it must stay stable once
 * shipped or users lose the sorting they picked inside a mode.
 */
enum class BrowseMode(val key: String) {
	ALL("all"),
	GENRES("genres"),
	STUDIOS("studios"),
	JUST_ADDED("justadded"),
	NEW_RELEASES("newreleases"),
	RANDOM("random"),
	HIGHEST_RATED("highestrated"),
	TOP_RATED("toprated"),
	TRENDING("trending"),
	BEST_UNSEEN("bestunseen"),
	CRITICS_PICKS("criticspicks"),
	UNWATCHED("unwatched"),
	FAVORITES("favorites"),
	RECENTLY_PLAYED("recentlyplayed"),
	LONGEST("longest"),
}

/**
 * The sort and filters a mode starts out with. Seeded once into the mode's own preference store,
 * so a sort the user picks inside a mode afterwards is kept.
 */
data class BrowsePreset(
	val sortBy: ItemSortBy? = null,
	val sortOrder: SortOrder? = null,
	val unwatchedOnly: Boolean = false,
	val favoritesOnly: Boolean = false,
)

data class BrowseModeDefinition(
	val mode: BrowseMode,
	@field:StringRes val label: Int,
	@field:DrawableRes val icon: Int,
	/** Applied to the icon at draw time. Null keeps whatever colours the drawable carries. */
	@field:ColorRes val iconTint: Int? = null,
	/** Seeded over the library's defaults. Absent means the library's plain item grid. */
	val preset: BrowsePreset? = null,
	/** Opens an existing screen rather than the item grid. */
	val destination: BrowseModeDestination? = null,
)

/** Screens reachable from a tile that are not the item grid. */
enum class BrowseModeDestination {
	GENRES,
	STUDIOS,

	/** A server-ranked list fetched from the custom Discover endpoints. */
	DISCOVER,
}

private val allMode = BrowseModeDefinition(
	mode = BrowseMode.ALL,
	label = R.string.lbl_all_items,
	icon = R.drawable.ic_grid,
	iconTint = R.color.browse_mode_all,
)

private val genresMode = BrowseModeDefinition(
	mode = BrowseMode.GENRES,
	label = R.string.lbl_genres,
	icon = R.drawable.ic_masks,
	iconTint = R.color.browse_mode_genres,
	destination = BrowseModeDestination.GENRES,
)

private val studiosMode = BrowseModeDefinition(
	mode = BrowseMode.STUDIOS,
	label = R.string.lbl_studios,
	icon = R.drawable.ic_clapperboard,
	iconTint = R.color.browse_mode_studios,
	destination = BrowseModeDestination.STUDIOS,
)

// Series libraries call the same thing networks.
private val networksMode = studiosMode.copy(
	label = R.string.lbl_networks,
	icon = R.drawable.ic_tv,
	iconTint = R.color.browse_mode_networks,
)

private val topRatedMode = BrowseModeDefinition(
	mode = BrowseMode.TOP_RATED,
	label = R.string.lbl_browse_mode_top_rated,
	icon = R.drawable.ic_medal,
	iconTint = R.color.browse_mode_top_rated,
	destination = BrowseModeDestination.DISCOVER,
)

private val trendingMode = BrowseModeDefinition(
	mode = BrowseMode.TRENDING,
	label = R.string.lbl_browse_mode_trending,
	icon = R.drawable.ic_trending_up,
	iconTint = R.color.browse_mode_trending,
	destination = BrowseModeDestination.DISCOVER,
)

private val justAddedMode = BrowseModeDefinition(
	mode = BrowseMode.JUST_ADDED,
	label = R.string.lbl_browse_mode_just_added,
	icon = R.drawable.ic_add,
	iconTint = R.color.browse_mode_just_added,
	preset = BrowsePreset(ItemSortBy.DATE_CREATED, SortOrder.DESCENDING),
)

private val newReleasesMode = BrowseModeDefinition(
	mode = BrowseMode.NEW_RELEASES,
	label = R.string.lbl_browse_mode_new_releases,
	icon = R.drawable.ic_new_releases,
	iconTint = R.color.browse_mode_new_releases,
	preset = BrowsePreset(ItemSortBy.PREMIERE_DATE, SortOrder.DESCENDING),
)

private val randomMode = BrowseModeDefinition(
	mode = BrowseMode.RANDOM,
	label = R.string.random,
	icon = R.drawable.ic_shuffle,
	iconTint = R.color.browse_mode_random,
	preset = BrowsePreset(ItemSortBy.RANDOM, SortOrder.ASCENDING),
)

private val highestRatedMode = BrowseModeDefinition(
	mode = BrowseMode.HIGHEST_RATED,
	label = R.string.lbl_browse_mode_highest_rated,
	icon = R.drawable.ic_star,
	iconTint = R.color.browse_mode_highest_rated,
	preset = BrowsePreset(ItemSortBy.COMMUNITY_RATING, SortOrder.DESCENDING),
)

// The best thing you own but have not got to yet.
private val bestUnseenMode = BrowseModeDefinition(
	mode = BrowseMode.BEST_UNSEEN,
	label = R.string.lbl_browse_mode_best_unseen,
	icon = R.drawable.ic_lightbulb,
	iconTint = R.color.browse_mode_best_unseen,
	preset = BrowsePreset(ItemSortBy.COMMUNITY_RATING, SortOrder.DESCENDING, unwatchedOnly = true),
)

private val criticsPicksMode = BrowseModeDefinition(
	mode = BrowseMode.CRITICS_PICKS,
	label = R.string.lbl_browse_mode_critics_picks,
	icon = R.drawable.ic_rt_fresh,
	preset = BrowsePreset(ItemSortBy.CRITIC_RATING, SortOrder.DESCENDING),
)

private val unwatchedMode = BrowseModeDefinition(
	mode = BrowseMode.UNWATCHED,
	label = R.string.lbl_unwatched,
	icon = R.drawable.ic_unwatch,
	iconTint = R.color.browse_mode_unwatched,
	preset = BrowsePreset(unwatchedOnly = true),
)

private val favoritesMode = BrowseModeDefinition(
	mode = BrowseMode.FAVORITES,
	label = R.string.lbl_favorites,
	icon = R.drawable.ic_heart,
	iconTint = R.color.browse_mode_favorites,
	preset = BrowsePreset(favoritesOnly = true),
)

private val longestMode = BrowseModeDefinition(
	mode = BrowseMode.LONGEST,
	label = R.string.lbl_browse_mode_longest,
	icon = R.drawable.ic_time,
	iconTint = R.color.browse_mode_longest,
	preset = BrowsePreset(ItemSortBy.RUNTIME, SortOrder.DESCENDING),
)

// Series record their last play against the series rather than the episode, matching the sort
// the item grid itself offers for each collection type.
private val movieRecentlyPlayedMode = BrowseModeDefinition(
	mode = BrowseMode.RECENTLY_PLAYED,
	label = R.string.lbl_browse_mode_recently_played,
	icon = R.drawable.ic_resume,
	iconTint = R.color.browse_mode_recently_played,
	preset = BrowsePreset(ItemSortBy.DATE_PLAYED, SortOrder.DESCENDING),
)

private val seriesRecentlyPlayedMode = movieRecentlyPlayedMode.copy(
	preset = BrowsePreset(ItemSortBy.SERIES_DATE_PLAYED, SortOrder.DESCENDING),
)

private val movieBrowseModes = listOf(
	allMode,
	unwatchedMode,
	justAddedMode,
	bestUnseenMode,
	randomMode,
	favoritesMode,
	genresMode,
	highestRatedMode,
	topRatedMode,
	trendingMode,
	newReleasesMode,
	studiosMode,
	movieRecentlyPlayedMode,
	criticsPicksMode,
	longestMode,
)

// Critics' picks is left out because almost no series carry a critic rating.
private val seriesBrowseModes = listOf(
	allMode,
	unwatchedMode,
	justAddedMode,
	bestUnseenMode,
	randomMode,
	favoritesMode,
	genresMode,
	highestRatedMode,
	topRatedMode,
	trendingMode,
	newReleasesMode,
	networksMode,
	seriesRecentlyPlayedMode,
	longestMode,
)

/**
 * The browse modes offered for a library. A collection type without modes keeps the stock
 * behaviour of opening straight into its own screen.
 */
fun getBrowseModes(collectionType: CollectionType?) = when (collectionType) {
	CollectionType.MOVIES -> movieBrowseModes
	CollectionType.TVSHOWS -> seriesBrowseModes
	else -> null
}
