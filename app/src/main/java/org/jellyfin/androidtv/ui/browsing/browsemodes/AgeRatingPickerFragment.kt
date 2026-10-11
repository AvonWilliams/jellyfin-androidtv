package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.QueryFiltersLegacy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

/** The official content ratings of a library, as a selectable text list with sort options. */
class AgeRatingPickerFragment : Fragment() {
	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var itemType: BaseItemKind
	private val title = mutableStateOf("")
	private val items = mutableStateOf<List<BaseItemDto>>(emptyList())
	private var sortMode = SortMode.A_Z
	private var rawRatings: List<String> = emptyList()
	private val ratingCounts = mutableStateOf<Map<String, Int>>(emptyMap())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)

		itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		title.value = getBrowseModeDefinition(folder.collectionType, BrowseMode.AGE_RATING)
			?.label
			?.let { getString(it) }
			?: "Age Rating"
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setContent {
			JellyfinTheme {
				TextListGrid(
					title.value,
					items.value,
					counts = ratingCounts.value,
					sortMode = sortMode,
					onSortChange = { sortMode = it; refreshGrid() },
				) { item -> onClick(item) }
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		load()
	}

	private fun onClick(item: BaseItemDto) {
		val rating = item.originalTitle ?: item.name ?: return
		navigationRepository.navigate(
			Destinations.libraryByAgeRatingItems(folder, rating, itemType.serialName)
		)
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
		launch { withContext(Dispatchers.IO) { fetchRatingCounts() } }
	}

	private fun refreshGrid() {
		val counts = ratingCounts.value
		val sorted = when (sortMode) {
			SortMode.A_Z -> rawRatings.sorted()
			SortMode.Z_A -> rawRatings.sortedDescending()
			SortMode.MOST -> rawRatings.sortedByDescending { counts[it] ?: 0 }
			SortMode.FEWEST -> rawRatings.sortedBy { counts[it] ?: 0 }
		}

		items.value = sorted.map { rating ->
			val json = buildJsonObject {
				put("Name", rating)
				put("OriginalTitle", rating)
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			Json.decodeFromString<BaseItemDto>(json)
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

	private suspend fun fetchRatingCounts() {
		ratingCounts.value = fetchItemCounts(
			api = apiClient,
			cacheKey = countCacheKey(folder.id, "rating"),
			type = "rating",
			parentId = folder.id,
			itemType = itemType,
		)
	}
}
