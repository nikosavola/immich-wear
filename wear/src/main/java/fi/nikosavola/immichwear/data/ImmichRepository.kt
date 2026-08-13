package fi.nikosavola.immichwear.data

import fi.nikosavola.immichwear.data.api.ImmichApi
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.data.api.dto.MetadataSearchRequest
import fi.nikosavola.immichwear.data.api.dto.UserDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

private const val HTTP_UNAUTHORIZED = 401

// Small enough that a single page renders quickly on a watch radio, large enough that scrolling
// to the bottom of the first page is uncommon.
internal const val TIMELINE_PAGE_SIZE = 30

/**
 * One page of the recent-photos timeline. [nextPage] is null when there is nothing more to load.
 */
data class TimelinePage(val items: List<AssetDto>, val nextPage: Int?)

/**
 * The single repository for Immich data: wraps [api] with settings-backed server address/identity.
 * Every public function returns [ImmichResult] instead of throwing for the expected failure modes
 * (auth, offline, parse). Coroutine cancellation is the one exception: every catch block below
 * rethrows [CancellationException] before anything else, so a cancelled caller sees cancellation,
 * not a wrapped "offline" failure.
 */
class ImmichRepository(private val api: ImmichApi, private val settingsStore: SettingsStore) {
  /**
   * Normalizes and persists [serverUrlInput] and [apiKey], then validates them with `GET
   * /users/me`. Never leaves an unvalidated or rejected server/key pair persisted.
   */
  suspend fun connect(serverUrlInput: String, apiKey: String): ImmichResult<UserDto> {
    val normalizedUrl =
      normalizeServerUrl(serverUrlInput)
        ?: return ImmichResult.Failure(ImmichError.InvalidServerUrl)

    settingsStore.setServerUrl(normalizedUrl)
    settingsStore.setApiKey(apiKey)

    return when (val userResult = runCatchingImmich { api.getCurrentUser() }) {
      is ImmichResult.Failure -> {
        settingsStore.setServerUrl(null)
        settingsStore.setApiKey(null)
        userResult
      }
      is ImmichResult.Success -> {
        settingsStore.setEmail(userResult.value.email)
        userResult
      }
    }
  }

  suspend fun signOut() {
    settingsStore.clear()
  }

  /**
   * Fetches one page of the recent-photos timeline, newest first. [page] is the opaque page number
   * from a previous [TimelinePage.nextPage]; null fetches the first page.
   */
  suspend fun timeline(page: Int? = null): ImmichResult<TimelinePage> =
    when (val configured = requireConfigured()) {
      is ImmichResult.Failure -> configured
      is ImmichResult.Success ->
        runCatchingImmich {
          val response =
            api.searchMetadata(MetadataSearchRequest(size = TIMELINE_PAGE_SIZE, page = page))
          TimelinePage(
            items = response.assets.items,
            nextPage = response.assets.nextPage?.toIntOrNull(),
          )
        }
    }

  private suspend fun requireConfigured(): ImmichResult<Unit> {
    val settings = settingsStore.currentSettings()
    return if (settings.serverUrl != null && settings.apiKey != null) {
      ImmichResult.Success(Unit)
    } else {
      ImmichResult.Failure(ImmichError.NotConfigured)
    }
  }
}

internal suspend fun <T> runCatchingImmich(block: suspend () -> T): ImmichResult<T> =
  try {
    ImmichResult.Success(block())
  } catch (e: CancellationException) {
    // Coroutine cancellation must propagate, never be swallowed as an API failure.
    throw e
  } catch (e: HttpException) {
    ImmichResult.Failure(httpError(e.code()))
  } catch (e: SerializationException) {
    ImmichResult.Failure(ImmichError.ParseError)
  } catch (e: IOException) {
    ImmichResult.Failure(ImmichError.Offline)
  }

internal fun httpError(code: Int): ImmichError =
  if (code == HTTP_UNAUTHORIZED) ImmichError.Unauthorized else ImmichError.Http(code)
