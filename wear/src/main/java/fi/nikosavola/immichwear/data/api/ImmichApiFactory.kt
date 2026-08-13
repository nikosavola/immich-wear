package fi.nikosavola.immichwear.data.api

import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val API_KEY_HEADER = "x-api-key"
private const val CONNECT_TIMEOUT_SECONDS = 10L
private const val READ_WRITE_TIMEOUT_SECONDS = 30L

private val jsonMediaType = "application/json".toMediaType()

/**
 * Bundles the Retrofit client with the OkHttpClient it runs on, so Coil (see AppContainer) can
 * share the exact same authenticated, base-URL-aware pipeline for image requests instead of
 * duplicating the header/host logic.
 */
class ImmichClients(val api: ImmichApi, val okHttpClient: OkHttpClient)

/**
 * @param apiKey read on every request, not captured once: the key lives in user-editable settings
 *   and can change (or be cleared) while the client is alive.
 * @param serverBaseUrl same reasoning as [apiKey], but for the self-hosted server address. Expected
 *   to already be normalized (scheme + host + optional path prefix, single trailing slash) by
 *   SettingsStore; see its serverUrl normalizer.
 */
fun createImmichClients(apiKey: () -> String?, serverBaseUrl: () -> String?): ImmichClients {
  val okHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .readTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .writeTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .addInterceptor(ApiKeyInterceptor(apiKey))
      .addInterceptor(DynamicBaseUrlInterceptor(serverBaseUrl))
      .build()

  val retrofit =
    Retrofit.Builder()
      .baseUrl(PLACEHOLDER_BASE_URL)
      .client(okHttpClient)
      .addConverterFactory(immichJson.asConverterFactory(jsonMediaType))
      .build()

  return ImmichClients(retrofit.create(ImmichApi::class.java), okHttpClient)
}

private class ApiKeyInterceptor(private val apiKey: () -> String?) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val key = apiKey() ?: return chain.proceed(chain.request())
    val request = chain.request().newBuilder().addHeader(API_KEY_HEADER, key).build()
    return chain.proceed(request)
  }
}

/**
 * Rewrites every request built against [PLACEHOLDER_BASE_URL] to target the real, user-configured
 * server. [target]'s path (if any, e.g. a reverse-proxy subpath like `/immich/`) is prepended to
 * the request's own path rather than replacing it, since Retrofit's `@GET("api/...")` paths are
 * already relative to the server root.
 *
 * If [baseUrl] returns null (not configured yet) or an unparseable value, the request proceeds
 * unchanged against the placeholder host, which fails as a plain [java.io.IOException] (DNS
 * resolution failure) - the same failure shape as being offline. Callers are expected to check
 * settings before issuing a request (see ImmichRepository's requireServerConfigured), so this is a
 * defensive fallback, not the primary guard.
 */
private class DynamicBaseUrlInterceptor(private val baseUrl: () -> String?) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val target = baseUrl()?.toHttpUrlOrNull() ?: return chain.proceed(request)

    val pathPrefix = target.encodedPath.removeSuffix("/")
    val rewrittenUrl =
      request.url
        .newBuilder()
        .scheme(target.scheme)
        .host(target.host)
        .port(target.port)
        .encodedPath(pathPrefix + request.url.encodedPath)
        .build()
    return chain.proceed(request.newBuilder().url(rewrittenUrl).build())
  }
}
