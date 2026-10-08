package org.jellyfin.androidtv.ui.browsing.browsemodes

import androidx.compose.ui.graphics.Color

/**
 * A ranked-list data source offered as a chip in the Trending / Top Rated source bar.
 *
 * The bar renders one chip per enabled source, so adding a source is a data change, not a UI
 * change: add it to [BROWSE_SOURCES] for its chip presentation and to [ENABLED_SOURCE_IDS] once
 * the plugin exposes the matching /Discover endpoint (which passes the `source` query parameter
 * straight through).
 */
data class BrowseSource(
    /** Identifier sent as the /Discover `source` query parameter. */
    val id: String,
    /** Chip label. Proper nouns, so not localised. */
    val label: String,
    /** Accent colour used for the chip's active state. */
    val color: Color,
)

/** Known ranked sources and their chip presentation. */
val BROWSE_SOURCES: List<BrowseSource> = listOf(
    BrowseSource(id = "tmdb", label = "TMDb", color = Color(0xFF01B4E4)),
    BrowseSource(id = "imdb", label = "IMDb", color = Color(0xFFF5C518)),
    BrowseSource(id = "rotten-tomatoes", label = "Rotten Tomatoes", color = Color(0xFFFA320A)),
    BrowseSource(id = "netflix", label = "Netflix", color = Color(0xFFE50914)),
)

/**
 * The sources a user can currently pick from, in chip order. Mirrors the plugin's enabled-source
 * configuration; only TMDb is implemented in this phase, so it is the sole enabled source.
 */
val ENABLED_SOURCE_IDS: List<String> = listOf("tmdb")

/** The sources rendered as chips in the source bar. */
val ENABLED_BROWSE_SOURCES: List<BrowseSource> =
    BROWSE_SOURCES.filter { it.id in ENABLED_SOURCE_IDS }

/** Fallback source when no preference has been saved. */
const val DEFAULT_BROWSE_SOURCE = "tmdb"
