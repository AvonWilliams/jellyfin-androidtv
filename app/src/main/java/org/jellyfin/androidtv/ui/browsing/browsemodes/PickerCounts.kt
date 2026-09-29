package org.jellyfin.androidtv.ui.browsing.browsemodes

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID

/** In-memory cache of picker item counts, keyed by (library, filter type). */
internal object PickerCountsCache {
	private val cache = mutableMapOf<String, Map<String, Int>>()

	fun get(key: String): Map<String, Int>? = cache[key]

	fun put(key: String, counts: Map<String, Int>) {
		cache[key] = counts
	}
}

/**
 * Fetches per-value item counts from the server's /Discover/Counts endpoint in one request.
 * Cached in memory; falls back to an empty map when the plugin endpoint is unavailable.
 */
internal suspend fun fetchItemCounts(
	api: ApiClient,
	cacheKey: String,
	type: String,
	parentId: UUID,
	itemType: BaseItemKind,
): Map<String, Int> {
	PickerCountsCache.get(cacheKey)?.let { return it }

	val counts = try {
		api.get<Map<String, Int>>(
			pathTemplate = "/Discover/Counts",
			queryParameters = mapOf(
				"type" to type,
				"parentId" to parentId.toString(),
				"itemTypes" to itemType.serialName,
			),
		).content
	} catch (error: Exception) {
		Timber.e(error, "Failed to fetch picker counts from /Discover/Counts (type=%s)", type)
		emptyMap()
	}

	PickerCountsCache.put(cacheKey, counts)
	return counts
}

/** Cache key helper: scopes counts to a library id + a filter label. */
internal fun countCacheKey(libraryId: UUID, filter: String): String = "$libraryId:$filter"
