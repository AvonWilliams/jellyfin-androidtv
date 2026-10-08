package org.jellyfin.androidtv.ui.browsing.browsemodes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PersonKind
import org.koin.android.ext.android.inject
import timber.log.Timber

/** The /Persons response, pared down to the fields a name list needs. */
@Serializable
private data class PersonsResponse(
	@SerialName("Items") val items: List<PersonEntry> = emptyList(),
)

@Serializable
private data class PersonEntry(
	@SerialName("Id") val id: String? = null,
	@SerialName("Name") val name: String? = null,
)

/**
 * The people of one kind (actor/director/writer) in a library, as a selectable text list.
 *
 * Tapping a person opens their detail page, mirroring how person rows are launched elsewhere
 * in the app.
 */
class PersonListFragment : Fragment() {
	private val apiClient by inject<ApiClient>()
	private val navigationRepository by inject<NavigationRepository>()
	private val userRepository by inject<UserRepository>()

	private lateinit var folder: BaseItemDto
	private lateinit var personType: String
	private val title = mutableStateOf("")
	private val items = mutableStateOf<List<BaseItemDto>>(emptyList())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		folder = Json.decodeFromString<BaseItemDto>(requireArguments().getString(Extras.Folder)!!)
		personType = requireArguments().getString(Extras.PersonType) ?: PersonKind.ACTOR.serialName

		title.value = when (personType) {
			PersonKind.DIRECTOR.serialName -> getString(R.string.lbl_browse_mode_directors)
			PersonKind.WRITER.serialName -> getString(R.string.lbl_browse_mode_writers)
			else -> getString(R.string.lbl_browse_mode_actors)
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setContent {
			JellyfinTheme {
				TextListGrid(title.value, items.value) { item -> onClick(item) }
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		load()
	}

	private fun onClick(item: BaseItemDto) {
		navigationRepository.navigate(Destinations.itemDetails(item.id))
	}

	private fun load() = lifecycleScope.launch {
		val persons = try {
			withContext(Dispatchers.IO) { fetchPersons() }
		} catch (error: Exception) {
			Timber.e(error, "Unable to load persons for %s / %s", folder.name, personType)
			emptyList()
		}

		if (!isAdded) return@launch

		items.value = persons
	}

	private suspend fun fetchPersons(): List<BaseItemDto> {
		val userId = userRepository.currentUser.value?.id ?: return emptyList()

		val response = apiClient.get<PersonsResponse>(
			pathTemplate = "/Persons",
			queryParameters = mapOf(
				"userId" to userId,
				"parentId" to folder.id,
				"personTypes" to personType,
				"enableImages" to "false",
				"limit" to "500",
			),
		)

		return response.content.items.mapNotNull { person ->
			val id = person.id ?: return@mapNotNull null
			val name = person.name ?: return@mapNotNull null
			buildJsonObject {
				put("Name", name)
				put("Id", id)
			}.toString().let { Json.decodeFromString<BaseItemDto>(it) }
		}
	}
}
