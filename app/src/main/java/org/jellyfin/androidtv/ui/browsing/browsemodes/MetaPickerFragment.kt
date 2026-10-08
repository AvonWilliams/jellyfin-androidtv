package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject

/**
 * The secondary choices of a meta browse mode, e.g. "Story" opens Story Themes + Plot Elements.
 *
 * Each choice is an existing mode, so tapping one reuses [openBrowseMode] and the flows it reaches.
 */
class MetaPickerFragment : VerticalGridSupportFragment() {
	private companion object {
		const val COLUMNS = 4
	}

	private val navigationRepository by inject<NavigationRepository>()
	private val preferencesRepository by inject<PreferencesRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var children: List<BrowseModeDefinition>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		val modeKey = requireArguments().getString(Extras.BrowseMode)!!
		val mode = BrowseMode.entries.first { it.key == modeKey }
		val definition = getBrowseModeDefinition(folder.collectionType, mode)

		children = definition?.children.orEmpty()
		title = definition?.label?.let { getString(it) } ?: modeKey

		setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = COLUMNS })

		adapter = ArrayObjectAdapter(BrowseModeTilePresenter()).apply {
			children.forEach { child -> add(BrowseModeTile(child, getString(child.label))) }
		}

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			if (item is BrowseModeTile) {
				openBrowseMode(folder, item.definition, navigationRepository, preferencesRepository)
			}
		}
	}
}
