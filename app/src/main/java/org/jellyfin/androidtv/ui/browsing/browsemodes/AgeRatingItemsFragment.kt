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
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * The items matching a single official content rating (e.g. "PG-13", "TV-MA"),
 * shown as a poster grid.
 *
 * Rating selection from [AgeRatingPickerFragment] navigates here. The items are retrieved
 * from the standard GET /Items endpoint with an officialRatings filter.
 *
 * Pattern follows [TagItemsFragment].
 */
class AgeRatingItemsFragment : VerticalGridSupportFragment() {
	private companion object {
		const val COLUMNS = 6
		const val CARD_HEIGHT = 260
		const val LIMIT = 200
	}

	private val apiClient by inject<ApiClient>()
	private val itemLauncher by inject<ItemLauncher>()

	private lateinit var folder: BaseItemDto
	private lateinit var rating: String
	private lateinit var itemType: BaseItemKind
	private lateinit var itemsAdapter: MutableObjectAdapter<Any>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		rating = requireArguments().getString(Extras.Tag)!!
		itemType = BaseItemKind.fromNameOrNull(requireArguments().getString(Extras.IncludeType)!!)
			?: BaseItemKind.MOVIE

		title = "${folder.name} - $rating"

		setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = COLUMNS })

		itemsAdapter = MutableObjectAdapter(CardPresenter(false, CARD_HEIGHT))
		adapter = itemsAdapter

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			if (item is BaseRowItem) itemLauncher.launch(item, itemsAdapter, requireContext())
		}

		load()
	}

	private fun load() = lifecycleScope.launch {
		val items = try {
			withContext(Dispatchers.IO) {
				apiClient.itemsApi.getItems(
					GetItemsRequest(
						parentId = folder.id,
						includeItemTypes = setOf(itemType),
						officialRatings = setOf(rating),
						sortBy = setOf(ItemSortBy.RANDOM),
						sortOrder = setOf(SortOrder.ASCENDING),
						recursive = true,
						fields = ItemRepository.itemFields,
						limit = LIMIT,
					)
				).content.items
			}
		} catch (error: Exception) {
			Timber.e(error, "Unable to load age rating items for %s / %s", folder.name, rating)
			emptyList()
		}

		if (!isAdded) return@launch

		items.orEmpty().forEach { itemsAdapter.add(BaseItemDtoBaseRowItem(it)) }
	}
}
