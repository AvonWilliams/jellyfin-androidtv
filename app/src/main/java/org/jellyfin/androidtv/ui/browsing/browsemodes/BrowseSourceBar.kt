package org.jellyfin.androidtv.ui.browsing.browsemodes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.LocalColorScheme
import org.jellyfin.androidtv.ui.base.Text

/**
 * A compact, focusable row of source chips shown above the Trending / Top Rated results.
 *
 * Kept mounted while the grid refreshes, so switching a source re-fetches in place and never
 * navigates away. Mirrors the web source bar: each chip is a pill holding a provider logo plus a
 * label; the active chip is filled with a subtle paper scrim and outlined with its source colour.
 */
@Composable
internal fun BrowseSourceBar(
    sources: List<BrowseSource>,
    activeSource: String,
    onSelect: (String) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sources.forEachIndexed { index, source ->
            val active = source.id == activeSource
            var focused by remember(source.id) { mutableStateOf(false) }

            val chipBorderColor = when {
                active -> source.color
                focused -> Color.White
                else -> BrowseGlass.border
            }
            val chipBackground = when {
                active -> BrowseGlass.surface(0.5f)
                focused -> Color(0x33FFFFFF)
                else -> Color.Transparent
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .then(if (focused) Modifier.graphicsLayer { scaleX = 1.1f; scaleY = 1.1f } else Modifier)
                    .clip(RoundedCornerShape(999.dp))
                    .background(chipBackground)
                    .border(1.dp, chipBorderColor, RoundedCornerShape(999.dp))
                    .then(if (index == 0 && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onSelect(source.id) }
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                sourceLogo(source.id)?.let { logo ->
                    Image(
                        painter = painterResource(logo),
                        contentDescription = source.label,
                        contentScale = ContentScale.Fit,
                        colorFilter = if (active) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                        modifier = Modifier
                            .size(18.dp)
                            .alpha(if (active) 1f else 0.75f),
                    )
                }

                Text(
                    text = source.label,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) source.color else LocalColorScheme.current.listCaption,
                )
            }
        }
    }
}

/** The drawable logo for each source, keyed by the source id sent to /Discover. */
private fun sourceLogo(id: String): Int? = when (id) {
    "tmdb" -> R.drawable.tmdb
    "imdb" -> R.drawable.imdb
    "netflix", "netflix-au", "netflix-ph" -> R.drawable.netflix
    "letterboxd" -> R.drawable.letterboxd
    "rottentomatoes" -> R.drawable.rottentomatoes
    else -> null
}
