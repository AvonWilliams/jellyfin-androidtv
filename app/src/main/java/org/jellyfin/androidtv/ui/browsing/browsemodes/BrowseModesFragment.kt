package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject

/**
 * The grid of tiles shown when a library is opened, each tile a way of browsing that library.
 *
 * The primary actions (All, Trending, Top Rated, New Releases, Just Added, Random) come first,
 * followed by a "Browse by…" section of meta tiles that open pickers or a secondary choice grid.
 */
class BrowseModesFragment : VerticalGridSupportFragment() {
	private companion object {
		const val COLUMNS = 6
	}

	private val navigationRepository by inject<NavigationRepository>()
	private val preferencesRepository by inject<PreferencesRepository>()

	private lateinit var folder: BaseItemDto

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		title = folder.name

		setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = COLUMNS })

		val presenterSelector = ClassPresenterSelector()
			.addClassPresenter(BrowseModeTile::class.java, BrowseModeTilePresenter())
			.addClassPresenter(BrowseModeHeader::class.java, BrowseModeHeaderPresenter())

		adapter = ArrayObjectAdapter(presenterSelector).apply {
			val (primary, meta) = getBrowseModes(folder.collectionType).orEmpty()
				.partition { it.tier == BrowseTier.PRIMARY }

			primary.forEach { add(BrowseModeTile(it, getString(it.label))) }
			add(BrowseModeHeader(getString(R.string.lbl_browse_by)))
			meta.forEach { add(BrowseModeTile(it, getString(it.label), small = true)) }
		}

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			if (item is BrowseModeTile) {
				openBrowseMode(folder, item.definition, navigationRepository, preferencesRepository)
			}
		}
	}
}
