package org.jellyfin.androidtv.ui.composable.item

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Badge
import org.jellyfin.androidtv.ui.base.Text

/**
 * The position of a card within a server-ranked list (e.g. the Discover browse modes), shown as a
 * small numbered circle at the card's top-start. A rank of zero or less renders nothing.
 */
@Composable
fun RankBadge(rank: Int, modifier: Modifier = Modifier) {
	if (rank <= 0) return
	Badge(modifier = modifier) {
		Text(text = rank.toString(), fontSize = 10.sp)
	}
}
