package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
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

/** The curated tags of a browse mode, as a selectable text list with sort options. */
class TagPickerFragment : Fragment() {
	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var mode: BrowseMode
	private lateinit var itemType: BaseItemKind
	private val title = mutableStateOf("")
	private val items = mutableStateOf<List<BaseItemDto>>(emptyList())
	private var sortMode = SortMode.A_Z
	private var rawTags: List<String> = emptyList()
	private val tagCounts = mutableStateOf<Map<String, Int>>(emptyMap())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		mode = BrowseMode.entries.first { it.key == requireArguments().getString(Extras.BrowseMode) }

		itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		val label = getBrowseModeDefinition(folder.collectionType, mode)?.label
		title.value = label?.let { getString(it) } ?: mode.key
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setContent {
			JellyfinTheme {
				val modeDefinition = getBrowseModeDefinition(folder.collectionType, mode)
				val tagIcon = modeDefinition?.icon
				val tagColor = modeDefinition?.iconTint?.let { colorResource(it) } ?: Color.Unspecified
				Box(modifier = Modifier.fillMaxSize()) {
					LibraryBackdrop(folder = folder)
					TextListGrid(
						title.value,
						items.value,
						counts = tagCounts.value,
						iconFor = { _ -> tagIcon },
						iconTint = tagColor,
						sortMode = sortMode,
						onSortChange = { sortMode = it; refreshGrid() },
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
		// Tag row — use originalTitle (raw tag) for filtering
		val tag = item.originalTitle ?: item.name ?: return
		navigationRepository.navigate(
			Destinations.libraryByTagItems(folder, tag, itemType.serialName)
		)
	}

	private fun load() = lifecycleScope.launch {
		val tags = try {
			withContext(Dispatchers.IO) { fetchMatchingTags() }
		} catch (error: Exception) {
			Timber.e(error, "Unable to load tags for %s / %s", folder.name, mode.key)
			emptyList()
		}

		if (!isAdded) return@launch

		rawTags = tags
		refreshGrid()
		// Fetch per-tag counts in the background; tiles show them and Most/Fewest sort by them.
		launch { withContext(Dispatchers.IO) { fetchTagCounts() } }
	}

	private fun refreshGrid() {
		val counts = tagCounts.value
		val sorted = when (sortMode) {
			SortMode.A_Z -> rawTags.sorted()
			SortMode.Z_A -> rawTags.sortedDescending()
			SortMode.MOST -> rawTags.sortedByDescending { counts[it] ?: 0 }
			SortMode.FEWEST -> rawTags.sortedBy { counts[it] ?: 0 }
		}

		items.value = sorted.map { tagName ->
			val json = buildJsonObject {
				put("Name", tagName.toTitleCase())
				put("OriginalTitle", tagName)
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			Json.decodeFromString<BaseItemDto>(json)
		}
	}

	private suspend fun fetchMatchingTags(): List<String> {
		val userId = userRepository.currentUser.value?.id ?: return emptyList()

		val response = apiClient.get<QueryFiltersLegacy>(
			pathTemplate = "/Items/Filters",
			queryParameters = mapOf(
				"userId" to userId,
				"parentId" to folder.id,
				"includeItemTypes" to itemType.serialName,
			),
		)
		val available = response.content.tags.orEmpty()

		val curatedSet = curatedTagsFor(mode).toSet()
		return available.filter { curatedSet.contains(it) }.sorted()
	}

	/** Fetches per-tag item counts for Most/Fewest items sorting. */
	private suspend fun fetchTagCounts() {
		tagCounts.value = fetchItemCounts(
			api = apiClient,
			cacheKey = countCacheKey(folder.id, "tag:${mode.key}"),
			type = "tag",
			parentId = folder.id,
			itemType = itemType,
		)
	}
}

enum class SortMode(val label: String) {
	A_Z("A–Z"),
	Z_A("Z–A"),
	MOST("Most items"),
	FEWEST("Fewest items");

	companion object {
		/** The sort pills offered on every picker, in display order. */
		val PILLS = listOf(A_Z, Z_A, MOST, FEWEST)
	}
}

internal fun curatedTagsFor(mode: BrowseMode): List<String> = when (mode) {
	BrowseMode.MOOD -> MOOD_TAGS
	BrowseMode.STORY_THEMES -> STORY_THEME_TAGS
	BrowseMode.PLOT_ELEMENTS -> PLOT_ELEMENT_TAGS
	BrowseMode.WORLDS -> WORLD_TAGS
	BrowseMode.STYLES -> STYLE_TAGS
	else -> emptyList()
}

internal fun String.toTitleCase(): String = buildString {
	var capitalise = true
	for (char in this@toTitleCase) {
		if (char.isWhitespace() || char == '-') {
			capitalise = true
			append(char)
		} else if (capitalise) {
			append(char.uppercaseChar())
			capitalise = false
		} else {
			append(char)
		}
	}
}
