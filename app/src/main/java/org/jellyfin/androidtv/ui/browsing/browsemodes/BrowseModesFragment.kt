package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.QueryFiltersLegacy
import org.jellyfin.sdk.model.api.request.GetStudiosRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * The grid of tiles shown when a library is opened, each tile a way of browsing that library.
 *
 * The library's backdrop art fills the viewport behind the grid (see [LibraryBackdrop]). Headings
 * are quiet left-aligned text labels, not glass bars: the library-name title and the "Browse by…"
 * heading were removed entirely, matching the web. Meta tiles marked [BrowseModeDefinition.inline]
 * (Categories, Mood & Themes, People, Discover) render their children directly on the home grid
 * under a quiet label; the remaining meta tiles open pickers or a secondary grid.
 */
private const val COLUMNS = 6

/** An inline meta section: a sub-heading label followed by its child tiles on the home grid. */
private data class BrowseModeInline(
	val label: String,
	val children: List<BrowseModeTile>,
)

class BrowseModesFragment : Fragment() {
	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val preferencesRepository by inject<PreferencesRepository>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var primary: List<BrowseModeTile>
	private lateinit var inline: List<BrowseModeInline>
	private lateinit var meta: List<BrowseModeTile>
	private val counts = mutableStateOf<Map<BrowseMode, Int>>(emptyMap())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)

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
				Box(modifier = Modifier.fillMaxSize()) {
					LibraryBackdrop(folder = folder)
					BrowseModesGrid(
						primary = primary,
						inline = inline,
						meta = meta,
						counts = counts.value,
						onTileClick = ::openTile,
					)
				}
			}
		}
	}

	private fun openTile(definition: BrowseModeDefinition) =
		openBrowseMode(folder, definition, navigationRepository, preferencesRepository)

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		lifecycleScope.launch {
			val result = try {
				withContext(Dispatchers.IO) { fetchChildCounts() }
			} catch (error: Exception) {
				Timber.e(error, "Unable to load browse-mode counts for %s", folder.name)
				emptyMap()
			}

			if (isAdded) counts.value = result
		}
	}

	/**
	 * Number of distinct entries under each meta child (genres, studios, decades, years, ratings,
	 * curated tags, people), mirroring the web's child-tile counts.
	 */
	private suspend fun fetchChildCounts(): Map<BrowseMode, Int> {
		val itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}
		val userId = userRepository.currentUser.value?.id ?: return emptyMap()

		val result = mutableMapOf<BrowseMode, Int>()

		// Years, ratings and tags come from the library's filter metadata in one call.
		val filters = try {
			apiClient.get<QueryFiltersLegacy>(
				pathTemplate = "/Items/Filters",
				queryParameters = mapOf(
					"userId" to userId,
					"parentId" to folder.id,
					"includeItemTypes" to itemType.serialName,
				),
			).content
		} catch (error: Exception) {
			Timber.e(error, "Unable to load filters for %s", folder.name)
			null
		}

		val years = filters?.years.orEmpty()
		result[BrowseMode.DECADES] = years.map { (it / 10) * 10 }.distinct().size
		result[BrowseMode.YEAR] = years.distinct().size
		result[BrowseMode.AGE_RATING] = filters?.officialRatings.orEmpty().map { getRatingLevel(it) }.toSet().size

		// Curated tags that are actually present in this library.
		val libraryTags = filters?.tags.orEmpty().map { it.lowercase() }.toSet()
		for (mode in listOf(BrowseMode.MOOD, BrowseMode.STORY_THEMES, BrowseMode.PLOT_ELEMENTS, BrowseMode.WORLDS, BrowseMode.STYLES)) {
			result[mode] = curatedTagsFor(mode).count { libraryTags.contains(it.lowercase()) }
		}

		// Distinct genres and studios.
		result[BrowseMode.GENRES] = try {
			apiClient.genresApi.getGenres(parentId = folder.id, sortBy = setOf(ItemSortBy.SORT_NAME)).content.items.orEmpty().size
		} catch (error: Exception) { 0 }

		result[BrowseMode.STUDIOS] = try {
			apiClient.studiosApi.getStudios(GetStudiosRequest(parentId = folder.id)).content.items.orEmpty().size
		} catch (error: Exception) { 0 }

		// Distinct people per kind.
		val personCounts = fetchItemCounts(apiClient, countCacheKey(folder.id, "person"), "person", folder.id, itemType)
		result[BrowseMode.ACTORS] = personCounts["Actor"] ?: 0
		result[BrowseMode.DIRECTORS] = personCounts["Director"] ?: 0
		result[BrowseMode.WRITERS] = personCounts["Writer"] ?: 0

		return result
	}
}

@Composable
private fun BrowseModesGrid(
	primary: List<BrowseModeTile>,
	inline: List<BrowseModeInline>,
	meta: List<BrowseModeTile>,
	counts: Map<BrowseMode, Int>,
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
		itemsIndexed(primary, key = { _, tile -> tile.definition.mode.key }) { index, tile ->
			BrowseModeTileCard(
				tile = tile,
				focusRequester = if (index == 0) firstTileFocusRequester else null,
				onClick = { onTileClick(tile.definition) },
			)
		}

		inline.forEach { section ->
			item(key = "inline-heading-${section.label}", span = { GridItemSpan(maxLineSpan) }) {
				BrowseModeSectionLabel(section.label)
			}

			itemsIndexed(section.children, key = { _, tile -> tile.definition.mode.key }) { _, tile ->
				BrowseModeTileCard(
					tile = tile,
					onClick = { onTileClick(tile.definition) },
					count = counts[tile.definition.mode],
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

/**
 * Quiet left-aligned group label (Categories / Mood & Themes / People / Discover), matching the
 * web's de-pilled group headings (`GROUP_LABEL_SX`: 0.875rem, weight 600, text-secondary, 0.03em
 * letter-spacing, 4px left padding). No glass bar.
 */
@Composable
private fun BrowseModeSectionLabel(text: String) {
	Text(
		text = text,
		fontSize = 14.sp,
		fontWeight = FontWeight.SemiBold,
		color = Color.White.copy(alpha = 0.7f),
		letterSpacing = 0.42.sp,
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 4.dp),
	)
}
