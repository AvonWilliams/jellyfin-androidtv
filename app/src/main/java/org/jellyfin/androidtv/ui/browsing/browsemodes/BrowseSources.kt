package org.jellyfin.androidtv.ui.browsing.browsemodes

import androidx.compose.ui.graphics.Color

/**
 * A ranked-list data source offered as a chip in the Trending / Top Rated source bar.
 *
 * The bar renders one chip per enabled source, so adding a source is a data change, not a UI
 * change: add it to the relevant per-view list below once the plugin exposes the matching
 * /Discover endpoint (which passes the `source` query parameter straight through).
 */
data class BrowseSource(
    /** Identifier sent as the /Discover `source` query parameter. */
    val id: String,
    /** Chip label. Proper nouns, so not localised. */
    val label: String,
    /** Accent colour used for the chip's active state. */
    val color: Color,
)

/**
 * The lists offered on the Trending view, in display order. Netflix is trending-only; TMDb and
 * IMDb serve both.
 */
val TRENDING_SOURCES: List<BrowseSource> = listOf(
    BrowseSource(id = "tmdb", label = "TMDb Trending", color = Color(0xFF01B4E4)),
    BrowseSource(id = "imdb", label = "IMDb Most Popular", color = Color(0xFFF5C518)),
    BrowseSource(id = "netflix", label = "Netflix Global", color = Color(0xFFE50914)),
    BrowseSource(id = "netflix-au", label = "Netflix Australia", color = Color(0xFFE50914)),
    BrowseSource(id = "netflix-ph", label = "Netflix Philippines", color = Color(0xFFE50914)),
)

/**
 * The lists offered on the Top Rated view, in display order. Letterboxd and Rotten Tomatoes are
 * top-rated-only; TMDb and IMDb serve both.
 */
val TOPRATED_SOURCES: List<BrowseSource> = listOf(
    BrowseSource(id = "tmdb", label = "TMDb Top Rated", color = Color(0xFF01B4E4)),
    BrowseSource(id = "imdb", label = "IMDb Top 250", color = Color(0xFFF5C518)),
    BrowseSource(id = "letterboxd", label = "Letterboxd Top 250", color = Color(0xFF00E054)),
    BrowseSource(id = "rottentomatoes", label = "Rotten Tomatoes Top Movies", color = Color(0xFFFA320A)),
)

/**
 * The lists offered on a Shows library. Only TMDb and Netflix carry series data; IMDb, Letterboxd
 * and Rotten Tomatoes are movie-only, so they are hidden on shows.
 */
val TRENDING_SOURCES_SHOWS: List<BrowseSource> =
    TRENDING_SOURCES.filter { it.id == "tmdb" || it.id.startsWith("netflix") }

/** No snapshot source carries a top-rated series list yet, so shows are TMDb-only. */
val TOPRATED_SOURCES_SHOWS: List<BrowseSource> =
    TOPRATED_SOURCES.filter { it.id == "tmdb" }

/** Fallback source when no preference has been saved. */
const val DEFAULT_BROWSE_SOURCE = "tmdb"

/** The sources rendered as chips for a given ranked view and library kind. */
fun getEnabledSources(viewType: BrowseMode, isShows: Boolean = false): List<BrowseSource> {
    if (viewType == BrowseMode.TRENDING) {
        return if (isShows) TRENDING_SOURCES_SHOWS else TRENDING_SOURCES
    }

    return if (isShows) TOPRATED_SOURCES_SHOWS else TOPRATED_SOURCES
}
