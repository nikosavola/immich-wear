package fi.nikosavola.immichwear.datalayer

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import fi.nikosavola.immichwear.ImmichApp
import kotlinx.coroutines.runBlocking

/**
 * Receives a [LoginRequest] sent by the phone companion app and replies on [LOGIN_RESULT_PATH] with
 * the outcome - see [PhoneLoginHandler] for the actual connect logic.
 *
 * `onMessageReceived` already runs off the main thread (a Play Services binder thread), and
 * [fi.nikosavola.immichwear.data.ImmichRepository.connect] is a single bounded HTTP call (OkHttp's
 * own connect/read timeouts apply), so blocking here with [runBlocking] is the pragmatic choice
 * over standing up a longer-lived coroutine scope for a one-shot service callback.
 */
class PhoneLoginListenerService : WearableListenerService() {
  override fun onMessageReceived(event: MessageEvent) {
    if (event.path != LOGIN_REQUEST_PATH) return
    val appContainer = (application as ImmichApp).appContainer
    val handler = PhoneLoginHandler(appContainer.repository, applicationContext)
    val resultPayload = runBlocking { handler.handle(event.data) }
    Wearable.getMessageClient(this)
      .sendMessage(event.sourceNodeId, LOGIN_RESULT_PATH, resultPayload)
  }
}
