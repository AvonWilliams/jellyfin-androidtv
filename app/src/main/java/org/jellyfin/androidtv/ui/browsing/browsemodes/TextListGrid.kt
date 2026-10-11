package org.jellyfin.androidtv.ui.browsing.browsemodes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.sdk.model.api.BaseItemDto

const val TEXT_LIST_COLUMNS = 3

/**
 * A list of selectable text rows (no pictures), shown as a narrow grid.
 * Used for pickers where each choice is just a label.
 *
 * Item counts are kept separate from the items so their arrival does not rebuild the list
 * (which would drop focus and require a second click).
 */
@Composable
internal fun TextListGrid(
	title: String,
	items: List<BaseItemDto>,
	counts: Map<String, Int> = emptyMap(),
	iconFor: ((BaseItemDto) -> Int?)? = null,
	iconTint: Color = Color.Unspecified,
	iconTintFor: ((BaseItemDto) -> Color)? = null,
	sortMode: SortMode? = null,
	onSortChange: ((SortMode) -> Unit)? = null,
	labelFontSize: TextUnit = 16.sp,
	labelFontWeight: FontWeight? = null,
	onItemClick: (BaseItemDto) -> Unit,
) {
	val gridState = rememberLazyGridState()
	val firstItemFocusRequester = remember { FocusRequester() }

	LaunchedEffect(items.isNotEmpty()) {
		if (items.isNotEmpty()) firstItemFocusRequester.requestFocus()
	}

	Column(modifier = Modifier.fillMaxSize()) {
		Text(
			text = title,
			fontSize = 24.sp,
			color = Color.White,
			modifier = Modifier.padding(16.dp),
		)

		if (sortMode != null && onSortChange != null) {
			SortPillRow(sortMode, onSortChange)
		}

		LazyVerticalGrid(
			columns = GridCells.Fixed(TEXT_LIST_COLUMNS),
			state = gridState,
			modifier = Modifier.fillMaxSize(),
		) {
			itemsIndexed(items, key = { _, item -> item.originalTitle ?: item.name ?: item.id.toString() }) { index, item ->
				val key = item.originalTitle ?: item.name.orEmpty()
				val count = counts[key]
				val label = if (count != null) "${item.name.orEmpty()} ($count)" else item.name.orEmpty()
				TextRow(
					label,
					icon = iconFor?.invoke(item),
					iconTint = iconTintFor?.invoke(item) ?: iconTint,
					focusRequester = if (index == 0) firstItemFocusRequester else null,
					labelFontSize = labelFontSize,
					labelFontWeight = labelFontWeight,
					onClick = { onItemClick(item) },
				)
			}
		}
	}
}

/** A row of sort pills (A–Z / Z–A / Most / Fewest) shown above a picker's list. */
@Composable
private fun SortPillRow(
	current: SortMode,
	onSelect: (SortMode) -> Unit,
) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 4.dp),
	) {
		SortMode.PILLS.forEach { mode ->
			val active = mode == current
			var focused by remember(mode) { mutableStateOf(false) }

			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier
					.clip(RoundedCornerShape(999.dp))
					.background(if (active || focused) Color(0x33FFFFFF) else Color.Transparent)
					.border(1.dp, if (active) Color.White else BrowseGlass.border, RoundedCornerShape(999.dp))
					.onFocusChanged { focused = it.isFocused }
					.clickable { onSelect(mode) }
					.padding(horizontal = 14.dp, vertical = 6.dp),
			) {
				Text(
					text = mode.label,
					fontSize = 14.sp,
					color = if (active) Color.White else Color.White.copy(alpha = 0.7f),
				)
			}
		}
	}
}

@Composable
private fun TextRow(
	label: String,
	icon: Int?,
	iconTint: Color,
	focusRequester: FocusRequester?,
	labelFontSize: TextUnit,
	labelFontWeight: FontWeight?,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(4.dp)
			.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.onFocusChanged { focused = it.isFocused }
			.clickable(onClick = onClick)
			.background(if (focused) Color(0x33FFFFFF) else Color.Transparent),
		contentAlignment = Alignment.Center,
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
		) {
			if (icon != null) {
				Image(
					painter = painterResource(icon),
					contentDescription = null,
					colorFilter = ColorFilter.tint(iconTint),
					modifier = Modifier.size(24.dp),
				)
				Spacer(modifier = Modifier.width(8.dp))
			}

			Text(
				text = label,
				fontSize = labelFontSize,
				fontWeight = labelFontWeight,
				color = Color.White,
				textAlign = TextAlign.Center,
			)
		}
	}
}
