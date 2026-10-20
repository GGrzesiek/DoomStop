# Releasing

Releases are built, signed and published by [`release.yml`](../.github/workflows/release.yml)
when a tag `vMAJOR.MINOR.PATCH` is pushed.

## One-time setup: the signing key

### 1. Generate a keystore

Keep it **outside** the repository.

```bash
keytool -genkeypair -v \
  -keystore doomstop-release.jks -storetype PKCS12 \
  -alias doomstop -keyalg RSA -keysize 4096 -validity 10000
```

On Windows, `keytool` ships with Android Studio:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore doomstop-release.jks -storetype PKCS12 `
  -alias doomstop -keyalg RSA -keysize 4096 -validity 10000
```

PKCS12 keystores use a single password for the store and the key, so `KEYSTORE_PASSWORD` and
`KEY_PASSWORD` below get the same value.

### 2. Encode it as base64

| OS | Command |
|---|---|
| Linux | `base64 -w0 doomstop-release.jks > keystore.b64` |
| macOS | `base64 -i doomstop-release.jks -o keystore.b64` |
| Windows (PowerShell) | `[Convert]::ToBase64String([IO.File]::ReadAllBytes("doomstop-release.jks")) \| Set-Clipboard` (copies it to the clipboard) |

### 3. Add repository secrets

GitHub → repository **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the base64 text from step 2 |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `doomstop` |
| `KEY_PASSWORD` | the same password as `KEYSTORE_PASSWORD` |

Or with the GitHub CLI:

```bash
gh secret set KEYSTORE_BASE64 < keystore.b64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body doomstop
gh secret set KEY_PASSWORD
```

### 4. Clean up and back up

- Delete `keystore.b64`.
- **Back up `doomstop-release.jks` and its password** (e.g. in a password manager). Without them nobody can
  ship an update to installed copies: users would have to uninstall and reinstall the app.

## Cutting a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

- `v1.2.3-rc.1` is published as a pre-release.
- `versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`, so keep `MINOR` and `PATCH` at 99 or below and never
  reuse a version: Android refuses to install an update with a lower `versionCode`.
- Release notes are generated automatically from the commits and pull requests since the previous tag.
- If a secret is missing, the workflow stops at the first step and names the missing secret.

## Verifying a download

```bash
sha256sum -c doomstop-1.0.0.apk.sha256
apksigner verify --print-certs doomstop-1.0.0.apk
```
