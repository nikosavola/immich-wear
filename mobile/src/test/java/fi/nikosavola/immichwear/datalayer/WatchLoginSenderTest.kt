package fi.nikosavola.immichwear.datalayer

import org.junit.Assert.assertEquals
import org.junit.Test

// toOutcome() is the one piece of WatchLoginSender that's a pure function of the watch's raw
// reply bytes - the rest is a real MessageClient/CapabilityClient round trip, which needs Play
// Services and is exercised on-device instead (see the real-device verification in this feature's
// history, not an automated test here).
class WatchLoginSenderTest {
  @Test
  fun `toOutcome maps a successful reply with stats`() {
    val payload =
      """{"success":true,"stats":{"total":7,"images":5,"videos":2}}""".encodeToByteArray()

    val outcome = toOutcome(payload)

    assertEquals(LoginOutcome.Success(LoginStats(total = 7, images = 5, videos = 2)), outcome)
  }

  @Test
  fun `toOutcome maps a successful reply without stats`() {
    val payload = """{"success":true}""".encodeToByteArray()

    val outcome = toOutcome(payload)

    assertEquals(LoginOutcome.Success(stats = null), outcome)
  }

  @Test
  fun `toOutcome maps a failure reply to its message`() {
    val payload = """{"success":false,"errorMessage":"Invalid API key"}""".encodeToByteArray()

    val outcome = toOutcome(payload)

    assertEquals(LoginOutcome.Failure("Invalid API key"), outcome)
  }

  @Test
  fun `toOutcome falls back to a generic message when the watch sends none`() {
    val payload = """{"success":false}""".encodeToByteArray()

    val outcome = toOutcome(payload)

    assertEquals(LoginOutcome.Failure("Unknown error"), outcome)
  }

  @Test
  fun `toOutcome maps malformed payloads to SendFailed instead of throwing`() {
    val outcome = toOutcome("not json".encodeToByteArray())

    assertEquals(LoginOutcome.SendFailed, outcome)
  }
}
