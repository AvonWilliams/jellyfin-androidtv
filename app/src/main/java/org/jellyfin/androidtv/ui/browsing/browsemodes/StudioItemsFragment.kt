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
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
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

/** The items a single studio is credited on, within one library, as a Compose poster grid. */
class StudioItemsFragment : Fragment() {
	private val apiClient by inject<ApiClient>()
	private val itemLauncher by inject<ItemLauncher>()

	private lateinit var folder: BaseItemDto
	private val title = mutableStateOf("")
	private val items = mutableStateOf<List<BaseItemDto>>(emptyList())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		val studio = requireArguments().getString(Extras.Studio).orEmpty()

		title.value = studio
		load(studio)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setContent {
			JellyfinTheme {
				PosterGrid(title.value, items.value, showRankBadge = false) { item -> launch(item) }
			}
		}
	}

	private fun launch(item: BaseItemDto) {
		itemLauncher.launch(BaseItemDtoBaseRowItem(item), MutableObjectAdapter<Any>(), requireContext())
	}

	private fun load(studio: String) = lifecycleScope.launch {
		val itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		val result = try {
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

		items.value = result
	}
}
