# Queue Display for Android TV

Native Android TV signage app that will replace the display part of the Flutter app.
The design and milestones are in [`docs/design/2026-11-15-android-tv-display.md`](../docs/design/2026-11-15-android-tv-display.md).

- App ID: `pro.joaosousa.queuedisplay.tv` (installs beside the Flutter app)
- `minSdk 26`, `targetSdk 35`, `compileSdk 37`
- Kotlin + Jetpack Compose for TV (`androidx.tv:tv-material`), Hilt, single activity (`DisplayActivity`)

## Modules

| Module | What lives there |
|---|---|
| `:app` | `DisplayActivity`, the Hilt application and app-wide bindings. Later: boot receiver, kiosk, pairing and diagnostics UI. |
| `:domain` | Pure Kotlin/JVM, no Android dependency: models and logic (scheduler, sync planner, chime detector, ...). |
| `:data` | Firestore mappers and repositories, media cache and prefetch. Empty until M2. |
| `:ui` | Theme, templates, zones and previews. Depends on `:domain`, never on `:data`. |

Versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Requirements

- JDK 17 or newer (CI uses 17).
- Android SDK with `ANDROID_HOME` set, or `sdk.dir` in `android-tv/local.properties`.
  Android Studio sets this up. Missing SDK platforms are downloaded on the first build if the licences are accepted.

## Build and test

From this directory:

```bash
./gradlew lint testDebugUnitTest assembleDebug
```

CI runs the same command from this directory (`.github/workflows/android-tv.yml`). From the repo root, use `android-tv/gradlew -p android-tv ...`.

- `:domain` tests run on the JUnit Platform. `testDebugUnitTest` is an alias for its `test` task.
- `:app` and `:ui` tests run on Robolectric (Android SDK 35).
- The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
- Reports: `*/build/reports/` (lint and tests).

## Run on an Android TV emulator

1. Install a TV system image and create an AVD. In Android Studio: **Device Manager → Create Virtual Device → TV → 1080p**, with an API 34 or 35 Google TV image. Or from the command line:

   ```bash
   sdkmanager "system-images;android-34;google-tv;x86_64"
   ```

   ```bash
   avdmanager create avd -n tv_1080p -k "system-images;android-34;google-tv;x86_64" -d tv_1080p
   ```

   On Apple Silicon use the `arm64-v8a` image instead of `x86_64`.

2. Start it, then install and launch the app:

   ```bash
   emulator -avd tv_1080p &
   ```

   ```bash
   ./gradlew installDebug
   ```

   ```bash
   adb shell am start -n pro.joaosousa.queuedisplay.tv/.DisplayActivity
   ```

The app also appears in the TV launcher's app row with its banner.

## Install on a box with `adb`

1. On the box, enable developer options (**Settings → System → About → Android TV OS build**, press OK seven times).
   Then turn on **USB debugging** and, for network installs, **Network debugging** / **Wireless debugging**.
2. Connect over the network. The box shows its IP in the debugging settings:

   ```bash
   adb connect 192.168.1.50:5555
   ```

   Accept the prompt on the TV the first time. On Android 11+ with wireless debugging, pair first with `adb pair <ip>:<pairing-port>` and the code shown on the TV.
3. Install (or update, keeping data) and launch:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

   ```bash
   adb shell am start -n pro.joaosousa.queuedisplay.tv/.DisplayActivity
   ```

CI uploads the same debug APK as the `android-tv-debug-apk` artifact on every run, so you can install a build without compiling locally.

Launch on boot, keeping the box awake and other kiosk setup come in M8 (`docs/android-tv/DEPLOY.md`).
