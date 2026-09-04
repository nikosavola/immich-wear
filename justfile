# justfile for immich-wear. Run `just` or `just --list` to see all recipes.
#
# Every gradle recipe caps parallelism via max_workers (default 2, override with
# JUST_MAX_WORKERS): a workstation preference, not a project requirement, so it lives here rather
# than in gradle.properties, which is committed and shared across machines.

max_workers := env('JUST_MAX_WORKERS', '2')
sdk := env('ANDROID_HOME', env('ANDROID_SDK_ROOT', env('HOME') + '/Android/Sdk'))
gradle := './gradlew --max-workers=' + max_workers
package := 'fi.nikosavola.immichwear'

# List available recipes
default:
    @just --list

# Run ktfmt and ktlint auto-format over the Kotlin sources
[group('lint')]
format:
    {{ gradle }} formatAll

# Run ktfmt, ktlint and detekt checks, matching the CI "verify" job
[group('lint')]
lint:
    {{ gradle }} lintAll

# Build the debug APK. flavor: "direct" (default, sideload), "fdroid", or "playstore" - see wear/build.gradle.kts
[group('build')]
assemble flavor='direct':
    {{ gradle }} :wear:assemble{{ capitalize(flavor) }}Debug

# Remove build outputs
[group('build')]
clean:
    {{ gradle }} clean

# Run the host-JVM unit tests. Only "direct" has a full suite; "playstore" is compile-checked, not test-run
[group('test')]
test flavor='direct':
    {{ gradle }} :wear:test{{ capitalize(flavor) }}DebugUnitTest

# Full local gate: lint, build (all flavors) and test, matching CI's verify + test jobs combined
[group('test')]
verify:
    {{ gradle }} lintAll :wear:assembleDirectDebug :wear:assemblePlaystoreDebug :wear:assembleFdroidDebug \
        :wear:testDirectDebugUnitTest --no-daemon

# List connected adb devices, including wireless ones
[group('device')]
devices:
    {{ sdk }}/platform-tools/adb devices -l

# Connect to a previously paired watch found via mDNS (run `just pair` first if this fails)
[group('device')]
connect:
    #!/usr/bin/env bash
    set -euo pipefail
    ADB="{{ sdk }}/platform-tools/adb"
    "$ADB" start-server
    target=$("$ADB" mdns services | grep '_adb-tls-connect._tcp' | awk '{print $3}')
    if [ -z "$target" ]; then
      echo "No paired watch found via mDNS. Enable Wireless debugging on the watch, then run: just pair <ip:port> <code>" >&2
      exit 1
    fi
    "$ADB" connect "$target"

# Pair a watch: get ip:port and code from its Wireless debugging > Pair new device screen
[group('device')]
pair ip_port code:
    {{ sdk }}/platform-tools/adb pair {{ ip_port }} {{ code }}

# Build and install the debug APK on the connected device. flavor: "direct", "fdroid", or "playstore"
[group('device')]
install flavor='direct': (assemble flavor)
    {{ sdk }}/platform-tools/adb install -r wear/build/outputs/apk/{{ flavor }}/debug/wear-{{ flavor }}-debug.apk

# Launch the app on the connected device
[group('device')]
launch:
    {{ sdk }}/platform-tools/adb shell am start -n {{ package }}/.ui.MainActivity

# Stream logcat filtered to this app's process, crashes and stderr
[group('device')]
logcat:
    {{ sdk }}/platform-tools/adb logcat -s AndroidRuntime System.err {{ package }}

# Remove the app from the connected device
[group('device')]
uninstall:
    {{ sdk }}/platform-tools/adb uninstall {{ package }}

# Boot a Wear OS emulator; plain `-avd` alone segfaults on GPU/display init here, hence the flags
[group('emulator')]
emulator avd='wear5':
    {{ sdk }}/emulator/emulator -avd {{ avd }} -no-window -no-audio -no-boot-anim \
        -gpu swiftshader_indirect -no-snapshot

# List available AVDs
[group('emulator')]
avds:
    {{ sdk }}/emulator/emulator -list-avds
