package org.jellyfin.androidtv.preference

import org.jellyfin.androidtv.constant.GridDirection
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.constant.PosterSize
import org.jellyfin.androidtv.preference.store.DisplayPreferencesStore
import org.jellyfin.preference.booleanPreference
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.stringPreference
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

class LibraryPreferences(
	displayPreferencesId: String,
	api: ApiClient,
) : DisplayPreferencesStore(
	displayPreferencesId = displayPreferencesId,
	api = api,
) {
	companion object {
		val posterSize = enumPreference("PosterSize", PosterSize.MED)
		val imageType = enumPreference("ImageType", ImageType.POSTER)
		val gridDirection = enumPreference("GridDirection", GridDirection.HORIZONTAL)
		val enableSmartScreen = booleanPreference("SmartScreen", false)
		val enableBrowseModes = booleanPreference("BrowseModes", true)

		// Set once a browse mode has seeded its preset, so that a sort the user picks inside a
		// mode afterwards is not overwritten on the next visit.
		val browseModeSeeded = booleanPreference("BrowseModeSeeded", false)

		// Filters
		val filterFavoritesOnly = booleanPreference("FilterFavoritesOnly", false)
		val filterUnwatchedOnly = booleanPreference("FilterUnwatchedOnly", false)

		// Date cutoffs — ISO 8601 date strings for the earliest items to include.
		val filterMinDateLastSaved = stringPreference("FilterMinDateLastSaved", "")
		val filterMinPremiereDate = stringPreference("FilterMinPremiereDate", "")

		// Item sorting
		val sortBy = enumPreference("SortBy", ItemSortBy.SORT_NAME)
		val sortOrder = enumPreference("SortOrder", SortOrder.ASCENDING)
	}
}
