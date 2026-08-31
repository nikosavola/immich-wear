package fi.nikosavola.immichwear.datalayer

import android.content.Context
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.api.immichJson
import fi.nikosavola.immichwear.ui.errorMessage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Decodes a [LoginRequest] from the phone companion app, attempts [ImmichRepository.connect] with
 * it - the exact path Settings itself uses, so encryption-at-rest and the rollback-on-failure
 * guarantee both apply here too - and encodes the [LoginResult] to reply with.
 *
 * Kept separate from [PhoneLoginListenerService] so this decision logic is unit-testable without
 * Play Services or a real `WearableListenerService`, neither of which Robolectric provides.
 */
class PhoneLoginHandler(private val repository: ImmichRepository, private val context: Context) {
  suspend fun handle(payload: ByteArray): ByteArray {
    val request =
      try {
        immichJson.decodeFromString<LoginRequest>(payload.decodeToString())
      } catch (e: SerializationException) {
        return encode(LoginResult(success = false, errorMessage = "Malformed request from phone"))
      }
    val result =
      when (val outcome = repository.connect(request.serverUrl, request.apiKey)) {
        is ImmichResult.Success -> LoginResult(success = true, stats = fetchStats())
        is ImmichResult.Failure ->
          LoginResult(success = false, errorMessage = errorMessage(context, outcome.error))
      }
    return encode(result)
  }

  // Best-effort: a failed stats fetch doesn't fail the login the user is actually waiting on, it
  // just leaves the phone's summary blank - same silent-degrade pattern as Home's hero photo.
  private suspend fun fetchStats(): LoginStats? =
    when (val result = repository.assetStatistics()) {
      is ImmichResult.Success ->
        LoginStats(
          total = result.value.total,
          images = result.value.images,
          videos = result.value.videos,
        )
      is ImmichResult.Failure -> null
    }

  private fun encode(result: LoginResult): ByteArray =
    immichJson.encodeToString(result).encodeToByteArray()
}
