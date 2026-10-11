package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jellyfin.androidtv.R
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

/** The age-rating levels of a library (raw official ratings grouped into eight levels). */
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
	private var levelCodes: Map<RatingLevel, List<String>> = emptyMap()
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
				val counts = ratingCounts.value
				val levelCounts = levelCodes.map { (level, codes) ->
					level.key to codes.sumOf { counts[it] ?: 0 }
				}.toMap()
				TextListGrid(
					title.value,
					items.value,
					counts = levelCounts,
					iconFor = { ratingIconFor(it.originalTitle) },
					iconTintFor = { ratingColorFor(it.originalTitle) },
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
		val level = item.originalTitle?.let { key -> RatingLevel.entries.firstOrNull { it.key == key } } ?: return
		val ratings = levelCodes[level].orEmpty()
		if (ratings.isEmpty()) return
		navigationRepository.navigate(
			Destinations.libraryByAgeRatingItems(folder, ratings.toSet(), itemType.serialName)
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
		levelCodes = rawRatings.groupBy { getRatingLevel(it) }.mapValues { (_, codes) -> codes.distinct() }

		val counts = ratingCounts.value
		val sorted = when (sortMode) {
			SortMode.A_Z -> levelCodes.keys.sortedBy { RATING_LEVEL_LABEL.getValue(it) }
			SortMode.Z_A -> levelCodes.keys.sortedByDescending { RATING_LEVEL_LABEL.getValue(it) }
			SortMode.MOST -> levelCodes.keys.sortedByDescending { level -> levelCount(level, counts) }
			SortMode.FEWEST -> levelCodes.keys.sortedBy { level -> levelCount(level, counts) }
		}

		items.value = sorted.map { level ->
			val json = buildJsonObject {
				put("Name", RATING_LEVEL_LABEL.getValue(level))
				put("OriginalTitle", level.key)
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			Json.decodeFromString<BaseItemDto>(json)
		}
	}

	private fun levelCount(level: RatingLevel, counts: Map<String, Int>): Int =
		levelCodes[level].orEmpty().sumOf { counts[it] ?: 0 }

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

/** Drawable for a rating-level tile, keyed by the level's web key. */
@DrawableRes
private fun ratingIconFor(levelKey: String?): Int? =
	levelKey?.let { key -> RatingLevel.entries.firstOrNull { it.key == key } }
		?.let { RATING_LEVELS[it]?.icon }

/** Accent colour for a rating-level tile's icon. */
private fun ratingColorFor(levelKey: String?): Color =
	levelKey?.let { key -> RatingLevel.entries.firstOrNull { it.key == key } }
		?.let { RATING_LEVELS[it]?.iconColor }
		?: Color.Unspecified
