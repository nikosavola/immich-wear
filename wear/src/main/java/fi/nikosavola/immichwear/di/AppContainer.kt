package fi.nikosavola.immichwear.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.Settings
import fi.nikosavola.immichwear.data.SettingsStore
import fi.nikosavola.immichwear.data.api.createImmichClients
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

private const val SETTINGS_DATASTORE_FILE_NAME = "settings.preferences_pb"

/**
 * Manual DI root: no Hilt/Koin. Built once by [fi.nikosavola.immichwear.ImmichApp] and handed down
 * to composables.
 *
 * @param context used to locate [Context.getFilesDir] and to build [imageLoader]; retained safely
 *   since in production this is always the [android.app.Application] instance, which lives for the
 *   whole process, never an Activity.
 */
class AppContainer(context: Context) {
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val settingsStore: SettingsStore =
    SettingsStore(
      PreferenceDataStoreFactory.create(
        produceFile = { File(context.filesDir, SETTINGS_DATASTORE_FILE_NAME) }
      )
    )

  private val clients =
    createImmichClients(
      apiKey = settingsStore.apiKeySupplier,
      serverBaseUrl = settingsStore.serverUrlSupplier,
    )

  val repository: ImmichRepository = ImmichRepository(clients.api, settingsStore)

  // Shares clients.okHttpClient (api-key header + dynamic base URL rewrite) with every thumbnail
  // request, so a Coil AsyncImage(model = thumbnailUrl(id, size)) call is authenticated for free
  // and never needs to know the live server URL - see ImmichEndpoints.
  val imageLoader: ImageLoader =
    ImageLoader.Builder(context)
      .components { add(OkHttpNetworkFetcherFactory(callFactory = { clients.okHttpClient })) }
      .build()

  // SettingsStore's apiKeySupplier/serverUrlSupplier read in-memory mirrors that start null until
  // something reads `settings`. A cold start could otherwise send the first request with no
  // x-api-key header and against the unreachable placeholder host. Priming here, once, up front
  // removes the ordering dependency on which screen/ViewModel happens to run first.
  val settingsPrimed: Deferred<Settings> = applicationScope.async {
    settingsStore.currentSettings()
  }
}
