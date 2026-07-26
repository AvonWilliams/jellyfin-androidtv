package org.jellyfin.androidtv.ui.browsing.browsemodes

import androidx.leanback.widget.Presenter
import org.jellyfin.androidtv.ui.card.LegacyImageCardView
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.presentation.CardPresenter

/**
 * Cards for a Discover list, badged with their position in it.
 *
 * The server writes each item's place in the TMDB ranking into `IndexNumber`, which is otherwise
 * unused for the movies and series these lists hold.
 */
class DiscoverCardPresenter(
	staticHeight: Int,
) : CardPresenter(true, staticHeight) {
	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
		super.onBindViewHolder(viewHolder, item)

		val rank = (item as? BaseItemDtoBaseRowItem)?.baseItem?.indexNumber ?: return
		(viewHolder.view as? LegacyImageCardView)?.setRankBadge(rank)
	}
}
