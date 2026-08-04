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

/**
 * A grid of curated TMDb keyword tags, shown when a tag-based browse mode tile (Mood, Story
 * Themes, etc.) is opened.
 */
class TagPickerFragment : VerticalGridSupportFragment() {
	private companion object {
		const val COLUMNS = 6
		const val CARD_HEIGHT = 200
		const val SORT_BUTTON_MARKER = "__sort_button__"
	}

	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var mode: BrowseMode
	private lateinit var itemType: BaseItemKind
	private lateinit var tagsAdapter: MutableObjectAdapter<Any>
	private var sortMode = SortMode.A_Z
	/** Raw (lowercase) tags before sorting, so we can re-sort when the mode changes. */
	private var rawTags: List<String> = emptyList()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		mode = BrowseMode.entries.first { it.key == requireArguments().getString(Extras.BrowseMode) }

		itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		val label = getBrowseModes(folder.collectionType)?.firstOrNull { it.mode == mode }?.label
		title = label?.let { getString(it) } ?: mode.key

		setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = COLUMNS })

		tagsAdapter = MutableObjectAdapter(CardPresenter(true, CARD_HEIGHT))
		adapter = tagsAdapter

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			val baseItem = (item as? BaseItemDtoBaseRowItem)?.baseItem ?: return@OnItemViewClickedListener

			// Sort button — cycle to next mode and refresh.
			if (baseItem.originalTitle == SORT_BUTTON_MARKER) {
				sortMode = sortMode.next()
				refreshGrid()
				return@OnItemViewClickedListener
			}

			// Regular tag — use originalTitle (raw tag) for filtering.
			val tag = baseItem.originalTitle ?: baseItem.name ?: return@OnItemViewClickedListener
			navigationRepository.navigate(
				Destinations.libraryByTagItems(folder, tag, itemType.serialName)
			)
		}

		load()
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
	}

	/** Rebuilds the grid in the current [sortMode], with the sort button at position 0. */
	private fun refreshGrid() {
		tagsAdapter.clear()

		// Sort button
		tagsAdapter.add(makeSortButtonItem())

		// Tag tiles, sorted by current mode
		val sorted = when (sortMode) {
			SortMode.A_Z -> rawTags.sorted()
			SortMode.Z_A -> rawTags.sortedDescending()
			SortMode.RANDOM -> rawTags.shuffled()
		}

		sorted.forEach { tagName ->
			val displayName = tagName.toTitleCase()
			val json = buildJsonObject {
				put("Name", displayName)
				put("OriginalTitle", tagName)
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			val item = Json.decodeFromString<BaseItemDto>(json)
			tagsAdapter.add(BaseItemDtoBaseRowItem(item))
		}
	}

	/** Creates the sort-mode toggle button shown as the first grid tile. */
	private fun makeSortButtonItem(): BaseItemDtoBaseRowItem {
		val label = "Sort: ${sortMode.label}"
		val json = buildJsonObject {
			put("Name", label)
			put("OriginalTitle", SORT_BUTTON_MARKER)
			put("Id", java.util.UUID.randomUUID().toString())
			put("Type", "Folder")
		}.toString()
		return BaseItemDtoBaseRowItem(Json.decodeFromString<BaseItemDto>(json))
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
}

/** Sort order for the tag/decade/rating picker grids. */
enum class SortMode(val label: String) {
	A_Z("A–Z"),
	Z_A("Z–A"),
	RANDOM("Random");

	fun next(): SortMode = when (this) {
		A_Z -> Z_A
		Z_A -> RANDOM
		RANDOM -> A_Z
	}
}

/** Maps a browse mode to its curated tag list. Internal — shared with TagBrowseRowsFragment. */
internal fun curatedTagsFor(mode: BrowseMode): List<String> = when (mode) {
	BrowseMode.MOOD -> MOOD_TAGS
	BrowseMode.STORY_THEMES -> STORY_THEME_TAGS
	BrowseMode.PLOT_ELEMENTS -> PLOT_ELEMENT_TAGS
	BrowseMode.WORLDS -> WORLD_TAGS
	BrowseMode.STYLES -> STYLE_TAGS
	else -> emptyList()
}

/**
 * Capitalises each word in a tag name for display, handling hyphens as word boundaries.
 * "feel good" → "Feel Good", "post-apocalyptic" → "Post-Apocalyptic".
 */
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
