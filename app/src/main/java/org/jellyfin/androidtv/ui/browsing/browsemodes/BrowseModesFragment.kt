package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject

/**
 * The grid of tiles shown when a library is opened, each tile a way of browsing that library.
 *
 * The library name and the "Browse by…" section label are full-width headings rendered outside the
 * tile columns: the library name sits above the primary actions (All, Trending, Top Rated, New
 * Releases, Just Added, Random), and "Browse by…" separates them from the meta tiles that open
 * pickers or a secondary choice grid.
 */
private const val COLUMNS = 6

class BrowseModesFragment : Fragment() {
	private val navigationRepository by inject<NavigationRepository>()
	private val preferencesRepository by inject<PreferencesRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var primary: List<BrowseModeTile>
	private lateinit var meta: List<BrowseModeTile>
	private lateinit var browseByLabel: String

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		browseByLabel = getString(R.string.lbl_browse_by)

		val modes = getBrowseModes(folder.collectionType).orEmpty()
		primary = modes.filter { it.tier == BrowseTier.PRIMARY }
			.map { BrowseModeTile(it, getString(it.label)) }
		meta = modes.filter { it.tier == BrowseTier.META }
			.map { BrowseModeTile(it, getString(it.label), small = true) }
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setContent {
			JellyfinTheme {
				BrowseModesGrid(
					title = folder.name.orEmpty(),
					browseByLabel = browseByLabel,
					primary = primary,
					meta = meta,
					onTileClick = ::openTile,
				)
			}
		}
	}

	private fun openTile(definition: BrowseModeDefinition) =
		openBrowseMode(folder, definition, navigationRepository, preferencesRepository)
}

@Composable
private fun BrowseModesGrid(
	title: String,
	browseByLabel: String,
	primary: List<BrowseModeTile>,
	meta: List<BrowseModeTile>,
	onTileClick: (BrowseModeDefinition) -> Unit,
) {
	val gridState = rememberLazyGridState()
	val firstTileFocusRequester = remember { FocusRequester() }

	LaunchedEffect(primary.isNotEmpty()) {
		if (primary.isNotEmpty()) firstTileFocusRequester.requestFocus()
	}

	LazyVerticalGrid(
		columns = GridCells.Fixed(COLUMNS),
		state = gridState,
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(16.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		item(key = "library-heading", span = { GridItemSpan(maxLineSpan) }) {
			BrowseModeHeading(title)
		}

		itemsIndexed(primary, key = { _, tile -> tile.definition.mode.key }) { index, tile ->
			BrowseModeTileCard(
				tile = tile,
				focusRequester = if (index == 0) firstTileFocusRequester else null,
				onClick = { onTileClick(tile.definition) },
			)
		}

		item(key = "browse-by-heading", span = { GridItemSpan(maxLineSpan) }) {
			BrowseModeHeading(browseByLabel)
		}

		itemsIndexed(meta, key = { _, tile -> tile.definition.mode.key }) { _, tile ->
			BrowseModeTileCard(
				tile = tile,
				onClick = { onTileClick(tile.definition) },
			)
		}
	}
}

/** A full-width, non-focusable section heading rendered on its own line. */
@Composable
private fun BrowseModeHeading(text: String) {
	Text(
		text = text,
		fontSize = 24.sp,
		fontWeight = FontWeight.Bold,
		color = Color.White,
		modifier = Modifier
			.fillMaxWidth()
			.padding(top = 8.dp),
	)
}
