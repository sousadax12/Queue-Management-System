# Design: native Android operator app (rewrite of the Flutter app)

Refs #21

## Status

**Accepted (2026-11-21).** The owner picked **Option B, phase 1 = TV-box parity only**, and
answered the open questions. See [Decision](#decision). The sections below are updated to
match. The main change from the proposal: advancing works **offline, last writer wins**
(a plain `update`, not a transaction).

## Context

### Goal

Rewrite the Flutter operator app (`lib/`, about 1,340 lines of Dart) as a native Android app
that does everything the Flutter app does, on the hardware it runs on today, and works under
rules v1 (`firestore.rules`, #16) from day one. The owner has made this the top priority. The
admin portal is not a priority, so this plan must not depend on new portal work.

### What the operator app actually is

The "operator app" is more than a remote control. On `/home` it is **also a display**: queue
tiles on the left (flex 1), the selected queue's video on the right (flex 3), the RTP news
ticker over the video, and a clock in the corner. An operator with a TV remote presses
left/right on the same box that drives the screen. So a native rewrite has to rebuild most
of the display surface that the TV app's M3 (queue/clock/ticker zones) and M6 (legacy
`videoURL` mode) also plan to build. This overlap drives the code-sharing options below.

### Feature inventory of the Flutter app (the parity checklist)

Every item below is a parity requirement unless it is marked *bug* or *quirk*. Where we
propose to keep a quirk, the item says so.

| # | Area | Behaviour today | Where |
|---|---|---|---|
| F1 | Start-up | `Firebase.initializeApp`, then `loadOperatorScope`: no user → login; **anonymous user → signed out**; a user whose claims give no scope → signed out. A spinner while loading, `Error: …` text if init throws. Initial route: `/` (login) without a scope, `/setup` with one. | `lib/main.dart:27-79`, `lib/auth/operator_scope.dart:79-91` |
| F2 | Scope from claims | `{role, org, sites}`. `platformAdmin` → every queue; `orgAdmin` → `orgId == org`; `manager`/`operator` → `orgId == org` **and** `siteId in sites` (first 30, the Firestore `in` limit). Unknown role, missing org, or a site role with no sites → no scope. | `lib/auth/operator_scope.dart:20-73`, `test/operator_scope_test.dart` |
| F3 | Login | Email + password form. Validation ("Please enter an email/a password"), email trimmed, Enter on email moves to password, Enter on password submits, autofill hints, spinner while signing in. After sign-in the token is **force-refreshed** so new claims are seen. No scope → dialog "No operator access" and sign out. Auth error codes map to fixed messages (wrong credentials, invalid email, disabled, too many requests, no network). | `lib/pages/login_page.dart` |
| F4 | `email_verified` | *Gap:* the app does **not** check it. An unverified account gets a scope and then every read fails with `PERMISSION_DENIED`, because the rules ignore its role (`firestore.rules:32-37`). | `lib/auth/operator_scope.dart` |
| F5 | Setup (queue picker) | One-off `get()` of the scoped queue query. Queues in one horizontal row of 300 px tiles. D-pad **left/right** (on key **up**) moves the highlight (green), **select** (DPAD_CENTER) picks, a **tap** also picks. Empty → "No documents found". Error → `Error: …`. App-bar **sign-out** icon (signs out, clears selection, back to login). | `lib/pages/setup_page.dart` |
| F6 | Home: queue column | Live `snapshots()` of the scoped query. One tile per queue: name (45 sp, bold) and number (70 sp, bold) on `backgroundColor`, text in white. Same empty/error/loading states as F5. | `lib/pages/home_page.dart:88-124`, `lib/widgets/queue_widget.dart` |
| F7 | Advancing | D-pad **right** (key up) → +1, **left** → −1, wrapping **99→0** and **0→99**. Writes `update({'number': n})`. *Bug:* `n` comes from the `selectedQueue` copy taken by the setup page's one-off `get()`, not from the live snapshot, so a change made elsewhere (another operator, the portal) is overwritten with a stale value. Write errors are ignored. | `lib/providers/queues_provider.dart`, `lib/pages/home_page.dart:59-80` |
| F8 | Chime | `pixiedust.mp3` plays on **every** left/right press, on the pressing box only, at `soundVolume` (an int 0 or 1 in the contract, so on/off). It plays whether or not the write succeeds. | `lib/pages/home_page.dart:69-79`, `assets/sounds/pixiedust.mp3` |
| F9 | Video | The selected queue's `videoURL`, looping. YouTube URLs (`youtube.com`, `youtu.be`) go to `youtube_player_flutter` (a WebView iframe), **started muted**; playlists-only URLs are rejected. Other URLs go to `video_player`. Volume is `videoVolume` (0 or 1). Errors → empty black area. *Quirk:* the URL comes from the setup-time copy, so a portal edit needs a restart. | `lib/widgets/video_player_widget.dart` |
| F10 | RSS ticker | Hard-coded `https://www.rtp.pt/noticias/rss`. Item titles joined by `   \|   `, white 20 sp, 40 px high, endless marquee at the queue's `velocity` (logical px/s), at the bottom of the video area. Any error → nothing shown. *Bug:* the feed is fetched again on every rebuild, with no refresh timer and no cache. | `lib/widgets/rss_feed_widget.dart`, `lib/pages/home_page.dart:137-144` |
| F11 | Clock | `HH:mm`, white, bold 16 sp, top-right of the video area. *Quirk:* it is redrawn every frame by a `Ticker`. | `lib/widgets/current_date_widget.dart` |
| F12 | Queue icon | `icon` is mapped to a Font Awesome **Solid** `IconData` (hence `--no-tree-shake-icons` in CI), but *gap:* `QueueWidget` **never renders it**. | `lib/mappers/queue_doc_mapper.dart:12`, `lib/widgets/queue_widget.dart` |
| F13 | Navigation | Login → setup → home with `pushReplacement`, so **Back on home exits the app**. There is no sign-out on home. The selected queue is in memory only: after a restart the session is restored (F1) but the operator picks the queue again. | `lib/main.dart:64-69`, `lib/pages/setup_page.dart:43-45` |
| F14 | Android host | `applicationId pro.joaosousa.queue_management_system`. `LEANBACK_LAUNCHER` **and** `LAUNCHER`, `leanback` and `touchscreen` both `required="false"`, cleartext allowed, `singleTop`. Version `1.0.0+1` → `versionCode 1`. **Release builds are signed with the debug key**, and CI builds `flutter build apk --debug` on an ephemeral runner, so each CI APK carries a different, throwaway debug signature. | `android/app/src/main/AndroidManifest.xml`, `android/app/build.gradle`, `pubspec.yaml:19`, `.github/workflows/build-apk.yml` |
| F15 | Firebase | Project `santos-1b6f6`. Android, iOS and web apps are registered; `android/app/google-services.json` is for `pro.joaosousa.queue_management_system`. | `lib/firebase_options.dart`, `android/app/google-services.json` |
| F16 | Strings | English (the login screen, "Select a queue: "). | `lib/pages/*` |

### Form factor: what the code settles and what it doesn't

The code points to a **TV box with a remote** as the real target. The home screen has **no
touch control at all**: on a phone you can pick a queue (F5 has a tap handler) but you can
never advance it. The layout is a fixed landscape row with 300 px tiles. The manifest is
TV-first but keeps `LAUNCHER` and makes touch optional, which is what lets it install on
generic boxes too. The code does **not** settle whether phones or tablets matter: an iOS app
is registered in Firebase and `ios/` builds, and the portal design planned a phone-friendly
operator console (A11) for that job. **Answered (Q1):** TV boxes with a remote only. iOS is
out of scope.

### Constraints from rules v1 and the contract

- **Accounts.** Email/password with custom claims `{role, org, sites}` and
  `email_verified == true`. Anonymous users have no role (`firestore.rules:29-37`).
- **Queue reads.** A query is allowed only if every result could pass `operates(orgId,
  siteId)`. So it must filter `orgId == claim org` and `siteId in claim sites` for
  `operator`/`manager` (`firestore.rules:175-179`; rules-tests `queues.test.ts:53-56, 90-91`).
  The query has equality and `in` filters and no `orderBy`, so single-field indexes serve it
  and no composite index is needed. We sort by name on the client to keep it that way.
- **Queue writes.** An operator may change only `number`, and only to an int in 0..99
  (`firestore.rules:183-187`).
- **Sites.** An operator may `get` the sites it operates (`firestore.rules:160`) but may not
  list `sites` unscoped. The site always comes from the claims, never from a picker shown
  before sign-in.
- **Queue fields.** `soundVolume` and `videoVolume` are ints 0..1, `icon` is a Font Awesome
  code point, colours are unsigned 32-bit ARGB (a `Long` on Android), and any number may arrive
  as `Long` or `Double` (TV invariant 11; `admin-portal/packages/contract/src/schemas/queues.ts`).
- **Operator accounts today.** Nothing creates them yet. The contract package has
  `grant-platform-admin` (`admin-portal/packages/contract/scripts/grant-platform-admin.ts`,
  `src/admin/grant.ts`) but no operator equivalent, and the portal's invites (A4/A11) are not
  built. Existing queues need `orgId`/`siteId` from `pnpm backfill:queues` before any scoped
  query returns them.

### The `android-tv` project and M2

- `android-tv/` is one Gradle build (`settings.gradle.kts`) with `:app`, `:domain` (pure
  JVM), `:data` and `:ui` (Compose for TV, `tv-material`), Hilt, one version catalog
  (`gradle/libs.versions.toml`: AGP 9.5.1, Kotlin 2.4.21, `minSdk 26`, `targetSdk 35`), and
  its own workflow `android-tv.yml` (`lint testDebugUnitTest assembleDebug`, path-filtered to
  `android-tv/**`).
- `:ui` depends on `:domain` only. `:data` has a Hilt `DataModule` installed in
  `SingletonComponent`, empty until M2.
- **M2 is being written now** (session `tv-m2`; `orch/5-tv-m2` is not pushed yet, so its
  exact API can't be read). From the TV design's M2 scope and the closed first attempt
  (PR #19, `origin/hermes/5-android-tv-m2`), it will expose: a `Queue` model in `:domain` with
  `orgId`, `siteId`, `number`, `name`, `icon`, colours as `Long`, volumes as `Double` and
  `velocity`, plus `QueueDefaults`; a manual `QueueMapper` in `:data` written against a small
  `DocumentData` view, not the SDK type; and a Kotlin loader for the contract fixtures. It will
  also bring **TV-only** pieces: anonymous device auth, `devices/{uid}`, and site-scoped reads
  that follow the device doc.

### What the closed attempt (PR #20) got wrong, and what this design does instead

PR #20 (branch `hermes/qms-native-port`, design
`docs/design/2026-11-17-native-operator-app.md` on that branch) was closed after the review
comment on the PR. This design answers each point:

| PR #20 | This design |
|---|---|
| Signed in **anonymously** at start-up and read `sites` before login (D8), and kept a "Continuar sem conta" path. | **No anonymous auth anywhere.** Start-up mirrors F1: an anonymous session is signed out. Nothing touches Firestore before an email/password sign-in with a valid scope. |
| A **site/screen picker before login** over an unscoped `sites` list (denied for everyone but platform admins). | **No site picker.** Sites come from the claims. Setup picks a **queue**, as Flutter does, from the claims-scoped query. Screens are a TV concept and stay out of the operator app. |
| Queue query filtered by site only. | The query is built by a port of `OperatorScope` (F2): `orgId ==` **and** `siteId in`, unit-tested on the same cases as `test/operator_scope_test.dart`, and mirrored by a rules-test. |
| Ignored the `org` claim and `email_verified`, did not force-refresh the token, and let a role-less account reach Home. | `OperatorScope.fromClaims` requires the org; the session checks `email_verified` (closing F4); the token is force-refreshed after sign-in; no scope → "No operator access" and sign out. |
| `.catch { emit(emptyList()) }`, so a permission denial looked like "no queues". | Repositories return **typed results** (`Loading`, `Data`, `Error(PermissionDenied/Network/Unknown)`). The UI shows "No queues for this account" and "Access denied" as different states. |
| `number + 1` with no wrap, and no decrement. | Pure `QueueNumber.next()/previous()` with the 99↔0 wrap, both directions, computed from the **live** snapshot rather than a setup-time copy (fixing F7's stale-copy bug) and written with a plain `update({number})` that works offline (owner decision Q6). |
| Material 3 "phone/tablet-shaped touch UI", no `LEANBACK_LAUNCHER`, touchscreen required, no D-pad. | **TV box with a remote first:** `LEANBACK_LAUNCHER` + `LAUNCHER`, touch optional, D-pad left/right/center as in F5/F7, Compose for TV like `android-tv`. |
| No chime, no default RTP ticker, Font Awesome icon left as a TODO. | All three are milestones with acceptance criteria (O3, O4). The icon is rendered (F12 was a gap in Flutter too). |
| Sibling build including `android-tv/{domain,data}` by **`projectDir`**; its CI ran on `android-tv/**`. | The recommended option has **no `projectDir` include**. The operator is a second app module inside the one `android-tv` build, with one settings file and one catalog (Option B). Option A, if chosen instead, shares nothing but the fixtures. |
| `versionCode = 1`, same `applicationId`, different signing key, so it could not upgrade the installed app, and this wasn't said. | [Distribution and upgrade](#distribution-and-upgrade) states it plainly: an in-place upgrade is impossible unless we hold the key that signed the installed APK, so the plan is a one-time reinstall, then a real release key and a `versionCode` above any Flutter build. |
| `gradle.properties` tuned for one agent's memory cap; a wip commit and merge commits. | Repo defaults stay. Each milestone is one squash-merged PR. |
| One "whole port" PR of about 20k lines. | Six reviewable milestone PRs, each usable or testable on its own. |

## Options

All three options are Kotlin + Compose, Hilt, single activity and unidirectional data flow,
like `android-tv` (TV design D9). They differ in **where the code lives and what it shares**.
The phase split (TV box only vs TV + phone) is the same decision in every option, so it is
covered once at the end.

### Option A: standalone `android-operator/` build, sharing only the contract fixtures

**How it works.** A new top-level Gradle build `android-operator/` with its own
`settings.gradle.kts`, its own catalog and modules `:app` and `:core` (model, mapper, scope,
repositories). It writes its own small `Queue` model and mapper (about 150 lines; the operator
needs one collection) and its own clock, ticker and video zones. Its mapper tests read
`admin-portal/packages/contract/fixtures/queues*.json` with a small loader, just as the TV
does. It gets a new workflow `android-operator.yml`, path-filtered to `android-operator/**`
and the contract fixtures.

**What changes.** A new directory and a new workflow. `build-apk.yml` gets
`android-operator/**` in `paths-ignore`. Nothing in `android-tv/` changes.

**Effort.** The highest of the three in total lines: the display zones (queue tile, clock,
RSS parser and ticker, video with the YouTube WebView) are written here and again in TV
M3/M6. In calendar terms it is the fastest start, because nothing waits on M2.

**Risks and costs.**

- **Duplication and drift.** Two queue mappers, two RSS parsers, two video players. The
  shared fixtures catch mapper drift on the contract side, but nothing catches behaviour drift
  in the zones (ticker speed, wrap, chime).
- **Catalog drift.** Two catalogs on the same AGP/Kotlin/Compose/Firebase stack drift apart
  unless something keeps them aligned (Renovate grouping, or a CI script that diffs the shared
  `[versions]` keys). This costs little but is ongoing.
- CI is fully decoupled, which is the main benefit.

**Fit.** Mirrors how `android-tv/` was started ("a new native project in a directory beside
the Flutter app"). It throws away the chance to build the shared zones once.

### Option B (recommended): a second app module in the existing `android-tv` build

**How it works.** Add an application module `android-tv/operator/` (Gradle path
`:operator`) to the **same** build: one `include(":operator")` line in
`android-tv/settings.gradle.kts`, the same catalog, the same Hilt setup. The operator app is
also an Android TV app on the same boxes, the same `minSdk` and the same D-pad focus rules,
so it fits under `android-tv/`.

| Module | Operator uses | Rule |
|---|---|---|
| `:domain` | `Queue`, `QueueDefaults` (from M2); **adds** `OperatorScope`, `StaffRole`, `QueueNumber` (pure, JVM-tested) | Pure Kotlin, as today. |
| `:data` | `QueueMapper` and `DocumentData` (from M2); **adds** the RSS client/parser (TV M3 needs it too) | The operator **never rebinds** an interface that `:data`'s `DataModule` binds for the TV. Its own repositories (`OperatorAuthRepository`, `OperatorQueueRepository`) and their Hilt module live in `:operator`. |
| `:ui` | **Adds** the shared zones: `QueueTile` (with the Font Awesome icon), `ClockZone`, `TickerZone`, `LegacyVideoZone` | Depends on `:domain` only, as today. TV M3/M6 reuse these zones instead of writing their own. |
| `:operator` | Login, no-access, setup and home screens; session state; key handling; chime; Firebase wiring | Its own `applicationId`, `google-services.json`, manifest and version. |

**What changes.** `settings.gradle.kts` (+1 line), new `operator/` module, the catalog gains
what the operator needs (Firebase Auth, Media3, the Google Services plugin if M2 hasn't added
it), and `android-tv.yml` uploads a second APK. `build-apk.yml` already ignores `android-tv/**`. TV milestones M3 and M6
shrink because their zones exist by then.

**Effort.** Lowest in total lines, since the display zones are built once. The first code
milestone that needs `Queue`/`QueueMapper` waits for M2 to merge. Scaffold, auth and the
operator-account CLI don't, so the operator starts now.

**Risks and costs.**

- **CI coupling.** One workflow builds and tests both apps. A TV-only change re-runs the
  operator's tests and the other way round. That adds a few minutes, and since the code is
  shared, it is the check we want. The two apps still release independently (separate
  `assemble` tasks and tags).
- **Catalog drift:** none, because there is one catalog. The price is that a dependency bump
  for one app is a bump for both.
- **`minSdk` is shared.** `:domain`/`:data`/`:ui` use `minSdk 26`, and the operator can't go
  lower than the libraries it uses. If operator boxes run Android 7.x (API 24/25), either
  the shared modules drop to 24 or Option A becomes necessary ([Open question 2](#open-questions)).
- **`:data` weight.** From TV M5, `:data` gains Room, WorkManager and the Media3 cache, which
  the operator doesn't need. It works (Hilt instantiates bindings lazily), but costs APK size.
  If it matters, split a `:data-firestore` module **at M5**, not now.
- **Coordination.** Operator milestones build zones that TV M3 planned to build. The
  orchestrator has to sequence that (the operator goes first, because it is the priority) and
  TV M3's brief has to say "reuse `:ui` zones".
- **Directory name.** `android-tv/` will hold a second app. A rename (for example to
  `android-native/`) is cosmetic and can wait until Flutter is retired.

**Fit.** It follows `android-tv`'s own conventions (D9 modules, manual mappers, fixture tests,
one workflow) instead of inventing parallel ones. It avoids PR #20's specific trap: with a
`projectDir` include, the shared modules' build scripts resolve `libs.*` against the
**including** build's catalog, so the same module silently compiles against different versions
in each build. Here there is one build and one catalog, so that can't happen.

### Option C: an "operator mode" inside the TV display app (one APK)

**How it works.** No separate operator app. The TV app (`pro.joaosousa.queuedisplay.tv`) gets
an operator sign-in. A box signed in as an operator renders the `queues_media` template and
lets D-pad left/right advance the selected queue. This is TV design Open question 1 answered
"yes". Because the TV identity is an anonymous device user and the operator is an
email/password user, the box would run a **second `FirebaseApp` instance** for the operator
(each `FirebaseApp` has its own `FirebaseAuth`), or give up pairing and run as an operator only.

**What changes.** The TV app's start-up, kiosk and pairing flow gain a second mode. The TV
roadmap absorbs the operator's parity list.

**Effort and timing.** Least code at the end, but the **longest wait**: it needs TV M3
(zones), M6 (legacy video) and, for paired boxes, M7 (pairing). The top priority would sit
behind the TV roadmap.

**Risks.** Two auth identities in one process. A TV app whose rules story (device-scoped reads)
and operator story (claim-scoped writes) are mixed. No upgrade path from the Flutter app (a
different `applicationId`, so every box is a reinstall anyway). It contradicts the portal
design's direction, which leans away from D-pad advancing on the TV.

**Fit.** It makes sense only if the owner wants one APK per box **and** answers TV Q1 "the
display advances numbers". Then the separate operator app would be temporary.

### The phase split: TV box only, or TV + phone

| | Phase 1 = TV box parity only (recommended) | TV + phone from the start |
|---|---|---|
| Scope | Landscape 16:9 layout, remote/keyboard input, touch optional (tap still picks a queue, as in F5). | Adds on-screen −/+ buttons, portrait layouts, phone window sizes and a touch-first setup. |
| Why | It matches what runs in the field (F14, the form-factor analysis). Phones can't advance numbers in Flutter today, so nobody can be relying on it. | Only worth it if phones are a real use (Open question 1), and the portal's planned A11 console was meant for that job. |
| Cost later | Adding −/+ buttons and a compact layout is one extra milestone; the session, repository and `QueueNumber` code don't change. | About 1.5× the UI work now, for an unconfirmed need. |

## Recommendation

**Option B, phase 1 = TV-box parity only.** Build the operator as a second app module in the
`android-tv` build, sharing `:domain`, `:data` and new `:ui` zones, with no `projectDir`
include and one catalog. Keep `pro.joaosousa.queue_management_system` as its `applicationId`.

Why B over A: the operator app is a TV display with a remote-controlled number, on the same
boxes as the TV app. About half of its parity list (queue tile, clock, ticker, legacy video)
is work the TV roadmap also has to do. Doing it once, in shared modules that already have
conventions and CI, is cheaper than doing it twice and policing drift between two catalogs.
Why B over C: it ships the top priority without waiting for TV M3–M7, keeps the operator's auth
model (claims, email/password) apart from the TV's (anonymous device), and keeps the Flutter
upgrade path open.

**What would change my mind.**

- Operator boxes run Android 7.x and the TV side won't lower `minSdk` to 24 → **Option A**.
- M2 slips by more than about a week, or the M2 API turns out SDK-bound (mappers taking
  `DocumentSnapshot`) → start with **Option A**'s own mapper inside `:operator`, and move to
  the shared one when M2 lands.
- The owner wants the operator released and reviewed on a fully separate track → **Option A**.
- The owner answers TV Q1 "the display advances numbers" and wants one APK per box → **Option C**,
  with B's modules as the stepping stone (the zones and session code carry over).
- Phones turn out to matter → keep B and add milestone O-touch (below) right after O3.

### Architecture (inside Option B)

- **Session.** `OperatorSession` exposes `StateFlow<SessionState>`: `Starting`, `SignedOut`,
  `SigningIn`, `NoAccess(reason)` (no role, missing org/sites, **email not verified**),
  `Ready(scope, email)`. It mirrors F1/F3 exactly: an anonymous user is signed out, the token
  is force-refreshed after sign-in, and a scope comes only from `OperatorScope.fromClaims`.
- **Queues.** `OperatorQueueRepository.observe(scope): Flow<QueueResult>` builds the query
  from the scope (`orgId ==`, `siteId in` the first 30 sites; no filter for `platformAdmin`, as
  in F2) and maps through M2's `QueueMapper`. Errors are typed, never empty lists. Sorted by
  name on the client.
- **Advancing (owner decision Q6: offline, last writer wins).** `advance(queueId, direction)`
  takes the **last known** `number` of that queue from the live listener (read through
  `Number`, so `Long` or `Double`; the listener includes this device's own pending writes, so
  repeated presses build on each other), computes `QueueNumber.next/previous` (99↔0), and
  calls a plain `update({number: Long})`. No transaction: Firestore's offline cache queues
  the write and applies it locally at once, so the tile changes with the network off and the
  write syncs on reconnect, as in Flutter. Concurrent writers to the same queue are last
  writer wins. Presses go through one serial queue so fast presses apply in order. Being
  offline is **not** an error. Only a write the server rejects (for example
  `PERMISSION_DENIED`) shows a short on-screen message. The chime plays on the press (F8
  parity) through `SoundPool`, preloaded, at `soundVolume`.
- **Input.** `onKeyEvent` on the home root: `DPAD_LEFT`/`DPAD_RIGHT` on key up (F7). Setup:
  focusable tiles in a `LazyRow`, `DPAD_CENTER`/`ENTER`/`NUMPAD_ENTER` or a tap selects (F5).
- **Display zones** (in `:ui`). `QueueTile` renders name, number and the Font Awesome Solid
  glyph (bundled `fa-solid-900` font, SIL OFL 1.1, the same font family `font_awesome_flutter`
  uses, so the stored code points stay valid with no contract change). `ClockZone` ticks on
  minute boundaries, not every frame. `TickerZone` uses `Modifier.basicMarquee(velocity =
  velocity.dp)`, with the feed fetched at start and every 15 minutes, keeping the last good
  list. `LegacyVideoZone` uses Media3 ExoPlayer, looping, for direct URLs, and the YouTube
  IFrame player in a `WebView` for YouTube, started muted like Flutter. The video follows the
  **live** queue document, so it fixes F9's quirk.
- **Navigation.** Login → setup → home, as in Flutter, with sign-out on setup. One deliberate
  change: **Back on home returns to setup** instead of exiting the app (F13), so an operator
  can switch queue without killing the app. The selected queue id is **remembered across
  restarts** in DataStore (default for Q9): with a restored session and a remembered queue
  still in scope, the app opens on home. Sign-out clears it. Starting on boot is deferred.

### Distribution and upgrade

- **`applicationId`:** keep `pro.joaosousa.queue_management_system`. That Android app is
  already registered in `santos-1b6f6`, so we reuse `android/app/google-services.json` (copied
  to `android-tv/operator/`) with no Firebase console step, and the native app **replaces** the
  Flutter one on a box. The Kotlin `namespace` can be `pro.joaosousa.queuedisplay.operator`,
  since the namespace and the `applicationId` don't have to match. Rejected alternative: a new
  id beside Flutter during a pilot, which needs a new Firebase app registration (an owner step
  in the console) and leaves two operator apps on a box.
- **Signing:** Android upgrades in place only if the new APK has **the same signing
  certificate**. Flutter release builds are signed with *a* debug key and CI APKs with a fresh
  runner key each time (F14). So an in-place upgrade works only if we still hold the exact
  keystore that signed the APK on each box, and a debug key shouldn't sign production anyway.
  **Plan: a one-time uninstall of the Flutter app, then install of the native one.** Nothing
  of value is lost: the only local state is the Auth session, and operators must sign in again
  under rules v1 anyway. From the first native release on, a **real release keystore** (created
  by the owner, stored in GitHub secrets) signs every build, so later updates install in place.
  You can check what signed a box's APK with `adb shell pm path pro.joaosousa.queue_management_system`,
  `adb pull`, then `apksigner verify --print-certs`.
- **`versionCode`:** start at **1000**. That is above anything Flutter produced (`1.0.0+1` →
  1, and CI never passes `--build-number`), so a box that still has the Flutter app can never
  "downgrade". Release builds use `1000 + github.run_number`. `versionName` starts at `2.0.0`.
- **Channel:** a sideloaded APK from a tagged release workflow (default for Q4).
- **Owner decision (Q3):** a one-time uninstall of Flutter on each box, plus a new release
  keystore that the owner creates and adds to GitHub secrets. The `applicationId` and the
  `versionCode` 1000+ scheme above are confirmed.
- **Rules deployment (#17):** the native app works with the current allow-all rules **and**
  rules v1, because its claim-scoped queries are valid under both. Deploy rules v1 once every
  box runs the native app, or the current Flutter `main` build (which also uses claims).

### Milestones

Each milestone is one squash-merged PR. O1 and O2 can start now and run in parallel. O3
needs M2 merged. O4 and O5 can run in parallel after O3.

| # | PR | Acceptance criteria |
|---|---|---|
| O0 | **This design doc.** | Done: decision recorded below on 2026-11-21. |
| O1 | **`grant-operator` CLI** in `admin-portal/packages/contract` (Admin SDK, beside `grant-platform-admin`). It creates the user if missing (with a password prompt or a generated one), sets `emailVerified: true`, sets claims `{role: 'operator' \| 'manager', org, sites}`, and mirrors `users/{uid}`. It checks that the org exists and that each site belongs to it, and supports `--dry-run`. A short `docs/operator-accounts.md` covers the CLI and running `pnpm backfill:queues` first. | Vitest on the emulator: the account it creates can run the operator query and advance a queue of its site, and is denied another org's or site's queue (reusing the rules-tests pattern). The tool refuses an unknown org, a site from another org, and an empty site list. It never targets production without `--project … --production`, exactly like `grant-platform-admin`. |
| O2 | **Scaffold `:operator` + sign-in.** The module in `android-tv/settings.gradle.kts`; `applicationId pro.joaosousa.queue_management_system`, `versionCode 1000`, the existing `google-services.json`; manifest with `LEANBACK_LAUNCHER` + `LAUNCHER`, `leanback` and `touchscreen` not required, a TV banner, `INTERNET`. `OperatorScope`/`StaffRole` in `:domain`; `OperatorSession`, login and no-access screens, error-message mapping (F3), sign-out. `android-tv.yml` uploads the operator APK. | JVM tests port every case in `test/operator_scope_test.dart`, plus `email_verified == false` → `NoAccess`, anonymous user → signed out, and force-refresh after sign-in. A Robolectric test drives login with the D-pad only (Enter on email moves to password, Enter submits). On an Android TV emulator against the Auth emulator: an O1 account reaches the (placeholder) setup screen, and a role-less account sees "No operator access". `lint testDebugUnitTest assembleDebug` is green for all modules. |
| O3 | **Queues, setup, advancing and chime (usable operator).** Needs M2. `OperatorQueueRepository` over M2's `QueueMapper`, with typed errors; the setup screen (F5); the home queue column (F6); advancing with a plain `update({number})` from the last known value, with the wrap, working offline (F7, Q6); `SoundPool` chime at `soundVolume` (F8); Back → setup; the selected queue remembered across restarts (Q9). A rules-test mirroring the native write (a plain `updateDoc` of `number` by an operator, at 0, 99 and the wrapped values) is added to `admin-portal/packages/rules-tests`, if the existing operator cases in `queues.test.ts` don't already cover it. | `QueueNumber` tests (0→99, 99→0, ±1). Repository tests over the contract fixtures (both orgs, legacy docs, `Long`/`Double`). The rules-test passes. On the TV emulator with the Firestore emulator seeded by `pnpm seed` and rules v1: setup shows only the operator's site's queues; right/left change the number and every client sees it; 99→0 and 0→99 work; logcat shows **zero** `PERMISSION_DENIED`. **With the network off**, presses update the local number at once (and chime) with no error; after reconnecting, the last value syncs and other clients see it. A denied query shows "Access denied", not "No queues". After an app restart, the remembered queue opens on home. |
| O4 | **Display zones in `:ui`: queue tile with icon, clock, RTP ticker.** `QueueTile` with the Font Awesome Solid glyph (font bundled, OFL notice added), `ClockZone`, `TickerZone` with an RSS parser and client in `:data` (RTP default URL, 15-minute refresh, last good list kept). Roborazzi added to the catalog; home screen layout at flex 1:3 as in Flutter. | RSS parser tests on a saved RTP feed, plus malformed, empty and HTTP-error cases (the ticker hides itself on error). Roborazzi goldens at 1920×1080 and 1280×720: home with two queues, a long queue name, and the ticker hidden. The icon for code point `61713` renders the same glyph as `font_awesome_flutter`. TV M3's brief can say "use these zones". |
| O5 | **Legacy video zone.** `LegacyVideoZone` in `:ui` (Media3 for direct URLs, looping, `videoVolume` 0/1; YouTube in a WebView IFrame player, muted, looping; playlist-only links rejected as in Flutter; any error → black). It follows the live queue document. | URL classification tests (YouTube watch, `youtu.be`, playlist-only, direct MP4, empty). A manual check on the TV emulator: an MP4 loops, a YouTube link plays muted, a broken URL leaves the area black without affecting the queue column or the ticker. Changing `videoURL` in the emulator switches the video without a restart. |
| O6 | **Release and cutover.** A tag-triggered workflow building a signed `:operator:assembleRelease` (keystore from secrets that the owner adds), `versionCode = 1000 + run_number`; `docs/operator/DEPLOY.md` with the cutover checklist (check the installed signature, uninstall Flutter, install, sign in with an O1 account, pick a queue) and the rollback (reinstall the Flutter APK). A pilot on one box. **Before O6, confirm the box inventory runs Android 8.0+** (`minSdk 26`, Q2 default). Retiring `lib/`, `ios/`, `web/`, `test/` and `build-apk.yml` is a **separate** PR after the owner signs off on the pilot (Q11). | A signed release APK installs over a previous native release in place. The pilot box runs a full working day; the owner signs off. |
| O-touch | **Deferred** (Q1: TV boxes only). Kept here in case phones or tablets come back: on-screen −/+ buttons, a compact/portrait layout, touch-first setup. | Goldens at phone sizes; advancing works by touch alone. |

## Open questions

All twelve were settled on 2026-11-21. **Owner** marks the owner's answer. **Default**
marks a default the orchestrator chose, which the owner can override at any time.

1. **Form factor.** Do phones or tablets matter, or only TV boxes with a remote? Is anyone
   still running the iOS build? **Owner:** TV boxes with a remote only. iOS is out of scope.
   O-touch stays deferred.
2. **Box inventory.** Which boxes and Android versions run the operator app? **Default:** keep
   `minSdk 26`, and confirm the box inventory before O6.
3. **Current installs and signing.** Is a one-time uninstall/reinstall per box acceptable?
   **Owner:** yes, a one-time uninstall of Flutter on each box, plus a new release keystore.
   Keep `applicationId pro.joaosousa.queue_management_system` and `versionCode` 1000+.
4. **Release key and channel.** **Owner:** the owner creates the keystore and adds it to
   GitHub secrets. **Default:** a sideloaded APK from a tagged release.
5. **Operator accounts.** One per site or one per person? **Default:** one operator account
   per site for now.
6. **Advancing semantics.** Online-only transactions, or offline? **Owner:** it must work
   **offline, last writer wins**, as in Flutter. A plain `update({number})` from the last known
   value, with the 99↔0 wrap. O3 is rewritten to match.
7. **Font Awesome icon.** **Default:** render it beside the name.
8. **YouTube.** **Default:** keep it, muted as in Flutter.
9. **Kiosk behaviour.** **Default:** remember the selected queue across restarts. Starting on
   boot is deferred.
10. **Who advances numbers in the long run?** **Default:** unchanged. B now, and C stays
    possible later if TV design Q1 says the display should advance numbers.
11. **Retiring Flutter.** **Default:** retire `lib/`, `ios/`, `web/`, `test/` and
    `build-apk.yml` in a separate PR after the pilot sign-off.
12. **Ticker feed.** **Default:** the RTP feed stays hard-coded for now.

## Decision

**Accepted on 2026-11-21 by the owner.**

- **Option B:** the operator is a second app module, `:operator`, in the `android-tv` build,
  sharing `:domain`, `:data` and new `:ui` zones, with one catalog and no `projectDir` include.
- **Phase 1 = TV-box parity only.** TV boxes with a remote; iOS out of scope; O-touch deferred.
- **Rollout:** a one-time uninstall of Flutter on each box; a new release keystore that the
  owner creates and adds to GitHub secrets; `applicationId pro.joaosousa.queue_management_system`;
  `versionCode` 1000+.
- **Advancing works offline, last writer wins.** A plain `update({number})` computed from the
  last known value with the 99↔0 wrap replaces the proposed transaction. O3's scope and
  acceptance criteria are updated accordingly, and its rules-test mirrors a plain update.
- **Delivery plan:** milestones O1–O6 are adopted as revised above. O1 and O2 can start now;
  O3 needs TV M2.
- **Defaults chosen by the orchestrator, which the owner can override:** Q2 (`minSdk 26`,
  inventory confirmed before O6), Q4 (sideload from tagged releases), Q5 (one account per
  site), Q7 (render the icon), Q8 (keep YouTube, muted), Q9 (remember the queue; boot start
  deferred), Q10 (unchanged), Q11 (retire Flutter in a separate PR after the pilot), Q12 (RTP
  hard-coded).
