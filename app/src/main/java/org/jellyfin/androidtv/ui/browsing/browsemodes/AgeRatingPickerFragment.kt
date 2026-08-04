package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.QueryFiltersLegacy
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * A grid of official content ratings (MPAA, BBFC, etc.) present in this library.
 *
 * Fetches the available official ratings from /Items/Filters and shows only ratings
 * that have at least one matching item.
 */
class AgeRatingPickerFragment : VerticalGridSupportFragment() {
	private companion object {
		const val COLUMNS = 6
		const val CARD_HEIGHT = 200
	}

	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var itemType: BaseItemKind
	private lateinit var ratingsAdapter: MutableObjectAdapter<Any>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)

		itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		title = getBrowseModes(folder.collectionType)
			?.firstOrNull { it.mode == BrowseMode.AGE_RATING }
			?.label
			?.let { getString(it) }
			?: "Age Rating"

		setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = COLUMNS })

		ratingsAdapter = MutableObjectAdapter(CardPresenter(true, CARD_HEIGHT))
		adapter = ratingsAdapter

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			val rating = (item as? BaseItemDtoBaseRowItem)?.baseItem?.name
				?: return@OnItemViewClickedListener
			navigationRepository.navigate(
				Destinations.libraryByAgeRatingItems(folder, rating, itemType.serialName)
			)
		}

		load()
	}

	private fun load() = lifecycleScope.launch {
		val ratings = try {
			withContext(Dispatchers.IO) { fetchRatings() }
		} catch (error: Exception) {
			Timber.e(error, "Unable to load age ratings for %s", folder.name)
			emptyList()
		}

		if (!isAdded) return@launch

		ratings.forEach { rating ->
			// BaseItemDto has no Kotlin-level defaults — use JSON to build a synthetic item.
		val syntheticItem = Json.decodeFromString<BaseItemDto>("""{"Name":"$rating"}""")
		ratingsAdapter.add(BaseItemDtoBaseRowItem(syntheticItem))
		}
	}

	/** Fetches available official ratings from the server and returns them sorted A–Z. */
	private suspend fun fetchRatings(): List<String> {
		val userId = userRepository.currentUser.value?.id ?: return emptyList()

		val response = apiClient.get<QueryFiltersLegacy>(
			pathTemplate = "/Items/Filters",
			queryParameters = mapOf(
				"userId" to userId,
				"parentId" to folder.id,
				"includeItemTypes" to itemType.serialName,
			),
		)
		return response.content.officialRatings.orEmpty().sorted()
	}
}
