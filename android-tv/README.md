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
| `:domain` | Pure Kotlin/JVM, no Android dependency: models, repository interfaces and logic (`ObserveDisplay`; later the scheduler, sync planner, chime detector, ...). |
| `:data` | Firestore mappers and repositories (M2). Later: media cache and prefetch. |
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

## Firestore contract

The TV reads `devices/{uid}`, `sites/{siteId}`, its screen, its playlist and `queues where siteId == <its site>`.
The contract is [`admin-portal/packages/contract`](../admin-portal/packages/contract) (zod schemas, golden fixtures, seed and backfill scripts) and the design doc's "Interface with the admin portal".

- Mapping is manual (`:data`, `mapping/`), never `toObject()`. Every number is read through `Number`, colours stay `Long`, and a bad or missing field falls back to the defaults in the `:domain` models (`QueueDefaults` mirrors the contract's `QUEUE_DEFAULTS`).
- Wrong types, unknown templates, and dropped playlist items or renditions are logged once under the `QueueDisplay` tag as `Contract: <doc path>.<field>: <problem>`. A screen whose template is missing or unknown shows `queues_media` and carries `Screen.templateFallback`.
- The mapper tests load the contract's fixtures directly (`ContractFixtures`), so CI also runs when `admin-portal/packages/contract/fixtures/**` or `src/schemas/**` changes.

## Run against the Firestore emulator

Debug builds talk to the Firebase emulators when built with `-PfirebaseEmulatorHost=<host>`. They then use the emulator project `demo-qms`, the same one the contract's seed writes to, and never touch the real project. Release builds ignore the property.

You need Node 24+, pnpm and `firebase-tools`. All `pnpm` commands below run from `admin-portal/`.

1. Start the Auth and Firestore emulators from the repo root. They load `firestore.rules` and use the ports in `firebase.json`:

   ```bash
   firebase emulators:start --only auth,firestore --project demo-qms
   ```

2. Seed the contract fixtures (two orgs, sites, screens, queues, playlists and devices):

   ```bash
   pnpm install && pnpm seed
   ```

3. Build and launch the app. From an Android emulator the dev machine is `10.0.2.2`:

   ```bash
   ./gradlew installDebug -PfirebaseEmulatorHost=10.0.2.2
   ```

   ```bash
   adb shell am start -n pro.joaosousa.queuedisplay.tv/.DisplayActivity
   ```

   On a real box, run `adb reverse tcp:8080 tcp:8080` and `adb reverse tcp:9099 tcp:9099` and build with `-PfirebaseEmulatorHost=127.0.0.1`.

4. The box signs in anonymously, creates its `devices/{uid}` doc and shows **Not claimed yet** with its device id. Under the rules it can read nothing else until it's claimed. Claim it the way the portal will, with a pairing code for that uid:

   ```bash
   pnpm seed pairing-code --uid <device id>
   ```

   ```bash
   pnpm seed claim <CODE> site_porto_3fKq scr_lobby_left_Q2mz
   ```

   Within a second or two the screen lists the site's queues with their numbers, plus the site, screen, template and playlist it resolved.

5. Change a number and watch it update. The Flutter operator does this in real use. Against the emulator, an `owner` token skips the rules:

   ```bash
   curl -s -X PATCH -H "Authorization: Bearer owner" -H "Content-Type: application/json" "http://127.0.0.1:8080/v1/projects/demo-qms/databases/(default)/documents/queues/q_balcao_a_P4tc?updateMask.fieldPaths=number" -d '{"fields":{"number":{"integerValue":"43"}}}'
   ```

Flutter-era queues without `orgId`/`siteId` are invisible to boxes until they're backfilled. The contract's backfill sets both (a dry run unless `--apply`; see `scripts/backfill-queues.ts` for real projects):

```bash
pnpm backfill:queues --org <orgId> --site <siteId>
```

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

1. Start the Auth and Firestore emulators from the repo root, then seed the contract fixtures (from `admin-portal/`; see [Run against the Firestore emulator](#run-against-the-firestore-emulator)):

   ```bash
   firebase emulators:start --only auth,firestore --project demo-qms
   ```

   ```bash
   pnpm install && pnpm seed
   ```

2. Create a verified operator account with `grant-operator` ([`docs/operator-accounts.md`](../docs/operator-accounts.md)). It prints a generated password once:

   ```bash
   pnpm grant-operator --email lisboa@clinica.example --org org_clinica_K9xP --site site_lisboa_Wd2e
   ```

   To see the no-access screen, also create an account without claims through the emulator's REST API:

   ```bash
   curl -s -H "Content-Type: application/json" "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key" -d '{"email":"nobody@clinica.example","password":"operator-test-123"}'
   ```

   An account without claims sees **No operator access** whether or not its email is verified. To get the role message rather than the unverified one, verify it with the `localId` the call returned:

   ```bash
   curl -s -H "Authorization: Bearer owner" -H "Content-Type: application/json" "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/projects/demo-qms/accounts:update" -d '{"localId":"<localId>","emailVerified":true}'
   ```

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
