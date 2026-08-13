package fi.nikosavola.immichwear

import android.app.Application
import coil3.SingletonImageLoader
import fi.nikosavola.immichwear.di.AppContainer

open class ImmichApp : Application() {
  // by lazy instead of lateinit-in-onCreate: Application's Context is valid before onCreate()
  // runs (attachBaseContext already completed), and MainActivity is always created after
  // onCreate(), so deferring construction to first access needs no manual init-order bookkeeping.
  // open so a Robolectric test Application subclass can swap in an AppContainer built with a fake
  // ApiKeyCipher - the real Android Keystore provider isn't available under Robolectric/JVM tests.
  open val appContainer: AppContainer by lazy { AppContainer(this) }

  override fun onCreate() {
    super.onCreate()
    // setSafe (not set): the singleton may already be set if this Application is recreated
    // in-process (e.g. after a config change), and set() would throw on a second call.
    SingletonImageLoader.setSafe { appContainer.imageLoader }
  }
}
