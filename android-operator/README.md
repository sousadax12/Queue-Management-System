# Queue Operator (native Android)

The operator terminal — the counter device a clerk taps to advance a queue
number — as a native Kotlin/Compose app. It replaces the Flutter app in `lib/`
at the repository root (setup → login → home). Decisions and frozen interfaces
are in [`docs/design/2026-11-17-native-operator-app.md`](../docs/design/2026-11-17-native-operator-app.md).

- `applicationId`: `pro.joaosousa.queue_management_system` — deliberately the
  Flutter app's registered Firebase package (D2), so the committed
  `app/google-services.json` works with no new Firebase app record. It
  **replaces** the Flutter app on a device instead of installing beside it.
- `minSdk 26`, `targetSdk 35`, **`compileSdk 37`** (required; `ANDROID_HOME` must
  have `platforms;android-37`).
- Kotlin + Jetpack Compose (Material 3), Hilt, single activity (`MainActivity`).

## Modules

| Module | What lives there |
|---|---|
| `:app` | `MainActivity`, the Hilt application and app-wide bindings, state holders, screen wiring. |
| `:ui` | `OperatorUiState` / `OperatorEvent`, screens. Depends on `:domain`, never on `:data`. |
| `:domain` | **Shared with `android-tv`** by `projectDir` (`../android-tv/domain`): models, `VideoSourceResolver`, pure logic. |
| `:data` | **Shared with `android-tv`** by `projectDir` (`../android-tv/data`): Firestore mappers, repositories, auth, RSS client. |

### Shared modules (D1)

`settings.gradle.kts` includes `:domain` and `:data` pointing at
`android-tv/domain` and `android-tv/data`, so both apps build one copy of the
contract and mappers — but it also means **a change under `android-tv/` can
break this build**. CI therefore runs on `android-tv/**` changes as well as
`android-operator/**`. Keep those modules free of TV-only dependencies.

Versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Build and test

Requirements: JDK 17 and the Android SDK (`compileSdk 37`).

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/android

./gradlew :app:assembleDebug     # debug APK -> app/build/outputs/apk/debug/
./gradlew lint testDebugUnitTest # lint + JVM/Robolectric unit tests
```

CI (`.github/workflows/android-operator.yml`) runs
`./gradlew lint testDebugUnitTest assembleDebug` from this directory and uploads
the APK and the reports under `*/build/reports/`.