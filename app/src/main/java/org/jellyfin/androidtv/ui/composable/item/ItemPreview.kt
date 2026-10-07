package org.jellyfin.androidtv.ui.composable.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.design.Tokens

@Composable
@Stable
fun ItemPreview(
	card: @Composable () -> Unit,
	cardWidth: Dp,
	modifier: Modifier = Modifier,
	title: (@Composable () -> Unit)? = null,
	subtitle: (@Composable () -> Unit)? = null,
	spacing: Dp = Tokens.Space.spaceXs,
) {
	Column(modifier = modifier.width(cardWidth)) {
		card()
		Spacer(modifier = Modifier.height(spacing))
		ItemPreviewMetadata(
			title = title,
			subtitle = subtitle,
			spacing = spacing,
		)
	}
}

@Composable
@Stable
private fun ItemPreviewMetadata(
	title: (@Composable () -> Unit)?,
	subtitle: (@Composable () -> Unit)?,
	spacing: Dp,
) {
	Column(
		modifier = Modifier.padding(spacing),
		verticalArrangement = Arrangement.spacedBy(spacing),
	) {
		title?.let { content ->
			ProvideTextStyle(
				value = JellyfinTheme.typography.default.copy(
					color = Tokens.Color.colorGrey100,
					fontSize = 12.sp,
				),
				content = content,
			)
		}

		subtitle?.let { content ->
			ProvideTextStyle(
				value = JellyfinTheme.typography.default.copy(
					color = Tokens.Color.colorGrey300,
					fontSize = 10.sp,
				),
				content = content,
			)
		}
	}
}
