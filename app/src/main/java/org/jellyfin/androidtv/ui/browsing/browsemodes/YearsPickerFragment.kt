package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.QueryFiltersLegacy
import org.koin.android.ext.android.inject
import timber.log.Timber

/** The individual years a library spans, as a selectable text list. */
class YearsPickerFragment : Fragment() {
	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var itemType: BaseItemKind
	private val title = mutableStateOf("")
	private val items = mutableStateOf<List<BaseItemDto>>(emptyList())
	private var yearList: List<Int> = emptyList()
	private val yearCounts = mutableStateOf<Map<String, Int>>(emptyMap())
	private var sortMode = SortMode.A_Z

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)

		itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		title.value = getString(R.string.lbl_browse_mode_year)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setContent {
			JellyfinTheme {
				Box(modifier = Modifier.fillMaxSize()) {
					LibraryBackdrop(folder = folder)
					TextListGrid(
						title.value,
						items.value,
						counts = yearCounts.value,
						sortMode = sortMode,
						onSortChange = { sortMode = it; rebuildItems() },
						labelFontSize = 32.sp,
						labelFontWeight = FontWeight.Bold,
						iconTile = true,
					) { item -> onClick(item) }
				}
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		load()
	}

	private fun onClick(item: BaseItemDto) {
		val yearLabel = item.originalTitle ?: item.name ?: return
		val year = yearLabel.toIntOrNull() ?: return
		navigationRepository.navigate(
			Destinations.libraryByYearItems(folder, year, itemType.serialName)
		)
	}

	private fun load() = lifecycleScope.launch {
		val years = try {
			withContext(Dispatchers.IO) { fetchYears() }
		} catch (error: Exception) {
			Timber.e(error, "Unable to load years for %s", folder.name)
			emptyList()
		}

		if (!isAdded) return@launch

		yearList = years
		rebuildItems()
		launch { withContext(Dispatchers.IO) { fetchYearCounts() } }
	}

	private fun rebuildItems() {
		val counts = yearCounts.value
		val sorted = when (sortMode) {
			SortMode.A_Z -> yearList.sorted()
			SortMode.Z_A -> yearList.sortedDescending()
			SortMode.MOST -> yearList.sortedByDescending { counts[it.toString()] ?: 0 }
			SortMode.FEWEST -> yearList.sortedBy { counts[it.toString()] ?: 0 }
		}
		items.value = sorted.map { year ->
			val label = year.toString()
			val json = buildJsonObject {
				put("Name", label)
				put("OriginalTitle", label)
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			Json.decodeFromString<BaseItemDto>(json)
		}
	}

	private suspend fun fetchYearCounts() {
		yearCounts.value = fetchItemCounts(
			api = apiClient,
			cacheKey = countCacheKey(folder.id, "year"),
			type = "year",
			parentId = folder.id,
			itemType = itemType,
		)
	}

	private suspend fun fetchYears(): List<Int> {
		val userId = userRepository.currentUser.value?.id ?: return emptyList()

		val response = apiClient.get<QueryFiltersLegacy>(
			pathTemplate = "/Items/Filters",
			queryParameters = mapOf(
				"userId" to userId,
				"parentId" to folder.id,
				"includeItemTypes" to itemType.serialName,
			),
		)
		return response.content.years.orEmpty().distinct().sorted()
	}
}
