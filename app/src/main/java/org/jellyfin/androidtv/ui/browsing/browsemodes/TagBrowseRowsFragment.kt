package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.QueryFiltersLegacy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

class TagBrowseRowsFragment : RowsSupportFragment() {
	private companion object {
		const val MAX_ROWS = 30
		const val CHUNK_SIZE = 50
		const val CARD_HEIGHT = 260
	}

	private val apiClient by inject<ApiClient>()
	private val userRepository by inject<UserRepository>()
	private val itemLauncher by inject<ItemLauncher>()

	private lateinit var folder: BaseItemDto
	private lateinit var mode: BrowseMode
	private lateinit var itemType: BaseItemKind
	private lateinit var rowsAdapter: MutableObjectAdapter<Row>
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
		requireActivity().title = label?.let { getString(it) } ?: mode.key

		rowsAdapter = MutableObjectAdapter(PositionableListRowPresenter())
		adapter = rowsAdapter

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			val baseItem = (item as? BaseItemDtoBaseRowItem)?.baseItem
		if (baseItem?.originalTitle == "__sort__") {
				sortMode = sortMode.next()
				refreshRows()
			} else if (baseItem?.originalTitle == "__reshuffle__") {
				refreshRows()
			} else if (item is BaseRowItem) {
				itemLauncher.launch(item, null, requireContext())
			}
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
		refreshRows()
		launch { withContext(Dispatchers.IO) { fetchTagCounts() } }
	}

	private fun refreshRows() {
		rowsAdapter.clear()

		// Sort toggle row
		val sortJson = buildJsonObject {
			put("Name", "Sort: ${sortMode.label}")
			put("OriginalTitle", "__sort__")
			put("Id", java.util.UUID.randomUUID().toString())
			put("Type", "Folder")
		}.toString()
		val sortItem = BaseItemDtoBaseRowItem(Json.decodeFromString<BaseItemDto>(sortJson))
		// Sort + optional reshuffle row
		val sortRowAdapter = ArrayObjectAdapter(object : Presenter() {
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
		})
		sortRowAdapter.add(sortItem)
		if (sortMode == SortMode.RANDOM) {
			val shuffleJson = buildJsonObject {
				put("Name", " ↻ Reshuffle")
				put("OriginalTitle", "__reshuffle__")
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			sortRowAdapter.add(BaseItemDtoBaseRowItem(Json.decodeFromString<BaseItemDto>(shuffleJson)))
		}
		rowsAdapter.add(ListRow(HeaderItem(""), sortRowAdapter))

		val sorted = when (sortMode) {
			SortMode.RANDOM -> interleavedShuffle(rawTags, tagCounts)
			SortMode.A_Z -> rawTags.sorted()
			SortMode.Z_A -> rawTags.sortedDescending()
		}

		val cardPresenter = CardPresenter(false, CARD_HEIGHT)

		sorted.take(MAX_ROWS).forEach { tag ->
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

			val rowAdapter = ItemRowAdapter(
				requireContext(),
				query,
				CHUNK_SIZE,
				false,
				false,
				cardPresenter as Presenter,
				rowsAdapter
			)
			rowAdapter.Retrieve()

			val row = ListRow(HeaderItem(tag.toTitleCase()), rowAdapter)
			rowsAdapter.add(row)
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
