package org.jellyfin.androidtv.ui.browsing.browsemodes

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import org.jellyfin.androidtv.R

/** Icon + accent colour for a single genre tile. */
data class GenreStyle(
	@DrawableRes val icon: Int,
	val color: Color,
)

/**
 * Each genre gets its own icon and accent colour so the grid reads as distinct destinations
 * rather than a set of identical tiles. Mirrors the web client's `GENRE_STYLES` in `pickTiles.ts`;
 * the alias keys (`animated`/`romantic`/`sciencefiction`) point at the same icon + colour as their
 * canonical spellings.
 */
private val GENRE_STYLES: Map<String, GenreStyle> = mapOf(
	"action" to GenreStyle(R.drawable.ic_genre_action, Color(0xFFE53935)),
	"adventure" to GenreStyle(R.drawable.ic_genre_adventure, Color(0xFFFB8C00)),
	"animation" to GenreStyle(R.drawable.ic_genre_animation, Color(0xFFFDD835)),
	"animated" to GenreStyle(R.drawable.ic_genre_animation, Color(0xFFFDD835)),
	"comedy" to GenreStyle(R.drawable.ic_genre_comedy, Color(0xFFF06292)),
	"crime" to GenreStyle(R.drawable.ic_genre_crime, Color(0xFF546E7A)),
	"documentary" to GenreStyle(R.drawable.ic_genre_documentary, Color(0xFF1E88E5)),
	"drama" to GenreStyle(R.drawable.ic_genre_drama, Color(0xFF8E24AA)),
	"family" to GenreStyle(R.drawable.ic_genre_family, Color(0xFF43A047)),
	"fantasy" to GenreStyle(R.drawable.ic_genre_fantasy, Color(0xFF5E35B1)),
	"history" to GenreStyle(R.drawable.ic_genre_history, Color(0xFF8D6E63)),
	"horror" to GenreStyle(R.drawable.ic_genre_horror, Color(0xFFC62828)),
	"music" to GenreStyle(R.drawable.ic_genre_music, Color(0xFF26A69A)),
	"mystery" to GenreStyle(R.drawable.ic_genre_mystery, Color(0xFF3949AB)),
	"romance" to GenreStyle(R.drawable.ic_genre_romance, Color(0xFFEC407A)),
	"romantic" to GenreStyle(R.drawable.ic_genre_romance, Color(0xFFEC407A)),
	"scifi" to GenreStyle(R.drawable.ic_genre_scifi, Color(0xFF00ACC1)),
	"sciencefiction" to GenreStyle(R.drawable.ic_genre_scifi, Color(0xFF00ACC1)),
	"thriller" to GenreStyle(R.drawable.ic_genre_thriller, Color(0xFFEF6C00)),
	"war" to GenreStyle(R.drawable.ic_genre_war, Color(0xFF6D4C41)),
	"western" to GenreStyle(R.drawable.ic_genre_western, Color(0xFFC9A227)),
)

/** Genres without a dedicated icon share the generic category glyph. */
private val FALLBACK_GENRE_STYLE = GenreStyle(R.drawable.ic_masks, Color(0xFF9AA5B1))

/** Characters stripped from a genre name before lookup (everything non-alphanumeric). */
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]")

/** Normalises a genre name for lookup: case-insensitive with punctuation and spacing ignored. */
private fun normalizeGenre(genre: String): String =
	genre.trim().lowercase().replace(NON_ALPHANUMERIC, "")

/** The icon + accent colour for a genre, falling back to the generic category glyph. */
fun getGenreStyle(genre: String?): GenreStyle =
	GENRE_STYLES[normalizeGenre(genre.orEmpty())] ?: FALLBACK_GENRE_STYLE
