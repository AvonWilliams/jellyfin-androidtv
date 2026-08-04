package org.jellyfin.androidtv.data.model

import org.jellyfin.sdk.model.api.ItemFilter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FilterOptions {
	var isFavoriteOnly = false
	var isUnwatchedOnly = false

	/** ISO 8601 date strings read from display preferences. */
	var minDateLastSaved: String? = null
	var minPremiereDate: String? = null

	val filters: Set<ItemFilter>
		get() = buildSet {
			if (isFavoriteOnly) add(ItemFilter.IS_FAVORITE)
			if (isUnwatchedOnly) add(ItemFilter.IS_UNPLAYED)
		}

	/** Parsed form of [minDateLastSaved], or null when blank or unparseable. */
	val minDateLastSavedParsed: LocalDateTime?
		get() = parseDate(minDateLastSaved)

	/** Parsed form of [minPremiereDate], or null when blank or unparseable. */
	val minPremiereDateParsed: LocalDateTime?
		get() = parseDate(minPremiereDate)

	private companion object {
		fun parseDate(iso: String?): LocalDateTime? {
			if (iso.isNullOrBlank()) return null
			return try {
				val cleaned = iso.removeSuffix("Z")
				LocalDateTime.parse(cleaned, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
			} catch (_: Exception) {
				null
			}
		}
	}
}
