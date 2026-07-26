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
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * The items of a Discover list, in the order the server ranked them.
 *
 * These lists come from a custom server addition rather than the generated SDK, so they are
 * fetched as a raw request. The server matches TMDB's ranking against what the library owns, so
 * the result is short and already ordered — no paging.
 */
class DiscoverFragment : VerticalGridSupportFragment() {
	private companion object {
		const val COLUMNS = 6
		const val CARD_HEIGHT = 260
		const val LIMIT = 60
	}

	private val apiClient by inject<ApiClient>()
	private val itemLauncher by inject<ItemLauncher>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var itemsAdapter: MutableObjectAdapter<Any>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		val mode = BrowseMode.entries.first { it.key == requireArguments().getString(Extras.BrowseMode) }

		val label = getBrowseModes(folder.collectionType)?.first { it.mode == mode }?.label
		title = label?.let { "${folder.name} - ${getString(it)}" } ?: folder.name

		setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = COLUMNS })

		itemsAdapter = MutableObjectAdapter(DiscoverCardPresenter(CARD_HEIGHT))
		adapter = itemsAdapter

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			if (item is BaseRowItem) itemLauncher.launch(item, itemsAdapter, requireContext())
		}

		load(mode)
	}

	private fun load(mode: BrowseMode) = lifecycleScope.launch {
		val path = discoverPath(mode, folder.collectionType)
		val items = try {
			withContext(Dispatchers.IO) {
				apiClient.get<BaseItemDtoQueryResult>(
					pathTemplate = path,
					queryParameters = mapOf(
						"userId" to userRepository.currentUser.value?.id,
						"parentId" to folder.id,
						"fields" to ItemRepository.itemFields.joinToString(",") { it.serialName },
						"limit" to LIMIT,
					),
				).content.items
			}
		} catch (error: Exception) {
			Timber.e(error, "Unable to load discover list %s", path)
			emptyList()
		}

		if (!isAdded) return@launch

		items.forEach { itemsAdapter.add(BaseItemDtoBaseRowItem(it)) }

		if (items.isEmpty()) {
			title = getString(
				when (mode) {
					BrowseMode.TRENDING -> R.string.lbl_no_trending_items
					else -> R.string.lbl_no_top_rated_items
				}
			)
		}
	}

	private fun discoverPath(mode: BrowseMode, collectionType: CollectionType?): String {
		val kind = if (collectionType == CollectionType.TVSHOWS) "Shows" else "Movies"
		val list = if (mode == BrowseMode.TRENDING) "Trending" else "TopRated"

		return "/Discover/$list/$kind"
	}
}
