# Design: native Android operator app (port of the Flutter app)

**Status:** accepted, in progress. Supersedes the Flutter operator app
(`lib/` at the repository root, 1,231 lines of Dart).

*This section is the shared source of truth for the port. Decisions here are
binding on the implementation; anything not decided here is an implementation
detail.*

## Goal

The operator terminal — the device a counter clerk taps to advance a queue
number — becomes a **native Android app** in this repository, so the whole
Android surface (TV box + operator terminal) is one Kotlin codebase sharing one
contract, and the Flutter app can be retired.

## Scope

In: setup (pick site + screen), login, home (queue number, current date, RSS
ticker, queue video), Firebase wiring, live Firestore, Media3 video, and tests.
Out: the portal (`admin-portal/`), the TV app's remaining milestones (#5 M3+),
Firestore rules/claims rollout (#17), anything in `fap-*` repositories.

## Decisions

| # | Decision | Why |
|---|---|---|
| D1 | A sibling Gradle build `android-operator/` with `:app` and `:ui`, **reusing** `android-tv`'s `:domain` and `:data` by `projectDir` | One source of truth for models, mappers and repositories; no duplicated contract code, and the TV build is untouched |
| D2 | `applicationId = pro.joaosousa.queue_management_system` | That package is already a registered Firebase Android app in `santos-1b6f6`, so the existing `google-services.json` works and no new app record is needed. Consequence: it replaces the Flutter app on a device rather than installing beside it |
| D3 | Compose **Material 3** for the operator UI (the TV app keeps `tv-material`) | A counter terminal is a phone/tablet-shaped touch UI, not a 10-foot leanback UI |
| D4 | Direct video URLs play in **Media3/ExoPlayer** | The native equivalent of `video_player` |
| D5 | YouTube URLs play in a **WebView iframe**, reduced to a video id by `VideoSourceResolver` | There is no supported native YouTube player (the Android Player API is deprecated and needs the YouTube app). A WebView is the closest honest equivalent to `youtube_player_flutter` |
| D6 | The RSS ticker uses **our own parser** (`RssRepository`) | `webfeed` has no Android equivalent; the feed shapes are simple and the parser is unit-testable |
| D7 | Queue icons keep the contract's **FontAwesome code point**; the TTF ships in the app | The `icon` field is a Firestore value today and changing it is a contract change (invariant 9: additive-only) |
| D8 | Auth supports **both** anonymous and email/password + claims | The Flutter app signs in anonymously and rules v1 (#17) is not deployed, so shipping only the claims path would break live devices; shipping only anonymous blocks the hardening |
| D9 | UI state is one `OperatorUiState` + `OperatorEvent` contract in `:ui`; state holders live in `:app` | Keeps `:ui` free of `:data` (the TV app's rule) and lets screens be written independently |
| D10 | Screenshots come from the **headless emulator** (`android-avd`) at 1920x1080 | No physical device here, and the owner wants visual evidence per change |

## Frozen interfaces (do not change without editing this doc)

`:domain` — `Queue`/`SITE`/`Screen`/`Playlist` models, `TemplateId`, `Feed`,
`FeedItem`, `VideoSource` + `VideoSourceResolver`, `OperatorRole`,
`OperatorIdentity`.

`:data` — `AuthRepository` (+ `AuthState`), `QueueRepository`,
`ConfigRepository` (+ `ScreenResult`), `RssRepository`.

`:ui` — `OperatorUiState`, `OperatorEvent`, `OperatorScreen`, `SiteOption`,
`ScreenOption`, and `OperatorApp(state, onEvent, modifier)`.

## Acceptance

- `./gradlew :app:assembleDebug` green in `android-operator/`, and
  `lint testDebugUnitTest assembleDebug` green in `android-tv/`.
- The app boots in the emulator, and **a screenshot of each of the three
  screens** is attached to the PR.
- Anonymous sign-in and email/password sign-in both reach `HOME`; the claims
  path reads `role`/`sites` from the ID token and never from a request value.
- Advancing a number writes only `queues/{id}.number`.
- RSS parses a real feed; an unreachable or malformed feed shows an error state
  without crashing the ticker.
- Tests: JVM tests for the RSS parser, `VideoSourceResolver` and the mappers the
  port adds; a Robolectric/Compose test per screen.

## Deferred to the owner (not blocking the port)

- Whether to keep an iOS build of the operator app (the Flutter app also builds
  iOS; a native Android port forks the codebase).
- Migrating the `queues.icon` field off FontAwesome code points.
- The overlap with the portal's operator console (portal A11).
- Release signing keystore.
