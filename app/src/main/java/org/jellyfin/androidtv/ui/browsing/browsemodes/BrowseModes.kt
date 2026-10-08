package org.jellyfin.androidtv.ui.browsing.browsemodes

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.SortOrder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
	TOP_RATED("toprated"),
	TRENDING("trending"),
	HIDDEN_GEMS("bestunseen"),
	CRITICS_PICKS("criticspicks"),
	WATCH_AGAIN("recentlyplayed"),
	MOOD("mood"),
	STORY_THEMES("storythemes"),
	PLOT_ELEMENTS("plotelements"),
	WORLDS("worlds"),
	STYLES("styles"),
	DECADES("decades"),
	YEAR("year"),
	AGE_RATING("agerating"),
	STORY("story"),
	WORLD_STYLE("worldstyle"),
	QUALITY("quality"),
	TIME("time"),
	PEOPLE("people"),
	ACTORS("actors"),
	DIRECTORS("directors"),
	WRITERS("writers"),
}

/** Which section of the browse-modes home a tile belongs to. */
enum class BrowseTier {
	PRIMARY,
	META,
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
	/** ISO 8601 date string for the earliest DateLastSaved value to include. */
	val minDateLastSaved: String? = null,
	/** ISO 8601 date string for the earliest PremiereDate value to include. */
	val minPremiereDate: String? = null,
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
	/** Home section placement: primary actions vs the "Browse by…" meta section. */
	val tier: BrowseTier = BrowseTier.PRIMARY,
	/** For a [BrowseModeDestination.META_PICKER] tile: the choices, each an existing mode. */
	val children: List<BrowseModeDefinition>? = null,
	/** Renders its children inline on the home grid instead of opening a secondary menu. */
	val inline: Boolean = false,
	/** For a [BrowseModeDestination.PERSON_LIST] tile: the person kind to list. */
	val personType: String? = null,
)

/** Screens reachable from a tile that are not the item grid. */
enum class BrowseModeDestination {
	GENRES,
	STUDIOS,

	/** A server-ranked list fetched from the custom Discover endpoints. */
	DISCOVER,

	/** A grid of curated TMDb keyword tags to pick from. */
	TAG_PICKER,

	/** A grid of decades to filter by. */
	DECADES_PICKER,

	/** A grid of individual years to filter by. */
	YEARS_PICKER,

	/** A grid of age ratings to filter by. */
	AGE_RATING_PICKER,

	/** A secondary grid of choices, held in the tile's [BrowseModeDefinition.children]. */
	META_PICKER,

	/** A list of people of one kind (actor/director/writer). */
	PERSON_LIST,
}

/**
 * When `true`, tag-based modes (Mood, Story Themes, Plot Elements, Worlds, Styles)
 * open as stacked horizontal poster shelves instead of the flat tag-picker grid.
 *
 * Set to `false` to revert to the grid picker. The grid picker is simpler and has
 * been tested more thoroughly; ribbon shelves are the recommended experience on the
 * web but need on-TV verification before making them the default here.
 *
 * @see [TagBrowseRowsFragment]
 */
/** ISO 8601 date [n] months in the past, for seeding date-cutoff presets. */
private fun monthsAgo(n: Int): String {
	val cal = Calendar.getInstance()
	cal.add(Calendar.MONTH, -n)
	val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
	fmt.timeZone = TimeZone.getTimeZone("UTC")
	return fmt.format(cal.time)
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
	tier = BrowseTier.META,
)

private val studiosMode = BrowseModeDefinition(
	mode = BrowseMode.STUDIOS,
	label = R.string.lbl_studios,
	icon = R.drawable.ic_clapperboard,
	iconTint = R.color.browse_mode_studios,
	destination = BrowseModeDestination.STUDIOS,
	tier = BrowseTier.META,
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
	preset = BrowsePreset(ItemSortBy.DATE_CREATED, SortOrder.DESCENDING, minDateLastSaved = monthsAgo(9)),
)

private val newReleasesMode = BrowseModeDefinition(
	mode = BrowseMode.NEW_RELEASES,
	label = R.string.lbl_browse_mode_new_releases,
	icon = R.drawable.ic_new_releases,
	iconTint = R.color.browse_mode_new_releases,
	preset = BrowsePreset(ItemSortBy.PREMIERE_DATE, SortOrder.DESCENDING, minPremiereDate = monthsAgo(9)),
)

private val randomMode = BrowseModeDefinition(
	mode = BrowseMode.RANDOM,
	label = R.string.random,
	icon = R.drawable.ic_shuffle,
	iconTint = R.color.browse_mode_random,
	preset = BrowsePreset(ItemSortBy.RANDOM, SortOrder.ASCENDING),
)

// The best thing you own but have not got to yet.
private val hiddenGemsMode = BrowseModeDefinition(
	mode = BrowseMode.HIDDEN_GEMS,
	label = R.string.lbl_browse_mode_hidden_gems,
	icon = R.drawable.ic_lightbulb,
	iconTint = R.color.browse_mode_hidden_gems,
	preset = BrowsePreset(ItemSortBy.COMMUNITY_RATING, SortOrder.DESCENDING, unwatchedOnly = true),
)

private val criticsPicksMode = BrowseModeDefinition(
	mode = BrowseMode.CRITICS_PICKS,
	label = R.string.lbl_browse_mode_critics_picks,
	icon = R.drawable.ic_rt_fresh,
	preset = BrowsePreset(ItemSortBy.CRITIC_RATING, SortOrder.DESCENDING),
)

private val moodToneMode = BrowseModeDefinition(
	mode = BrowseMode.MOOD,
	label = R.string.lbl_browse_mode_mood_tone,
	icon = R.drawable.ic_mood,
	iconTint = R.color.browse_mode_mood,
	destination = BrowseModeDestination.TAG_PICKER,
	tier = BrowseTier.META,
)

private val storyThemesMode = BrowseModeDefinition(
	mode = BrowseMode.STORY_THEMES,
	label = R.string.lbl_browse_mode_story_themes,
	icon = R.drawable.ic_book,
	iconTint = R.color.browse_mode_story_themes,
	destination = BrowseModeDestination.TAG_PICKER,
)

private val plotElementsMode = BrowseModeDefinition(
	mode = BrowseMode.PLOT_ELEMENTS,
	label = R.string.lbl_browse_mode_plot_elements,
	icon = R.drawable.ic_timeline,
	iconTint = R.color.browse_mode_plot_elements,
	destination = BrowseModeDestination.TAG_PICKER,
)

private val worldsMode = BrowseModeDefinition(
	mode = BrowseMode.WORLDS,
	label = R.string.lbl_browse_mode_worlds,
	icon = R.drawable.ic_world,
	iconTint = R.color.browse_mode_worlds,
	destination = BrowseModeDestination.TAG_PICKER,
)

private val stylesMode = BrowseModeDefinition(
	mode = BrowseMode.STYLES,
	label = R.string.lbl_browse_mode_styles,
	icon = R.drawable.ic_palette,
	iconTint = R.color.browse_mode_styles,
	destination = BrowseModeDestination.TAG_PICKER,
)

private val ageRatingMode = BrowseModeDefinition(
	mode = BrowseMode.AGE_RATING,
	label = R.string.lbl_browse_mode_age_rating,
	icon = R.drawable.ic_flask,
	iconTint = R.color.browse_mode_age_rating,
	destination = BrowseModeDestination.AGE_RATING_PICKER,
)

// Series record their last play against the series rather than the episode, matching the sort
// the item grid itself offers for each collection type.
private val watchAgainMovieMode = BrowseModeDefinition(
	mode = BrowseMode.WATCH_AGAIN,
	label = R.string.lbl_browse_mode_watch_again,
	icon = R.drawable.ic_resume,
	iconTint = R.color.browse_mode_watch_again,
	preset = BrowsePreset(ItemSortBy.DATE_PLAYED, SortOrder.DESCENDING),
)

private val watchAgainSeriesMode = watchAgainMovieMode.copy(
	preset = BrowsePreset(ItemSortBy.SERIES_DATE_PLAYED, SortOrder.DESCENDING),
)

// The "Browse by…" meta tiles. Each opens an existing flow: either a single picker/shelf directly,
// or a secondary grid of the modes listed in [children].
private val storyMode = BrowseModeDefinition(
	mode = BrowseMode.STORY,
	label = R.string.lbl_browse_mode_story,
	icon = R.drawable.ic_book,
	iconTint = R.color.browse_mode_story_themes,
	destination = BrowseModeDestination.META_PICKER,
	tier = BrowseTier.META,
	children = listOf(storyThemesMode, plotElementsMode),
)

private val worldStyleMode = BrowseModeDefinition(
	mode = BrowseMode.WORLD_STYLE,
	label = R.string.lbl_browse_mode_world_style,
	icon = R.drawable.ic_world,
	iconTint = R.color.browse_mode_worlds,
	destination = BrowseModeDestination.META_PICKER,
	tier = BrowseTier.META,
	children = listOf(worldsMode, stylesMode),
)

// The leaves of the inline Time meta tile: a decades picker and a single-year picker.
private val decadesMode = BrowseModeDefinition(
	mode = BrowseMode.DECADES,
	label = R.string.lbl_browse_mode_decades,
	icon = R.drawable.ic_calendar,
	iconTint = R.color.browse_mode_decades,
	destination = BrowseModeDestination.DECADES_PICKER,
)

private val yearMode = BrowseModeDefinition(
	mode = BrowseMode.YEAR,
	label = R.string.lbl_browse_mode_year,
	icon = R.drawable.ic_calendar,
	iconTint = R.color.browse_mode_decades,
	destination = BrowseModeDestination.YEARS_PICKER,
)

private val timeMode = BrowseModeDefinition(
	mode = BrowseMode.TIME,
	label = R.string.lbl_browse_mode_time,
	icon = R.drawable.ic_calendar,
	iconTint = R.color.browse_mode_decades,
	tier = BrowseTier.META,
	inline = true,
	children = listOf(decadesMode, yearMode),
)

// The leaves of the inline People meta tile, each listing the library's persons of one kind.
private val actorsMode = BrowseModeDefinition(
	mode = BrowseMode.ACTORS,
	label = R.string.lbl_browse_mode_actors,
	icon = R.drawable.ic_user,
	iconTint = R.color.browse_mode_people,
	destination = BrowseModeDestination.PERSON_LIST,
	personType = PersonKind.ACTOR.serialName,
)

private val directorsMode = BrowseModeDefinition(
	mode = BrowseMode.DIRECTORS,
	label = R.string.lbl_browse_mode_directors,
	icon = R.drawable.ic_movie,
	iconTint = R.color.browse_mode_directors,
	destination = BrowseModeDestination.PERSON_LIST,
	personType = PersonKind.DIRECTOR.serialName,
)

private val writersMode = BrowseModeDefinition(
	mode = BrowseMode.WRITERS,
	label = R.string.lbl_browse_mode_writers,
	icon = R.drawable.ic_book,
	iconTint = R.color.browse_mode_writers,
	destination = BrowseModeDestination.PERSON_LIST,
	personType = PersonKind.WRITER.serialName,
)

private val peopleMode = BrowseModeDefinition(
	mode = BrowseMode.PEOPLE,
	label = R.string.lbl_browse_mode_people,
	icon = R.drawable.ic_users,
	iconTint = R.color.browse_mode_people,
	tier = BrowseTier.META,
	inline = true,
	children = listOf(actorsMode, directorsMode, writersMode),
)

// Children differ per collection type (critics' picks and the watch-again preset), so they are
// attached when the per-type list is built.
private val qualityMode = BrowseModeDefinition(
	mode = BrowseMode.QUALITY,
	label = R.string.lbl_browse_mode_quality,
	icon = R.drawable.ic_star,
	iconTint = R.color.browse_mode_hidden_gems,
	tier = BrowseTier.META,
	inline = true,
)

private val movieBrowseModes = listOf(
	allMode,
	trendingMode,
	topRatedMode,
	newReleasesMode,
	justAddedMode,
	randomMode,
	genresMode,
	moodToneMode,
	storyMode,
	worldStyleMode,
	peopleMode,
	timeMode,
	qualityMode.copy(children = listOf(criticsPicksMode, hiddenGemsMode, ageRatingMode, watchAgainMovieMode)),
	studiosMode,
)

// Critics' picks is left out because almost no series carry a critic rating.
private val seriesBrowseModes = listOf(
	allMode,
	trendingMode,
	topRatedMode,
	newReleasesMode,
	justAddedMode,
	randomMode,
	genresMode,
	moodToneMode,
	storyMode,
	worldStyleMode,
	peopleMode,
	timeMode,
	qualityMode.copy(children = listOf(hiddenGemsMode, ageRatingMode, watchAgainSeriesMode)),
	networksMode,
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

/**
 * Resolves a mode by key anywhere on the home grid, including the children of meta tiles, so
 * screens reached through a meta tile (e.g. Story Themes) can still look up their label.
 */
fun getBrowseModeDefinition(collectionType: CollectionType?, mode: BrowseMode): BrowseModeDefinition? =
	getBrowseModes(collectionType)
		?.flatMap { listOf(it) + it.children.orEmpty() }
		?.firstOrNull { it.mode == mode }
