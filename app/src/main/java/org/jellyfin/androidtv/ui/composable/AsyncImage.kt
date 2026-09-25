package org.jellyfin.androidtv.ui.composable

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.compose.rememberAsyncImagePainter
import org.jellyfin.androidtv.util.BlurHashDecoder
import org.koin.compose.koinInject
import kotlin.math.round

private fun ImageView.ScaleType.toContentScale(): ContentScale = when (this) {
	ImageView.ScaleType.CENTER_CROP -> ContentScale.Crop
	ImageView.ScaleType.FIT_XY -> ContentScale.FillBounds
	ImageView.ScaleType.CENTER -> ContentScale.None
	ImageView.ScaleType.CENTER_INSIDE -> ContentScale.Inside
	else -> ContentScale.Fit
}

@Composable
fun AsyncImage(
	modifier: Modifier = Modifier,
	url: String? = null,
	blurHash: String? = null,
	placeholder: Drawable? = null,
	aspectRatio: Float = 1f,
	blurHashResolution: Int = 32,
	scaleType: ImageView.ScaleType? = null,
) {
	val imageLoader = koinInject<ImageLoader>()
	val contentScale = scaleType?.toContentScale() ?: ContentScale.Fit

	val placeholderPainter = placeholder?.let { drawable ->
		remember(drawable) { BitmapPainter(drawable.toBitmap().asImageBitmap()) }
	}

	val blurHashPlaceholder = blurHash?.let { hash ->
		val width = if (aspectRatio > 1) round(blurHashResolution * aspectRatio).toInt() else blurHashResolution
		val height = if (aspectRatio >= 1) blurHashResolution else round(blurHashResolution / aspectRatio).toInt()
		remember(hash, width, height) {
			BlurHashDecoder.decode(hash, width, height)?.asImageBitmap()?.let(::BitmapPainter)
		}
	}

	if (url == null) {
		placeholderPainter?.let {
			Image(
				painter = it,
				contentDescription = null,
				modifier = modifier,
				contentScale = contentScale,
			)
		}
		return
	}

	Image(
		painter = rememberAsyncImagePainter(
			model = url,
			imageLoader = imageLoader,
			placeholder = blurHashPlaceholder ?: placeholderPainter,
			error = placeholderPainter,
		),
		contentDescription = null,
		modifier = modifier,
		contentScale = contentScale,
	)
}

@Composable
fun blurHashPainter(
	blurHash: String,
	size: IntSize,
	punch: Float = 1f,
): Painter = remember(blurHash, size, punch) {
	val bitmap = BlurHashDecoder.decode(
		blurHash = blurHash,
		width = size.width,
		height = size.height,
		punch = punch,
	)

	BitmapPainter(requireNotNull(bitmap).asImageBitmap())
}
