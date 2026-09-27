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
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetStudiosRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * The studios of a library, as a selectable text list with item counts.
 *
 * Deliberately not modelled on [org.jellyfin.androidtv.ui.browsing.ByGenreFragment], which builds
 * one row per value and retrieves them all up front. A library holds an order of magnitude more
 * studios than genres, so that shape would fire hundreds of requests at once on opening the screen.
 */
class ByStudioFragment : Fragment() {
	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var itemType: BaseItemKind
	private val title = mutableStateOf("")
	private val items = mutableStateOf<List<BaseItemDto>>(emptyList())
	private var studioList: List<BaseItemDto> = emptyList()
	private val studioCounts = mutableStateOf<Map<String, Int>>(emptyMap())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		val includeType = requireArguments().getString(Extras.IncludeType)

		itemType = when (folder.collectionType) {
			org.jellyfin.sdk.model.api.CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		title.value = folder.name.orEmpty()
		load(includeType)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setContent {
			JellyfinTheme {
				TextListGrid(title.value, items.value, counts = studioCounts.value) { item -> onClick(item) }
			}
		}
	}

	private fun onClick(item: BaseItemDto) {
		val studio = item.originalTitle ?: item.name ?: return
		navigationRepository.navigate(
			Destinations.libraryByStudioItems(folder, studio)
		)
	}

	private fun load(includeType: String?) = lifecycleScope.launch {
		val studios = try {
			withContext(Dispatchers.IO) {
				apiClient.studiosApi.getStudios(
					GetStudiosRequest(
						parentId = folder.id,
						includeItemTypes = includeType?.let(BaseItemKind::fromNameOrNull)?.let(::setOf),
					)
				).content.items
			}
		} catch (error: Exception) {
			Timber.e(error, "Unable to load studios for %s", folder.name)
			emptyList()
		}

		if (!isAdded) return@launch

		studioList = studios.filter { !it.name.isNullOrBlank() }.sortedBy { it.name }
		rebuildItems()
		launch { withContext(Dispatchers.IO) { fetchStudioCounts() } }
	}

	private fun rebuildItems() {
		items.value = studioList.map { studio ->
			val name = studio.name.orEmpty()
			val json = buildJsonObject {
				put("Name", name)
				put("OriginalTitle", name)
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			Json.decodeFromString<BaseItemDto>(json)
		}
	}

	private suspend fun fetchStudioCounts() {
		studioCounts.value = fetchItemCounts(
			api = apiClient,
			cacheKey = countCacheKey(folder.id, "studio"),
			type = "studio",
			parentId = folder.id,
			itemType = itemType,
		)
	}
}
