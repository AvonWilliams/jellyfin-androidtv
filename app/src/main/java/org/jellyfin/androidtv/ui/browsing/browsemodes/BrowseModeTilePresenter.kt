package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.leanback.widget.Presenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text

// Six of these plus leanback's own padding have to fit the width of a 720p TV, which at
// its usual density is only around 960dp.
private const val TILE_WIDTH = 140
private const val TILE_HEIGHT = 79
private const val ICON_SIZE = 48
private const val LABEL_SIZE = 20

// The "Browse by…" meta tiles are visibly smaller than the primary row.
private const val META_TILE_WIDTH = 110
private const val META_TILE_HEIGHT = 62
private const val META_ICON_SIZE = 38
private const val META_LABEL_SIZE = 16

/** Shared glass surface colours, matching the web's `--jf-palette-background-paperChannel`
 *  (#202020) and divider border (`rgba(255, 255, 255, 0.12)`). */
internal object BrowseGlass {
	val base = Color(0xFF202020)
	val border = Color(0x1FFFFFFF)

	fun surface(alpha: Float) = base.copy(alpha = alpha)
}

private fun tileRadius(small: Boolean): Dp = if (small) 8.dp else 10.dp

/**
 * Wraps a ComposeView so it can be measured by a leanback grid presenter without crashing.
 * Presenters that host Compose content need this to survive [androidx.leanback.widget.Presenter]
 * view recycling.
 */
private class ComposeViewWrapper(
	composeView: ComposeView,
	focusable: Boolean,
) : FrameLayout(composeView.context) {
	init {
		isFocusable = focusable
		isFocusableInTouchMode = focusable
		descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
		addView(composeView)
	}

	// Hack to prevent Compose crash with leanback presenters
	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (isAttachedToWindow) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		else setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
	}
}

/** The visual content of a browse-mode tile: rounded background, icon and centred label. */
@Composable
internal fun BrowseModeTileContent(
	tile: BrowseModeTile,
	modifier: Modifier = Modifier,
) {
	val width = if (tile.small) META_TILE_WIDTH else TILE_WIDTH
	val height = if (tile.small) META_TILE_HEIGHT else TILE_HEIGHT
	val iconSize = if (tile.small) META_ICON_SIZE else ICON_SIZE
	val labelSize = if (tile.small) META_LABEL_SIZE else LABEL_SIZE
	val radius = tileRadius(tile.small)

	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier
			.size(width.dp, height.dp)
			.clip(RoundedCornerShape(radius))
			.background(BrowseGlass.surface(if (tile.small) 0.70f else 0.60f))
			.border(1.dp, BrowseGlass.border, RoundedCornerShape(radius)),
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(4.dp),
			modifier = Modifier.padding(horizontal = 8.dp),
		) {
			Image(
				painter = painterResource(tile.definition.icon),
				contentDescription = null,
				colorFilter = tile.definition.iconTint
					?.let { ColorFilter.tint(colorResource(it)) },
				modifier = Modifier.size(iconSize.dp),
			)

			Text(
				text = tile.label,
				color = colorResource(R.color.button_default_normal_text),
				fontSize = labelSize.sp,
				textAlign = TextAlign.Center,
			)
		}
	}
}

/** A focusable, clickable browse-mode tile for Compose grids, centred within its grid cell. */
@Composable
internal fun BrowseModeTileCard(
	tile: BrowseModeTile,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	focusRequester: FocusRequester? = null,
) {
	var focused by remember { mutableStateOf(false) }

	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier
			.fillMaxWidth()
			.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.onFocusChanged { focused = it.isFocused }
			.clickable(onClick = onClick),
	) {
		BrowseModeTileContent(
			tile = tile,
			modifier = if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(tileRadius(tile.small))) else Modifier,
		)
	}
}

/**
 * Draws a browse mode as a wide tile with its name centred.
 *
 * Kept separate from [org.jellyfin.androidtv.ui.presentation.GridButtonPresenter], which sizes
 * itself around an image and leaves a text-only button too short to read as a tile.
 */
class BrowseModeTilePresenter : Presenter() {
	inner class ViewHolder(
		private val composeView: ComposeView,
	) : Presenter.ViewHolder(ComposeViewWrapper(composeView, focusable = true)) {
		fun bind(tile: BrowseModeTile) = composeView.setContent {
			BrowseModeTileContent(tile)
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
		ViewHolder(ComposeView(parent.context))

	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
		if (viewHolder !is ViewHolder || item !is BrowseModeTile) return

		viewHolder.bind(item)
	}

	override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) = Unit
	override fun onViewAttachedToWindow(viewHolder: Presenter.ViewHolder) = Unit
}

/** A single tile in the browse modes grid. */
data class BrowseModeTile(
	val definition: BrowseModeDefinition,
	val label: String,
	/** Renders at the smaller meta-tile size. */
	val small: Boolean = false,
)
