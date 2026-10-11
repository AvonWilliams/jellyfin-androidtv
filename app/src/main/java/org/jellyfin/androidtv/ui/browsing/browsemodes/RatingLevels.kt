package org.jellyfin.androidtv.ui.browsing.browsemodes

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import org.jellyfin.androidtv.R

/** The eight age-rating levels the web groups raw official ratings into. */
enum class RatingLevel(val key: String) {
	SPECIAL_ADULT("specialAdult"),
	ADULT("adult"),
	MATURE("mature"),
	TEEN("teen"),
	GUIDANCE("guidance"),
	EVERYONE("everyone"),
	PENDING("pending"),
	UNRATED("unrated"),
}

/** Monochrome icon plus accent colour for each rating level (mirrors web RATING_LEVELS). */
data class RatingLevelStyle(
	@DrawableRes val icon: Int,
	val iconColor: Color,
)

val RATING_LEVELS: Map<RatingLevel, RatingLevelStyle> = mapOf(
	RatingLevel.EVERYONE to RatingLevelStyle(R.drawable.ic_rating_everyone, Color(0xFF5CD672)),
	RatingLevel.GUIDANCE to RatingLevelStyle(R.drawable.ic_rating_guidance, Color(0xFF9CCC65)),
	RatingLevel.TEEN to RatingLevelStyle(R.drawable.ic_rating_teen, Color(0xFFEECE55)),
	RatingLevel.MATURE to RatingLevelStyle(R.drawable.ic_rating_mature, Color(0xFFF08A5D)),
	RatingLevel.ADULT to RatingLevelStyle(R.drawable.ic_rating_adult, Color(0xFFE0533D)),
	RatingLevel.SPECIAL_ADULT to RatingLevelStyle(R.drawable.ic_rating_special_adult, Color(0xFFB71C1C)),
	RatingLevel.UNRATED to RatingLevelStyle(R.drawable.ic_rating_unrated, Color(0xFF9AA5B1)),
	RatingLevel.PENDING to RatingLevelStyle(R.drawable.ic_rating_pending, Color(0xFF9AA5B1)),
)

/** Display order for grouped rating tiles, most restrictive first. */
val RATING_LEVEL_ORDER: List<RatingLevel> = listOf(
	RatingLevel.SPECIAL_ADULT,
	RatingLevel.ADULT,
	RatingLevel.MATURE,
	RatingLevel.TEEN,
	RatingLevel.GUIDANCE,
	RatingLevel.EVERYONE,
	RatingLevel.PENDING,
	RatingLevel.UNRATED,
)

/** Canonical label plus the recommended minimum age shown on each grouped rating tile. */
val RATING_LEVEL_LABEL: Map<RatingLevel, String> = mapOf(
	RatingLevel.EVERYONE to "G · All ages",
	RatingLevel.GUIDANCE to "PG · 6+",
	RatingLevel.TEEN to "PG-13 · 13+",
	RatingLevel.MATURE to "MA · 15+",
	RatingLevel.ADULT to "R · 18+",
	RatingLevel.SPECIAL_ADULT to "X · 18+",
	RatingLevel.UNRATED to "Unrated",
	RatingLevel.PENDING to "Pending",
)

/**
 * Ratings whose meaning cannot be read off a number.
 *
 * Order matters: the first match wins, so longer codes have to precede the shorter ones they
 * start with, otherwise `R18+` would be read as `R` and `PG-13` as `PG`.
 */
private val RATING_TOKENS: List<Pair<String, RatingLevel>> = listOf(
	// Special adult — prohibited / adult-entertainment / objectionable.
	"OBJECTIONABLE" to RatingLevel.SPECIAL_ADULT,
	"X18+" to RatingLevel.SPECIAL_ADULT,
	"AO" to RatingLevel.SPECIAL_ADULT,
	"RC" to RatingLevel.SPECIAL_ADULT,
	"X" to RatingLevel.SPECIAL_ADULT,
	// Adult (18+) — longer R-prefixed codes before the bare "R".
	"NC-17" to RatingLevel.ADULT,
	"R21" to RatingLevel.ADULT,
	"R18+" to RatingLevel.ADULT,
	"R18" to RatingLevel.ADULT,
	"RP16" to RatingLevel.MATURE,
	"RP13" to RatingLevel.TEEN,
	"R16" to RatingLevel.MATURE,
	"R15" to RatingLevel.MATURE,
	"R13" to RatingLevel.TEEN,
	"R" to RatingLevel.ADULT,
	// Mature (15+) — longer M-prefixed codes before the bare "M".
	"MA15+" to RatingLevel.MATURE,
	"M18" to RatingLevel.MATURE,
	"MA" to RatingLevel.MATURE,
	"NC16" to RatingLevel.MATURE,
	"18A" to RatingLevel.MATURE,
	"14A" to RatingLevel.MATURE,
	"TV-MA" to RatingLevel.MATURE,
	// Teen (13+).
	"PG-13" to RatingLevel.TEEN,
	"PG13" to RatingLevel.TEEN,
	"12A" to RatingLevel.TEEN,
	"TV-14" to RatingLevel.TEEN,
	"M" to RatingLevel.TEEN,
	"T" to RatingLevel.TEEN,
	// Guidance (6+).
	"TV-Y7-FV" to RatingLevel.GUIDANCE,
	"TV-Y7" to RatingLevel.GUIDANCE,
	"TV-PG" to RatingLevel.GUIDANCE,
	"PG" to RatingLevel.GUIDANCE,
	// Everyone.
	"TV-G" to RatingLevel.EVERYONE,
	"TV-Y" to RatingLevel.EVERYONE,
	"G" to RatingLevel.EVERYONE,
	"U" to RatingLevel.EVERYONE,
	"E" to RatingLevel.EVERYONE,
)

/** Age in years above which each level applies, most restrictive first. */
private val RATING_AGES: List<Pair<Int, RatingLevel>> = listOf(
	18 to RatingLevel.ADULT,
	15 to RatingLevel.MATURE,
	12 to RatingLevel.TEEN,
	6 to RatingLevel.GUIDANCE,
	0 to RatingLevel.EVERYONE,
)

/**
 * Maps a raw official rating (which may carry a country prefix such as "AU-MA15+") to one of the
 * eight levels. The negative lookahead on the prefix matters: US television ratings keep their own
 * "TV-" prefix, so "TV-MA" stays "TV-MA" instead of becoming "MA".
 */
fun getRatingLevel(rating: String): RatingLevel {
	val normalized = rating.trim().uppercase().replace(Regex("^(?!TV-)[A-Z]{2}-"), "")

	if (normalized.isEmpty() || Regex("^(NR|UR|UNRATED|NOT RATED|NONE)$").matches(normalized)) {
		return RatingLevel.UNRATED
	}

	if (Regex("^(CTC|TBA|TO BE CLASSIFIED|CHECK THE CLASSIFICATION)$").matches(normalized) ||
		normalized.contains("CLASSIFICATION")
	) {
		return RatingLevel.PENDING
	}

	val token = RATING_TOKENS.firstOrNull { (code) -> normalized.startsWith(code) }
	if (token != null) return token.second

	// Whatever is left starts with an age in most systems: "15", "16+", "12A".
	val age = Regex("^(\\d{1,2})").find(normalized)
	if (age != null) {
		val years = age.groupValues[1].toInt()
		return RATING_AGES.firstOrNull { (minimum) -> years >= minimum }?.second ?: RatingLevel.UNRATED
	}

	return RatingLevel.UNRATED
}
