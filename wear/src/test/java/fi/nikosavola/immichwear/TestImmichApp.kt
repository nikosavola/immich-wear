package fi.nikosavola.immichwear

import fi.nikosavola.immichwear.data.FakeApiKeyCipher
import fi.nikosavola.immichwear.di.AppContainer

/**
 * Swaps in a [FakeApiKeyCipher] since the real Android Keystore provider isn't registered under
 * Robolectric/JVM tests. Used only via `@Config(application = TestImmichApp::class)` on
 * [EndToEndFlowTest] - production always uses the real [ImmichApp].
 */
class TestImmichApp : ImmichApp() {
  override val appContainer: AppContainer by lazy {
    AppContainer(this, apiKeyCipher = FakeApiKeyCipher())
  }
}
