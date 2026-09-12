package org.jellyfin.androidtv.ui.browsing.browsemodes

import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.ui.presentation.CardPresenter

/**
 * Cards for a Discover list, badged with their position in it.
 *
 * The server writes each item's place in the TMDB ranking into `IndexNumber`, which is otherwise
 * unused for the movies and series these lists hold. The rank badge is opt-in on [CardPresenter]
 * so that `IndexNumber` keeps its "episode number" meaning everywhere else.
 */
fun discoverCardPresenter(staticHeight: Int) =
	CardPresenter(
		showInfo = true,
		imageType = ImageType.POSTER,
		staticHeight = staticHeight,
		uniformAspect = false,
		showRankBadge = true,
	)
