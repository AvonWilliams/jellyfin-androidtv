package org.jellyfin.androidtv.ui.browsing.browsemodes

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.request.GetItemsRequest
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
 * Fetches how many titles match each filter value, cached in memory and fetched in parallel so
 * the badges load quickly. `request` builds a `GetItemsRequest` (limit=0) for a single value.
 */
internal suspend fun fetchItemCounts(
	api: ApiClient,
	cacheKey: String,
	values: List<String>,
	request: (String) -> GetItemsRequest,
): Map<String, Int> {
	PickerCountsCache.get(cacheKey)?.let { return it }

	val counts = coroutineScope {
		values.map { value ->
			async {
				try {
					val result = api.itemsApi.getItems(request(value))
					value to (result.content.totalRecordCount ?: 0)
				} catch (_: Exception) {
					value to 0
				}
			}
		}.awaitAll().toMap()
	}

	PickerCountsCache.put(cacheKey, counts)
	return counts
}

/** Cache key helper: scopes counts to a library id + a filter label. */
internal fun countCacheKey(libraryId: UUID, filter: String): String = "$libraryId:$filter"
