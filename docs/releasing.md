# Releasing

How the app gets from `main` to your phone: a signed APK on GitHub Releases for every version tag (ADR-0027). Google Play comes later (#35) and will use the same key.

- **Once:** create the release key, back it up, add it to GitHub as secrets.
- **Each release:** update `CHANGELOG.md`, tag `main`, push the tag. `.github/workflows/release.yml` does the rest.
- **On the phone:** download the APK from the release page and install it; later releases install as updates.

## 1. Create the release key (once)

You need `keytool`, which comes with any JDK (Android Studio has one in `jbr/bin/`). Run this somewhere outside the repository:

```bash
keytool -genkeypair -v \
  -keystore before-you-buy-release.jks -storetype PKCS12 \
  -alias release -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=locngu"
```

- It asks for a password twice. Use a long random one from your password manager. With PKCS12 the key has the **same password** as the keystore, so `RELEASE_KEY_PASSWORD` below is that same password.
- `-validity 10000` is about 27 years. Google Play requires validity past 2033.
- The certificate (with its `CN`) is public: anyone can read it from the APK. Don't put anything private in `-dname`.
- **Never** put the `.jks` file into the repository. `.gitignore` ignores `*.jks` and `*.keystore` as a safety net.

Note the certificate's fingerprint. The release workflow prints it on every run (as `Signer #1 certificate SHA-256 digest`, lowercase and without colons), so you can check it's your key:

```bash
keytool -list -v -keystore before-you-buy-release.jks -alias release | grep SHA256
```

## 2. Back up the key (once)

**If the key is lost, no new version can ever be installed over the existing app.** Every installed copy would have to be uninstalled first, which deletes all its data (see [Changing the key or the ID](#changing-the-key-or-the-id)). GitHub secrets can't be read back, so they are not a backup.

Keep at least two copies, one of them offline:

- In your password manager: the `.jks` file as an attachment, the password, the alias (`release`) and the SHA-256 fingerprint.
- Offline: the `.jks` file on a USB stick or similar, stored somewhere safe. The password stays in the password manager, not next to the file.

## 3. Add the secrets to GitHub (once)

Encode the keystore as base64 (one line):

```bash
base64 -w 0 before-you-buy-release.jks > keystore.b64       # Linux
base64 -i before-you-buy-release.jks -o keystore.b64        # macOS
```

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("before-you-buy-release.jks")) | Set-Content keystore.b64  # Windows
```

The secrets go into a GitHub **environment** that only version tags can use. Plain repository secrets would be readable by any workflow on any branch, including pull request runs.

1. The repository → **Settings → Environments → New environment**, name it `release`.
2. Under **Deployment branches and tags**, choose **Selected branches and tags** → **Add deployment branch or tag rule** → type **Tag**, pattern `v*`.
3. Under **Environment secrets**, add four secrets:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the contents of `keystore.b64` |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | `release` |
| `RELEASE_KEY_PASSWORD` | the same password (PKCS12) |

Then delete `keystore.b64`. Don't add these as repository secrets as well.

GitHub masks secret values in logs. The release workflow only puts them into the one build step that signs, and deletes the decoded keystore after it. That build job can't write to the repository; a separate job without any build code publishes the release. Pull request CI (`ci.yml`) never gets the secrets and builds unsigned.

Optional: protect version tags with a ruleset (**Settings → Rules → Rulesets → New tag ruleset**, target `v*`, restrict creation, update and deletion to yourself), so a tag can't be moved or deleted by mistake.

## 4. Cut a release

Versions are `vMAJOR.MINOR.PATCH`, with MINOR and PATCH from 0 to 99 and no suffix like `-rc.1`. The tag gives `versionName = MAJOR.MINOR.PATCH` and `versionCode = MAJOR*10000 + MINOR*100 + PATCH` (`v0.1.0` → 100, `v1.2.3` → 10203), so a later version always installs as an update.

1. **Changelog PR.** In `CHANGELOG.md`, rename the entries under `## [Unreleased]` to a new section `## [0.1.0] - 2026-10-12` (version without the `v`, today's date) and leave an empty `## [Unreleased]` above it. The release notes are exactly this section; the release fails if it's missing or empty. Merge the PR.
2. **Tag `main`** and push the tag:

   ```bash
   git fetch origin main
   git tag -a v0.1.0 origin/main -m "v0.1.0"
   git push origin v0.1.0
   ```

   Use `git`, not GitHub's "Draft a new release" button: the workflow creates the release itself and fails if one already exists.
3. **Watch the run** under **Actions → Release**. It fails, without publishing anything, if:
   - the tag isn't on `main`, or isn't `vX.Y.Z`;
   - a signing secret is missing from the `release` environment;
   - `CHANGELOG.md` has no section for the version;
   - `verify` fails;
   - the APK isn't signed with the release key (unsigned or debug-signed) or has the wrong version.
4. The release appears under **Releases**, named after the tag, with `before-you-buy-0.1.0.apk`, `before-you-buy-0.1.0.apk.sha256` and the changelog section as notes.

**If a run fails:** fix the cause on `main` through a PR, then release a new patch version (`v0.1.1`). Don't move an existing tag. If only the publish step failed (e.g. GitHub was down), re-run the job; if the release was already created, delete the release (not the tag) first.

## 5. Install on the phone

1. On the phone, open the repository's **Releases** page and download `before-you-buy-<version>.apk`.
2. Open the download. The first time, Android asks to allow installing apps from this source (**Settings → Apps → Special app access → Install unknown apps → your browser → Allow**). You can turn it off again afterwards.
3. Google Play Protect may warn about an unknown developer. Choose **Install anyway** (the app has no internet access at all, ADR-0003).

Optional: check the download against the `.sha256` file (`sha256sum -c before-you-buy-<version>.apk.sha256` on a computer).

**Updates:** install the next release's APK the same way. Android shows **Update**, and all data stays, because the ID (ADR-0026) and the key are the same and the `versionCode` is higher. Android refuses older versions ("downgrade").

## Changing the key or the ID

Android installs an update only if the `applicationId` and the signing key match the installed app:

- **A different key:** Android refuses the update ("App not installed as package conflicts with an existing package"). The only way is to uninstall the old app, which **deletes all its data**. This is why the key is backed up and never replaced.
- **A different `applicationId`:** Android installs it as a second, separate app with no data. ADR-0026 describes the move (export from the old app, import into the new one, #27).

Until export/import (#27) exists, both mean starting over with an empty app.

## Later: Google Play (#35)

When the app goes to Play, enable Play App Signing with **this key** (choose to upload your existing app signing key instead of letting Google generate one). Then Play builds and GitHub builds are signed with the same certificate and can update each other.

## Signing a local build (optional)

Normally only the release workflow signs. To sign locally, set the four variables for that one command (don't `export` them, or every later build in the shell would sign) and build with the tag:

```bash
read -rs PASSWORD   # type the keystore password; it isn't shown or saved in the shell history
RELEASE_KEYSTORE_PATH=/path/to/before-you-buy-release.jks RELEASE_KEY_ALIAS=release \
  RELEASE_KEYSTORE_PASSWORD="$PASSWORD" RELEASE_KEY_PASSWORD="$PASSWORD" \
  ./gradlew :app:assembleRelease -Papp.versionTag=v0.1.0 --no-configuration-cache
unset PASSWORD
```

- `RELEASE_KEYSTORE_PATH` is absolute or relative to the repository root.
- Set all four or none: with only some, the build fails.
- Signing refuses to run with the configuration cache (it would store the passwords on disk), so `--no-configuration-cache` is required.
- Without the variables, `assembleRelease` builds `app-release-unsigned.apk`, which can't be installed.
