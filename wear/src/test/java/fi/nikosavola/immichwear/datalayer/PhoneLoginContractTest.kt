package fi.nikosavola.immichwear.datalayer

import fi.nikosavola.immichwear.data.api.immichJson
import org.junit.Assert.assertEquals
import org.junit.Test

// Pins the wire format against :mobile's independent copy of LoginRequest/LoginResult (see the
// comment on PhoneLoginContract.kt) - a field rename here without updating the other module's
// copy would otherwise fail silently at runtime instead of at build/test time.
class PhoneLoginContractTest {
  @Test
  fun `LoginRequest serializes with the field names the phone app expects`() {
    val json =
      immichJson.encodeToString(
        LoginRequest.serializer(),
        LoginRequest(serverUrl = "https://immich.example.com", apiKey = "secret"),
      )

    assertEquals("""{"serverUrl":"https://immich.example.com","apiKey":"secret"}""", json)
  }

  @Test
  fun `LoginResult deserializes the field names the phone app sends`() {
    val result =
      immichJson.decodeFromString(
        LoginResult.serializer(),
        """{"success":false,"errorMessage":"Offline"}""",
      )

    assertEquals(LoginResult(success = false, errorMessage = "Offline"), result)
  }

  @Test
  fun `LoginResult with stats serializes with the field names the phone app expects`() {
    val json =
      immichJson.encodeToString(
        LoginResult.serializer(),
        LoginResult(success = true, stats = LoginStats(total = 7, images = 5, videos = 2)),
      )

    assertEquals("""{"success":true,"stats":{"total":7,"images":5,"videos":2}}""", json)
  }
}
