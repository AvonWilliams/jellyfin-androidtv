package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.ComposeView
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
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.preference.Preference
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * The items of a Discover list, rendered as a Compose poster grid with a source bar above it.
 *
 * These lists come from a custom server addition rather than the generated SDK, so they are
 * fetched as a raw request. The server matches the active source's ranking against what the
 * library owns, so the result is short and already ordered — no paging. The source bar is kept
 * mounted while the grid refreshes, so switching sources re-fetches in place.
 */
class DiscoverFragment : Fragment() {
	private companion object {
		const val LIMIT = 500
	}

	private val apiClient by inject<ApiClient>()
	private val itemLauncher by inject<ItemLauncher>()
	private val userRepository by inject<UserRepository>()
	private val systemPreferences by inject<SystemPreferences>()

	private lateinit var folder: BaseItemDto
	private lateinit var mode: BrowseMode
	private lateinit var sourcePreference: Preference<String>
	private val title = mutableStateOf("")
	private val items = mutableStateOf<List<BaseItemDto>>(emptyList())
	private val activeSource = mutableStateOf(DEFAULT_BROWSE_SOURCE)
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
		activeSource.value = systemPreferences[sourcePreference].ifBlank { DEFAULT_BROWSE_SOURCE }
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
					BrowseSourceBar(
						sources = ENABLED_BROWSE_SOURCES,
						activeSource = activeSource.value,
						onSelect = ::selectSource,
						focusRequester = sourceBarFocusRequester,
					)
					PosterGrid(
						title = title.value,
						sortDescription = "Sort by: Rank",
						items = items.value,
						showRankBadge = true,
						emptyMessage = if (loaded.value) emptyMessage else null,
						onItemClick = ::launch,
					)
				}

				LaunchedEffect(items.value.isEmpty(), loaded.value) {
					if (loaded.value && items.value.isEmpty()) sourceBarFocusRequester.requestFocus()
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

	private fun load() = lifecycleScope.launch {
		val source = activeSource.value
		val path = discoverPath(mode, folder.collectionType)
		val result = try {
			withContext(Dispatchers.IO) {
				apiClient.get<BaseItemDtoQueryResult>(
					pathTemplate = path,
					queryParameters = mapOf(
						"userId" to userRepository.currentUser.value?.id,
						"parentId" to folder.id,
						"fields" to ItemRepository.itemFields.joinToString(",") { it.serialName },
						"limit" to LIMIT,
						"source" to source,
					),
				).content.items
			}
		} catch (error: Exception) {
			Timber.e(error, "Unable to load discover list %s", path)
			emptyList()
		}

		if (!isAdded) return@launch

		items.value = result
		loaded.value = true
	}

	private fun discoverPath(mode: BrowseMode, collectionType: CollectionType?): String {
		val kind = if (collectionType == CollectionType.TVSHOWS) "Shows" else "Movies"
		val list = if (mode == BrowseMode.TRENDING) "Trending" else "TopRated"

		return "/Discover/$list/$kind"
	}
}
