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
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

/** The genres of a library, as a selectable text list with item counts. */
class GenrePickerFragment : Fragment() {
	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var itemType: BaseItemKind
	private val title = mutableStateOf("")
	private val items = mutableStateOf<List<BaseItemDto>>(emptyList())
	private var genreList: List<BaseItemDto> = emptyList()
	private var genreCounts: Map<String, Int> = emptyMap()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)

		itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		title.value = getBrowseModes(folder.collectionType)
			?.firstOrNull { it.mode == BrowseMode.GENRES }
			?.label
			?.let { getString(it) }
			?: "Genres"
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setContent {
			JellyfinTheme {
				TextListGrid(title.value, items.value) { item -> onClick(item) }
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		load()
	}

	private fun onClick(item: BaseItemDto) {
		val genre = item.originalTitle ?: item.name ?: return
		navigationRepository.navigate(
			Destinations.libraryByGenreItems(folder, genre, itemType.serialName)
		)
	}

	private fun load() = lifecycleScope.launch {
		val genres = try {
			withContext(Dispatchers.IO) {
				apiClient.genresApi.getGenres(
					parentId = folder.id,
					sortBy = setOf(ItemSortBy.SORT_NAME),
				).content.items
			}
		} catch (error: Exception) {
			Timber.e(error, "Unable to load genres for %s", folder.name)
			emptyList()
		}

		if (!isAdded) return@launch

		genreList = genres.filter { !it.name.isNullOrBlank() }.sortedBy { it.name }
		rebuildItems()
		launch { withContext(Dispatchers.IO) { fetchGenreCounts() } }
	}

	private fun rebuildItems() {
		items.value = genreList.map { genre ->
			val name = genre.name.orEmpty()
			val count = genreCounts[name]
			val display = if (count != null) "$name ($count)" else name
			val json = buildJsonObject {
				put("Name", display)
				put("OriginalTitle", name)
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			Json.decodeFromString<BaseItemDto>(json)
		}
	}

	private suspend fun fetchGenreCounts() {
		genreCounts = fetchItemCounts(
			api = apiClient,
			cacheKey = countCacheKey(folder.id, "genre"),
			values = genreList.map { it.name.orEmpty() },
			request = { genre ->
				GetItemsRequest(
					parentId = folder.id,
					includeItemTypes = setOf(itemType),
					genres = setOf(genre),
					recursive = true,
					limit = 0,
				)
			},
		)
		if (isAdded) withContext(Dispatchers.Main) { rebuildItems() }
	}
}
