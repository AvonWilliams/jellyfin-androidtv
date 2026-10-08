package org.jellyfin.androidtv.ui.browsing.browsemodes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text

/**
 * A compact, focusable row of source chips shown above the Trending / Top Rated results.
 *
 * Kept mounted while the grid refreshes, so switching a source re-fetches in place and never
 * navigates away. Mirrors the web source bar: each chip is a labelled accent-coloured pill, the
 * active chip filled with its source colour.
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sources.forEachIndexed { index, source ->
            val active = source.id == activeSource
            var focused by remember(source.id) { mutableStateOf(false) }

            val chipBackground = when {
                focused -> Color(0x33FFFFFF)
                active -> source.color
                else -> Color.Transparent
            }
            val chipBorderColor = when {
                focused -> Color.White
                active -> source.color
                else -> Color(0x66FFFFFF)
            }
            val chipTextColor = if (active || focused) Color.White else Color(0xCCFFFFFF)

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .then(if (index == 0 && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onSelect(source.id) }
                    .border(if (focused) 3.dp else 1.dp, chipBorderColor, RoundedCornerShape(16.dp))
                    .background(chipBackground, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = source.label,
                    fontSize = 14.sp,
                    color = chipTextColor,
                )
            }
        }
    }
}
