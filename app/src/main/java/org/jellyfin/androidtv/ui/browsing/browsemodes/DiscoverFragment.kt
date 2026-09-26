package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.composable.item.ItemCard
import org.jellyfin.androidtv.ui.composable.item.RankBadge
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import timber.log.Timber

/**
 * Proof of concept: the items of a Discover list rendered as a Compose [LazyVerticalGrid]
 * instead of a Leanback [androidx.leanback.app.VerticalGridSupportFragment].
 *
 * These lists come from a custom server addition rather than the generated SDK, so they are
 * fetched as a raw request. The server matches TMDB's ranking against what the library owns, so
 * the result is short and already ordered — no paging.
 */
class DiscoverFragment : Fragment() {
	companion object {
		const val COLUMNS = 7
		const val LIMIT = 500
	}

	private val apiClient by inject<ApiClient>()
	private val itemLauncher by inject<ItemLauncher>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var mode: BrowseMode
	private val title = mutableStateOf("")
	private val items = mutableStateOf<List<BaseItemDto>>(emptyList())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		mode = BrowseMode.entries.first { it.key == requireArguments().getString(Extras.BrowseMode) }

		val label = getBrowseModes(folder.collectionType)?.first { it.mode == mode }?.label
		title.value = label?.let { "${folder.name} - ${getString(it)}" } ?: folder.name.orEmpty()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setContent {
			JellyfinTheme {
				DiscoverGrid(title.value, items.value) { item -> launch(item) }
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		load()
	}

	private fun launch(item: BaseItemDto) {
		itemLauncher.launch(
			BaseItemDtoBaseRowItem(item, staticHeight = true),
			MutableObjectAdapter<Any>(),
			requireContext(),
		)
	}

	private fun load() = lifecycleScope.launch {
		val path = discoverPath(mode, folder.collectionType)
		val result = try {
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

		items.value = result
	}

	private fun discoverPath(mode: BrowseMode, collectionType: CollectionType?): String {
		val kind = if (collectionType == CollectionType.TVSHOWS) "Shows" else "Movies"
		val list = if (mode == BrowseMode.TRENDING) "Trending" else "TopRated"

		return "/Discover/$list/$kind"
	}
}

@Composable
private fun DiscoverGrid(title: String, items: List<BaseItemDto>, onItemClick: (BaseItemDto) -> Unit) {
	val api = koinInject<ApiClient>()
	val imageHelper = remember(api) { ImageHelper(api) }
	val gridState = rememberLazyGridState()

	Column(modifier = Modifier.fillMaxSize()) {
		Text(
			text = title,
			fontSize = 24.sp,
			color = Color.White,
			modifier = Modifier.padding(16.dp),
		)

		LazyVerticalGrid(
			columns = GridCells.Fixed(DiscoverFragment.COLUMNS),
			state = gridState,
			modifier = Modifier.fillMaxSize(),
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			items(items) { item ->
				DiscoverCard(item, imageHelper, api) { onItemClick(item) }
			}
		}
	}
}

@Composable
private fun DiscoverCard(
	item: BaseItemDto,
	imageHelper: ImageHelper,
	api: ApiClient,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }
	val url = remember(item) { imageHelper.getPrimaryImageUrl(item, width = 200, height = 300) }
	val blurHash = item.itemImages[ImageType.PRIMARY]?.blurHash
	val aspectRatio = item.primaryImageAspectRatio?.toFloat() ?: (2f / 3f)

	Box(
		modifier = Modifier
			.aspectRatio(2f / 3f)
			.padding(4.dp)
			.onFocusChanged { focused = it.isFocused }
			.focusable()
			.clickable(onClick = onClick)
			.then(if (focused) Modifier.border(3.dp, Color.White) else Modifier),
	) {
		ItemCard(
			modifier = Modifier.fillMaxSize(),
			image = {
				AsyncImage(
					url = url,
					blurHash = blurHash,
					aspectRatio = aspectRatio,
					modifier = Modifier.fillMaxSize(),
				)
			},
			overlay = {
				item.indexNumber?.takeIf { it > 0 }?.let { rank ->
					RankBadge(
						rank = rank,
						modifier = Modifier
							.align(Alignment.TopStart)
							.padding(4.dp),
					)
				}
			},
		)
	}
}
