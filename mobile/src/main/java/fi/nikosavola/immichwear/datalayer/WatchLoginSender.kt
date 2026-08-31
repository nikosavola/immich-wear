package fi.nikosavola.immichwear.datalayer

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** What happened after [WatchLoginSender.send] returned. */
sealed interface LoginOutcome {
  /**
   * [stats] is null if the watch's own follow-up stats fetch failed - the login still succeeded.
   */
  data class Success(val stats: LoginStats?) : LoginOutcome

  data class Failure(val message: String) : LoginOutcome

  /**
   * No node reachable is advertising [LOGIN_CAPABILITY] - Immich Wear isn't installed, or the watch
   * isn't currently connected.
   */
  data object NoWatchFound : LoginOutcome

  /** The message never got a reply within [RESULT_TIMEOUT_MS], or the send itself failed. */
  data object SendFailed : LoginOutcome
}

private const val RESULT_TIMEOUT_MS = 15_000L

/**
 * Sends [LoginRequest] to a paired watch running Immich Wear over the Wear OS Data Layer, and waits
 * for its [LoginResult] reply on [LOGIN_RESULT_PATH]. See PhoneLoginListenerService on the watch
 * side for the other end of this exchange.
 */
class WatchLoginSender(private val context: Context) {
  suspend fun send(serverUrl: String, apiKey: String): LoginOutcome {
    val capabilityClient = Wearable.getCapabilityClient(context)
    val messageClient = Wearable.getMessageClient(context)

    val nodeId =
      try {
        capabilityClient
          .getCapability(LOGIN_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
          .await()
          .nodes
          .firstOrNull()
          ?.id
      } catch (e: ApiException) {
        null
      } ?: return LoginOutcome.NoWatchFound

    val payload = immichJsonEncode(LoginRequest(serverUrl = serverUrl, apiKey = apiKey))

    return try {
      withTimeoutOrNull(RESULT_TIMEOUT_MS) {
        awaitResultAfterSending(messageClient, nodeId, payload)
      } ?: LoginOutcome.SendFailed
    } catch (e: ApiException) {
      LoginOutcome.SendFailed
    }
  }

  // The listener is registered before sendMessage() to avoid missing a reply that arrives before
  // registration would otherwise complete.
  private suspend fun awaitResultAfterSending(
    messageClient: MessageClient,
    nodeId: String,
    payload: ByteArray,
  ): LoginOutcome = suspendCancellableCoroutine { continuation ->
    val listener = ResultListener(messageClient, continuation)
    messageClient.addListener(listener)
    messageClient.sendMessage(nodeId, LOGIN_REQUEST_PATH, payload)
    continuation.invokeOnCancellation { messageClient.removeListener(listener) }
  }
}

// A named class (not a lambda holding a lateinit self-reference) so it can unregister itself via
// `this` once its one reply arrives.
private class ResultListener(
  private val messageClient: MessageClient,
  private val continuation: CancellableContinuation<LoginOutcome>,
) : MessageClient.OnMessageReceivedListener {
  override fun onMessageReceived(event: MessageEvent) {
    if (event.path == LOGIN_RESULT_PATH && continuation.isActive) {
      messageClient.removeListener(this)
      // No cancellation cleanup needed here beyond what awaitResultAfterSending's own
      // invokeOnCancellation already does (removing this listener), hence the empty lambda.
      continuation.resume(toOutcome(event.data)) { _, _, _ -> }
    }
  }
}

// internal (not private): unit-tested directly in WatchLoginSenderTest without needing Play
// Services, since it's the only piece of the phone->watch round trip that's a pure function.
internal fun toOutcome(payload: ByteArray): LoginOutcome {
  val result = immichJsonDecode(payload) ?: return LoginOutcome.SendFailed
  return if (result.success) {
    LoginOutcome.Success(stats = result.stats)
  } else {
    LoginOutcome.Failure(result.errorMessage ?: "Unknown error")
  }
}
