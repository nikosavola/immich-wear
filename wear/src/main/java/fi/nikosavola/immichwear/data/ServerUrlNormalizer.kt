package fi.nikosavola.immichwear.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://")

/**
 * Turns whatever a user types into Settings into the exact form [fi.nikosavola.immichwear.data.api]
 * expects: `scheme://host[:port][/reverseProxyPrefix]/`, with any trailing `/api` suffix stripped.
 *
 * Handles the two mistakes real input reliably makes:
 * - No scheme (`myserver.local:2283`) - defaults to `https://`. A user who genuinely runs a
 *   cleartext LAN server (common for self-hosted Immich) must type `http://` explicitly; that
 *   scheme is still accepted (see the manifest's `usesCleartextTraffic`) rather than rejected,
 *   since blocking it outright would break the single most common self-hosting setup.
 * - A trailing `/api` (`https://myserver.local/api`) - Immich's own mobile app instructs users to
 *   enter the URL *including* `/api`, so this is the highest-frequency first-run failure if left
 *   unhandled. [fi.nikosavola.immichwear.data.api.ImmichApi] paths already start with `api/`, so a
 *   stored `/api` prefix would double up to `/api/api/...`.
 *
 * Returns null for blank input or anything that doesn't parse as an http(s) URL.
 */
fun normalizeServerUrl(rawInput: String): String? {
  val trimmed = rawInput.trim()
  if (trimmed.isEmpty()) return null

  val withScheme = if (SCHEME_PREFIX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
  return withScheme
    .toHttpUrlOrNull()
    ?.takeIf { it.scheme == "http" || it.scheme == "https" }
    ?.let { url ->
      val prefixPath = url.encodedPath.removeSuffix("/").removeSuffix("/api")
      url.newBuilder().encodedPath("$prefixPath/").query(null).fragment(null).build().toString()
    }
}
