package org.jellyfin.androidtv.ui.browsing.browsemodes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.request.ImageRequest
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.browsing.composable.inforow.BaseItemInfoRow
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.composable.item.ItemCard
import org.jellyfin.androidtv.ui.composable.item.RankBadge
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

const val POSTER_COLUMNS = 7

/**
 * A single ranked grid entry: either an in-library item or an external missing-title stub. Both
 * carry their source rank so the two groups can be interleaved into one rank-ordered sequence.
 */
private sealed interface RankedEntry {
	val rank: Int

	data class Item(val item: BaseItemDto, override val rank: Int) : RankedEntry
	data class Stub(val stub: MissingTitleDto, override val rank: Int) : RankedEntry
}

/** A poster grid with a legacy-style detail header, rendered Compose-native. */
@Composable
internal fun PosterGrid(
	title: String,
	sortDescription: String,
	items: List<BaseItemDto>,
	showRankBadge: Boolean,
	emptyMessage: String? = null,
	missing: List<MissingTitleDto> = emptyList(),
	onItemClick: (BaseItemDto) -> Unit,
) {
	val api = koinInject<ApiClient>()
	val imageLoader = koinInject<ImageLoader>()
	val context = LocalContext.current
	val imageHelper = remember(api) { ImageHelper(api) }
	val gridState = rememberLazyGridState()
	var focusedItem by remember { mutableStateOf<BaseItemDto?>(null) }
	val firstItemFocusRequester = remember { FocusRequester() }

	// Interleave in-library items and external missing stubs into a single rank-ordered
	// sequence, so a stub with rank N renders at position N in the grid.
	val entries = remember(items, missing) {
		buildList<RankedEntry> {
			items.forEach { item ->
				add(RankedEntry.Item(item, item.indexNumber?.takeIf { it > 0 } ?: Int.MAX_VALUE))
			}
			missing.forEach { stub ->
				add(RankedEntry.Stub(stub, stub.rank.takeIf { it > 0 } ?: Int.MAX_VALUE))
			}
		}.sortedBy { it.rank }
	}

	// Focus the first card so a single DPAD center press activates it immediately.
	LaunchedEffect(entries.isNotEmpty()) {
		if (entries.isNotEmpty()) firstItemFocusRequester.requestFocus()
	}

	// Prefetch posters ahead of the scroll so images are ready when they come into view.
	LaunchedEffect(gridState) {
		snapshotFlow { gridState.firstVisibleItemIndex }
			.collect { first ->
				val preloadEnd = (first + POSTER_COLUMNS * 4).coerceAtMost(items.size)
				for (i in first until preloadEnd) {
					val url = imageHelper.getPrimaryImageUrl(items[i], 200, 300) ?: continue
					imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
				}
			}
	}

	Column(modifier = Modifier.fillMaxSize()) {
		// Header: big title (library, or the focused item) + detail info row.
		Text(
			text = focusedItem?.name?.orEmpty() ?: title,
			fontSize = 24.sp,
			color = Color.White,
			modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
		)
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(28.dp)
				.padding(horizontal = 16.dp),
		) {
			focusedItem?.let { BaseItemInfoRow(it, it.mediaSources?.firstOrNull(), includeRuntime = true) }
		}

		Box(modifier = Modifier.fillMaxSize()) {
			val firstItemIndex = entries.indexOfFirst { it is RankedEntry.Item }

			LazyVerticalGrid(
				columns = GridCells.Fixed(POSTER_COLUMNS),
				state = gridState,
				modifier = Modifier.fillMaxSize(),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				itemsIndexed(entries) { index, entry ->
					when (entry) {
						is RankedEntry.Item -> PosterCard(
							item = entry.item,
							imageHelper = imageHelper,
							api = api,
							showRankBadge = showRankBadge,
							focusRequester = if (index == firstItemIndex) firstItemFocusRequester else null,
							onFocus = { focusedItem = entry.item },
							onClick = { onItemClick(entry.item) },
						)
						is RankedEntry.Stub -> ComingSoonCard(
							stub = entry.stub,
							focusRequester = null,
						)
					}
				}
			}

			if (entries.isEmpty()) {
				emptyMessage?.let { message ->
					Text(
						text = message,
						fontSize = 18.sp,
						color = Color(0xCCFFFFFF),
						textAlign = TextAlign.Center,
						modifier = Modifier
							.align(Alignment.Center)
							.padding(16.dp),
					)
				}
			}

			if (sortDescription.isNotEmpty()) {
				Text(
					text = sortDescription,
					fontSize = 12.sp,
					color = Color(0xCCFFFFFF),
					modifier = Modifier
						.align(Alignment.BottomStart)
						.padding(16.dp)
						.background(Color(0x99000000))
						.padding(horizontal = 8.dp, vertical = 4.dp),
				)
			}
		}
	}
}

@Composable
private fun PosterCard(
	item: BaseItemDto,
	imageHelper: ImageHelper,
	api: ApiClient,
	showRankBadge: Boolean,
	focusRequester: FocusRequester?,
	onFocus: () -> Unit,
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
			.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.onFocusChanged { focusState ->
				focused = focusState.isFocused
				if (focusState.isFocused) onFocus()
			}
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
				if (showRankBadge) {
					item.indexNumber?.takeIf { it > 0 }?.let { rank ->
						RankBadge(
							rank = rank,
							modifier = Modifier
								.align(Alignment.TopStart)
								.padding(4.dp),
						)
					}
				}
				item.name?.let { name ->
					Box(
						modifier = Modifier
							.align(Alignment.BottomCenter)
							.fillMaxWidth()
							.background(Color(0x99000000))
							.padding(horizontal = 8.dp, vertical = 4.dp),
					) {
						Text(
							text = name,
							fontSize = 12.sp,
							color = Color.White,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							textAlign = TextAlign.Center,
							modifier = Modifier.fillMaxWidth(),
						)
					}
				}
			},
		)
	}
}

/**
 * A tile for an external title that is not in the library: the source poster desaturated and
 * dimmed, a diagonal "Coming soon" ribbon, and the active source's rank badge. Deliberately not
 * clickable or focusable — a missing title has no detail or play navigation.
 */
@Composable
private fun ComingSoonCard(
	stub: MissingTitleDto,
	focusRequester: FocusRequester?,
) {
	var focused by remember { mutableStateOf(false) }
	val colorFilter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

	Box(
		modifier = Modifier
			.aspectRatio(2f / 3f)
			.padding(4.dp)
			.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.onFocusChanged { focused = it.isFocused }
			.then(if (focused) Modifier.border(3.dp, Color.White) else Modifier),
	) {
		ItemCard(
			modifier = Modifier.fillMaxSize(),
			image = {
				AsyncImage(
					url = stub.posterUrl.ifBlank { null },
					aspectRatio = 2f / 3f,
					colorFilter = colorFilter,
					modifier = Modifier.fillMaxSize(),
				)
			},
			overlay = {
				// Dim the desaturated poster further so the tile reads as unavailable.
				Box(
					modifier = Modifier
						.fillMaxSize()
						.background(Color(0x80000000)),
				)

				if (stub.rank > 0) {
					RankBadge(
						rank = stub.rank,
						modifier = Modifier
							.align(Alignment.TopStart)
							.padding(4.dp),
					)
				}

				// A diagonal ribbon across the poster: a strip 119% of the card width, centered and
				// rotated to the 2:3 portrait diagonal angle (~56.31deg), spans ~66% of the full
				// corner-to-corner diagonal. Mirrors the web client's coming-soon banner.
				BoxWithConstraints(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.Center,
				) {
					Box(
						modifier = Modifier
							.requiredWidth(maxWidth * 1.19f)
							.rotate(56.31f)
							.background(Color(0xD9000000))
							.padding(horizontal = 8.dp, vertical = 4.dp),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = stringResource(R.string.coming_soon),
							fontSize = 11.sp,
							color = Color.White,
							textAlign = TextAlign.Center,
						)
					}
				}

				stub.title.ifBlank { null }?.let { title ->
					Box(
						modifier = Modifier
							.align(Alignment.BottomCenter)
							.fillMaxWidth()
							.background(Color(0x99000000))
							.padding(horizontal = 8.dp, vertical = 4.dp),
					) {
						Text(
							text = title,
							fontSize = 12.sp,
							color = Color.White,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							textAlign = TextAlign.Center,
							modifier = Modifier.fillMaxWidth(),
						)
					}
				}
			},
		)
	}
}
