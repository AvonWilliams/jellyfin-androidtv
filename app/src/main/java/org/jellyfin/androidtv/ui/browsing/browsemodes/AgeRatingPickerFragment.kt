package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.PresenterSelector
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
	private var sortMode = SortMode.A_Z
	private var rawRatings: List<String> = emptyList()

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

		val sortPresenter = CardPresenter(true, 48)
		val ratingPresenter = CardPresenter(true, CARD_HEIGHT)
		ratingsAdapter = MutableObjectAdapter(object : PresenterSelector() {
			override fun getPresenter(item: Any?): Presenter {
				val baseItem = (item as? BaseItemDtoBaseRowItem)?.baseItem
				return if (baseItem?.originalTitle == "__sort__") sortPresenter else ratingPresenter
			}
		})
		adapter = ratingsAdapter

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			val baseItem = (item as? BaseItemDtoBaseRowItem)?.baseItem ?: return@OnItemViewClickedListener

			if (baseItem.originalTitle == "__sort__") {
				sortMode = sortMode.next()
				refreshGrid()
				return@OnItemViewClickedListener
			}

			val rating = baseItem.name ?: return@OnItemViewClickedListener
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

		rawRatings = ratings
		refreshGrid()
	}

	private fun refreshGrid() {
		ratingsAdapter.clear()

		val sortJson = buildJsonObject {
			put("Name", " Sort: ${sortMode.label}")
			put("OriginalTitle", "__sort__")
			put("Id", java.util.UUID.randomUUID().toString())
			put("Type", "Folder")
		}.toString()
		ratingsAdapter.add(BaseItemDtoBaseRowItem(Json.decodeFromString<BaseItemDto>(sortJson)))

		val sorted = when (sortMode) {
			SortMode.A_Z -> rawRatings.sorted()
			SortMode.Z_A -> rawRatings.sortedDescending()
			SortMode.RANDOM -> rawRatings.shuffled()
		}

		sorted.forEach { rating ->
			val json = buildJsonObject {
				put("Name", rating)
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			ratingsAdapter.add(BaseItemDtoBaseRowItem(Json.decodeFromString(json)))
		}
	}

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
		return response.content.officialRatings.orEmpty()
	}
}
