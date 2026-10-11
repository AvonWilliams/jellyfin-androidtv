package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

/** Backdrop fanart width cap, matching the web's 2560px `getScaledImageUrl` request. */
private const val BACKDROP_MAX_WIDTH = 2560

/** Radial frost mask: mostly clear at the centre, opaque toward the edges (web `mask-image`). */
private val FROST_MASK = Brush.radialGradient(
	0.0f to Color.Black.copy(alpha = 0.2f),
	0.32f to Color.Black.copy(alpha = 0.2f),
	0.82f to Color.Black,
)

/** Light scrim fading toward the bottom, keeping the frosted zone dim enough for tiles. */
private val SCRIM_BRUSH = Brush.verticalGradient(
	0.0f to BrowseGlass.base.copy(alpha = 0.55f),
	0.45f to BrowseGlass.base.copy(alpha = 0.2f),
	0.8f to Color.Transparent,
)

/**
 * Full-viewport frosted library backdrop (backdrop fanart falling back to the primary image, else
 * a scrim only). Non-focusable and non-clickable, drawn behind the browse grid. Mirrors the web's
 * `LibraryBackdrop`: a sharp base image under a blurred + saturated copy masked radially so the
 * centre stays clear and the edges frost.
 */
@Composable
internal fun LibraryBackdrop(folder: BaseItemDto) {
	val api = koinInject<ApiClient>()
	val backdropUrl = remember(folder) {
		folder.itemBackdropImages.firstOrNull()?.getUrl(api, maxWidth = BACKDROP_MAX_WIDTH)
			?: folder.itemImages[ImageType.PRIMARY]?.getUrl(api, maxWidth = BACKDROP_MAX_WIDTH)
	}
	val saturation = remember {
		ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.3f) })
	}

	Box(modifier = Modifier.fillMaxSize()) {
		if (backdropUrl != null) {
			// Sharp base image, filling the whole surface.
			AsyncImage(
				url = backdropUrl,
				scaleType = ImageView.ScaleType.CENTER_CROP,
				modifier = Modifier.fillMaxSize(),
			)

			// Blurred + saturated copy, masked radially so only the edges frost.
			AsyncImage(
				url = backdropUrl,
				scaleType = ImageView.ScaleType.CENTER_CROP,
				colorFilter = saturation,
				modifier = Modifier
					.fillMaxSize()
					.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
					.drawWithContent {
						drawContent()
						drawRect(brush = FROST_MASK, blendMode = BlendMode.DstIn)
					}
					.blur(20.dp),
			)
		}

		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(SCRIM_BRUSH),
		)
	}
}
