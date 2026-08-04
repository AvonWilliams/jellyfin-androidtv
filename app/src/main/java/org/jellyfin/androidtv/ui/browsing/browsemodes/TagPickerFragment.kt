package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import android.view.View
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.TitleView
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.QueryFiltersLegacy
import org.koin.android.ext.android.inject
import timber.log.Timber

class TagPickerFragment : VerticalGridSupportFragment() {
	private companion object {
		const val COLUMNS = 6
		const val CARD_HEIGHT = 200
	}

	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var mode: BrowseMode
	private lateinit var itemType: BaseItemKind
	private lateinit var tagsAdapter: MutableObjectAdapter<Any>
	private var sortMode = SortMode.A_Z
	private var rawTags: List<String> = emptyList()
	private var baseTitle: String = ""

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		mode = BrowseMode.entries.first { it.key == requireArguments().getString(Extras.BrowseMode) }

		itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		baseTitle = getBrowseModes(folder.collectionType)?.firstOrNull { it.mode == mode }?.label
			?.let { getString(it) } ?: mode.key
		updateTitle()

		setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = COLUMNS })

		tagsAdapter = MutableObjectAdapter(CardPresenter(true, CARD_HEIGHT))
		adapter = tagsAdapter

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			val baseItem = (item as? BaseItemDtoBaseRowItem)?.baseItem ?: return@OnItemViewClickedListener
			val tag = baseItem.originalTitle ?: baseItem.name ?: return@OnItemViewClickedListener
			navigationRepository.navigate(
				Destinations.libraryByTagItems(folder, tag, itemType.serialName)
			)
		}

		load()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		// Make the header title clickable to cycle sort mode.
		findTitleView(view)?.setOnClickListener {
			sortMode = sortMode.next()
			updateTitle()
			refreshGrid()
		}
	}

	private fun updateTitle() {
		title = "$baseTitle · ${sortMode.label}"
	}

	private fun load() = lifecycleScope.launch {
		val tags = try {
			withContext(Dispatchers.IO) { fetchMatchingTags() }
		} catch (error: Exception) {
			Timber.e(error, "Unable to load tags for %s / %s", folder.name, mode.key)
			emptyList()
		}

		if (!isAdded) return@launch

		rawTags = tags
		refreshGrid()
	}

	private fun refreshGrid() {
		tagsAdapter.clear()

		val sorted = when (sortMode) {
			SortMode.A_Z -> rawTags.sorted()
			SortMode.Z_A -> rawTags.sortedDescending()
			SortMode.RANDOM -> rawTags.shuffled()
		}

		sorted.forEach { tagName ->
			val json = buildJsonObject {
				put("Name", tagName.toTitleCase())
				put("OriginalTitle", tagName)
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			tagsAdapter.add(BaseItemDtoBaseRowItem(Json.decodeFromString(json)))
		}
	}

	private suspend fun fetchMatchingTags(): List<String> {
		val userId = userRepository.currentUser.value?.id ?: return emptyList()

		val response = apiClient.get<QueryFiltersLegacy>(
			pathTemplate = "/Items/Filters",
			queryParameters = mapOf(
				"userId" to userId,
				"parentId" to folder.id,
				"includeItemTypes" to itemType.serialName,
			),
		)
		val available = response.content.tags.orEmpty()

		val curatedSet = curatedTagsFor(mode).toSet()
		return available.filter { curatedSet.contains(it) }.sorted()
	}
}

/** Find the Leanback [TitleView] in the fragment's view hierarchy. */
internal fun findTitleView(root: View): TitleView? {
	if (root is TitleView) return root
	if (root is android.view.ViewGroup) {
		for (i in 0 until root.childCount) {
			val found = findTitleView(root.getChildAt(i))
			if (found != null) return found
		}
	}
	return null
}

enum class SortMode(val label: String) {
	A_Z("A–Z"),
	Z_A("Z–A"),
	RANDOM("Random");

	fun next(): SortMode = when (this) {
		A_Z -> Z_A
		Z_A -> RANDOM
		RANDOM -> A_Z
	}
}

internal fun curatedTagsFor(mode: BrowseMode): List<String> = when (mode) {
	BrowseMode.MOOD -> MOOD_TAGS
	BrowseMode.STORY_THEMES -> STORY_THEME_TAGS
	BrowseMode.PLOT_ELEMENTS -> PLOT_ELEMENT_TAGS
	BrowseMode.WORLDS -> WORLD_TAGS
	BrowseMode.STYLES -> STYLE_TAGS
	else -> emptyList()
}

internal fun String.toTitleCase(): String = buildString {
	var capitalise = true
	for (char in this@toTitleCase) {
		if (char.isWhitespace() || char == '-') {
			capitalise = true
			append(char)
		} else if (capitalise) {
			append(char.uppercaseChar())
			capitalise = false
		} else {
			append(char)
		}
	}
}
