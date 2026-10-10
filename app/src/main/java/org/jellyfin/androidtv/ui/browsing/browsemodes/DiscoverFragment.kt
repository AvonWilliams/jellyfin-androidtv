package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.preference.SystemPreferences
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalColorScheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.preference.Preference
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * The items of a Discover list, rendered as a Compose poster grid with a source bar above it.
 *
 * These lists come from a custom server addition rather than the generated SDK, so they are
 * fetched as a raw request. The server matches the active source's ranking against what the
 * library owns, so the result is short and already ordered — no paging. The source bar is kept
 * mounted while the grid refreshes, so switching sources re-fetches in place. External titles not
 * in the library are surfaced as non-clickable "coming soon" stubs, interleaved with the in-library
 * items by their source rank.
 */
class DiscoverFragment : Fragment() {
	private companion object {
		const val LIMIT = 500
		const val DEFAULT_BROWSE_WINDOW = "week"
	}

	private val apiClient by inject<ApiClient>()
	private val itemLauncher by inject<ItemLauncher>()
	private val userRepository by inject<UserRepository>()
	private val systemPreferences by inject<SystemPreferences>()

	private lateinit var folder: BaseItemDto
	private lateinit var mode: BrowseMode
	private lateinit var sourcePreference: Preference<String>
	private lateinit var windowPreference: Preference<String>
	private val title = mutableStateOf("")
	private val items = mutableStateOf<List<BaseItemDto>>(emptyList())
	private val missing = mutableStateOf<List<MissingTitleDto>>(emptyList())
	private val showMissing = mutableStateOf(true)
	private val activeSource = mutableStateOf(DEFAULT_BROWSE_SOURCE)
	private val activeWindow = mutableStateOf(DEFAULT_BROWSE_WINDOW)
	private val loaded = mutableStateOf(false)
	private var emptyMessage: String = ""

	// Focus the first chip when the active source returns nothing, so the empty state is not a
	// focus dead end.
	private val sourceBarFocusRequester = FocusRequester()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		mode = BrowseMode.entries.first { it.key == requireArguments().getString(Extras.BrowseMode) }

		sourcePreference = SystemPreferences.browseSourcePreference(mode.key)
		windowPreference = SystemPreferences.browseWindowPreference(mode.key)
		activeSource.value = systemPreferences[sourcePreference].ifBlank { DEFAULT_BROWSE_SOURCE }
		activeWindow.value = systemPreferences[windowPreference].ifBlank { DEFAULT_BROWSE_WINDOW }
		showMissing.value = systemPreferences[SystemPreferences.showMissingTitles]
		emptyMessage = getString(R.string.msg_no_items_in_category)

		val label = getBrowseModes(folder.collectionType)?.first { it.mode == mode }?.label
		title.value = label?.let { "${folder.name} - ${getString(it)}" } ?: folder.name.orEmpty()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setContent {
			JellyfinTheme {
				Column(modifier = Modifier.fillMaxSize()) {
					if (mode == BrowseMode.TRENDING) {
						TrendingWindowChips(
							activeWindow = activeWindow.value,
							onSelect = ::selectWindow,
						)
					}
					BrowseSourceBar(
						sources = getEnabledSources(mode, folder.collectionType == CollectionType.TVSHOWS),
						activeSource = activeSource.value,
						onSelect = ::selectSource,
						focusRequester = sourceBarFocusRequester,
					)
					ShowMissingToggle(
						checked = showMissing.value,
						onToggle = ::toggleShowMissing,
					)
					PosterGrid(
						title = title.value,
						sortDescription = "Sort by: Rank",
						items = items.value,
						showRankBadge = true,
						emptyMessage = if (loaded.value) emptyMessage else null,
						missing = if (showMissing.value) missing.value else emptyList(),
						onItemClick = ::launch,
					)
				}

				LaunchedEffect(items.value.isEmpty(), missing.value.isEmpty(), showMissing.value, loaded.value) {
					val nothingVisible = items.value.isEmpty() && (!showMissing.value || missing.value.isEmpty())
					if (loaded.value && nothingVisible) sourceBarFocusRequester.requestFocus()
				}
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		load()
	}

	private fun launch(item: BaseItemDto) {
		itemLauncher.launch(
			BaseItemDtoBaseRowItem(item, staticHeight = true),
			MutableObjectAdapter<Any>(),
			requireContext(),
		)
	}

	private fun selectSource(id: String) {
		if (id == activeSource.value) return

		activeSource.value = id
		systemPreferences[sourcePreference] = id
		load()
	}

	private fun selectWindow(value: String) {
		if (value == activeWindow.value) return

		activeWindow.value = value
		systemPreferences[windowPreference] = value
		load()
	}

	private fun toggleShowMissing() {
		val next = !showMissing.value
		showMissing.value = next
		systemPreferences[SystemPreferences.showMissingTitles] = next
	}

	private fun load() = lifecycleScope.launch {
		val source = activeSource.value
		val path = discoverPath(mode, folder.collectionType)
		val queryParameters = mutableMapOf(
			"userId" to userRepository.currentUser.value?.id,
			"parentId" to folder.id,
			"fields" to ItemRepository.itemFields.joinToString(",") { it.serialName },
			"limit" to LIMIT,
			"source" to source,
		)
		if (mode == BrowseMode.TRENDING) queryParameters["window"] = activeWindow.value
		val result = try {
			withContext(Dispatchers.IO) {
				apiClient.get<DiscoverRankedResult>(
					pathTemplate = path,
					queryParameters = queryParameters,
				).content
			}
		} catch (error: Exception) {
			Timber.e(error, "Unable to load discover list %s", path)
			DiscoverRankedResult()
		}

		if (!isAdded) return@launch

		Timber.d("Discover %s source=%s window=%s items=%d missing=%d", path, source, queryParameters["window"], result.items.size, result.missing.size)
		items.value = result.items
		missing.value = result.missing
		loaded.value = true
	}

	private fun discoverPath(mode: BrowseMode, collectionType: CollectionType?): String {
		val kind = if (collectionType == CollectionType.TVSHOWS) "Shows" else "Movies"
		val list = if (mode == BrowseMode.TRENDING) "Trending" else "TopRated"

		return "/Discover/$list/$kind"
	}
}

/**
 * A focusable client toggle for the show-missing setting, shown between the source bar and the
 * results grid. Toggling it only changes this client's rendering, never the server default.
 */
@Composable
private fun ShowMissingToggle(
	checked: Boolean,
	onToggle: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }

	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 4.dp)
			.onFocusChanged { focused = it.isFocused }
			.clickable { onToggle() }
			.then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
			.padding(horizontal = 8.dp, vertical = 4.dp),
	) {
		Checkbox(checked = checked)
		Text(
			text = stringResource(R.string.pref_show_missing_titles),
			fontSize = 14.sp,
			color = Color(0xCCFFFFFF),
		)
	}
}

/** The trending window choices, mirroring the web Day / Week / Month picker. */
private val trendingWindows = listOf("day" to "Day", "week" to "Week", "month" to "Month")

/**
 * A compact, focusable Day | Week | Month chip row shown above the source bar on Trending.
 *
 * Selecting a chip persists the window and re-fetches in place, exactly like switching sources.
 * Styled lighter than the source tiles: bordered text chips with the Trending accent on the
 * active one and the white focus ring this app uses for d-pad focus.
 */
@Composable
private fun TrendingWindowChips(
	activeWindow: String,
	onSelect: (String) -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		trendingWindows.forEach { (value, label) ->
			val active = value == activeWindow
			var focused by remember(value) { mutableStateOf(false) }

			val accent = colorResource(R.color.browse_mode_trending)
			val borderColor = when {
				focused -> Color.White
				active -> accent
				else -> Color.Black.copy(alpha = 0.12f)
			}

			Text(
				text = label,
				fontSize = 14.sp,
				color = if (active) accent else LocalColorScheme.current.listCaption,
				modifier = Modifier
					.then(if (focused) Modifier.graphicsLayer { scaleX = 1.15f; scaleY = 1.15f } else Modifier)
					.then(if (active) Modifier.background(Color(0xFF0A0A0A), RoundedCornerShape(16.dp)) else Modifier)
					.border(
						width = if (focused || active) 2.dp else 1.dp,
						color = borderColor,
						shape = RoundedCornerShape(16.dp),
					)
					.onFocusChanged { focused = it.isFocused }
					.clickable { onSelect(value) }
					.padding(horizontal = 16.dp, vertical = 6.dp),
			)
		}
	}
}
