package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.preference.LibraryPreferences
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.sdk.compat.copyWithDisplayPreferencesId
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.android.ext.android.inject

/**
 * The grid of tiles shown when a library is opened, each tile a way of browsing that library.
 */
class BrowseModesFragment : VerticalGridSupportFragment() {
	private companion object {
		const val COLUMNS = 4
	}

	private val navigationRepository by inject<NavigationRepository>()
	private val preferencesRepository by inject<PreferencesRepository>()

	private lateinit var folder: BaseItemDto

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		title = folder.name

		setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = COLUMNS })

		adapter = ArrayObjectAdapter(BrowseModeTilePresenter()).apply {
			getBrowseModes(folder.collectionType).orEmpty().forEach { definition ->
				add(BrowseModeTile(definition, getString(definition.label)))
			}
		}

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			if (item is BrowseModeTile) open(item.definition)
		}
	}

	private fun open(definition: BrowseModeDefinition) {
		val itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		when {
			definition.destination == BrowseModeDestination.GENRES ->
				navigationRepository.navigate(Destinations.libraryByGenres(folder, itemType.serialName))

			definition.destination == BrowseModeDestination.STUDIOS ->
				navigationRepository.navigate(Destinations.libraryByStudio(folder, itemType.serialName))

			definition.destination == BrowseModeDestination.DISCOVER ->
				navigationRepository.navigate(Destinations.discover(folder, definition.mode.key))

			definition.destination == BrowseModeDestination.TAG_PICKER -> {
				if (useTagRibbonShelves) {
					navigationRepository.navigate(Destinations.tagBrowseRows(folder, definition.mode.key))
				} else {
					navigationRepository.navigate(Destinations.tagPicker(folder, definition.mode.key))
				}
			}

			definition.destination == BrowseModeDestination.DECADES_PICKER ->
				navigationRepository.navigate(Destinations.decadesPicker(folder))

			definition.destination == BrowseModeDestination.AGE_RATING_PICKER ->
				navigationRepository.navigate(Destinations.ageRatingPicker(folder))

			definition.preset == null ->
				navigationRepository.navigate(Destinations.libraryBrowser(folder))

			else -> lifecycleScope.launch {
				// Each mode keeps its own display preferences so that changing the sort inside a
				// mode does not disturb the sort chosen for the plain library view.
				val scoped = folder.copyWithDisplayPreferencesId("${folder.id}-${definition.mode.key}")
				// Reading the store fetches it from the server, so keep it off the main thread.
				withContext(Dispatchers.IO) { seed(scoped.displayPreferencesId!!, definition.preset) }
				navigationRepository.navigate(Destinations.libraryBrowser(scoped))
			}
		}
	}

	/**
	 * Applies a mode's preset the first time it is opened. Later visits leave the store alone so
	 * that any sort or filter the user picked inside the mode survives.
	 */
	private suspend fun seed(preferencesId: String, preset: BrowsePreset) {
		val preferences = preferencesRepository.getLibraryPreferences(preferencesId)
		if (preferences[LibraryPreferences.browseModeSeeded]) return

		preferences[LibraryPreferences.browseModeSeeded] = true
		preset.sortBy?.let { preferences[LibraryPreferences.sortBy] = it }
		preset.sortOrder?.let { preferences[LibraryPreferences.sortOrder] = it }
		preferences[LibraryPreferences.filterUnwatchedOnly] = preset.unwatchedOnly
		preferences[LibraryPreferences.filterFavoritesOnly] = preset.favoritesOnly
		preset.minDateLastSaved?.let { preferences[LibraryPreferences.filterMinDateLastSaved] = it }
		preset.minPremiereDate?.let { preferences[LibraryPreferences.filterMinPremiereDate] = it }
		preferences.commit()
	}
}
