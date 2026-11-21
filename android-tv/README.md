# Queue Display for Android TV

Native Android TV signage app that will replace the display part of the Flutter app.
The design and milestones are in [`docs/design/2026-11-15-android-tv-display.md`](../docs/design/2026-11-15-android-tv-display.md).

The same build also holds the native **operator app** (`:operator`), which will replace the Flutter operator app.
See [Operator app](#operator-app) and [`docs/design/2026-11-21-native-operator-app.md`](../docs/design/2026-11-21-native-operator-app.md).

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
| `:operator` | The operator app: email/password sign-in, the claim-based session (`OperatorSession`) and its screens. Uses `:domain` (`OperatorScope`) and `:ui`, not `:data`. |

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
- `:app`, `:ui` and `:operator` tests run on Robolectric (Android SDK 35).
- The debug APKs are written to `app/build/outputs/apk/debug/app-debug.apk` and `operator/build/outputs/apk/debug/operator-debug.apk`.
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

## Operator app

`:operator` is the native rewrite of the Flutter operator app (design [`2026-11-21-native-operator-app.md`](../docs/design/2026-11-21-native-operator-app.md), Option B).
It **replaces** the Flutter app on a box: same `applicationId` (`pro.joaosousa.queue_management_system`), same Firebase registration (`operator/google-services.json` is a copy of `android/app/google-services.json`), `versionCode` 1000+.
Because the Flutter builds were signed with throwaway debug keys, the first install over the Flutter app needs an uninstall (`adb uninstall pro.joaosousa.queue_management_system`).

Operators sign in with an email/password account whose ID token has `email_verified: true` and the claims `{role, org, sites}` (`firestore.rules`).
There is no anonymous sign-in: a restored anonymous session is signed out.
An account without a usable role, org or sites, or with an unverified email, sees **No operator access** and is signed out.
Today the setup screen is a placeholder that shows the account's scope and a sign-out button. Queues arrive in O3.

### Run against the Auth emulator

Debug builds talk to the Auth emulator (project `demo-qms`) when built with `-PfirebaseEmulatorHost=<host>`; release builds ignore it.

1. Start the Auth emulator from the repo root:

   ```bash
   firebase emulators:start --only auth --project demo-qms
   ```

2. Create a verified operator account. Until `grant-operator` (O1) lands, use the emulator's REST API. Sign up:

   ```bash
   curl -s -H "Content-Type: application/json" "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key" -d '{"email":"balcao@clinica.example","password":"operator123","returnSecureToken":true}'
   ```

   Then, with the `localId` it returns, verify the email and set the claims (the `owner` token is the emulator's admin access):

   ```bash
   curl -s -H "Authorization: Bearer owner" -H "Content-Type: application/json" "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/projects/demo-qms/accounts:update" -d '{"localId":"<localId>","emailVerified":true,"customAttributes":"{\"role\":\"operator\",\"org\":\"org_clinica_K9xP\",\"sites\":[\"site_lisboa_Wd2e\"]}"}'
   ```

   Sign up a second account (for example `nobody@clinica.example`) and leave it without claims to see the no-access screen.

3. Build, install and launch. From an Android emulator the dev machine is `10.0.2.2`:

   ```bash
   ./gradlew :operator:installDebug -PfirebaseEmulatorHost=10.0.2.2
   ```

   ```bash
   adb shell am start -n pro.joaosousa.queue_management_system/pro.joaosousa.queuedisplay.operator.OperatorActivity
   ```

4. With the remote (or `adb shell input keyevent`): type the email, press Enter (focus moves to the password), type the password, press Enter.
   The operator account reaches the placeholder setup screen; the role-less one sees **No operator access**.

CI uploads the operator's debug APK as the `operator-debug-apk` artifact.
