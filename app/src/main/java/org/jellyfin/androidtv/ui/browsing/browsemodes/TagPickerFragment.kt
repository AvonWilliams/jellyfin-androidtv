package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
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
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.QueryFiltersLegacy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

class TagPickerFragment : VerticalGridSupportFragment() {
	private companion object {
		const val COLUMNS = 6
		const val CARD_HEIGHT = 200
	}

	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var mode: BrowseMode
	private lateinit var itemType: BaseItemKind
	private lateinit var tagsAdapter: MutableObjectAdapter<Any>
	private var sortMode = SortMode.RANDOM
	private var rawTags: List<String> = emptyList()
	private var tagCounts: Map<String, Int> = emptyMap()

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

		val sortPresenter = object : Presenter() {
			override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
				val tv = TextView(parent.context).apply {
					isFocusable = true
					isFocusableInTouchMode = true
					gravity = Gravity.CENTER
					setTextColor(Color.WHITE)
					textSize = 14f
					setPadding(24, 10, 24, 10)
				}
				return object : ViewHolder(tv) {}
			}
			override fun onBindViewHolder(vh: Presenter.ViewHolder, item: Any?) {
				(vh.view as TextView).text = (item as? BaseItemDtoBaseRowItem)?.baseItem?.name
			}
			override fun onUnbindViewHolder(vh: Presenter.ViewHolder) {}
		}
		val tagPresenter = CardPresenter(true, CARD_HEIGHT)
		tagsAdapter = MutableObjectAdapter(object : PresenterSelector() {
			override fun getPresenter(item: Any?): Presenter {
				val baseItem = (item as? BaseItemDtoBaseRowItem)?.baseItem
				return if (baseItem?.originalTitle == "__sort__") sortPresenter else tagPresenter
			}
		})
		adapter = tagsAdapter

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			val baseItem = (item as? BaseItemDtoBaseRowItem)?.baseItem ?: return@OnItemViewClickedListener

			// Sort button
			if (baseItem.originalTitle == "__sort__") {
				sortMode = sortMode.next()
				refreshGrid()
				return@OnItemViewClickedListener
			}
			// Reshuffle button
			if (baseItem.originalTitle == "__reshuffle__") {
				refreshGrid()
				return@OnItemViewClickedListener
			}

			// Tag tile — use originalTitle (raw tag) for filtering
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
		// Fetch counts in background for interleaved random shuffle.
		launch { withContext(Dispatchers.IO) { fetchTagCounts() } }
	}

	private fun refreshGrid() {
		tagsAdapter.clear()

		// Sort toggle tile
		val sortJson = buildJsonObject {
			put("Name", " Sort: ${sortMode.label}")
			put("OriginalTitle", "__sort__")
			put("Id", java.util.UUID.randomUUID().toString())
			put("Type", "Folder")
		}.toString()
		tagsAdapter.add(BaseItemDtoBaseRowItem(Json.decodeFromString<BaseItemDto>(sortJson)))

		// Reshuffle button (only in Random mode)
		if (sortMode == SortMode.RANDOM) {
			val shuffleJson = buildJsonObject {
				put("Name", " ↻ Reshuffle")
				put("OriginalTitle", "__reshuffle__")
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			tagsAdapter.add(BaseItemDtoBaseRowItem(Json.decodeFromString<BaseItemDto>(shuffleJson)))
		}

		val sorted = when (sortMode) {
			SortMode.RANDOM -> interleavedShuffle(rawTags, tagCounts)
			SortMode.A_Z -> rawTags.sorted()
			SortMode.Z_A -> rawTags.sortedDescending()
		}

		sorted.forEach { tagName ->
			val json = buildJsonObject {
				put("Name", tagName.toTitleCase())
				put("OriginalTitle", tagName)
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			tagsAdapter.add(BaseItemDtoBaseRowItem(Json.decodeFromString(json)))
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
		val counts = mutableMapOf<String, Int>()
		rawTags.forEach { tag ->
			try {
				val result = apiClient.itemsApi.getItems(
					GetItemsRequest(
						parentId = folder.id,
						includeItemTypes = setOf(itemType),
						tags = setOf(tag),
						recursive = true,
						limit = 0,
					)
				)
				counts[tag] = result.content.totalRecordCount ?: 0
			} catch (_: Exception) {
				counts[tag] = 0
			}
		}
		tagCounts = counts
	}
}

enum class SortMode(val label: String) {
	RANDOM("Random"),
	A_Z("A–Z"),
	Z_A("Z–A");

	fun next(): SortMode = when (this) {
		RANDOM -> A_Z
		A_Z -> Z_A
		Z_A -> RANDOM
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

/**
 * Produces a shuffled list where high-count and low-count items are interleaved,
 * avoiding screens full of empty or near-empty categories.
 *
 * When counts aren't loaded yet, falls back to a plain shuffle.
 */
internal fun interleavedShuffle(items: List<String>, counts: Map<String, Int>): List<String> {
	if (counts.isEmpty() || items.size < 3) return items.shuffled()

	// Sort by count descending then split into three buckets.
	val sorted = items.sortedByDescending { counts[it] ?: 0 }
	val third = (sorted.size + 2) / 3
	val high = sorted.take(third).shuffled()
	val mid = sorted.drop(third).take(third).shuffled()
	val low = sorted.drop(third * 2).shuffled()

	// Interleave: pick one from high, mid, low in rotation.
	val result = mutableListOf<String>()
	val iters = listOf(high.iterator(), mid.iterator(), low.iterator())
	var i = 0
	while (result.size < items.size) {
		if (iters[i % 3].hasNext()) result.add(iters[i % 3].next())
		i++
	}
	return result
}
