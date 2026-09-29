// Plain Kotlin/JVM module (no Android plugin): the Immich REST client touches no Android APIs, so
// it is published standalone for other JVM/Android Immich clients to depend on - see
// .github/workflows/publish.yml. Kotlin comes from the root buildscript classpath (see the root
// build.gradle.kts comment), same as every Android module's built-in Kotlin.
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kover)
  `maven-publish`
}

kotlin { jvmToolchain(21) }

// Versioned by the release tag, not by :wear/:mobile's versionName literals: publish.yml already
// has the tag in hand, and it is the one thing that has to agree with what the registry serves.
// The "0.0.0-SNAPSHOT" default keeps a bare `./gradlew :immich-api:publishToMavenLocal` working
// without a tag.
version = providers.gradleProperty("releaseVersion").getOrElse("0.0.0-SNAPSHOT")

group = "fi.nikosavola"

dependencies {
  // api(), not implementation(): the DTOs are @Serializable, ImmichApi is annotated with Retrofit
  // annotations, and ImmichClients exposes the raw OkHttpClient, so all three are part of this
  // module's public surface and have to land on a consumer's compile classpath too.
  api(libs.kotlinx.serialization.json)
  api(libs.retrofit)
  api(libs.okhttp)

  // implementation(): only ImmichClients' own body needs the converter, no signature mentions it.
  implementation(libs.retrofit.converter.kotlinx.serialization)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      // The jar plus Gradle module metadata. No sources/javadoc artifacts and no signing: GitHub
      // Packages wants neither (Maven Central would want both).
      from(components["java"])
      artifactId = "immich-api"
      pom {
        name = "Immich API client"
        description =
          "Kotlin/Retrofit/OkHttp client for the Immich REST API, extracted from the Immich Wear OS app."
        url = "https://github.com/nikosavola/immich-wear"
        licenses {
          // Same AGPL-3.0 as the app this was extracted from, which also binds anything that links
          // the artifact - see the README section on the published package.
          license {
            name = "GNU Affero General Public License v3.0"
            url = "https://www.gnu.org/licenses/agpl-3.0.txt"
          }
        }
        scm { url = "https://github.com/nikosavola/immich-wear" }
      }
    }
  }
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/nikosavola/immich-wear")
      credentials {
        // GitHub Packages rejects anonymous reads (public package or not) and only accepts a
        // classic PAT or the workflow's own GITHUB_TOKEN - fine-grained tokens don't work here.
        username = System.getenv("GITHUB_ACTOR")
        password = System.getenv("GITHUB_TOKEN")
      }
    }
  }
}
