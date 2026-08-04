package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.QueryFiltersLegacy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Stacked horizontal poster shelves for tag-based browse modes (Mood, Story Themes, etc.)
 *
 * Each curated tag that has matching items in the library gets one horizontal row of poster cards.
 * This mirrors the web client's tag ribbon shelves and is gated behind [USE_TAG_RIBBON_SHELVES].
 *
 * **Reversibility:** Set `USE_TAG_RIBBON_SHELVES = false` in [BrowseModes.kt] to revert to the
 * flat picker grid ([TagPickerFragment]) instead. Both paths are fully wired and independent.
 *
 * Pattern follows [BrowseViewFragment] row construction with [ItemRowAdapter].
 *
 * ## Known areas needing on-TV verification
 *
 * - Row density: with many tags, vertical scrolling may feel long. Consider capping at ~20 rows.
 * - Card sizing: [CARD_HEIGHT] = 260 (poster). May need adjustment for TV readability.
 * - Sort: currently random-per-row. Add sort/shuffle header controls later.
 * - Lazy loading: [ItemRowAdapter.Retrieve] fires immediately per-row. For large tag sets
 *   (~50+ rows), consider deferring loads until rows scroll into view.
 * - Tag name length: long tag names may truncate in [HeaderItem]. The web uses sentence-case;
 *   we pass raw TMDb tag names for now.
 */
class TagBrowseRowsFragment : RowsSupportFragment() {
	private companion object {
		/** Maximum tags to show as rows to avoid overwhelming the UI. */
		const val MAX_ROWS = 30
		/** Items loaded per tag row. */
		const val CHUNK_SIZE = 50
		const val CARD_HEIGHT = 260
	}

	private val apiClient by inject<ApiClient>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var mode: BrowseMode
	private lateinit var itemType: BaseItemKind
	private lateinit var rowsAdapter: MutableObjectAdapter<Row>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		mode = BrowseMode.entries.first { it.key == requireArguments().getString(Extras.BrowseMode) }

		itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		val label = getBrowseModes(folder.collectionType)?.firstOrNull { it.mode == mode }?.label
		// RowsSupportFragment doesn't have a title property — set the activity title instead.
		val titleText = label?.let { getString(it) } ?: mode.key
		requireActivity().title = titleText

		rowsAdapter = MutableObjectAdapter(PositionableListRowPresenter())
		adapter = rowsAdapter

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

		val cardPresenter = CardPresenter(false, CARD_HEIGHT)

		tags.take(MAX_ROWS).forEach { tag ->
			val query = GetItemsRequest(
				parentId = folder.id,
				includeItemTypes = setOf(itemType),
				tags = setOf(tag),
				sortBy = setOf(ItemSortBy.RANDOM),
				sortOrder = setOf(SortOrder.ASCENDING),
				recursive = true,
				fields = ItemRepository.itemFields,
				limit = CHUNK_SIZE,
			)

			// ItemRowAdapter handles its own async loading via Retrieve().
			val rowAdapter = ItemRowAdapter(
				requireContext(),
				query,
				CHUNK_SIZE,          // chunkSize
				false,               // preferParentThumb
				false,               // staticHeight
				cardPresenter as Presenter,
				rowsAdapter          // parent adapter
			)
			rowAdapter.Retrieve()

			val row = ListRow(HeaderItem(tag), rowAdapter)
			rowsAdapter.add(row)
		}
	}

	/**
	 * Returns the curated tags that are actually present in this library, sorted A–Z.
	 * Shared logic with [TagPickerFragment.fetchMatchingTags].
	 */
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

		val curatedSet = curatedTagsForRibbonShelves(mode).toSet()
		return available.filter { curatedSet.contains(it) }.sorted()
	}
}

/** Maps a browse mode to its curated tag list. Duplicate of [curatedTagsFor] for clarity. */
private fun curatedTagsForRibbonShelves(mode: BrowseMode): List<String> = when (mode) {
	BrowseMode.MOOD -> MOOD_TAGS
	BrowseMode.STORY_THEMES -> STORY_THEME_TAGS
	BrowseMode.PLOT_ELEMENTS -> PLOT_ELEMENT_TAGS
	BrowseMode.WORLDS -> WORLD_TAGS
	BrowseMode.STYLES -> STYLE_TAGS
	else -> emptyList()
}
