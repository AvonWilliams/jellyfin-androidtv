package org.jellyfin.androidtv.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.item.ItemRowCard
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.GridButtonBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(FlowPreview::class)
@Composable
fun HomeScreen(
	modifier: Modifier = Modifier,
	viewModel: HomeViewModel = koinViewModel(),
) {
	val rows by viewModel.rows.collectAsState()
	val api = koinInject<ApiClient>()
	val itemLauncher = koinInject<ItemLauncher>()
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current

	// Refresh rows when returning to the home screen (after playback/detail).
	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

	// Refresh on server events while the home screen is visible.
	LaunchedEffect(lifecycleOwner) {
		lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
			launch {
				api.webSocket.subscribe<UserDataChangedMessage>()
					.debounce(1500L)
					.collect { viewModel.refresh(force = true) }
			}
			launch {
				api.webSocket.subscribe<LibraryChangedMessage>()
					.debounce(1500L)
					.collect { viewModel.refresh(force = false) }
			}
		}
	}

	val firstItemFocusRequester = remember { FocusRequester() }
	LaunchedEffect(rows.isNotEmpty()) {
		if (rows.isNotEmpty()) firstItemFocusRequester.requestFocus()
	}

	val onItemClick: (List<BaseRowItem>, BaseRowItem) -> Unit = { rowItems, item ->
		val adapter = MutableObjectAdapter<Any>().apply { rowItems.forEach { add(it) } }
		itemLauncher.launch(item, adapter, context)
	}

	LazyColumn(modifier = modifier) {
		itemsIndexed(rows, key = { _, row -> row.key }) { rowIndex, row ->
			HomeSectionRow(
				row = row,
				firstItemFocusRequester = if (rowIndex == 0) firstItemFocusRequester else null,
				onItemClick = { item -> onItemClick(row.items, item) },
			)
		}
	}
}

@Composable
private fun HomeSectionRow(
	row: HomeViewModel.HomeRow,
	firstItemFocusRequester: FocusRequester?,
	onItemClick: (BaseRowItem) -> Unit,
) {
	Column {
		Text(
			text = row.title,
			fontSize = 20.sp,
			color = Color.White,
			modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
		)

		LazyRow(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
		) {
			itemsIndexed(row.items, key = { _, item -> item.itemId?.toString() ?: item.toString() }) { index, item ->
				val focusRequester = if (firstItemFocusRequester != null && index == 0) firstItemFocusRequester else null

				when (item) {
					is GridButtonBaseRowItem -> GridButtonCard(
						button = item.gridButton,
						focusRequester = focusRequester,
						onClick = { onItemClick(item) },
					)

					else -> HomeCard(
						item = item,
						cardHeight = row.cardHeight,
						focusRequester = focusRequester,
						onClick = { onItemClick(item) },
					)
				}
			}
		}
	}
}

@Composable
private fun HomeCard(
	item: BaseRowItem,
	cardHeight: Int,
	focusRequester: FocusRequester?,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if (focused) 1.14f else 1f, label = "homeCardScale")

	Box(
		modifier = Modifier
			.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.onFocusChanged { focused = it.isFocused }
			.clickable(onClick = onClick)
			.zIndex(if (focused) 1f else 0f)
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			},
	) {
		ItemRowCard(
			item = item,
			focused = focused,
			showInfo = true,
			imageType = ImageType.POSTER,
			staticHeight = cardHeight,
			uniformAspect = false,
			showRankBadge = false,
		)
	}
}

@Composable
private fun GridButtonCard(
	button: GridButton,
	focusRequester: FocusRequester?,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier
			.width(110.dp)
			.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.onFocusChanged { focused = it.isFocused }
			.clickable(onClick = onClick)
			.clip(RoundedCornerShape(4.dp))
			.background(colorResource(R.color.button_default_normal_background))
			.then(if (focused) Modifier.border(3.dp, Color.White) else Modifier),
	) {
		if (button.imageRes != null) {
			Image(
				painter = painterResource(button.imageRes),
				contentDescription = button.text,
				contentScale = ContentScale.Crop,
				modifier = Modifier.size(110.dp, 110.dp),
			)
		}

		Text(
			text = button.text,
			style = TextStyle(
				color = colorResource(R.color.button_default_normal_text),
				fontSize = 12.sp,
			),
			modifier = Modifier
				.padding(15.dp, 10.dp)
				.align(Alignment.BottomStart),
		)
	}
}
