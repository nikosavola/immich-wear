package fi.nikosavola.immichwear.data.api

import kotlinx.serialization.json.Json

// Shared between the Retrofit converter and any standalone (de)serialization, e.g. tests.
// ignoreUnknownKeys = true because AssetResponseDto etc. carry far more fields than this client
// reads (people, tags, stack, exif...); explicitNulls = false so optional request fields (e.g.
// MetadataSearchRequest.albumIds) are omitted rather than sent as JSON null.
// encodeDefaults = true: without it, MetadataSearchRequest.order would be silently dropped from
// the wire whenever it's set to its own default ("desc"), leaving pagination ordering to the
// server's undocumented default instead of the explicit value the repository asked for.
val immichJson: Json = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
  encodeDefaults = true
}
