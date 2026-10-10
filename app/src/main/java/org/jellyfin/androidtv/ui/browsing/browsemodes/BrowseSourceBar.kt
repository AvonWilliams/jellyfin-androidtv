package org.jellyfin.androidtv.ui.browsing.browsemodes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.LocalColorScheme
import org.jellyfin.androidtv.ui.base.Text

/**
 * A compact, focusable row of source tiles shown above the Trending / Top Rated results.
 *
 * Kept mounted while the grid refreshes, so switching a source re-fetches in place and never
 * navigates away. Mirrors the web source bar: each tile is a provider logo on a white scrim with
 * a label underneath, the active tile outlined with its source colour.
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

            val tileBorderColor = when {
                focused -> Color.White
                active -> source.color
                else -> Color.Black.copy(alpha = 0.12f)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .width(84.dp)
                    .then(if (focused) Modifier.graphicsLayer { scaleX = 1.1f; scaleY = 1.1f } else Modifier)
                    .then(if (index == 0 && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onSelect(source.id) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .border(
                            if (focused) 3.dp else if (active) 2.dp else 1.dp,
                            tileBorderColor,
                            RoundedCornerShape(12.dp),
                        )
                        .background(if (active) Color(0xFF202020) else Color.White, RoundedCornerShape(12.dp)),
                ) {
                    sourceLogo(source.id)?.let { logo ->
                        Image(
                            painter = painterResource(logo),
                            contentDescription = source.label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(50.dp),
                        )
                    }
                }

                Text(
                    text = source.label,
                    fontSize = 14.sp,
                    color = if (active) source.color else LocalColorScheme.current.listCaption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
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
