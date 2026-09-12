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
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetStudiosRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * The studios of a library, as a grid to pick from.
 *
 * Deliberately not modelled on [org.jellyfin.androidtv.ui.browsing.ByGenreFragment], which builds
 * one row per value and retrieves them all up front. A library holds an order of magnitude more
 * studios than genres — a sample library of 56 films carries 209 — so that shape would fire
 * hundreds of requests at once on opening the screen.
 */
class ByStudioFragment : VerticalGridSupportFragment() {
	private companion object {
		const val COLUMNS = 6
		const val CARD_HEIGHT = 200
	}

	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var studiosAdapter: MutableObjectAdapter<Any>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		val includeType = requireArguments().getString(Extras.IncludeType)

		title = folder.name

		setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = COLUMNS })

		studiosAdapter = MutableObjectAdapter(CardPresenter(true, CARD_HEIGHT))
		adapter = studiosAdapter

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
			val studio = (item as? BaseItemDtoBaseRowItem)?.baseItem ?: return@OnItemViewClickedListener
			navigationRepository.navigate(
				Destinations.libraryByStudioItems(folder, studio.name.orEmpty())
			)
		}

		load(includeType)
	}

	private fun load(includeType: String?) = lifecycleScope.launch {
		val studios = try {
			withContext(Dispatchers.IO) {
				apiClient.studiosApi.getStudios(
					GetStudiosRequest(
						parentId = folder.id,
						includeItemTypes = includeType?.let(BaseItemKind::fromNameOrNull)?.let(::setOf),
					)
				).content.items
			}
		} catch (error: Exception) {
			Timber.e(error, "Unable to load studios for %s", folder.name)
			emptyList()
		}

		if (!isAdded) return@launch

		// The endpoint offers no sort option, so order by name here.
		studios
			.filter { !it.name.isNullOrBlank() }
			.sortedBy { it.name }
			.forEach { studiosAdapter.add(BaseItemDtoBaseRowItem(it)) }
	}
}
