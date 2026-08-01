# GitHub release builds

Build **desktop JAR**, **Windows app folder (`.exe`)**, and **signed Android APK** from this repo. Version numbers live in the root [`build.gradle`](../build.gradle) (`appVersionName`, `appVersionCode`).

**Quick index:** [README.md](README.md) · [streaming setup](streaming-setup-guide.md)

---

## Prerequisites

| Platform | Requirement |
|----------|-------------|
| **All** | JDK **21** (or 17+) on `PATH`; repo cloned |
| **Desktop JAR / EXE** | Windows 10+ to build the Windows `.exe` (jpackage is OS-specific) |
| **Android APK** | Android SDK via [Android Studio](https://developer.android.com/studio); `local.properties` with `sdk.dir=...` |
| **Signed APK** | `android/keystore.properties` (copy from [`keystore.properties.example`](../android/keystore.properties.example)) — **never commit** |

Bump before each public release:

```gradle
// build.gradle (allprojects.ext)
appVersionCode = 895   // must increase for each Play sideload update
appVersionName = '3.3.8'
```

---

## One-command packaging (Windows)

From repo root (PowerShell):

```powershell
.\scripts\package-release.ps1
```

This runs Gradle, then copies artifacts into **`release/`** (gitignored):

| Output | Source |
|--------|--------|
| `Shattered-Pixel-Dungeon-QoL-<ver>-desktop.jar` | `desktop/build/libs/desktop-<ver>.jar` |
| `Shattered-Pixel-Dungeon-QoL-<ver>-windows-x64.zip` | `desktop/build/jpackage/Shattered Pixel Dungeon QoL/` |
| `Shattered-Pixel-Dungeon-QoL-<ver>-android.apk` | `android/build/outputs/apk/release/android-release.apk` |

Options:

```powershell
.\scripts\package-release.ps1 -SkipAndroid          # desktop only
.\scripts\package-release.ps1 -SkipJpackage       # JAR + APK only (faster)
```

---

## Manual Gradle commands

```powershell
# Desktop fat JAR (needs Java installed to run)
.\gradlew.bat desktop:release

# Windows portable folder + bundled JRE (no Java install for players)
.\gradlew.bat desktop:jpackageImage

# Signed release APK (requires android/keystore.properties)
.\gradlew.bat android:assembleRelease
```

**Outputs**

- JAR: `desktop/build/libs/desktop-<appVersionName>.jar`
- EXE: `desktop/build/jpackage/Shattered Pixel Dungeon QoL/Shattered Pixel Dungeon QoL.exe` (zip the whole folder for GitHub)
- APK: `android/build/outputs/apk/release/android-release.apk`

Run the desktop JAR:

```powershell
java -jar "desktop\build\libs\desktop-3.3.7.jar"
```

Enable **Settings → Streaming** in-game for chat integration; Android builds do not include the overlay server.

---

## GitHub Releases (upload)

1. Tag the commit: `git tag v3.3.7` && `git push origin v3.3.7`
2. Run `.\scripts\package-release.ps1` locally (or use the **Build release** workflow).
3. On GitHub: **Releases → Draft new release** → choose tag `v3.3.7`
4. Attach from `release/`:
   - **JAR** — players with Java 21+
   - **windows-x64.zip** — extract and run the `.exe`
   - **android.apk** — sideload (same signing key for updates)
5. Paste viewer-facing notes from [user-facing-summary.md](user-facing-summary.md) / [COMMANDS.md](../COMMANDS.md).

**CI:** [.github/workflows/build-release.yml](../.github/workflows/build-release.yml) builds on tag push or manual dispatch. Configure Android signing secrets (below) for APK in CI.

---

## GitHub Actions secrets (optional APK in CI)

| Secret | Value |
|--------|--------|
| `ANDROID_KEYSTORE_BASE64` | Base64 of your `.jks` / keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Store password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Without these, the workflow still produces **JAR + Windows zip** on `windows-latest`.

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `Release signing requires android/keystore.properties` | Copy example file and fill in paths/passwords |
| `SDK location not found` | Create `local.properties`: `sdk.dir=C\:\\Users\\...\\AppData\\Local\\Android\\Sdk` |
| jpackage succeeds but no `.exe` | Open `desktop/build/jpackage/Shattered Pixel Dungeon QoL/`; zip entire folder |
| APK install blocked | Enable “Install unknown apps”; keep the same keystore for updates |
| Gradle OOM | Already set `-Xmx2048m` in `gradle.properties`; close other apps |

---

## License

GPLv3 — ship source with binaries. This repo satisfies that; link to your GitHub repo in release notes.
