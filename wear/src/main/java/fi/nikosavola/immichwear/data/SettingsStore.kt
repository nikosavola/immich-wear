package fi.nikosavola.immichwear.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * All persisted settings as one value. A single [Flow] of this data class is exposed instead of
 * separate flows per field: repository code almost always needs serverUrl and apiKey together, and
 * combining several independent flows at every call site would be more boilerplate than one read.
 */
data class Settings(
  val serverUrl: String? = null,
  val apiKey: String? = null,
  val email: String? = null,
)

/**
 * Wraps a Preferences [DataStore] supplied by the caller (never a `Context` directly) so tests can
 * point it at a temp file.
 *
 * The API key is encrypted at rest with [apiKeyCipher] before it reaches [dataStore]; see
 * [ApiKeyCipher] for why and how. The server URL is not a secret and is stored in plaintext, same
 * as the account email.
 */
class SettingsStore(
  private val dataStore: DataStore<Preferences>,
  private val apiKeyCipher: ApiKeyCipher = AndroidKeystoreApiKeyCipher(),
) {
  private val serverUrlKey = stringPreferencesKey("server_url")
  private val apiKeyKey = stringPreferencesKey("api_key")
  private val emailKey = stringPreferencesKey("email")

  // ImmichApiFactory needs synchronous `() -> String?` suppliers for its OkHttp interceptors,
  // which run on an OkHttp dispatcher thread and must never block on a DataStore Flow. These
  // fields mirror the latest known values so the suppliers can read them synchronously; kept
  // current by every read of `settings` and every write through this class. A caller that writes
  // only via `setServerUrl`/`setApiKey` and otherwise never collects `settings` (e.g. a future DI
  // container at cold start) should collect `settings` once, or call `currentSettings()`, before
  // issuing the first request so these caches are primed.
  @Volatile private var cachedServerUrl: String? = null

  @Volatile private var cachedApiKey: String? = null

  val settings: Flow<Settings> =
    dataStore.data.map { prefs ->
      Settings(
          serverUrl = prefs[serverUrlKey],
          apiKey = prefs[apiKeyKey]?.let(apiKeyCipher::decrypt),
          email = prefs[emailKey],
        )
        .also {
          cachedServerUrl = it.serverUrl
          cachedApiKey = it.apiKey
        }
    }

  /** Synchronous bridge for `createImmichClients`'s `serverBaseUrl: () -> String?` parameter. */
  val serverUrlSupplier: () -> String? = { cachedServerUrl }

  /** Synchronous bridge for `createImmichClients`'s `apiKey: () -> String?` parameter. */
  val apiKeySupplier: () -> String? = { cachedApiKey }

  suspend fun currentSettings(): Settings = settings.first()

  suspend fun setServerUrl(serverUrl: String?) {
    cachedServerUrl = serverUrl
    setOrRemove(serverUrlKey, serverUrl)
  }

  suspend fun setApiKey(apiKey: String?) {
    cachedApiKey = apiKey
    setOrRemove(apiKeyKey, apiKey?.let(apiKeyCipher::encrypt))
  }

  suspend fun setEmail(email: String?) = setOrRemove(emailKey, email)

  /** Clears all persisted settings, e.g. on sign-out. */
  suspend fun clear() {
    cachedServerUrl = null
    cachedApiKey = null
    dataStore.edit { it.clear() }
  }

  private suspend fun setOrRemove(key: Preferences.Key<String>, value: String?) {
    dataStore.edit { prefs -> if (value == null) prefs.remove(key) else prefs[key] = value }
  }
}
