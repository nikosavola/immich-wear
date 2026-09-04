# Releasing

How to cut a signed release of the `direct`-flavor watch app and its phone companion, and get them
onto GitHub Releases without triggering Android's "unverified developer" warning on certified
devices.

## 1. One-time keystore generation

Generate a release keystore once, on a machine you control, and never commit it:

```bash
keytool -genkeypair -v \
  -keystore immich-wear-release.jks \
  -alias immich-wear-release \
  -keyalg RSA -keysize 2048 -validity 10000
```

Modern `keytool` defaults to a PKCS12 keystore, which doesn't support separate store and key
passwords - it silently reuses the store password for both, so there's only one password to keep
track of, not two.

**This is the actual key that ends up on end users' devices.** Unlike a Play Store "upload key",
there is no Google-side reset process if it's lost or leaked: losing it permanently breaks the
upgrade path for anyone who installed a prior release (they'd have to uninstall and reinstall
under a new signature). Keep an offline backup of both the `.jks` file and its password somewhere
durable - a password manager's file-attachment feature, an encrypted backup, etc. - not just on
the machine that generated it. GitHub Actions secrets (section 3) are write-only: once set, not
even you can read them back, so they are not a backup.

`.gitignore` already excludes `*.jks`, `*.keystore`, `keystore.properties`, so none of this can be
accidentally committed.

## 2. Encoding the keystore for CI

```bash
base64 immich-wear-release.jks | tr -d '\n'
```

(`base64 -w0` also works, but that flag is GNU-specific and isn't available on macOS/BSD `base64`.)

Copy the single line of output; that's the value for the `RELEASE_KEYSTORE_BASE64` secret below.

## 3. GitHub repository secrets

Settings -> Secrets and variables -> Actions -> New repository secret. Add all four:

| Secret                    | Value                                             |
| -------------------------- | -------------------------------------------------- |
| `RELEASE_KEYSTORE_BASE64` | Output of the `base64 \| tr -d '\n'` command above |
| `RELEASE_STORE_PASSWORD`  | The keystore password from `keytool`              |
| `RELEASE_KEY_ALIAS`       | The `-alias` value from `keytool`                 |
| `RELEASE_KEY_PASSWORD`    | The key password from `keytool` (same value as the store password, per the PKCS12 note above) |

These map directly onto the `RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`/`RELEASE_KEY_ALIAS`/
`RELEASE_KEY_PASSWORD` properties `wear/build.gradle.kts` and `mobile/build.gradle.kts` already
read via `keystoreProp()` - `release.yml` decodes `RELEASE_KEYSTORE_BASE64` to a temp file and
exports its path as `RELEASE_STORE_FILE` itself, so there's no separate path secret. Setting none
of them leaves `release` builds signed with the debug keystore instead (this is also what a local
`./gradlew :wear:assembleDirectDebug` sees without any of this configured - day-to-day development
is unaffected).

**Local signed builds without exporting env vars**: create a gitignored `keystore.properties` in
the repo root, copying `keystore.properties.example` and filling in `RELEASE_STORE_FILE` (an
absolute path, or a path relative to the repo root works too since both modules resolve it via
`rootProject.file(...)`), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## 4. Registering the key for Android developer verification

Starting September 30, 2026 (in select regions first, global rollout in 2027), an app installed on
a certified Android device must be signed by a key registered to a verified developer identity, or
installation is blocked (with a one-time "advanced user" bypass available). This applies to
sideloaded APKs same as Play Store ones.

Once you're a verified developer in Play Console (a one-time identity check, separate from
publishing anything to Play), register this app **without needing a Play Store listing**:

1. Play Console -> Android developer verification -> Package names.
2. If `fi.nikosavola.immichwear` isn't listed yet, register it (it may already exist as a draft
   package name if a Play Store listing draft was ever started for it).
3. Open the package name's key list and add the SHA-256 certificate fingerprint of the release key
   from section 1:

   ```bash
   keytool -list -v -keystore immich-wear-release.jks -alias immich-wear-release
   ```

   Look for the `Certificate fingerprints: SHA256:` line and paste that value (colon-separated hex)
   into the "Add key" dialog.

This is independent of, and does not require or create, a Play Store listing - registering it here
doesn't move `fi.nikosavola.immichwear` any closer to being published on Play.

## 5. Cutting a release

1. Bump `releaseNumber` in `gradle.properties` (both `:wear` and `:mobile` derive their own
   `versionCode` from it with different offsets - see the comments in their `build.gradle.kts`).
2. Bump `versionName` in both `wear/build.gradle.kts` and `mobile/build.gradle.kts` - these are
   independent literals, not derived from anything, and must be bumped in both places by hand.
3. Commit that change.
4. Tag it `vX.Y.Z` (matching the `push: tags: 'v*'` trigger) and push the tag:

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

5. `release.yml` runs: builds both signed APKs and creates a GitHub Release for the tag with both
   attached.

## 6. Scope this pipeline does not cover

- **Play Store publishing**: not wired up here. `playstore` is a separate flavor with its own
  distribution path (Play Console review) - it isn't built by `release.yml`.
- **F-Droid distribution**: F-Droid builds and signs apps from source using its own
  infrastructure, entirely outside this repo's CI - nothing here is relevant to that path.
- **AAB / app bundle**: not built here, since GitHub Releases distributes a plain installable APK,
  not a Play-specific bundle format.
