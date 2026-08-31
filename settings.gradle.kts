pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

// Auto-provisions the JDK 21 toolchain so the build does not depend on the machine's default JDK.
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "ImmichWearOS"

include(":wear")

include(":mobile")
