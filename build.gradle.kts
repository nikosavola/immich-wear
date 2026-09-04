// AGP 9's built-in Kotlin would otherwise pull whatever Kotlin version it bundles. Force our own
// Kotlin onto the buildscript classpath so built-in Kotlin compiles with it and the
// compose-compiler plugin (which must match the Kotlin version exactly) stays aligned.
buildscript {
  // Literal (not libs.versions.kotlin): the version-catalog accessor isn't available this early in
  // buildscript{} evaluation. Keep in sync with `kotlin` in gradle/libs.versions.toml.
  dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10") }
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
  alias(libs.plugins.kover) apply false
  alias(libs.plugins.sonarqube)
}

sonar {
  properties {
    property("sonar.projectKey", "nikosavola_immich-wear")
    property("sonar.organization", "nikosavola")
    // Same Kover-generated report the Codecov step in ci.yml uploads; sonar-kotlin reads JaCoCo-XML
    // format under this key for both JaCoCo and Kover. Without it, SonarCloud has no coverage data
    // source at all and reports a flat 0% on every PR, regardless of actual test coverage. Must be
    // absolute: this property set from the root project is resolved relative to the *module*
    // directory (wear/), not the root, so a root-relative literal here silently resolves to
    // wear/wear/build/... and is never found. Only the "direct" flavor is covered, matching the
    // only variant ci.yml actually runs unit tests against (see wear/build.gradle.kts comment).
    property(
      "sonar.coverage.jacoco.xmlReportPaths",
      listOf(
          file("wear/build/reports/kover/reportDirectDebug.xml"),
          file("mobile/build/reports/kover/reportDebug.xml"),
        )
        .joinToString(",") { it.absolutePath },
    )
  }
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

  // Override the ktlint-gradle plugin's bundled ktlint-cli (1.5.0 as of ktlint-gradle 14.2.0),
  // whose pinned logback-classic 1.3.14 trips 3 Dependabot alerts fixed by logback 1.3.16 - see
  // .github/SECURITY.md for the ones this doesn't clear (ktlint stays on the 1.3.x logback branch).
  configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> { version.set("1.8.0") }

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

  // Same problem and fix as ktfmtCheckKotlin/ktfmtFormatKotlin above: the default detekt task
  // only discovers source through KGP's Kotlin source sets, which AGP 9's built-in Kotlin never
  // registers for a flavor-specific dir like :wear's src/googlePlayServices - it would otherwise
  // silently skip analyzing that code entirely. dev.detekt.gradle.Detekt extends SourceTask, so
  // (unlike ktfmt) no separate custom task is needed - just repoint the existing one explicitly.
  tasks.withType<dev.detekt.gradle.Detekt> { setSource(fileTree("src") { include("**/*.kt") }) }
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
    ":wear:verifyFdroidFlavorHasNoGoogleServices",
    ":mobile:ktfmtCheckScripts",
    ":mobile:ktfmtSourcesNotEmpty",
    ":mobile:ktfmtCheckKotlin",
    ":mobile:ktlintCheck",
    ":mobile:detekt",
    ":mobile:lintDebug",
  )
}

tasks.register("clean", Delete::class) { delete(rootProject.layout.buildDirectory) }
