package fi.nikosavola.immichwear.datalayer

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Mirrors :wear's ImmichApi JSON config (ignoreUnknownKeys so either side can add fields later
// without breaking the other's parser); kept local since :mobile has no other JSON traffic to
// share a config with.
private val json = Json { ignoreUnknownKeys = true }

fun immichJsonEncode(request: LoginRequest): ByteArray =
  json.encodeToString(request).encodeToByteArray()

fun immichJsonDecode(payload: ByteArray): LoginResult? =
  try {
    json.decodeFromString<LoginResult>(payload.decodeToString())
  } catch (e: SerializationException) {
    null
  }
