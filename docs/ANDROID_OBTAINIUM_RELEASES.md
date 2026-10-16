# Android releases and Obtainium

The Android release workflow builds a debug-signed APK for validation when release signing is not configured. In that case, it deliberately **does not publish a GitHub Release**. A fresh GitHub-hosted runner can generate a different Android debug certificate, so debug-signed APKs are not a reliable update channel.

## One-time signing setup

Use a single persistent keystore for every release. Do not regenerate it after publishing an APK.

In the repository's GitHub settings, open **Secrets and variables → Actions** and add these repository secrets:

| Secret | Value |
| --- | --- |
| `TRIAEVUM_RELEASE_KEYSTORE_BASE64` | Base64 text of the private release keystore |
| `TRIAEVUM_RELEASE_STORE_PASSWORD` | Keystore password |
| `TRIAEVUM_RELEASE_KEY_ALIAS` | Key alias inside the keystore |
| `TRIAEVUM_RELEASE_KEY_PASSWORD` | Password for that key |

The workflow decodes the keystore under the runner's temporary directory, checks the alias, signs the Release variant, and only then publishes the APK to GitHub Releases. Secrets are not written into the repository or APK.

Do not add the keystore, its base64 encoding, or passwords to Git. Keep an offline backup of the keystore and credentials. Anyone with the private key can sign an update that Android will treat as the same application publisher.

## Obtainium

After the first correctly signed release is available, add the repository URL:

`https://github.com/Ys-Andro/OOT-3DS`

Use the APK asset named `TriAevum-Oot3D-Android-arm64-v8a.apk`. Future releases use increasing Android version codes and the same signing key.

## Existing debug builds

Older APKs published by the previous workflow were built as Debug variants on fresh CI runners. Their signing certificate is not guaranteed to match the new persistent release key, so Android may refuse to update them in place. Before migrating to the first persistent-key release, back up any game saves you need. Preserve the keystore afterward; replacing it again would break signature continuity.

## Scope of validation

A release workflow passing means the source checks, ARM64 native build, title module build, Android package build, and configured signing steps passed. It does not establish that the game is fully playable on every Android device. Device qualification still needs direct launch, gameplay/input, audio, save/reload, pause/resume, renderer lifecycle, and performance tests.
