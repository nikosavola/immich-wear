# Baseline profile

`wear/src/main/baseline-prof.txt` lists the app's own startup and home screen classes so ART can
precompile them at install time instead of interpreting and JIT-ing them on a slow watch CPU.
AGP packs it into `assets/dexopt/baseline.prof`, together with the profiles the AndroidX libraries
already ship, and `androidx.profileinstaller` (already on the classpath through Compose) installs
it on first launch for sideloaded and F-Droid installs.

The file is hand-written with whole-class wildcards, not generated from a device run. It is a
starting point. A generated profile lists only methods that really ran, so it is smaller and more
precise.

## Generating a real profile

This needs the watch and is not part of the default build, CI or the F-Droid recipe. Do it in a
throwaway branch.

1. Add an `com.android.test` module (for example `:baselineprofile`) targeting `:wear` with the
   `androidx.benchmark:benchmark-macro-junit4` dependency and `minSdk` 30. Build the target as a
   non-debuggable variant such as `directRelease` signed with the debug key.
2. Write a `BaselineProfileRule` test that cold starts the app and walks the hot path: launch,
   home screen, open the timeline, scroll the first page, open one asset.
3. Run it on the watch over ADB, then copy the generated rules over `baseline-prof.txt`.

To see whether the profile helps, write a second `MacrobenchmarkRule` test that records
`StartupTimingMetric` while cold starting, once with `CompilationMode.None()` and once with
`CompilationMode.Partial(BaselineProfileMode.Require)`. Add a `FrameTimingMetric` run for the first
scroll. Compare the medians over 10 or more iterations.

Macrobenchmark needs `androidx.test` and uiautomator, none of which touch Google Play Services.
Keep it out of `settings.gradle.kts` on main so the F-Droid scan and `lintAll` stay unaffected.
