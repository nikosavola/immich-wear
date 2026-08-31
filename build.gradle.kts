// AGP 9 built-in Kotlin would otherwise pull its bundled Kotlin (2.3.10 for AGP 9.2). Force our
// Kotlin (2.4.0) onto the buildscript classpath so built-in Kotlin compiles with it and the
// compose-compiler plugin (which must match the Kotlin version exactly) stays aligned.
buildscript {
  // Literal (not libs.versions.kotlin): the version-catalog accessor isn't available this early in
  // buildscript{} evaluation. Keep in sync with `kotlin` in gradle/libs.versions.toml.
  dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0") }
}

plugins {
  alias(libs.plugins.android.application) apply false
  // kotlin.android removed: AGP 9 provides built-in Kotlin (applying it errors on a duplicate
  // `kotlin` extension). Version is pinned via the buildscript classpath above.
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ktfmt) apply false
  alias(libs.plugins.ktlint) apply false
  alias(libs.plugins.detekt) apply false
}

// Applied to the root project too so ktfmtFormat/ktfmtCheck also cover this file and
// settings.gradle.kts (via their ktfmtFormatScripts/ktfmtCheckScripts dependency), keeping every
// Kotlin/KTS file in the repo under one formatter.
apply(plugin = "com.ncorti.ktfmt.gradle")

configure<com.ncorti.ktfmt.gradle.KtfmtExtension> { googleStyle() }

subprojects {
  apply(plugin = "com.ncorti.ktfmt.gradle")
  apply(plugin = "org.jlleitschuh.gradle.ktlint")
  // detekt 2.0 (dev.detekt) is the first line compatible with Gradle 9; it's a pre-release chosen
  // deliberately for that compatibility, not for its features.
  apply(plugin = "dev.detekt")

  configure<com.ncorti.ktfmt.gradle.KtfmtExtension> { googleStyle() }

  // The plugin's own ktfmtCheck/ktfmtFormat discover sources through KGP's Kotlin source sets,
  // which AGP 9's built-in Kotlin never registers: they end up with no actions and pass without
  // reading a single .kt file. Drive ktfmt from an explicit source tree instead.
  tasks.register<com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask>("ktfmtCheckKotlin") {
    source(fileTree("src") { include("**/*.kt") })
  }
  tasks.register<com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask>("ktfmtFormatKotlin") {
    source(fileTree("src") { include("**/*.kt") })
  }

  // An empty source tree makes the ktfmt tasks NO-SOURCE, which passes silently. That is how the
  // formatter went unnoticed as a no-op in the first place, so fail loudly instead.
  tasks.register("ktfmtSourcesNotEmpty") {
    val sources = fileTree("src") { include("**/*.kt") }
    doLast { check(!sources.isEmpty) { "ktfmt matched no .kt files in ${project.path}/src" } }
  }

  configure<dev.detekt.gradle.extensions.DetektExtension> {
    buildUponDefaultConfig = true
    // allRules is the deliberate analog of clippy's pedantic/nursery groups: opt in to everything,
    // then re-disable only genuine conflicts in config/detekt/detekt.yml.
    allRules = true
    parallel = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
  }
}

tasks.register("formatAll") {
  group = "formatting"
  description = "Auto-format the Kotlin codebase with ktfmt and ktlint"
  dependsOn(
    "ktfmtFormat",
    ":wear:ktfmtFormatScripts",
    ":wear:ktfmtFormatKotlin",
    ":wear:ktlintFormat",
    ":mobile:ktfmtFormatScripts",
    ":mobile:ktfmtFormatKotlin",
    ":mobile:ktlintFormat",
  )
}

tasks.register("lintAll") {
  group = "verification"
  description = "Run ktfmt, ktlint, detekt and Android Lint checks"
  dependsOn(
    "ktfmtCheck",
    ":wear:ktfmtCheckScripts",
    ":wear:ktfmtSourcesNotEmpty",
    ":wear:ktfmtCheckKotlin",
    ":wear:ktlintCheck",
    ":wear:detekt",
    // "direct" only, matching the default flavor everything else here targets - see justfile.
    ":wear:lintDirectDebug",
    ":mobile:ktfmtCheckScripts",
    ":mobile:ktfmtSourcesNotEmpty",
    ":mobile:ktfmtCheckKotlin",
    ":mobile:ktlintCheck",
    ":mobile:detekt",
    ":mobile:lintDebug",
  )
}

tasks.register("clean", Delete::class) { delete(rootProject.layout.buildDirectory) }
