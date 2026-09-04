import java.util.Properties
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

plugins {
  alias(libs.plugins.android.application)
  // kotlin.android removed: AGP 9 built-in Kotlin (version pinned in the root buildscript
  // classpath).
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kover)
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
    // +1000 offset keeps this distinct from :mobile's versionCode - see gradle.properties.
    versionCode = property("releaseNumber").toString().toInt() + 1000
    versionName = "0.1.1"
  }

  // "direct" keeps on-watch server URL/API key entry (Settings) for anyone building/sideloading
  // from source, *and* still supports the phone-companion login flow as a convenience (both
  // mechanisms coexist). "playstore" strips on-watch entry via
  // BuildConfig.SUPPORTS_DIRECT_WATCH_LOGIN,
  // satisfying the Play Store Wear OS quality guideline against typing credentials on a watch -
  // login there is companion-app-only. "fdroid" is "direct" minus the companion-app path: F-Droid's
  // scanner rejects any dependency on Google Play Services outright (see the sourceSets block and
  // src/googlePlayServices/ below), which the companion login needs. None of the three flavors gets
  // an applicationIdSuffix: whichever one ships must keep the exact applicationId the :mobile app
  // expects (see PhoneLoginContract.kt).
  flavorDimensions += "distribution"
  productFlavors {
    create("direct") { dimension = "distribution" }
    create("fdroid") { dimension = "distribution" }
    create("playstore") {
      dimension = "distribution"
      buildConfigField("boolean", "SUPPORTS_DIRECT_WATCH_LOGIN", "false")
    }
  }
  defaultConfig { buildConfigField("boolean", "SUPPORTS_DIRECT_WATCH_LOGIN", "true") }

  // The phone-companion login path (PhoneLoginListenerService + its manifest entry + the
  // CapabilityClient resource it needs) is shared, byte-for-byte, by "direct" and "playstore" but
  // must be entirely absent from "fdroid". Gradle source sets can only ADD directories to a
  // flavor, never subtract from a shared one, so this can't live in src/main (fdroid would inherit
  // it) or be named after either flavor (the other one would miss it) - src/googlePlayServices is
  // wired into exactly the two flavors that need it, with zero duplication.
  sourceSets {
    getByName("direct") {
      java.directories.add("src/googlePlayServices/java")
      res.directories.add("src/googlePlayServices/res")
      manifest.srcFile("src/googlePlayServices/AndroidManifest.xml")
    }
    getByName("playstore") {
      java.directories.add("src/googlePlayServices/java")
      res.directories.add("src/googlePlayServices/res")
      manifest.srcFile("src/googlePlayServices/AndroidManifest.xml")
    }
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
  implementation(libs.androidx.core.splashscreen)
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
  // applicationId. Scoped to "direct" and "playstore" only (not a plain `implementation`): the
  // "fdroid" flavor must stay entirely free of Google Play Services so it can be built and
  // distributed through F-Droid, which rejects apps depending on non-free Google libraries
  // outright - see the sourceSets block above and src/googlePlayServices/ for the service itself
  // and its manifest/resource entries.
  "directImplementation"(libs.play.services.wearable)
  "directImplementation"(libs.kotlinx.coroutines.play.services)
  "playstoreImplementation"(libs.play.services.wearable)
  "playstoreImplementation"(libs.kotlinx.coroutines.play.services)

  testImplementation(libs.junit)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)
}

// Regression guard for F-Droid eligibility: fails loudly if a future dependency change
// (transitive or direct) pulls a non-free Google library back into the "fdroid" flavor, rather
// than silently shipping a build F-Droid's own scanner would reject. One task per "fdroid"
// variant (debug and release both get checked, not just whichever one CI happens to build), each
// checking both the compile and runtime classpath - a `compileOnly` dependency doesn't appear on
// the runtime classpath but is exactly as disqualifying to F-Droid's own scanner.
//
// This list is deliberately broader than "just play-services-wearable": it's every non-free
// Google group F-Droid's own scanner is known to reject, so this task guards the whole flavor's
// eligibility, not only the one dependency this PR happens to be about.
val nonFreeGoogleGroupPrefixes =
  listOf(
    "com.google.android.gms",
    "com.google.firebase",
    "com.google.android.play",
    "com.google.mlkit",
    "com.google.android.libraries",
    "com.android.billingclient",
  )

val verifyFdroidFlavorHasNoGoogleServices =
  tasks.register("verifyFdroidFlavorHasNoGoogleServices") {
    group = "verification"
    description = "Fail if any fdroid-flavor variant depends on a non-free Google library"
  }

androidComponents {
  onVariants(selector().withFlavor("distribution" to "fdroid")) { variant ->
    // A live Configuration can't be serialized into the configuration cache even when captured
    // as a val at configuration time - only its resolutionResult.rootComponent Provider can
    // (Gradle's documented config-cache-safe way to consume a resolved dependency graph), so
    // capture that instead and walk the ResolvedComponentResult tree at execution time.
    val compileRootComponent = variant.compileConfiguration.incoming.resolutionResult.rootComponent
    val runtimeRootComponent = variant.runtimeConfiguration.incoming.resolutionResult.rootComponent
    val variantName = variant.name
    // Referencing the top-level nonFreeGoogleGroupPrefixes val directly from inside doLast below
    // would capture the whole build script object as an implicit closure variable, which isn't
    // configuration-cache serializable - copy it into a plain local List instead.
    val blockedGroupPrefixes = nonFreeGoogleGroupPrefixes
    val perVariantTask =
      tasks.register("verify${variantName.replaceFirstChar(Char::uppercase)}HasNoGoogleServices") {
        group = "verification"
        description = "Fail if the $variantName variant depends on a non-free Google library"
        doLast {
          fun offendersIn(rootComponent: ResolvedComponentResult): List<String> {
            val offenders = mutableListOf<String>()
            val unresolved = mutableListOf<String>()
            val visited = mutableSetOf<ComponentIdentifier>()

            fun visit(component: ResolvedComponentResult) {
              if (!visited.add(component.id)) return
              for (dependency in component.dependencies) {
                when (dependency) {
                  is UnresolvedDependencyResult -> unresolved += dependency.attempted.displayName
                  is ResolvedDependencyResult -> {
                    val moduleVersion = dependency.selected.moduleVersion
                    if (
                      moduleVersion != null &&
                        blockedGroupPrefixes.any { moduleVersion.group.startsWith(it) }
                    ) {
                      offenders += moduleVersion.toString()
                    }
                    visit(dependency.selected)
                  }
                }
              }
            }
            visit(rootComponent)
            // An offender that fails to resolve at all (e.g. under --offline with an empty
            // cache) would otherwise be invisible to the walk above - which only reports what
            // *did* resolve - and produce a false pass, so check for that separately too.
            check(unresolved.isEmpty()) {
              "$variantName has unresolved dependencies, cannot verify: " +
                unresolved.joinToString()
            }
            return offenders
          }
          val offenders =
            (offendersIn(compileRootComponent.get()) + offendersIn(runtimeRootComponent.get()))
              .distinct()
          check(offenders.isEmpty()) {
            "The fdroid flavor must stay free of non-free Google libraries, but $variantName " +
              "resolved: " +
              offenders.joinToString()
          }
        }
      }
    verifyFdroidFlavorHasNoGoogleServices.configure { dependsOn(perVariantTask) }
  }
}
