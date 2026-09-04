# Contributing

## Setup

This is a two-module Gradle project: `:wear` (the watch app, in `direct` and `playstore` flavors -
see [wear/build.gradle.kts](../wear/build.gradle.kts)) and `:mobile` (the phone companion). The
Gradle wrapper is committed and provisions its own JDK toolchain, so there's no manual setup step:

```bash
./gradlew :wear:assembleDirectDebug
```

Everything below is also available as a [`just`](https://github.com/casey/just) recipe; run
`just --list` to see them all.

## Linting and formatting

ktfmt, ktlint and detekt run as Gradle tasks, so the same versions are used locally and in CI:

```bash
just lint      # check only
just format    # auto-fix ktfmt/ktlint issues
```

## Testing

```bash
just test            # direct flavor's full Robolectric/JUnit suite
just test playstore  # playstore flavor
just verify          # lint + build both flavors + test, matching CI
```

`playstore` is compile-checked rather than test-run by default - most Settings screen tests assume
the `direct` flavor's on-watch login UI, since that's the sideload build used for local iteration
and testing.

## Building and installing on a device

```bash
just assemble  # build the direct-flavor debug APK
just install   # build and install it on a connected watch
```

A release build needs a signing key: copy `keystore.properties.example` to `keystore.properties`
(gitignored) and fill in the four `RELEASE_*` properties, or set the equivalent `RELEASE_STORE_*`
environment variables. Without one, release builds fall back to debug signing. See
[docs/RELEASING.md](../docs/RELEASING.md) for generating a real key and cutting a signed GitHub
Release.

## Before opening a pull request

- `just verify` passes.
- Keep commits atomic: one logical change per commit, with an imperative-mood message ("Add x",
  not "Added x" or "Adds x").
- New behavior gets a test; a bug fix gets a regression test.

## AI usage policy

Using AI tools to accelerate your workflow, whether for prototyping, writing tests, or improving
documentation, is **encouraged** - most of this project has been built with them.

However, as a contributor, you remain **fully responsible** for the code and content you submit.
Please ensure the following:

1. **No "AI slop"**: don't submit unreviewed, low-quality, or redundant AI-generated content.
1. **Verify and test**: all AI-generated code must be reviewed, tested, and verified to work as
   intended.
1. **Maintainability**: the content must be clear, idiomatic, and maintainable by a human.
