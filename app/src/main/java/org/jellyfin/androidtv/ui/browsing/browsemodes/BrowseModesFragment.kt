package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
 * Releases, Just Added, Random), and "Browse by…" separates them from the meta section. Meta tiles
 * marked [BrowseModeDefinition.inline] (Time, People, Quality) render their children directly on
 * the home grid under a sub-heading; the remaining meta tiles open pickers or a secondary grid.
 */
private const val COLUMNS = 6

/** An inline meta section: a sub-heading label followed by its child tiles on the home grid. */
private data class BrowseModeInline(
	val label: String,
	val children: List<BrowseModeTile>,
)

class BrowseModesFragment : Fragment() {
	private val navigationRepository by inject<NavigationRepository>()
	private val preferencesRepository by inject<PreferencesRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var primary: List<BrowseModeTile>
	private lateinit var inline: List<BrowseModeInline>
	private lateinit var meta: List<BrowseModeTile>
	private lateinit var browseByLabel: String

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		browseByLabel = getString(R.string.lbl_browse_by)

		val modes = getBrowseModes(folder.collectionType).orEmpty()
		primary = modes.filter { it.tier == BrowseTier.PRIMARY }
			.map { BrowseModeTile(it, getString(it.label)) }
		val metaModes = modes.filter { it.tier == BrowseTier.META }
		inline = metaModes.filter { it.inline }
			.map { definition ->
				BrowseModeInline(
					label = getString(definition.label),
					children = definition.children.orEmpty()
						.map { child -> BrowseModeTile(child, getString(child.label), small = true) },
				)
			}
		meta = metaModes.filter { !it.inline }
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
					inline = inline,
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
	inline: List<BrowseModeInline>,
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
			BrowseModeHeading(title, fontSize = 30.sp, variant = HeadingVariant.MAIN)
		}

		itemsIndexed(primary, key = { _, tile -> tile.definition.mode.key }) { index, tile ->
			BrowseModeTileCard(
				tile = tile,
				focusRequester = if (index == 0) firstTileFocusRequester else null,
				onClick = { onTileClick(tile.definition) },
			)
		}

		item(key = "browse-by-heading", span = { GridItemSpan(maxLineSpan) }) {
			BrowseModeHeading(browseByLabel, fontSize = 22.sp)
		}

		inline.forEach { section ->
			item(key = "inline-heading-${section.label}", span = { GridItemSpan(maxLineSpan) }) {
				BrowseModeHeading(section.label, fontSize = 18.sp, variant = HeadingVariant.SUBHEADING)
			}

			itemsIndexed(section.children, key = { _, tile -> tile.definition.mode.key }) { _, tile ->
				BrowseModeTileCard(
					tile = tile,
					onClick = { onTileClick(tile.definition) },
				)
			}
		}

		itemsIndexed(meta, key = { _, tile -> tile.definition.mode.key }) { _, tile ->
			BrowseModeTileCard(
				tile = tile,
				onClick = { onTileClick(tile.definition) },
			)
		}
	}
}

/** Heading prominence levels, mirroring the web's main / section / sub-heading glass treatment. */
private enum class HeadingVariant(val radius: Dp, val weight: FontWeight, val alpha: Float) {
	MAIN(8.dp, FontWeight.Medium, 0.50f),
	SECTION(7.dp, FontWeight.Normal, 0.35f),
	SUBHEADING(6.dp, FontWeight.Normal, 0.35f),
}

/** A full-width, non-focusable section heading on a translucent glass bar, matching the web's heading bars. */
@Composable
private fun BrowseModeHeading(
	text: String,
	fontSize: TextUnit = 22.sp,
	variant: HeadingVariant = HeadingVariant.SECTION,
) {
	Text(
		text = text,
		fontSize = fontSize,
		fontWeight = variant.weight,
		color = Color.White,
		textAlign = TextAlign.Center,
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(variant.radius))
			.background(BrowseGlass.surface(variant.alpha))
			.border(1.dp, BrowseGlass.border, RoundedCornerShape(variant.radius))
			.padding(horizontal = 12.dp, vertical = 8.dp),
	)
}
