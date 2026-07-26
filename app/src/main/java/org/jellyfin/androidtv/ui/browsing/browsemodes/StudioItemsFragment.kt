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
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

/** The items a single studio is credited on, within one library. */
class StudioItemsFragment : VerticalGridSupportFragment() {
	private companion object {
		const val COLUMNS = 6
		const val CARD_HEIGHT = 260
	}

	private val apiClient by inject<ApiClient>()
	private val itemLauncher by inject<ItemLauncher>()

	private lateinit var folder: BaseItemDto
	private lateinit var itemsAdapter: MutableObjectAdapter<Any>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		val studio = requireArguments().getString(Extras.Studio).orEmpty()

		title = studio

		setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = COLUMNS })

		itemsAdapter = MutableObjectAdapter(CardPresenter(true, CARD_HEIGHT))
		adapter = itemsAdapter

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			if (item is BaseRowItem) itemLauncher.launch(item, itemsAdapter, requireContext())
		}

		load(studio)
	}

	private fun load(studio: String) = lifecycleScope.launch {
		val itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		val items = try {
			withContext(Dispatchers.IO) {
				apiClient.itemsApi.getItems(
					GetItemsRequest(
						parentId = folder.id,
						includeItemTypes = setOf(itemType),
						studios = setOf(studio),
						sortBy = setOf(ItemSortBy.SORT_NAME),
						recursive = true,
						fields = ItemRepository.itemFields,
					)
				).content.items
			}
		} catch (error: Exception) {
			Timber.e(error, "Unable to load items for studio %s", studio)
			emptyList()
		}

		if (!isAdded) return@launch

		items.forEach { itemsAdapter.add(BaseItemDtoBaseRowItem(it)) }
	}
}
