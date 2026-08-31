import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  // kotlin.android removed: AGP 9 built-in Kotlin (version pinned in the root buildscript
  // classpath).
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Real secrets come from keystore.properties (gitignored) or RELEASE_STORE_* env vars (CI); see
// keystore.properties.example. Shared verbatim with :mobile/build.gradle.kts so a real release
// build signs both apps with the same key - required for the Wear OS Data Layer to route
// messages between them at all, and for Play Store to accept them as a matching phone/watch pair.
val keystoreProps =
  Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
  }

fun keystoreProp(key: String): String? = keystoreProps.getProperty(key) ?: System.getenv(key)

android {
  namespace = "fi.nikosavola.immichwear"
  compileSdk = 36

  defaultConfig {
    applicationId = "fi.nikosavola.immichwear"
    minSdk = 30
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
  }

  // "direct" keeps on-watch server URL/API key entry (Settings) for anyone building/sideloading
  // from source. "playstore" strips it via BuildConfig.SUPPORTS_DIRECT_WATCH_LOGIN, satisfying
  // the Play Store Wear OS quality guideline against typing credentials on a watch - login there
  // is companion-app-only. Neither flavor gets an applicationIdSuffix: whichever one ships must
  // keep the exact applicationId the :mobile app expects (see PhoneLoginContract.kt).
  flavorDimensions += "distribution"
  productFlavors {
    create("direct") { dimension = "distribution" }
    create("playstore") {
      dimension = "distribution"
      buildConfigField("boolean", "SUPPORTS_DIRECT_WATCH_LOGIN", "false")
    }
  }
  defaultConfig { buildConfigField("boolean", "SUPPORTS_DIRECT_WATCH_LOGIN", "true") }

  signingConfigs {
    create("release") {
      val storeFilePath = keystoreProp("RELEASE_STORE_FILE")
      if (storeFilePath != null) {
        storeFile = rootProject.file(storeFilePath)
        storePassword = keystoreProp("RELEASE_STORE_PASSWORD")
        keyAlias = keystoreProp("RELEASE_KEY_ALIAS")
        keyPassword = keystoreProp("RELEASE_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Falls back to debug signing so assembleRelease still works without keystore.properties -
      // replace with a real key before uploading to Play. See the comment above.
      signingConfig =
        if (keystoreProp("RELEASE_STORE_FILE") != null) {
          signingConfigs.getByName("release")
        } else {
          signingConfigs.getByName("debug")
        }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }

  // Robolectric needs merged manifest/resource info to resolve the app context it fakes.
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(libs.kotlin.stdlib)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.activity.compose)

  implementation(libs.wear.compose.material3)
  implementation(libs.wear.compose.foundation)
  implementation(libs.wear.compose.navigation)
  implementation(libs.wear.tiles)
  implementation(libs.wear.protolayout.core)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.kotlinx.serialization)
  implementation(libs.okhttp)

  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)

  implementation(libs.androidx.datastore.preferences)

  // PhoneLoginListenerService: receives server URL/API key from the :mobile companion app over
  // the Wear OS Data Layer. See ../mobile/build.gradle.kts for why the two apps must share this
  // applicationId.
  implementation(libs.play.services.wearable)
  implementation(libs.kotlinx.coroutines.play.services)

  testImplementation(libs.junit)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)
}
