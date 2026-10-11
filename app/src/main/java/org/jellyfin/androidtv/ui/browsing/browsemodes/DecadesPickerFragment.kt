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
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

/** The decades a library spans, as a selectable text list. */
class DecadesPickerFragment : Fragment() {
	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var itemType: BaseItemKind
	private val title = mutableStateOf("")
	private val items = mutableStateOf<List<BaseItemDto>>(emptyList())
	private var decadeList: List<Int> = emptyList()
	private val decadeCounts = mutableStateOf<Map<String, Int>>(emptyMap())
	private var sortMode = SortMode.A_Z

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)

		itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		title.value = getString(R.string.lbl_browse_mode_decades)
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
						counts = decadeCounts.value,
						sortMode = sortMode,
						onSortChange = { sortMode = it; rebuildItems() },
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
		val decadeLabel = item.originalTitle ?: item.name ?: return
		val decadeStartYear = decadeLabel.removeSuffix("s").toIntOrNull() ?: return
		navigationRepository.navigate(
			Destinations.libraryByDecadeItems(folder, decadeStartYear, itemType.serialName)
		)
	}

	private fun load() = lifecycleScope.launch {
		val decades = try {
			withContext(Dispatchers.IO) { fetchDecades() }
		} catch (error: Exception) {
			Timber.e(error, "Unable to load decades for %s", folder.name)
			emptyList()
		}

		if (!isAdded) return@launch

		decadeList = decades
		rebuildItems()
		launch { withContext(Dispatchers.IO) { fetchDecadeCounts() } }
	}

	private fun rebuildItems() {
		val counts = decadeCounts.value
		val sorted = when (sortMode) {
			SortMode.A_Z -> decadeList.sorted()
			SortMode.Z_A -> decadeList.sortedDescending()
			SortMode.MOST -> decadeList.sortedByDescending { counts["${it}s"] ?: 0 }
			SortMode.FEWEST -> decadeList.sortedBy { counts["${it}s"] ?: 0 }
		}
		items.value = sorted.map { decadeStart ->
			val label = "${decadeStart}s"
			val json = buildJsonObject {
				put("Name", label)
				put("OriginalTitle", label)
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			Json.decodeFromString<BaseItemDto>(json)
		}
	}

	private suspend fun fetchDecadeCounts() {
		decadeCounts.value = fetchItemCounts(
			api = apiClient,
			cacheKey = countCacheKey(folder.id, "decade"),
			type = "decade",
			parentId = folder.id,
			itemType = itemType,
		)
	}

	private suspend fun fetchDecades(): List<Int> {
		val userId = userRepository.currentUser.value?.id ?: return emptyList()

		val response = apiClient.get<QueryFiltersLegacy>(
			pathTemplate = "/Items/Filters",
			queryParameters = mapOf(
				"userId" to userId,
				"parentId" to folder.id,
				"includeItemTypes" to itemType.serialName,
			),
		)
		val available = response.content.years.orEmpty()

		return available.map { (it / 10) * 10 }.distinct().sorted()
	}
}
