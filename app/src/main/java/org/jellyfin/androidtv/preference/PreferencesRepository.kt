package org.jellyfin.androidtv.preference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.ApiClient
import kotlin.collections.set

/**
 * Repository to access special preference stores.
 */
class PreferencesRepository(
	private val api: ApiClient,
	private val liveTvPreferences: LiveTvPreferences,
	private val userSettingPreferences: UserSettingPreferences,
) {
	private val libraryPreferences = mutableMapOf<String, LibraryPreferences>()

	suspend fun getLibraryPreferences(preferencesId: String): LibraryPreferences {
		val store = libraryPreferences[preferencesId] ?: LibraryPreferences(preferencesId, api)

		libraryPreferences[preferencesId] = store

		if (store.shouldUpdate) store.update()

		return store
	}

	fun getLibraryPreferencesBlocking(preferencesId: String): LibraryPreferences =
		runBlocking(Dispatchers.IO) { getLibraryPreferences(preferencesId) }

	suspend fun onSessionChanged() {
		// Note: Do not run parallel as the server can't deal with that
		// Relevant server issue: https://github.com/jellyfin/jellyfin/issues/5261
		liveTvPreferences.update()
		userSettingPreferences.update()

		libraryPreferences.clear()
	}
}
