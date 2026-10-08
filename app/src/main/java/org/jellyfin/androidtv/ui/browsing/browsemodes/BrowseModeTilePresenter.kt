package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.leanback.widget.Presenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text

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

/**
 * Draws a browse mode as a wide tile with its name centred.
 *
 * Kept separate from [org.jellyfin.androidtv.ui.presentation.GridButtonPresenter], which sizes
 * itself around an image and leaves a text-only button too short to read as a tile.
 */
class BrowseModeTilePresenter : Presenter() {
	private companion object {
		// Six of these plus leanback's own padding have to fit the width of a 720p TV, which at
		// its usual density is only around 960dp.
		const val TILE_WIDTH = 140
		const val TILE_HEIGHT = 79
		const val ICON_SIZE = 32
		const val LABEL_SIZE = 16

		// The "Browse by…" meta tiles are visibly smaller than the primary row.
		const val META_TILE_WIDTH = 110
		const val META_TILE_HEIGHT = 62
		const val META_ICON_SIZE = 24
		const val META_LABEL_SIZE = 14
	}

	inner class ViewHolder(
		private val composeView: ComposeView,
	) : Presenter.ViewHolder(ComposeViewWrapper(composeView, focusable = true)) {
		fun bind(tile: BrowseModeTile) = composeView.setContent {
			val width = if (tile.small) META_TILE_WIDTH else TILE_WIDTH
			val height = if (tile.small) META_TILE_HEIGHT else TILE_HEIGHT
			val iconSize = if (tile.small) META_ICON_SIZE else ICON_SIZE
			val labelSize = if (tile.small) META_LABEL_SIZE else LABEL_SIZE
			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier
					.size(width.dp, height.dp)
					.clip(RoundedCornerShape(4.dp))
					.background(colorResource(R.color.browse_mode_tile_background))
			) {
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(4.dp),
					modifier = Modifier.padding(horizontal = 8.dp)
				) {
					Image(
						painter = painterResource(tile.definition.icon),
						contentDescription = null,
						colorFilter = tile.definition.iconTint
							?.let { ColorFilter.tint(colorResource(it)) },
						modifier = Modifier.size(iconSize.dp)
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

/** A non-interactive section label between the primary and meta tile groups. */
class BrowseModeHeaderPresenter : Presenter() {
	private companion object {
		// Matches the meta tile height so the "Browse by…" label shares its row without
		// stretching it.
		const val WIDTH = 110
		const val HEIGHT = 62
	}

	inner class ViewHolder(
		private val composeView: ComposeView,
	) : Presenter.ViewHolder(ComposeViewWrapper(composeView, focusable = false)) {
		fun bind(header: BrowseModeHeader) = composeView.setContent {
			Box(
				contentAlignment = Alignment.BottomStart,
				modifier = Modifier
					.size(WIDTH.dp, HEIGHT.dp)
					.padding(horizontal = 4.dp)
			) {
				Text(
					text = header.label,
					color = colorResource(R.color.button_default_normal_text),
					fontSize = 16.sp,
				)
			}
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
		ViewHolder(ComposeView(parent.context))

	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
		if (viewHolder !is ViewHolder || item !is BrowseModeHeader) return

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

/** A section header in the browse modes grid. */
data class BrowseModeHeader(
	val label: String,
)
