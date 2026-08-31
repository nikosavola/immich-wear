import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  // kotlin.android removed: AGP 9 built-in Kotlin (version pinned in the root buildscript
  // classpath).
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Kept identical to :wear/build.gradle.kts's copy of this block - both apps must sign with the
// same key for the Wear OS Data Layer to route messages between them at all. See that file for
// the full explanation and keystore.properties.example for the properties this reads.
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
    // Must exactly match :wear's applicationId - the Wear OS Data Layer API (MessageClient) only
    // delivers between apps with the same package name and signing key, regardless of which
    // physical device (phone or watch) each is installed on. See PhoneLoginContract.kt.
    applicationId = "fi.nikosavola.immichwear"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
  }

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
  buildFeatures { compose = true }
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(libs.kotlin.stdlib)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.activity.compose)

  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.material3)
  debugImplementation(libs.compose.ui.tooling)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.play.services.wearable)
  implementation(libs.kotlinx.coroutines.play.services)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
