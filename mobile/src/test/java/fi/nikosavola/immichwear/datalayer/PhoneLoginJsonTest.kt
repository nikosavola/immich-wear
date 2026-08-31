package fi.nikosavola.immichwear.datalayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pins the wire format against :wear's independent copy of LoginRequest/LoginResult (see the
// comment on PhoneLoginContract.kt).
class PhoneLoginJsonTest {
  @Test
  fun `immichJsonEncode produces the field names the watch expects`() {
    val payload =
      immichJsonEncode(LoginRequest(serverUrl = "https://immich.example.com", apiKey = "secret"))

    assertEquals(
      """{"serverUrl":"https://immich.example.com","apiKey":"secret"}""",
      payload.decodeToString(),
    )
  }

  @Test
  fun `immichJsonDecode parses a successful result from the watch`() {
    val result = immichJsonDecode("""{"success":true}""".encodeToByteArray())

    assertEquals(LoginResult(success = true), result)
  }

  @Test
  fun `immichJsonDecode parses a failed result with a message from the watch`() {
    val result =
      immichJsonDecode("""{"success":false,"errorMessage":"Offline"}""".encodeToByteArray())

    assertEquals(LoginResult(success = false, errorMessage = "Offline"), result)
  }

  @Test
  fun `immichJsonDecode returns null for malformed input instead of throwing`() {
    assertNull(immichJsonDecode("not json".encodeToByteArray()))
  }

  @Test
  fun `immichJsonDecode parses a successful result with stats from the watch`() {
    val result =
      immichJsonDecode(
        """{"success":true,"stats":{"total":7,"images":5,"videos":2}}""".encodeToByteArray()
      )

    assertEquals(
      LoginResult(success = true, stats = LoginStats(total = 7, images = 5, videos = 2)),
      result,
    )
  }
}
