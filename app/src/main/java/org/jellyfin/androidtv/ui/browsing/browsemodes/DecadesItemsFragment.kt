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
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * The items from a single decade, shown as a Compose poster grid.
 *
 * Decade selection from [DecadesPickerFragment] navigates here. The items are retrieved from
 * the standard GET /Items endpoint with a years filter covering the full decade.
 */
class DecadesItemsFragment : Fragment() {
	private companion object {
		const val LIMIT = 200
	}

	private val apiClient by inject<ApiClient>()
	private val itemLauncher by inject<ItemLauncher>()

	private lateinit var folder: BaseItemDto
	private lateinit var itemType: BaseItemKind
	private var decadeStart: Int = 0
	private val title = mutableStateOf("")
	private val items = mutableStateOf<List<BaseItemDto>>(emptyList())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		decadeStart = requireArguments().getString(Extras.Tag)!!.toInt()
		itemType = BaseItemKind.fromNameOrNull(requireArguments().getString(Extras.IncludeType)!!)
			?: BaseItemKind.MOVIE

		title.value = "${folder.name} - ${decadeStart}s"
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setContent {
			JellyfinTheme {
				PosterGrid(title.value, "Sort by: Premiere date", items.value, showRankBadge = false) { item -> launch(item) }
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		load()
	}

	private fun launch(item: BaseItemDto) {
		itemLauncher.launch(BaseItemDtoBaseRowItem(item), MutableObjectAdapter<Any>(), requireContext())
	}

	private fun load() = lifecycleScope.launch {
		val years = (decadeStart..decadeStart + 9).toSet()

		val result = try {
			withContext(Dispatchers.IO) {
				apiClient.itemsApi.getItems(
					GetItemsRequest(
						parentId = folder.id,
						includeItemTypes = setOf(itemType),
						years = years,
						sortBy = setOf(ItemSortBy.PREMIERE_DATE),
						sortOrder = setOf(SortOrder.DESCENDING),
						recursive = true,
						fields = ItemRepository.itemFields,
						limit = LIMIT,
					)
				).content.items
			}
		} catch (error: Exception) {
			Timber.e(error, "Unable to load decade items for %s / %ds", folder.name, decadeStart)
			emptyList()
		}

		if (!isAdded) return@launch

		items.value = result.orEmpty()
	}
}
