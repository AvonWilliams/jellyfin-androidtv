package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.OnItemViewClickedListener
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

/**
 * A grid of decades derived from the years present in this library.
 *
 * Fetches the available years from /Items/Filters, groups them into decades (1980s, 1990s, …),
 * and shows only decades that have at least one matching item.
 */
class DecadesPickerFragment : VerticalGridSupportFragment() {
	private companion object {
		const val COLUMNS = 6
		const val CARD_HEIGHT = 200
	}

	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var itemType: BaseItemKind
	private lateinit var decadesAdapter: MutableObjectAdapter<Any>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)

		itemType = when (folder.collectionType) {
			CollectionType.TVSHOWS -> BaseItemKind.SERIES
			else -> BaseItemKind.MOVIE
		}

		title = getBrowseModes(folder.collectionType)
			?.firstOrNull { it.mode == BrowseMode.DECADES }
			?.label
			?.let { getString(it) }
			?: "Decades"

		setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = COLUMNS })

		decadesAdapter = MutableObjectAdapter(CardPresenter(true, CARD_HEIGHT))
		adapter = decadesAdapter

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			val decadeLabel = (item as? BaseItemDtoBaseRowItem)?.baseItem?.name
				?: return@OnItemViewClickedListener
			// Extract the decade start year from the label (e.g. "1980s" -> 1980).
			val decadeStartYear = decadeLabel.removeSuffix("s").toIntOrNull()
				?: return@OnItemViewClickedListener
			navigationRepository.navigate(
				Destinations.libraryByDecadeItems(folder, decadeStartYear, itemType.serialName)
			)
		}

		load()
	}

	private fun load() = lifecycleScope.launch {
		val decades = try {
			withContext(Dispatchers.IO) { fetchDecades() }
		} catch (error: Exception) {
			Timber.e(error, "Unable to load decades for %s", folder.name)
			emptyList()
		}

		if (!isAdded) return@launch

		decades.forEach { decadeStart ->
			val label = "${decadeStart}s"
			// Build safely through kotlinx.serialization. Name, Id and Type
			// are required fields on BaseItemDto.
			val json = buildJsonObject {
				put("Name", label)
				put("Id", java.util.UUID.randomUUID().toString())
				put("Type", "Folder")
			}.toString()
			val syntheticItem = Json.decodeFromString<BaseItemDto>(json)
			decadesAdapter.add(BaseItemDtoBaseRowItem(syntheticItem))
		}
	}

	/** Fetches available years from the server and groups them into decades. */
	private suspend fun fetchDecades(): List<Int> {
		val userId = userRepository.currentUser.value?.id ?: return emptyList()

		val response = apiClient.get<QueryFiltersLegacy>(
			pathTemplate = "/Items/Filters",
			queryParameters = mapOf(
				"userId" to userId,
				"parentId" to folder.id,
				"includeItemTypes" to itemType.serialName,
			),
		)
		val available = response.content.years.orEmpty()

		return available.map { (it / 10) * 10 }.distinct().sorted()
	}
}
