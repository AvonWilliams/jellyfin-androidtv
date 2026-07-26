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
 * Draws a browse mode as a wide tile with its name centred.
 *
 * Kept separate from [org.jellyfin.androidtv.ui.presentation.GridButtonPresenter], which sizes
 * itself around an image and leaves a text-only button too short to read as a tile.
 */
class BrowseModeTilePresenter : Presenter() {
	private companion object {
		// Four of these plus leanback's own padding have to fit the width of a 720p TV, which at
		// its usual density is only around 960dp — 220dp wide overflowed the last column.
		const val TILE_WIDTH = 200
		const val TILE_HEIGHT = 112
		const val ICON_SIZE = 32
	}

	private class ComposeViewWrapper(composeView: ComposeView) : FrameLayout(composeView.context) {
		init {
			isFocusable = true
			isFocusableInTouchMode = true
			descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
			addView(composeView)
		}

		// Hack to prevent Compose crash with leanback presenters
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			if (isAttachedToWindow) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			else setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
		}
	}

	inner class ViewHolder(
		private val composeView: ComposeView,
	) : Presenter.ViewHolder(ComposeViewWrapper(composeView)) {
		fun bind(tile: BrowseModeTile) = composeView.setContent {
			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier
					.size(TILE_WIDTH.dp, TILE_HEIGHT.dp)
					.clip(RoundedCornerShape(4.dp))
					.background(colorResource(R.color.button_default_normal_background))
			) {
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(6.dp),
					modifier = Modifier.padding(horizontal = 12.dp)
				) {
					Image(
						painter = painterResource(tile.definition.icon),
						contentDescription = null,
						colorFilter = tile.definition.iconTint
							?.let { ColorFilter.tint(colorResource(it)) },
						modifier = Modifier.size(ICON_SIZE.dp)
					)

					Text(
						text = tile.label,
						color = colorResource(R.color.button_default_normal_text),
						fontSize = 16.sp,
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

/** A single tile in the browse modes grid. */
data class BrowseModeTile(
	val definition: BrowseModeDefinition,
	val label: String,
)
