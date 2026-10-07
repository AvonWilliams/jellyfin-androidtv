package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.constant.LiveTvOption
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItemSelectAction
import org.jellyfin.androidtv.ui.itemhandling.GridButtonBaseRowItem
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetRecommendedProgramsRequest
import org.jellyfin.sdk.model.api.request.GetRecordingsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import java.time.Instant

/**
 * Compose-native home screen state holder.
 *
 * Replaces [HomeRowsFragment]'s data layer: builds the enabled home sections and fetches each row,
 * honoring the same per-row change-trigger contract (compare [DataRefreshService] timestamps against
 * a row's last fetch time).
 *
 * Deferred vs the Leanback home (follow-up, not needed for the lag fix):
 *  - Notifications and Now Playing rows (special presenters).
 *  - Single-item refresh on [CustomMessage.RefreshCurrentItem].
 *  - Delete handling via [DataRefreshService.lastDeletedItemId].
 * WebSocket-triggered refresh is driven from [HomeScreen] while the home is resumed.
 */
class HomeViewModel(
	private val context: Context,
	private val api: ApiClient,
	private val userRepository: UserRepository,
	private val userViewsRepository: UserViewsRepository,
	private val userSettingPreferences: UserSettingPreferences,
	private val userPreferences: UserPreferences,
	private val dataRefreshService: DataRefreshService,
) : ViewModel() {
	private class RowDef(
		val key: String,
		val title: String,
		val cardHeight: Int,
		val triggers: List<ChangeTriggerType>,
		val fetch: suspend () -> List<BaseRowItem>,
	)

	data class HomeRow(
		val key: String,
		val title: String,
		val cardHeight: Int,
		val triggers: List<ChangeTriggerType>,
		val items: List<BaseRowItem>,
		val lastFetch: Instant,
	)

	private val _rows = MutableStateFlow<List<HomeRow>>(emptyList())
	val rows: StateFlow<List<HomeRow>> = _rows.asStateFlow()

	private var defs: List<RowDef>? = null
	private var justLoaded = true
	private var lastForceRefresh = 0L

	init {
		load()
	}

	fun load() {
		viewModelScope.launch {
			val user = withTimeout(30_000L) { userRepository.currentUser.filterNotNull().first() }
			val views = userViewsRepository.views.first()

			val currentDefs = buildRowDefs(user, views)
			defs = currentDefs

			val rows = coroutineScope {
				currentDefs.map { def ->
					async(Dispatchers.IO) {
						HomeRow(
							key = def.key,
							title = def.title,
							cardHeight = def.cardHeight,
							triggers = def.triggers,
							items = def.fetch(),
							lastFetch = Instant.now(),
						)
					}
				}.awaitAll()
			}.filter { it.items.isNotEmpty() }

			_rows.value = rows
		}
	}

	/** Called on each resume. Skips the first (initial [load] already ran). */
	fun onResume() {
		if (justLoaded) {
			justLoaded = false
			return
		}

		refresh(force = false)
	}

	fun refresh(force: Boolean = false) {
		val currentDefs = defs ?: return

		if (force) {
			val now = System.currentTimeMillis()
			if (now - lastForceRefresh < 30_000L) return
			lastForceRefresh = now
		}

		viewModelScope.launch {
			val current = _rows.value.associateBy { it.key }

			val refreshed = currentDefs.map { def ->
				val existing = current[def.key]
				val needsFetch = force || existing == null || def.triggers.any { it.firedSince(existing.lastFetch) }

				if (needsFetch) {
					val items = try {
						withContext(Dispatchers.IO) { def.fetch() }
					} catch (e: Exception) {
						existing?.items.orEmpty()
					}
					HomeRow(def.key, def.title, def.cardHeight, def.triggers, items, Instant.now())
				} else {
					requireNotNull(existing)
				}
			}.filter { it.items.isNotEmpty() }

			_rows.value = refreshed
		}
	}

	private fun ChangeTriggerType.firedSince(since: Instant): Boolean {
		val timestamp = when (this) {
			ChangeTriggerType.LibraryUpdated -> dataRefreshService.lastLibraryChange
			ChangeTriggerType.MoviePlayback -> dataRefreshService.lastMoviePlayback
			ChangeTriggerType.TvPlayback -> dataRefreshService.lastTvPlayback
			ChangeTriggerType.FavoriteUpdate -> dataRefreshService.lastFavoriteUpdate
			ChangeTriggerType.MusicPlayback -> null
		}

		return timestamp != null && timestamp > since
	}

	private fun buildRowDefs(user: UserDto, views: Collection<BaseItemDto>): List<RowDef> = buildList {
		for (section in userSettingPreferences.activeHomesections) {
			when (section) {
				HomeSectionType.LATEST_MEDIA -> addAll(latestMediaDefs(user, views))

				HomeSectionType.LIBRARY_TILES_SMALL ->
					add(RowDef("library-tiles", title(R.string.lbl_my_media), CARD_HEIGHT_LARGE, emptyList()) { userViewsItems(views) })

				HomeSectionType.LIBRARY_BUTTONS ->
					add(RowDef("library-buttons", title(R.string.lbl_my_media), CARD_HEIGHT_SMALL, emptyList()) { userViewsItems(views) })

				HomeSectionType.RESUME ->
					add(RowDef("resume", title(R.string.lbl_continue_watching), CARD_HEIGHT, triggersOf(ChangeTriggerType.TvPlayback, ChangeTriggerType.MoviePlayback)) { resumeItems(listOf(MediaType.VIDEO)) })

				HomeSectionType.RESUME_AUDIO ->
					add(RowDef("resume-audio", title(R.string.continue_listening), CARD_HEIGHT, triggersOf(ChangeTriggerType.TvPlayback, ChangeTriggerType.MoviePlayback)) { resumeItems(listOf(MediaType.AUDIO)) })

				HomeSectionType.ACTIVE_RECORDINGS ->
					add(RowDef("recordings", title(R.string.lbl_recordings), CARD_HEIGHT, emptyList()) { recordingsItems() })

				HomeSectionType.NEXT_UP ->
					add(RowDef("next-up", title(R.string.lbl_next_up), CARD_HEIGHT, triggersOf(ChangeTriggerType.TvPlayback)) { nextUpItems() })

				HomeSectionType.LIVE_TV -> if (user.policy?.enableLiveTvAccess == true) {
					add(liveTvButtonsDef(user))
					add(RowDef("on-now", title(R.string.lbl_on_now), CARD_HEIGHT, emptyList()) { onNowItems() })
				}

				HomeSectionType.RESUME_BOOK, HomeSectionType.NONE -> Unit
			}
		}
	}

	private fun latestMediaDefs(user: UserDto, views: Collection<BaseItemDto>): List<RowDef> {
		val latestItemsExcludes = user.configuration?.latestItemsExcludes.orEmpty()
		val preferParentThumb = userPreferences[UserPreferences.seriesThumbnailsEnabled]

		return views
			.filterNot { view -> view.collectionType in EXCLUDED_COLLECTION_TYPES || view.id in latestItemsExcludes }
			.map { view ->
				RowDef(
					key = "latest:${view.id}",
					title = context.getString(R.string.lbl_latest_in, view.name),
					cardHeight = CARD_HEIGHT,
					triggers = triggersOf(ChangeTriggerType.LibraryUpdated),
				) {
					val request = GetLatestMediaRequest(
						fields = ItemRepository.browseFields,
						imageTypeLimit = 1,
						parentId = view.id,
						groupItems = true,
						limit = ITEM_LIMIT_LATEST,
					)

					api.userLibraryApi.getLatestMedia(request).content.map {
						BaseItemDtoBaseRowItem(it, preferParentThumb, true, BaseRowItemSelectAction.ShowDetails, preferParentThumb)
					}
				}
			}
	}

	private fun liveTvButtonsDef(user: UserDto) = RowDef(
		key = "livetv-buttons",
		title = title(R.string.pref_live_tv_cat),
		cardHeight = CARD_HEIGHT,
		triggers = emptyList(),
	) {
		buildList {
			add(GridButtonBaseRowItem(GridButton(LiveTvOption.LIVE_TV_GUIDE_OPTION_ID, title(R.string.lbl_live_tv_guide))))
			add(GridButtonBaseRowItem(GridButton(LiveTvOption.LIVE_TV_RECORDINGS_OPTION_ID, title(R.string.lbl_recorded_tv))))
			if (Utils.canManageRecordings(user)) {
				add(GridButtonBaseRowItem(GridButton(LiveTvOption.LIVE_TV_SCHEDULE_OPTION_ID, title(R.string.lbl_schedule))))
				add(GridButtonBaseRowItem(GridButton(LiveTvOption.LIVE_TV_SERIES_OPTION_ID, title(R.string.lbl_series))))
			}
		}
	}

	private suspend fun resumeItems(mediaTypes: Collection<MediaType>): List<BaseRowItem> {
		val query = GetResumeItemsRequest(
			limit = ITEM_LIMIT_RESUME,
			fields = ItemRepository.browseFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			mediaTypes = mediaTypes,
			excludeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
		)

		return api.itemsApi.getResumeItems(query).content.items.map {
			BaseItemDtoBaseRowItem(it, preferParentThumb = false, staticHeight = true)
		}
	}

	private suspend fun nextUpItems(): List<BaseRowItem> {
		val query = GetNextUpRequest(
			imageTypeLimit = 1,
			limit = ITEM_LIMIT_NEXT_UP,
			enableResumable = false,
			fields = ItemRepository.browseFields,
		)
		val preferParentThumb = userPreferences[UserPreferences.seriesThumbnailsEnabled]

		return api.tvShowsApi.getNextUp(query).content.items.map {
			BaseItemDtoBaseRowItem(it, preferParentThumb, true)
		}
	}

	private suspend fun recordingsItems(): List<BaseRowItem> {
		val query = GetRecordingsRequest(
			fields = ItemRepository.itemFields,
			enableImages = true,
			limit = ITEM_LIMIT_RECORDINGS,
		)

		return api.liveTvApi.getRecordings(query).content.items.map {
			BaseItemDtoBaseRowItem(it, false, true)
		}
	}

	private suspend fun onNowItems(): List<BaseRowItem> {
		val query = GetRecommendedProgramsRequest(
			isAiring = true,
			fields = ItemRepository.itemFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			limit = ITEM_LIMIT_ON_NOW,
		)

		return api.liveTvApi.getRecommendedPrograms(query).content.items.map {
			BaseItemDtoBaseRowItem(it, false, true)
		}
	}

	private fun userViewsItems(views: Collection<BaseItemDto>): List<BaseRowItem> =
		views.map { BaseItemDtoBaseRowItem(it, staticHeight = true) }

	private fun title(resId: Int) = context.getString(resId)

	private fun triggersOf(vararg triggers: ChangeTriggerType) = triggers.toList()

	private companion object {
		private const val CARD_HEIGHT = 150
		private const val CARD_HEIGHT_LARGE = 126
		private const val CARD_HEIGHT_SMALL = 75

		private const val ITEM_LIMIT_RESUME = 50
		private const val ITEM_LIMIT_RECORDINGS = 40
		private const val ITEM_LIMIT_NEXT_UP = 50
		private const val ITEM_LIMIT_ON_NOW = 20
		private const val ITEM_LIMIT_LATEST = 50

		private val EXCLUDED_COLLECTION_TYPES = setOf(
			CollectionType.PLAYLISTS,
			CollectionType.LIVETV,
			CollectionType.BOXSETS,
			CollectionType.BOOKS,
		)
	}
}
