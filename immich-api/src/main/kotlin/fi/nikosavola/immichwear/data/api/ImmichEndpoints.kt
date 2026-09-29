package fi.nikosavola.immichwear.data.api

// Retrofit needs a syntactically valid absolute base URL at build time, but the real server
// address is runtime config (self-hosted, entered in Settings). ImmichApiFactory builds Retrofit
// against this placeholder host, then DynamicBaseUrlInterceptor rewrites every outgoing request to
// the real server. Coil image requests use the same placeholder + the same OkHttpClient (see
// AppContainer), so a thumbnail URL can be built here from just an asset id, with no dependency on
// the live settings value.
const val PLACEHOLDER_BASE_URL = "http://immich.invalid/"

/** Wire values match [AssetMediaSize] in the Immich OpenAPI spec. */
enum class AssetThumbnailSize(internal val wireValue: String) {
  /** Small square crop, for grid rows. */
  THUMBNAIL("thumbnail"),

  /**
   * Larger, aspect-correct image, for the full-screen asset viewer. Deliberately not
   * `original`/`fullsize`: those can be multi-megabyte RAW/HEIC files, the wrong trade for a watch
   * radio and a small screen.
   */
  PREVIEW("preview"),
}

fun thumbnailUrl(assetId: String, size: AssetThumbnailSize): String =
  "${PLACEHOLDER_BASE_URL}api/assets/$assetId/thumbnail?size=${size.wireValue}"
