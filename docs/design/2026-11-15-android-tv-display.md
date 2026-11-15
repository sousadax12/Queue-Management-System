# Design: native Android TV display app

Refs #1

## Status

**Accepted (2026-11-15)**. See [Decision](#decision).

**Revision 2 (2026-11-15).** New constraints:

- The target hardware is now Google TV and generic Android TV boxes. Fire TV is out of scope.
- The model now has multiple screens per site, with queues shared per site.
- Media comes from a **CDN instead of Firebase Storage**.
- A separate **web admin portal** becomes the main writer of config. This doc now defines the Firestore contract between the portal and the TV app.

**Revision 3 (2026-11-15).** The admin portal design (#2, `docs/design/2026-11-15-admin-portal.md`, accepted) proposed contract changes **C1–C10**, and the owner accepted all of them. This revision writes them into the contract before M2 freezes the mappers:

- **Multi-org (C10).** New `orgs/{orgId}`, and an `orgId` on sites, screens, queues, playlists, media and claimed devices. `siteId: null` now means "shared within the org". Ids are globally unique. **No TV read path changes**, and the TV never writes `orgId`.
- **Pairing (C4).** The portal judges code expiry by the server's `createdAt`, not the TV's `expiresAt`. A `PERMISSION_DENIED` on creating a code means "collision, retry".
- **Write budget (C5).** We stay on **Spark**. The heartbeat default becomes **600 s**, reported as `info.heartbeatSec`. The TV never writes status per played item; `nowPlaying` rides on the heartbeat.
- **Template support (C6).** The app publishes `android-tv/contract/templates.json`, and `devices/{uid}.info` gains `appVersionCode`.
- **Shared fixtures (C8).** Fixtures and the one seed script live in `admin-portal/packages/contract` and replace `android-tv/tools/seed.ts`. M2 now depends on portal milestone **A1**.
- **Portal-only fields (C1, C2, C9)** and the **CDN deletion policy (C3)** are listed where the contract names fields and invariants.
- **Roles** are now `platformAdmin`, `orgAdmin`, `manager` and `operator`, with claims `{role, org, sites}`.
- Open questions 3 (partly), 4 and 10 are answered. Points where C1–C10 rub against what this design relies on are under [Contract conflicts](#contract-conflicts).

## Context

### Goal

We want a native Android TV app to replace the **display** part of the Flutter app. It should play videos and images and offer a few built-in screen templates. Each screen picks its template remotely.

Decisions made before or alongside this doc:

| Topic | Decision |
|---|---|
| Scope | TV display only. The Flutter operator app keeps advancing queue numbers for now. |
| Backend | Firebase stays for **auth, config and queues** (Firestore). **Media is served from a CDN**, not Firebase Storage. Screens keep playing from a local cache when the network drops. |
| Admin | A **web admin portal** (designed separately) writes sites, screens, device assignment, playlists and media items. The TV app only reads config. |
| Fleet | **Several TVs per site**, each possibly with its own template and playlist. Queues are shared within a site. |
| Hardware | **Google TV boxes and generic Android TV boxes.** Fire TV is out of scope unless it works for free. |
| Templates | A fixed set of layouts built into the app. Data-defined layouts are not a goal. |
| Repo | A new native project in a directory beside the Flutter app until cutover. |

### What the Flutter app does today

| Concern | Current behaviour | Where |
|---|---|---|
| Start-up and auth | `Firebase.initializeApp`, then **always** `signInAnonymously()`. The initial route is hard-coded to `/setup`, so the email/password `LoginScreen` at `/` exists but can't be reached. | `lib/main.dart:62-91`, `lib/pages/login_page.dart` |
| Queue selection | `/setup` does a one-off `get()` of **all** `queues` and lets the user pick one with the D-pad. It becomes `QueuesProvider.selectedQueue`. | `lib/pages/setup_page.dart:40-127` |
| Queue tiles | `/home` listens to the **whole** `queues` collection and shows each queue's name and number on its background colour in a left column (flex 1 of 4). | `lib/pages/home_page.dart:86-122`, `lib/widgets/queue_widget.dart` |
| Queue document | `number` (int), `icon` (int, a Font Awesome Solid code point), `name`, `backgroundColor` (int ARGB), `videoURL`, `videoVolume` (int), `soundVolume` (int), `velocity` (int). `foregroundColor` is hard-coded to white. Every field is read with `document['x']`, which **throws if the field is missing**. | `lib/mappers/queue_doc_mapper.dart`, `lib/models/queue_model.dart` |
| Icon | It's mapped into `QueueModel.icon`, but `QueueWidget` **never renders it**. | `lib/widgets/queue_widget.dart` |
| Number changes | D-pad left/right on `/home` decrements or increments the **selected** queue's number, wrapping 0↔99. The app writes the absolute value with `update({'number': n})`, which can race, and plays `pixiedust.mp3` at `soundVolume`. | `lib/pages/home_page.dart:59-79`, `lib/providers/queues_provider.dart` |
| Sound | Plays **only on the device whose key was pressed**, not when a number changes in Firestore. | same |
| Video | One looping video from the selected queue's `videoURL`. The URL is free-form and pasted into Firestore by hand, so it's in effect already a CDN/HTTP URL. It's streamed with no cache. YouTube URLs go to `youtube_player_flutter`. Volume is an int, so in practice muted or full. | `lib/widgets/video_player_widget.dart` |
| RSS ticker | A hard-coded feed (`https://www.rtp.pt/noticias/rss`) as a marquee over the video, with speed from the queue's `velocity`. It's fetched again on every rebuild and not cached offline. | `lib/pages/home_page.dart:135-142`, `lib/widgets/rss_feed_widget.dart` |
| Clock | Top-right, shows `HH:mm` only. A date gets formatted but isn't used. It updates on every frame via a `Ticker`. | `lib/widgets/current_date_widget.dart` |
| Firebase | Project `santos-1b6f6`. `firebase.json` only deploys Firestore rules. | `lib/firebase_options.dart`, `firebase.json` |
| Rules | Any authenticated user, anonymous included, can read **and write** every document. | `firestore.rules` |
| Android host | `minSdk 23`, `compileSdk 35`, AGP 8.1.1, Kotlin 1.9.22. `LEANBACK_LAUNCHER` is declared, touch is optional, cleartext traffic is allowed. | `android/app/build.gradle`, `android/gradle.properties`, `android/app/src/main/AndroidManifest.xml` |
| CI | One workflow runs on push to `main` only, with no PR checks. It runs `flutter build apk --debug` and uploads the APK. | `.github/workflows/build-apk.yml` |

### Things this changes or exposes

1. **The display and the operator are the same screen today.** `/home` renders the display and its D-pad arrows advance the number. After cutover, someone has to run the Flutter app on another device to advance numbers, unless the TV app keeps the shortcut (Open question 1).
2. **Screen config lives on the queue doc today** (video, volumes, ticker speed). The new model moves it to sites and screens and leaves the existing queue fields alone.
3. **The chime moves to "observed change".** The TV chimes when Firestore shows a new number, so every TV showing that queue chimes.
4. **There is no notion of a site.** Flutter treats the whole `queues` collection as one site. Sites need a `siteId` on queues, added in a way that keeps the Flutter mapper working (see [Interface with the admin portal](#interface-with-the-admin-portal)).
5. **Security.** The repository is **public** and includes `google-services.json` / `firebase_options.dart`. Anonymous auth is on, and the rules let any signed-in user write anything. With a portal writing config and TVs reading it, the rules must eventually separate **portal staff** (platform admins, org admins, site managers), **TV devices** and the **Flutter operator**, and, since revision 3, keep each customer org apart. A full rules rewrite is a separate topic, but the model below is shaped so it's possible, and [Auth and rules](#auth-and-rules-what-pairing-needs) says what pairing needs.
6. **Dropping Firebase Storage** removes the Blaze-plan dependency for media. Firestore read volume grows with the number of TVs, though. Every queue change costs one read per TV listening to it, so a multi-site fleet may still outgrow Spark's 50k reads/day. The project **stays on Spark** (Open question 4, answered), so the TV keeps its writes inside the budget in C5 (see [Interface with the admin portal](#interface-with-the-admin-portal)).

## Options

Each decision lists the realistic options. The pick and its reasoning are under [Recommendation](#recommendation).

### D1. UI stack and minSdk

| | A. Kotlin + Jetpack Compose with Compose for TV (`androidx.tv:tv-material`) | B. Kotlin + Leanback (Views/Fragments) | C. Keep Flutter, rewrite the display page |
|---|---|---|---|
| Fit for signage | Good. Free-form layouts, proportional zones, animation, `Modifier.basicMarquee`. | Poor. Built for browse/details catalogue UIs. | OK. It's what we have. |
| Status | `tv-material` 1.1.0 stable (May 2026). Google's recommended TV toolkit. | **Deprecated** in favour of Compose for TV. | Supported, but caching and decoder control go through plugins. |
| Media | Media3 has first-party Compose UI (`PlayerSurface`; the `Player` composable since 1.10). | Media3 `PlayerView`. | Plugins wrap ExoPlayer with less control. |
| Cheap boxes | A bit heavier than Views at start-up. A static signage layout is light, and Baseline Profiles help. | Lightest. | Engine overhead plus platform-view cost for video. |

**Target hardware and minSdk.**

- **Google TV boxes**: Chromecast with Google TV (4K/HD), Google TV Streamer, and operator boxes. They all ship **Android TV OS 10+** (Android 12–14 on current models).
- **Generic Android TV boxes**, which come in two kinds:
  - Certified Android TV OS boxes (Mi Box S, Nvidia Shield, operator boxes): Android 9–12.
  - Uncertified AOSP boxes (Amlogic S905X/X2/X3/X4/W2, Rockchip RK3318/3328/3566, Allwinner H313/H616): usually Android 9–11, often with a fake "Android 12/13" label, 1–4 GB RAM and 8–32 GB eMMC.
- **minSdk options:**
  - `23`: matches Flutter and the AndroidX floor. It reaches Android 7.x boxes nobody should still buy.
  - `26`: covers every Android TV OS 8+ box.
  - `28`: Android 9. It drops 8.x boxes like the original Mi Box S firmware.
- **targetSdk:** Play requires API 34+ for new **TV** apps (TV is exempt from the phone API-36 rule), so `targetSdk 35`.
- **Fire TV:** Fire OS 7/8 is based on Android 9/11, so Compose, Media3 and the Firestore/Auth SDKs would probably run. But Fire OS doesn't allow replacing the launcher, restricts boot launch, and ships without Play services. It isn't in the test matrix, and kiosk behaviour (D8) would differ.

### D2. Video playback

| A. Media3 ExoPlayer, one player reused across items | B. Media3 ExoPlayer with the whole mixed playlist in one timeline (images via `MediaItem.Builder().setImageDurationMs`) | C. Two alternating ExoPlayer instances |
|---|---|---|
| Our own scheduler drives items. Videos go to ExoPlayer, and we advance on end, cap or error. A ~100–300 ms gap between videos is hidden behind a crossfade. | Gapless and preloaded. Images go through the player's image output: less layout control, newer, less tested on cheap SoCs. | Gapless video→video. But many cheap SoCs allow **one** hardware decoder instance, so the second player fails or falls back to software decoding. |

Media3 is the only serious player. 1.10 (Mar 2026) is current. The real choice is about orchestration.

### D3. Image loading

| A. Coil 3 (`coil-compose`) | B. Glide | C. Raw `BitmapFactory` |
|---|---|---|
| Kotlin-first and Compose-native. Downsamples to the zone size, which matters on 1–2 GB RAM boxes. Pluggable `Fetcher`. | Mature, but its Compose integration is secondary. | No dependency, but we'd write our own downsampling and crossfade. |

### D4. CDN media: on-device caching and prefetch

Media arrives as CDN URLs listed in Firestore. The device must prefetch everything a screen needs, play it with no network, and keep within a disk budget.

| | A. **Media3 `SimpleCache` as the single store**: prefetch with Media3 `Downloader`s in a WorkManager worker, play through `CacheDataSource`, images via a Coil `Fetcher` reading the same cache | B. Our own file store: OkHttp downloads to `filesDir/media/<sha256>` with `Range` resume, players read `file://` | C. HTTP-level caching only (OkHttp cache / `CacheDataSource` while playing), driven by CDN `Cache-Control` |
|---|---|---|---|
| Offline | Yes, once prefetched. | Yes, once prefetched. | **No.** The first play needs the network, and caches can be evicted under us. |
| Resume / partial | Built in: span-based, resumes from cached byte ranges. | We write `Range` resume ourselves. | n/a |
| HLS/DASH later | `HlsDownloader` / `DashDownloader` drop in. | Would need a rewrite. | Partial. |
| Images | Same store via a ~40-line Coil `Fetcher`. | Same store, plain files. | Coil's own disk cache, separate and not pinnable. |
| Eviction | `NoOpCacheEvictor` + our planner (`cache.removeResource(key)`), so we control pinning. | Our planner deletes files. | The HTTP cache decides, so items we need can be evicted. |
| Cost | One Media3 dependency we already have. Cache internals are less transparent to inspect. | Simplest to reason about and inspect. More code for resume and progress. | Least code, but fails the offline requirement. |

**Prefetch scheduling, for A and B:** WorkManager `CoroutineWorker`, or Media3 `DownloadManager` + `DownloadService`.

- `DownloadManager` gives a persistent download index and retry, but needs a **foreground service**. On TV the notification is invisible, `dataSync` foreground services need a declared type on targetSdk 34+, and they're time-limited (6 h/day) on Android 15.
- A worker calling `Downloader.download()` directly avoids all of that.

**URL and cache-key strategy** (decides how CDN headers matter):

| Immutable, versioned URLs (content hash in the path, `Cache-Control: public, max-age=31536000, immutable`) | Stable URL + cache-busting query (`?v=3`) | Mutable URL (same URL, new bytes; relies on `ETag` / `Last-Modified` revalidation) |
|---|---|---|
| New content = new URL = new item. The device never revalidates. CDN edges cache forever. | Works if the CDN includes the query string in its cache key (not every CDN does by default), and the device must key on the version, not the raw URL. | The device must poll with conditional requests and can't tell from Firestore that content changed. Edges may serve stale bytes. |

**Signed vs public URLs.** Signed URLs (CloudFront/Cloud CDN/Bunny token auth) expire, so the URL can't be the cache key. They also need something to mint fresh URLs: the TV can't hold the signing key, so it would be a Cloud Function or the portal backend. Public, unguessable content-hash URLs need nothing extra.

**Progressive MP4 vs HLS/DASH.** Adaptive streaming solves bandwidth variation while **streaming**. Here every file is fully downloaded before it plays, so adaptive streaming adds segment overhead, more objects to prefetch and a more complex cache for no benefit. Progressive MP4 (one rendition per device class) is the natural fit.

### D5. Firestore data model: sites, screens, devices, playlists

| | A. `sites/{siteId}` with a `screens` subcollection. Top-level `devices`, `playlists`, `media` and `pairingCodes` carry `siteId`. `queues` stays top-level and gains `siteId`. | B. Everything nested under `sites/{siteId}/…`, queues included | C. Flat top-level collections, each with a `siteId` field |
|---|---|---|---|
| Flutter compatibility | **Kept.** Flutter keeps reading `queues`, and `siteId` is an extra field it ignores. | **Breaks** the Flutter operator (it reads `collection('queues')`). It needs a matching Flutter change plus site selection. | Kept. |
| Rules per site | Easy for `sites/{id}/**`. Top-level collections check `resource.data.siteId`. | Easiest: one `match /sites/{siteId}/{doc=**}`. | Every rule checks `siteId`. |
| Devices before pairing | A device exists before it belongs to any site, so a top-level `devices` fits. | Would need a holding area anyway. | Fits. |
| Shared content | Org-wide playlists/media (`siteId: null`) reused across sites. | Copy per site, or a parallel top-level area. | Same as A. |
| Portal queries | Per-site lists via subcollection or `where siteId ==`. Cross-site lists ("all offline devices") via top-level `devices`. | Cross-site lists need collection-group queries. | Simple. |

Within a playlist, items can **embed** media metadata (one atomic doc, one listener) or **reference** `media/{id}` (one more listener per item).

### D6. Template architecture

| A. Sealed set of Kotlin templates; each places typed **zones** | B. Generic grid layout driven by data | C. One composable per template with the zone UI inlined |
|---|---|---|
| A template is a small composable that places slots. Zones (queues, media, ticker, clock) are reusable and own their state. A new template means one file, a registry entry and a screenshot test. | The data-defined layout we decided **not** to build. | Quickest for the first one, then copy-paste for every new template. |

### D7. Provisioning and pairing many boxes

The portal is the admin surface, so pairing is "the TV shows a code, the admin claims it in the portal". The options differ in how they scale to many boxes per site.

| A. **Per-box pairing code** claimed in the portal | B. **Site enrolment token** entered on, or pushed to, the box | C. **MDM managed configuration** (`RestrictionsManager`) carrying a site enrolment token, or `adb shell am start … --es enrolToken …` |
|---|---|---|
| The TV shows a 6-character code. The admin types it into the portal, picks site and screen, and the TV switches over within seconds. About 15 s per box, no typing on a remote. Works for 1–20 boxes. | The box joins a site automatically and shows the site's default screen. The admin assigns screens in bulk in the portal later. Typing a token on a remote is painful. | Zero-touch: flash or enrol 50 boxes with the same config and they all appear in the portal under the site. Needs an MDM or an `adb` script per box. |

All three end in the same state (`devices/{uid}.siteId` and `.screenId` set by the portal), so they can coexist.

### D8. Kiosk behaviour (launch on boot, stay on, D-pad)

| A. `BOOT_COMPLETED` receiver + "display over other apps" | B. App is the **HOME** launcher | C. Device owner + lock-task mode (or an MDM kiosk policy) |
|---|---|---|
| Android 10+ blocks background activity starts unless the app holds `SYSTEM_ALERT_WINDOW`. Grant it once (`adb shell appops set <pkg> SYSTEM_ALERT_WINDOW allow`), because Google TV hides the toggle. Works on any box. | A `HOME` intent filter means the box boots straight into the app. Works on generic/AOSP boxes. Google TV doesn't let you replace its launcher. | The strongest option: survives crashes, blocks exits, allows silent updates. Needs a factory-reset box and `dpm set-device-owner`, or an MDM. |

Common to all three: `FLAG_KEEP_SCREEN_ON`, `Back` consumed in kiosk, and a deploy checklist (Google TV "Energy saver → Never", screensaver off, HDMI-CEC standby policy).

### D9. Project structure, DI and architecture

| Option | Shape |
|---|---|
| **Modules** A. `:app`, `:domain` (pure Kotlin/JVM), `:data`, `:ui` | B. One `:app` module | C. Fine-grained feature modules |
| **DI** A. Hilt (+ `hilt-work`) | B. Koin | C. Manual `AppContainer` |
| **Pattern** A. Single activity, unidirectional data flow: repositories expose `Flow`s, `ScreenViewModel` combines them into `StateFlow<ScreenState>`, zones are stateless composables | B. MVI with reducers/intents | |

### D10. CI

| A. A second workflow, `android-tv.yml`, path-filtered; the Flutter workflow ignores `android-tv/**` | B. One workflow, two jobs, `dorny/paths-filter` | C. One job that always builds both |
|---|---|---|
| Independent, readable, each has its own caching. Runs on PRs too. | One file, more conditional logic. | Simple but slow, and the two projects can block each other. |

## Recommendation

### Summary of picks

| Decision | Pick | What would change our mind |
|---|---|---|
| D1 UI stack | **Kotlin + Compose with Compose for TV** (`tv-material` 1.1.x, Compose BOM, Kotlin 2.x with the Compose compiler plugin). **`minSdk 26`, `targetSdk 35`, test floor Android 9.** Directory `android-tv/`, app ID `pro.joaosousa.queuedisplay.tv`, so both apps can run side by side during the pilot. | The fleet inventory shows only Android 9+ → raise to `minSdk 28` (it trims the test matrix). Compose jank on the cheapest box that Baseline Profiles can't fix → render the media zone in a `SurfaceView` island. |
| D2 Video | **Media3 ExoPlayer, one reused instance, driven by our own playlist scheduler (A).** | Gapless video→video becomes a requirement → try B on the target boxes. Never C on generic boxes. |
| D3 Images | **Coil 3**, with a custom `Fetcher` reading from the media cache, sized to the zone. | — |
| D4 CDN caching | **A: Media3 `SimpleCache` as the one media store** (`NoOpCacheEvictor`, under `filesDir`), **prefetched by a WorkManager worker running Media3 `Downloader`s**, played through `CacheDataSource`. **Immutable content-hash URLs**, cache key = `sha256`, **public URLs**, **progressive MP4**. | The CDN must use signed, expiring URLs → add a `MediaUrlResolver` backed by a callable function; the cache key is already independent of the URL. A real need for live/adaptive streams → `HlsDownloader` on the same cache. Cache internals turn out painful to debug on devices → B (plain files), with the same planner and key scheme. |
| D5 Data model | **A**: `sites/{siteId}` + `sites/{siteId}/screens/{screenId}`; top-level `devices`, `pairingCodes`, `playlists`, `media` with `siteId` (`null` = shared within the org); `queues` top-level + `siteId`. Revision 3 adds `orgs/{orgId}` and an `orgId` on config docs (C10) without moving any path. Playlist items **embed** media metadata (safe because media docs are immutable). | The Flutter operator gets rewritten for sites anyway → consider B for simpler rules. |
| D6 Templates | **A: sealed template registry + reusable zones.** | Customer-defined layouts become a goal → revisit B (explicitly out of scope). |
| D7 Provisioning | **A (per-box pairing code claimed in the portal) for the MVP**, plus a **"site default screen"** so a freshly assigned box shows something useful. **C (managed config/`adb` enrolment token) as the bulk path** in a later milestone. B only through C, never typed on a remote. | Sites with dozens of boxes at once → bring C forward. |
| D8 Kiosk | **A on every box**; B added on generic boxes (the manifest declares both); **C recommended for unattended public screens** when an MDM or a factory-reset setup is possible. | MDM chosen → its kiosk policy replaces the overlay grant. |
| D9 Structure | **`:app`/`:domain`/`:data`/`:ui`, Hilt, single-activity UDF.** | The team prefers no annotation processing → manual DI. |
| D10 CI | **A: separate `android-tv.yml` + path filters; PR checks for both.** | — |

Why Compose for TV over Leanback: Leanback is deprecated and built for media catalogues, while signage is free-form layout plus media. Why native: the hard parts (offline media cache, decoder control, kiosk/boot) are native APIs that Flutter reaches only through plugins.

### Video and image format guidance (for Google TV and generic boxes)

Every target box hardware-decodes **H.264 up to 1080p60** and **HEVC Main up to 4K30/60**. VP9 is common on newer chips. AV1 is only on recent ones (Google TV Streamer, Amlogic S905X4+, RK3588). The cheap boxes have 1–2 GB RAM and often render their UI at 1080p even when they advertise 4K.

| Asset | Required baseline (every screen) | Optional (only on screens marked `maxVideoHeight: 2160` **and** if the box reports a decoder for it) | Not supported in the MVP |
|---|---|---|---|
| Video | **MP4, H.264 High@4.1, 1920×1080 or less, 25 or 30 fps (50/60 OK), constant frame rate, ≤ 10 Mbps, 2 s closed GOP, `faststart` (moov first), 8-bit SDR** | HEVC Main (8-bit) 3840×2160 ≤ 30 fps, ≤ 25 Mbps, as a second **rendition** of the same media item | HDR / 10-bit, VP9, AV1, interlaced, HLS/DASH, YouTube |
| Audio | **AAC-LC, 48 kHz stereo, 128–192 kbps** (or no audio track) | — | Dolby/DTS passthrough |
| Image | **JPEG or WebP (PNG for graphics with text), sRGB, ≤ 3840×2160**, ideally exactly 1920×1080 for full-screen zones | — | Animated GIF/WebP, SVG |

- The app picks the best rendition the box can decode, using `MediaCodecList` / Media3 `MediaCodecUtil`, up to the screen's `maxVideoHeight`. The baseline rendition is always required, so every box can play every item.
- For disk planning: 1080p at 8 Mbps is about **60 MB per minute**. A 15-minute loop is about 0.9 GB, well within the 3–5 GB free on a typical 8 GB box.

### What the TV app assumes about the CDN

1. **HTTPS**, publicly readable objects at **unguessable, content-addressed paths** (e.g. `https://cdn.example.com/m/<sha256>.mp4`). No auth headers or cookies. Signed URLs are possible later (see D4). The portal design picked **Cloudflare R2** behind `cdn.<domain>`, one public bucket for all orgs (identical files dedupe across orgs).
2. **Immutable objects.** An object's bytes never change. New content gets a new URL, so the CDN can serve `Cache-Control: public, max-age=31536000, immutable`. The device doesn't depend on these headers, but CDN edges and egress costs do. **Objects are never deleted while a playlist or media doc references their `sha256`, nor for 30 days after the last reference goes** (C3), so a box that is offline for days or still caching an older version can always finish its downloads.
3. Supports **`Range` requests** (`206 Partial Content`, `Accept-Ranges: bytes`) and returns **`Content-Length`**. These are needed for resumable prefetch and Media3 progressive loading.
4. Correct **`Content-Type`**. `ETag` is welcome, but nothing relies on it.
5. Rate-limit and egress expectations: each box downloads each file **once**. When a new playlist is published, boxes start downloads with **0–10 minutes of random jitter**, so a site's boxes don't all hit the uplink at the same instant.
6. **No HTTP-cache semantics are needed for media.** Media skips the OkHttp HTTP cache entirely. Only the RSS ticker uses the HTTP cache (`ETag` / `If-Modified-Since`).

### Interface with the admin portal

The portal is the **writer** of everything below except `devices/*.status`/`info`/`lastSeenAt` (written by the TV), `pairingCodes` (created by the TV) and `queues/*.number` (written by the Flutter operator, later by the portal's operator console). The TV app is a **reader**. This section is the contract between the two. The portal design's [Contract feedback](2026-11-15-admin-portal.md#contract-feedback) (C1–C10, accepted) is folded in below.

**Source of truth for shapes.** From portal milestone A1, the zod schemas and golden fixtures in `admin-portal/packages/contract` (`fixtures/`) are the machine-readable form of this section (C8). The JSON below shows the shape; if the two disagree, fix whichever is wrong in the same PR, and never let the TV mappers drift from the fixtures.

#### Collections and fields

**Common to config docs (C9, C10).**

- **`orgId`** (string) is on `sites`, `screens`, `queues`, `playlists`, `media` and claimed `devices`. It's denormalised so rules never need a `get()` to find a doc's org. **The TV reads no `orgId` field** and never writes one.
- **`updatedAt`** (server timestamp) and **`updatedBy`** (uid) are on every portal write to `sites`, `screens`, `queues`, `playlists` and `devices`. The TV ignores both.
- **Ids are globally unique** across orgs (a random id, optionally with a readable prefix), never a bare slug like `lobby`. The examples below use readable ids for clarity only.

`orgs/{orgId}`, **portal-only** (C10): `name`, `status`, `createdAt` and optional limits (`maxSites`, `maxDevices`, `maxMediaBytes`). The TV never reads it.

`sites/{siteId}`, written by the portal:

```json
{
  "orgId": "org_7Hq2",
  "name": "Loja Porto Centro",
  "timeZone": "Europe/Lisbon",
  "locale": "pt-PT",
  "defaultScreenId": "lobby-main",
  "ticker": { "feedUrls": ["https://www.rtp.pt/noticias/rss"], "speedDpPerSec": 60, "refreshMinutes": 15 },
  "chime": { "enabled": true, "volume": 1.0 },
  "maintenanceHour": 4,
  "updatedAt": "<serverTimestamp>", "updatedBy": "<portal uid>"
}
```

`sites/{siteId}/screens/{screenId}`, written by the portal. A **screen** is a logical slot ("Lobby left"). Several boxes may show the same screen, and each box shows exactly one.

```json
{
  "orgId": "org_7Hq2",
  "name": "Lobby left",
  "template": "queues_media",
  "zones": {
    "queues": { "queueIds": ["balcao-a", "balcao-b"], "chime": true },
    "media":  { "playlistId": "pl_promo_nov", "volume": 0.3 },
    "ticker": { "enabled": true },
    "clock":  { "pattern": "HH:mm", "showDate": false }
  },
  "maxVideoHeight": 1080,
  "safeAreaPercent": 0,
  "updatedAt": "<serverTimestamp>", "updatedBy": "<portal uid>"
}
```

`playlists/{playlistId}`, written by the portal. `siteId: null` means the playlist is shared by every site **of the same org** (C10), never across customers. `mediaIds` mirrors `items[].mediaId` so the portal can query with `array-contains`, and `publishedBy` is optional (C2). The TV ignores both.

```json
{
  "orgId": "org_7Hq2",
  "name": "Promo Novembro",
  "siteId": null,
  "mediaIds": ["md_8f2c", "md_19ab"],
  "items": [
    { "mediaId": "md_8f2c", "type": "video", "durationSec": null,
      "renditions": [
        { "url": "https://cdn.example.com/m/8f2c…e1.mp4", "sha256": "8f2c…e1", "sizeBytes": 48211022,
          "mimeType": "video/mp4", "codecs": "avc1.640029,mp4a.40.2", "width": 1920, "height": 1080 },
        { "url": "https://cdn.example.com/m/b7d0…44.mp4", "sha256": "b7d0…44", "sizeBytes": 131002871,
          "mimeType": "video/mp4", "codecs": "hvc1.1.6.L150.B0,mp4a.40.2", "width": 3840, "height": 2160 }
      ],
      "durationMs": 30040 },
    { "mediaId": "md_19ab", "type": "image", "durationSec": 10,
      "renditions": [
        { "url": "https://cdn.example.com/m/19ab…77.jpg", "sha256": "19ab…77", "sizeBytes": 812004,
          "mimeType": "image/jpeg", "width": 1920, "height": 1080 }
      ] }
  ],
  "publishedBy": "<portal uid>",
  "updatedAt": "<serverTimestamp>", "updatedBy": "<portal uid>"
}
```

`media/{mediaId}`, written **only by the portal's media service** (Admin SDK). This is the library the portal manages, and the TV app never reads it. It holds the same `type`, `renditions[]` and `durationMs` as above, plus `orgId` and portal-only fields: `name`, `siteId` (`null` = org-wide), `uploadedBy`, `createdAt`, `tags`, and the pipeline state from C1 (`status`: `uploading`/`processing`/`ready`/`failed`/`deleted`, `error`, `source`, `posterUrl`, `processingAt`). Renditions, `sha256` and `codecs` are computed server side from the stored bytes, and playlist items copy them from a `ready` media doc.

`queues/{queueId}`: **existing**. The number is written by the Flutter operator, and the queue is created and edited by the portal. Existing queues get `orgId` and `siteId` from the portal's one-off backfill script (A1), with a dry-run mode.

```json
{ "orgId": "org_7Hq2", "siteId": "porto-centro", "name": "Balcão A", "number": 42, "icon": 61713,
  "backgroundColor": 4282339765, "foregroundColor": 4294967295,
  "videoURL": "", "videoVolume": 0, "soundVolume": 1, "velocity": 60,
  "updatedAt": "<serverTimestamp>", "updatedBy": "<portal uid>" }
```

`devices/{uid}`: the TV creates it and writes `status`/`lastSeenAt`/`info`. **Only the portal** writes `orgId`, `siteId`, `screenId`, `name`, `identifyUntil`, `updatedAt` and `updatedBy`. The rules reject a TV create that includes any of `orgId`, `siteId`, `screenId`, `name` or `identifyUntil`. Claiming sets `orgId` + `siteId` + `screenId` together, and unassigning clears all three, which sends the box back to the pairing screen.

```json
{
  "orgId": "org_7Hq2", "siteId": "porto-centro", "screenId": "lobby-left", "name": "Box 3 (left of door)",
  "info":   { "model": "Chromecast HD", "sdkInt": 34, "appVersion": "0.6.0 (21)", "appVersionCode": 21,
              "heartbeatSec": 600, "decoders": ["avc", "hevc"], "maxDisplay": "1920x1080" },
  "status": { "nowPlaying": "md_8f2c", "cacheBytes": 812004112, "freeBytes": 3210000000, "lastSyncAt": "…", "pendingItems": 0, "lastError": null },
  "lastSeenAt": "<serverTimestamp>",
  "identifyUntil": null,
  "updatedAt": "<serverTimestamp>", "updatedBy": "<portal uid>"
}
```

- **`info.appVersionCode`** (int, C6) is the app's `versionCode`. The portal uses it, not the `appVersion` string, to filter templates.
- **`info.heartbeatSec`** (int, C5) is the heartbeat interval this box uses, default **600**. The portal shows a box as offline after `2.5 × heartbeatSec` without a new `lastSeenAt`.
- **Write budget (C5).** The TV writes `status`, `info` and `lastSeenAt` in **one update per heartbeat**, plus one on sync-complete and one on a new error (at most one error write per heartbeat interval). It **never writes per played item**: `status.nowPlaying` and `status.pendingItems` are whatever they were at the last of those writes. That's about 150 writes per box per day.

`pairingCodes/{code}`: the TV creates it; the portal reads it and then deletes it on claim.

```json
{ "uid": "<device auth uid>", "createdAt": "<serverTimestamp>", "expiresAt": "<server-time estimate + 1 h>" }
```

- **Expiry is judged by the server's `createdAt`** (C4). The rules require `createdAt == request.time`, so the TV must write `serverTimestamp()`, and the portal treats a code as expired at `createdAt + 15 min`. A box clock that's hours off can no longer make a code look valid or expired.
- **`expiresAt` is only for clean-up**, by a Firestore TTL policy. The TV writes it as its best estimate of server time plus 1 hour, so TTL never removes a code the portal would still accept (see [Auth and rules](#auth-and-rules-what-pairing-needs)).
- The doc holds exactly `uid`, `createdAt` and `expiresAt`. TVs can create and delete their own codes but can't `get` or list any code.

#### Invariants the TV app relies on

1. **Rendition URLs are immutable.** The bytes behind a `url` never change, and `sha256` is the hex SHA-256 of exactly those bytes, computed by the media service from the stored object. Replacing content means a new URL, a new `sha256` and an updated playlist. The TV keys its cache on `sha256` and verifies it after download. **A referenced object is never deleted from the CDN**, and an unreferenced one only after 30 days (C3).
2. **Media metadata in playlist items is a snapshot.** Since renditions are immutable, the portal never needs to fan out changes from `media/{id}` into playlists, except when it **adds** a rendition (it finds the playlists through `mediaIds`, C2). Each item needs at least one rendition with `height ≤ 1080` in the H.264 baseline format.
3. **Required item fields:** `type` (`video` | `image`), `renditions[]` (non-empty; each with `url`, `sha256`, `sizeBytes`, `mimeType`, `width`, `height`, and `codecs` for video), `durationSec` (required for images; for videos, `null` = play to the end), `durationMs` for videos (used for cache planning and progress). Items with unknown `type` are skipped.
4. **Playlists are written atomically** (one doc write). The TV switches to a new version only after every item is cached, and keeps playing the old version until then.
5. **`template` values come from the app's enum** (`queues_media`, `fullscreen_media`, `media_ticker_clock`, `queues_only`). Unknown values fall back to `queues_media` and report an error. The portal only offers templates that the box supports, using **`android-tv/contract/templates.json`** (C6), which the app publishes in the repo:

   ```json
   [{ "id": "queues_media", "minVersionCode": 1, "zones": ["queues", "media", "ticker", "clock"] }, …]
   ```

   A template is supported when `devices/{uid}.info.appVersionCode >= minVersionCode`, and `zones` tells the portal which zone forms to show. A `:ui` unit test fails if the file and the `TemplateId` registry disagree.
6. **Queues created by the portal must include every legacy field** (`number`, `icon`, `name`, `backgroundColor`, `videoURL`, `videoVolume`, `soundVolume`, `velocity`), with neutral defaults. Otherwise the **Flutter mapper throws** (`document['x']`). Every queue needs a `siteId` (and an `orgId`), so existing queues must be **backfilled** before TVs use site-scoped queries. The portal's backfill script (A1) sets both.
7. **`screens/*.zones.queues.queueIds`** reference queues of the **same site**. If it's omitted, the screen shows all queues of the site, ordered by `name`.
8. **`devices/{uid}.screenId`** must exist under `sites/{siteId}/screens`. If it's `null` and `siteId` is set, the box shows `sites/{siteId}.defaultScreenId`. If `siteId` is `null` (never claimed, or unassigned), the box shows the pairing screen.
9. Field names and meanings are **additive-only**. Renaming or removing a field the TV reads needs an app release first.
10. **Orgs are invisible to the TV** (C10). Everything a box reads is reached from its own `devices/{uid}` by id or by `siteId`, and the rules keep it inside its org. A screen's `playlistId` and `queueIds` belong to the box's org and site (`siteId: null` playlists to its org), which the portal enforces.
11. **Numbers can arrive as `Long` or `Double`.** The portal writes from JavaScript, where `1.0` and `1` are the same number, so Firestore may store `volume: 1.0` as an integer. TV mappers read every numeric field through `Number` (`(value as? Number)?.toDouble()`, or the snapshot's `getDouble`/`getLong`, which convert), never with a cast like `as Double` that fails on the other kind. The same goes for maps parsed from the shared JSON fixtures.

### Auth and rules: what pairing needs

- **TV identity:** Firebase **anonymous auth**. The uid is the device id and lives until app data is cleared or the app is reinstalled. In that case the box shows a new code and gets claimed again, and the portal should offer "replace device" to move the assignment. **Do not enable Identity Platform's automatic clean-up of anonymous accounts**, because it would delete TV identities.
- **Portal identity:** real accounts (Google or email/password with a verified email) with **custom claims** `{role, org, sites}`, set by the portal's media service, which mirrors them to `users/{uid}`. The roles are:
  - `platformAdmin`: our team, every org;
  - `orgAdmin`: one customer org;
  - `manager`: some sites of one org (UI later);
  - `operator`: advances numbers in some sites of one org.

  A user whose sign-in provider is `anonymous` never gets a staff role, even if a claim were present. A signed-in user with no role can read and write nothing.
- **Flutter operator:** it signs in anonymously today. Locking down writes to `queues.number` means the operator signs in with the existing (unused) `LoginScreen` and gets an `operator` claim. That's a matching Flutter change, tracked with rules hardening (#6). After the TV pilot the portal's operator console takes over advancing (portal A11).
- **Pairing flow and the rules it needs:**
  1. The TV signs in anonymously and creates `devices/{uid}` with only `info`, `status` and `lastSeenAt` (no `orgId`, `siteId`, `screenId`, `name` or `identifyUntil`, which the create rule rejects). When the `lastSeenAt` server timestamp of that create (or of the start-up heartbeat, if the doc already exists) comes back on its `devices/{uid}` listener, the TV records the offset between the server clock and its own.
  2. It generates a random 6-character code from an unambiguous alphabet (no 0/O/1/I) and creates `pairingCodes/{code}` = `{uid, createdAt: serverTimestamp(), expiresAt}` with a plain `set()`. It can't use a transaction, because TVs can't read codes. Rule: create only if the caller is anonymous, `uid` is the caller, `createdAt == request.time` and the keys are exactly those three. A `set()` on an existing code counts as an update, which is denied, so **`PERMISSION_DENIED` on create means "collision": the TV retries with a new code** (C4). The rules don't need `!exists(...)`.
  3. The admin enters the code in the portal. The portal `get`s `pairingCodes/{code}` (any staff role: get allowed, list denied), treats it as expired if `createdAt` is more than 15 minutes old, then **in one transaction** sets `devices/{uid}.orgId`/`siteId`/`screenId`/`name` and deletes the code. Claiming puts the box into the claimer's org.
  4. The TV is listening to `devices/{uid}`, so it switches to the screen within seconds, and deletes its code if it's still there. A `PERMISSION_DENIED` on that delete means the portal already deleted it, so it counts as done.
  5. While unclaimed, the TV shows a new code **before the old one expires**: it rotates every 14 minutes, measured on the monotonic clock (`elapsedRealtime`) from the confirmed create. It leaves rotated-out codes for TTL to remove, which saves a write per rotation.
- **Rules shape.** The org-aware rules are drafted in the portal design's [Rules shape](2026-11-15-admin-portal.md#rules-shape-input-to-6), and #6 owns the final file and its tests. The device paths it gives the TV:
  - TVs may `get` their own `devices/{uid}` and update only `status`, `info` and `lastSeenAt`.
  - TVs may read `sites/{s}` and `sites/{s}/screens/*` where `s == get(devices/uid).siteId`.
  - TVs may read `queues` where `resource.data.siteId == s`, and `playlists/*` where `siteId == s`, or where `siteId == null` and the playlist's `orgId` is the box's `orgId`.
  - TVs never read `media`, `orgs` or `users`.
  - TV scoping stays on rules `get()` of the device doc (portal P5-A), so the TV needs no token refresh. Moving it to a `siteId` claim (C7) is a contingency only, not part of this contract.
  - TV queries must therefore always filter `queues` by `siteId`, which this design does.

### Contract conflicts

None of C1–C10 breaks an assumption this design relies on, and every TV read path is unchanged. These points rub, though, and each needs a decision rather than a silent fix:

1. **Pairing rotation vs the write budget (C4 × C5).** C5 counts heartbeats but not pairing codes. A box left unclaimed writes one code every 14 minutes, about 100 writes a day, which is less than a claimed box's heartbeat but not zero. A box that has failed to pair for days keeps spending it. **Proposed (TV side, no portal change):** after 2 hours unclaimed, stop rotating and show "Press OK for a new pairing code". *Owner to confirm.*
2. **`PERMISSION_DENIED` is ambiguous (C4).** It also comes back when the rules aren't deployed yet, the token is missing or the doc shape is wrong, and not only on a collision. **Proposed:** the TV retries with a new code up to 5 times, then backs off for a minute and reports `status.lastError: "pairing_create_denied"`. M7's rules tests cover the real collision case.
3. **`expiresAt` and a wrong box clock (C4).** C4 keeps `expiresAt` "as written by the TV" for TTL. With a box clock that's behind, a TTL policy could remove a code the portal would still accept. **Resolved in this doc:** the TV writes `expiresAt` from its server-clock offset plus 1 hour (see [Auth and rules](#auth-and-rules-what-pairing-needs)). This needs no portal change, but the TTL policy must be on `expiresAt` only.
4. **Fixture typing (C8).** JSON can't tell an integer from a double, a Firestore `Timestamp` from a string, or a `serverTimestamp()` placeholder from a value. **Ask for A1:** the fixtures need one documented encoding for timestamps and sentinels, and the TV's fixture loader mirrors it. Invariant 11 covers the number side.
5. **Portal reads `android-tv/contract/templates.json` (C6), and the TV tests read `admin-portal/packages/contract/fixtures/` (C8).** Each side's CI must also run when the other side's file changes, or drift won't fail CI as C8 intends. `android-tv.yml` adds the fixtures path (see [CI](#ci)). **Ask for A1/A2:** `admin-portal.yml` adds `android-tv/contract/**`.
6. **No claim step before portal A5 (C8).** C8 replaces the TV's own seed script, which was going to carry a temporary "claim" step for M7. A1's seed script as described doesn't include one. **Ask for A1:** add a `claim <code> <siteId> <screenId>` command to the shared seed script that runs the same transaction as the portal. Otherwise M7 waits for A5.

### Data model rationale (D5)

- **Orgs above sites, by field, not by path (C10).** `orgs/{orgId}` is a portal-only doc, and every config doc carries `orgId`. Nesting sites under orgs would have moved every TV read path and the Flutter operator's `queues` collection. A field lets rules check the org without a `get()`.
- **Sites as a top-level doc with a `screens` subcollection.** Screens never exist outside a site, and per-site rules are simple.
- **`devices` stays top-level.** A box exists before it's claimed, and the portal needs cross-site views like "offline boxes" and "boxes on old versions".
- **`queues` stays top-level** with a new `siteId`, so the Flutter operator keeps working unchanged. Its setup page will list queues from every site until it gets a site filter, which is a small optional Flutter change.
- **`playlists` and `media` stay top-level** with a nullable `siteId`, so an org-wide campaign is one playlist referenced by screens in many sites of the same org.
- **Listener budget per TV:** `devices/{uid}`, `sites/{s}`, its screen, its playlist, and `queues where siteId == s`. That's 5 listeners, and only queue changes are frequent.

### Template architecture

```kotlin
// :domain
enum class TemplateId(val wire: String) { QUEUES_MEDIA("queues_media"), FULLSCREEN_MEDIA("fullscreen_media"),
    MEDIA_TICKER_CLOCK("media_ticker_clock"), QUEUES_ONLY("queues_only") }

data class ScreenConfig(val template: TemplateId, val queues: QueueZoneConfig?, val media: MediaZoneConfig?,
                        val ticker: TickerZoneConfig?, val clock: ClockZoneConfig?, val safeAreaPercent: Int)

// :ui
interface ZoneSlots {                         // each slot is a ready-made, self-updating zone
    @Composable fun Queues(modifier: Modifier)
    @Composable fun Media(modifier: Modifier)
    @Composable fun Ticker(modifier: Modifier)
    @Composable fun Clock(modifier: Modifier)
}

fun interface ScreenTemplate { @Composable fun Render(slots: ZoneSlots) }

val templates: Map<TemplateId, ScreenTemplate> = mapOf(
    TemplateId.QUEUES_MEDIA to QueuesMediaTemplate,       // today's layout
    TemplateId.FULLSCREEN_MEDIA to FullscreenMediaTemplate,
    TemplateId.MEDIA_TICKER_CLOCK to MediaTickerClockTemplate,
    TemplateId.QUEUES_ONLY to QueuesOnlyTemplate,
)

object QueuesMediaTemplate : ScreenTemplate {
    @Composable override fun Render(slots: ZoneSlots) = Row(Modifier.fillMaxSize()) {
        slots.Queues(Modifier.weight(1f).fillMaxHeight())
        Box(Modifier.weight(3f).fillMaxHeight()) {
            slots.Media(Modifier.fillMaxSize())
            slots.Ticker(Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            slots.Clock(Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}
```

- **Templates only place slots.** They hold no state and do no I/O, so a new template is about 20 lines plus a registry entry plus a screenshot test.
- **Zones own their behaviour.** Each zone gets a state holder fed by repositories:
  - `QueueZone`: tiles plus the chime detector.
  - `MediaZone`: the playlist scheduler, ExoPlayer and Coil.
  - `TickerZone`: RSS fetch and cache plus `basicMarquee`.
  - `ClockZone`: ticks aligned to the minute, using the site's `timeZone`/`locale`.
- **Zone config merges site defaults with screen overrides.** Ticker feeds and the chime come from the site unless the screen overrides them.
- **Initial templates:**
  1. `queues_media`: parity with today's `/home`. This is the default.
  2. `fullscreen_media`.
  3. `media_ticker_clock`: the ticker is a bar under the media.
  4. `queues_only`: a grid of large tiles.
- **Queue tiles** draw the Font Awesome Solid code point with the bundled Font Awesome Free 6.x Solid font that matches `font_awesome_flutter` 10.9.1 (`pubspec.lock`). ARGB colours arrive as `Long`, and `Color(value.toInt())` keeps the bit pattern. `foregroundColor` is optional and defaults to white.
- **Sizing.** A 1080p TV runs at 960×540 dp (xhdpi), and some boxes report 720p at tvdpi. Templates use weights, and text scales with the zone height. `safeAreaPercent` handles TVs that overscan.

### Playback and media pipeline

```text
Firestore (devices → site → screen → playlist) ──► ConfigRepository ──► SyncPlanner (pure: wanted set, budget, evictions)
                                                        │                        │
                                                        ▼                        ▼
                                                 ScreenViewModel          MediaPrefetchWorker (WorkManager, CONNECTED, jitter)
                                                        │                   └─ Media3 Downloader(OkHttpDataSource → CacheDataSource → SimpleCache)
                                                        ▼                      verify sha256 → mark ready in CacheIndex (Room)
                                    MediaZone: PlaylistScheduler (pure, clock-injected) ◄── ready set
                                        ├─ image → Coil AsyncImage(CacheFetcher(sha256)) for durationSec, crossfade
                                        └─ video → shared ExoPlayer(CacheDataSource, key = sha256), advance on END / cap / error
```

- **One store.** `SimpleCache` lives in `filesDir/media-cache`, not `cacheDir`, because Android may clear `cacheDir` when storage runs low. It uses `NoOpCacheEvictor`, and a custom `CacheKeyFactory` returns the rendition's `sha256`, so a CDN host change or a signed-URL rotation never invalidates the cache.
- **Prefetch.**
  - Unique work runs on playlist or screen change, on network-available and every 6 h, with 0–10 min jitter on publish-triggered runs.
  - For each wanted rendition, the worker runs a Media3 `ProgressiveDownloader` (for the URL, with the `sha256` custom cache key). Interrupted downloads resume from the cached spans.
  - It then hashes the cached bytes against `sha256`, and on mismatch removes the resource and marks it `bad`.
  - Progress is kept locally and reaches `devices/{uid}.status.pendingItems` only with the next heartbeat or the sync-complete write (C5), never per file.
- **Ready-set rule.** The scheduler only plays verified renditions. A new playlist version is held back until every item is ready, then switches at the next item boundary. If it can never complete (no space), the ready subset plays and the error is reported.
- **Playback.** `CacheDataSource.Factory` uses the cache and an OkHttp upstream. With the ready-set rule, playback reads from disk. The upstream is only there so legacy-mode URLs can stream while online.
- **Disk budget and eviction.**
  - Budget = min(`site.maxCacheBytes` if set, 70 % of free space measured by `StatFs` at sync time, minus a 500 MB floor kept free for the OS).
  - **Pinned:** every rendition the box's current and pending playlist versions need.
  - **Evicted:** unpinned resources, after a 24 h grace period, then least recently played first, until under budget.
  - A download that can't fit is skipped and reported (`lastError: "insufficient_storage"`). It never evicts pinned items.
- **Errors.** A decode error skips the item and marks the rendition `bad` until its `sha256` changes. Repeated failures with nothing playable show the bundled fallback image.
- **Volume** comes from `zones.media.volume`. **Chime:** `SoundPool` with the bundled `pixiedust.mp3`. `NumberChangeDetector` (pure) ignores the first emission and the cache→server catch-up burst, fires once per real change (wrap 99→0 included), and ducks the media to 30 %.
- **Ticker:** OkHttp (HTTP cache on, for `ETag`/`If-Modified-Since`) + `XmlPullParser`. The last titles are kept in DataStore so the ticker survives an offline restart.
- **Legacy mode (cutover aid):** `zones.media.legacyQueueId` plays that queue's `videoURL`, streamed through `CacheDataSource` with the URL as the cache key so it also plays offline after one full play. It also uses the queue's `velocity` for the ticker. It's removed once screens use playlists.

### Start-up, offline and provisioning flow

1. **Boot.** The `BOOT_COMPLETED` receiver (or the HOME intent) launches `DisplayActivity`, which shows a splash.
2. **Firebase.** Init, and sign in anonymously **only if `currentUser == null`**. A cached user works offline, and Firestore's disk cache gives every listener its last-known docs immediately.
3. **Identity.** Observe `devices/{uid}`:
   - no doc → create it, without any portal-owned field (see [Auth and rules](#auth-and-rules-what-pairing-needs));
   - no `siteId` (never claimed, or unassigned by the portal) → **pairing screen** with a large code, the box model, and "Enter this code in the admin portal". The code is created with a server `createdAt`, retried on `PERMISSION_DENIED`, and rotated every 14 minutes (C4);
   - offline with no cache → "Waiting for network" plus the code.
4. **Render.** Resolve the screen (`screenId` or the site's `defaultScreenId`) and render its template from cache straight away. Zones fill in as data arrives, and the media zone plays ready items or the fallback image.
5. **Online again.** Listeners catch up, the chime ignores the catch-up burst, and prefetch resumes.
6. **Identify.** When the portal sets `identifyUntil` in the future, the box shows a full-screen banner with its name and code, so an installer can match boxes to screens.
7. **Watchdogs.**
   - An uncaught-exception handler schedules a relaunch with `AlarmManager`, or lock-task mode keeps the app up (D8-C).
   - An optional daily restart at `site.maintenanceHour`.
   - A heartbeat every **10 min** (`info.heartbeatSec` = 600, C5): one update of `status`, `info` and `lastSeenAt`, about 150 writes per box per day including sync-complete writes. Spark's 20k writes a day then cover about 130 boxes, less whatever the operators and the portal write. With 5-minute heartbeats plus per-item status writes the quota ran out below 70 boxes.

**D-pad.** The display ignores input by default. Back is consumed. **Long-press OK (3 s) or MENU** opens a diagnostics overlay with:

- the code, site, screen and template
- cache usage and per-item state
- decoders, last sync and last error
- buttons for "Sync now", "Unpair" (clears the local assignment cache and shows a new code; the portal must confirm by unassigning) and "Exit kiosk" (behind a site PIN)

Left/Right advancing a number only if Open question 1 asks for it.

### Project layout

```text
android-tv/
  settings.gradle.kts            # includes :app :domain :data :ui
  gradle/libs.versions.toml
  contract/
    templates.json               # template ids, minVersionCode, zones (C6); read by the portal
  app/                           # DisplayActivity, Hilt, BootReceiver, kiosk, pairing + diagnostics UI
    google-services.json         # NEW Android app registered in santos-1b6f6
  domain/                        # pure Kotlin/JVM: models, TemplateId, config merging/defaults,
                                 # PlaylistScheduler, SyncPlanner (budget/pinning/eviction), NumberChangeDetector,
                                 # RenditionSelector, PairingCode generator
  data/                          # Firestore mappers + repositories, Media3 cache/downloader wiring, CacheIndex (Room),
                                 # MediaPrefetchWorker, Coil CacheFetcher, RSS client, DataStore
  ui/                            # theme, templates, zones, previews
```

- **Firestore mapping is manual** (`DocumentSnapshot` → domain), with defaults for every optional field, rather than `toObject()`. One bad field degrades a zone instead of crashing the screen.
- **`:domain` has no Android dependency**, so scheduling, the ready-set switch, rendition choice, eviction planning, chime detection and config merging all run as fast JVM tests.

### Testing

| Layer | Tooling | What |
|---|---|---|
| `:domain` | JUnit 5, kotlinx-coroutines-test, Turbine | Scheduler with a fake clock; ready-set switching; `SyncPlanner` (budget, pinning, grace, LRU); `RenditionSelector` (decoders × `maxVideoHeight`); chime detector (first emission, catch-up, wrap); site/screen config merge and unknown template. |
| `:data` | Robolectric; **OkHttp `MockWebServer`** standing in for the CDN (`Range`/206, dropped connections, wrong bytes → sha mismatch); **Firestore emulator** in CI | Mappers on the **shared fixtures** in `admin-portal/packages/contract/fixtures/` (C8; two orgs), plus edge cases (Long colours, integer-valued doubles, missing fields); resumable prefetch; offline playback from cache; pairing (create, collision as `PERMISSION_DENIED`, claim, unassign) against the #6 rules. |
| `:ui` | Roborazzi screenshot tests at 1920×1080 and 1280×720; JUnit | One golden per template; long queue names; pairing and diagnostics screens. `contract/templates.json` matches the `TemplateId` registry (C6). |
| Device | Checklist on a Google TV box (Chromecast HD or Streamer) and two generic boxes (one Amlogic, one Rockchip); optional nightly emulator job | Boot launch, offline cold start, a 4K rendition on a 4K box, a 24 h soak (memory and decoder leaks), a 10-box pairing run. |

### CI

- **`build-apk.yml` (Flutter):** add `paths-ignore: ['android-tv/**', 'docs/**']` and a `pull_request` trigger.
- **New `android-tv.yml`:** runs on `push` to `main` and on `pull_request`, filtered to `android-tv/**`, the workflow file and, from M2, `admin-portal/packages/contract/fixtures/**`, so a contract change on the portal side runs the TV mapper tests (C8).
  - Steps: `actions/setup-java` (17, or 21 if the AGP version requires it), `gradle/actions/setup-gradle`, `./gradlew -p android-tv lint testDebugUnitTest verifyRoborazziDebug assembleDebug`. Upload the debug APK and reports.
  - A Firestore-emulator job (`firebase-tools`) seeds the emulator with the shared seed script from `admin-portal/packages/contract` (so it also sets up Node and pnpm), then runs the `:data` integration tests against the rules from #6.
  - From M9, a tag-triggered signed `assembleRelease` with the keystore in GitHub secrets and `versionCode` = `github.run_number`.
- The new app's `google-services.json` is committed, as the Flutter one already is. It isn't a secret, but see the rules note in Context.

## Delivery plan

Each milestone is one PR. Once M2 lands, M3–M4 and M5 can run in parallel. The **portal** is designed separately (#2), and its contract feedback C1–C10 is now part of this doc. **M2 depends on portal milestone A1**, which ships the contract package: zod schemas, golden fixtures covering two orgs, the one seed script for the emulator and the dev project, and the `orgId` + `siteId` backfill for existing queues (C8). The TV side writes no seed script of its own: `android-tv/tools/seed.ts` is dropped.

| # | PR | Done when |
|---|---|---|
| M0 | This design doc | Decision recorded below, and the portal design has acknowledged [Interface with the admin portal](#interface-with-the-admin-portal). |
| M1 | **Scaffold `android-tv/` + CI.** Gradle KTS, version catalog, 4 modules, Hilt, Compose for TV hello activity, `android-tv.yml`, Flutter path filters. Register the new app id in Firebase. | Green CI on a PR. The APK runs on a TV emulator and on a real box. |
| M2 | **Contract + read path.** Needs portal **A1**. Domain models and manual mappers for `sites`, `screens`, `devices`, `playlists`, `queues` (with `siteId`), ignoring `orgId`, `updatedBy`, `mediaIds` and the other portal-only fields; a fixture loader for `admin-portal/packages/contract/fixtures/`; anonymous auth. `android-tv.yml` also runs on fixture changes. The seed script and the queue backfill come from A1. | Mapper tests pass on **every A1 fixture** (both orgs), plus edge cases. Against the emulator seeded by the A1 script, live queue numbers from the Flutter operator show up in a debug list filtered by site. |
| M3 | **Template framework + `queues_media` parity.** Config merge, template registry, queue/clock/ticker zones (RSS cached offline), media placeholder. `android-tv/contract/templates.json` with `queues_media` (C6). | Roborazzi goldens. Matches Flutter `/home` minus video. The registry test passes against `templates.json`. |
| M4 | **Chime on number change.** | Detector tests. One chime per real change, none on start-up or reconnect. |
| M5 | **CDN prefetch + cache.** `SimpleCache` store, `CacheKeyFactory` by `sha256`, `MediaPrefetchWorker` with Media3 `Downloader`, sha verification, `SyncPlanner` budget/eviction, `RenditionSelector`, status reporting at heartbeat and sync-complete only (C5). | `MockWebServer` tests (resume, mismatch, out of space). A seeded playlist prefetches and survives reboot in airplane mode. |
| M6 | **Playlist playback + remaining templates.** `PlaylistScheduler`, ExoPlayer through `CacheDataSource`, Coil `CacheFetcher`, ready-set switching, `fullscreen_media`, `media_ticker_clock`, `queues_only`, legacy `videoURL` mode. The three new templates added to `templates.json`. | A mixed playlist loops offline for 24 h. Goldens for all 4 templates. |
| M7 | **Pairing + device management (TV side).** `pairingCodes` flow per C4: server `createdAt`, server-clock offset for `expiresAt`, `PERMISSION_DENIED` as collision with bounded retries, 14-minute rotation. Pairing screen, return to pairing on unassign, site default screen, `identifyUntil`. Heartbeat every 600 s with `info.heartbeatSec` and `info.appVersionCode`, and coalesced status writes (C5, C6). Diagnostics overlay. Emulator tests of the **device paths in the #6 rules** (portal A3), the TV's create never carrying `orgId`. Until portal A5 ships, a "claim" command in the shared seed script (A1) stands in for the portal. | A fresh box shows a code. Claiming via script/portal switches it within 5 s, and unassigning brings the code back. A collision retries with a new code. Over 24 h a box writes no more than about 150 times. Rules tests pass. |
| M8 | **Kiosk.** Boot receiver + HOME filter, keep-screen-on, Back handling, crash relaunch, daily restart. `docs/android-tv/DEPLOY.md` covers the overlay grant, energy saver and the device-owner path. | Power-cycle on Google TV and generic boxes. 24 h soak. |
| M9 | **Release + pilot.** Signing, release workflow, update path (Open question 2). Pilot one site with 2–3 boxes beside Flutter. | Pilot runs a week. Cutover checklist signed off. |
| M10 | **Bulk enrolment (D7-C).** Managed-config / `adb` enrolment token → auto-join site. | Ten boxes enrolled with one script/MDM profile appear under the site in the portal. |
| Later | Separate issues: **rules hardening** (#6: org-aware staff and operator claims, Flutter operator login), D-pad advancing (if wanted), HLS/signed URLs (if needed), removing the Flutter display route after cutover. | — |

## Open questions

**Answered in revision 3 (2026-11-15):** Q4 (**Spark**), Q10 (**multi-org**, C10), and Q3 in part (**Cloudflare R2**). The answers are inline below.

1. **Who advances numbers after cutover?** Today the display TV does it with D-pad left/right. Should the native app keep that (configurable per screen), or is advancing only done by the Flutter app on another device? *Still open.* The portal design adds an operator console after the TV pilot (portal A11), which leans away from D-pad advancing on the TV.
2. **Fleet and distribution.** Which exact box models are we buying or supporting? (This decides `minSdk 26` vs 28 and whether 4K renditions are worth it.) Will updates go through Play (closed track / managed Google Play), an MDM, or sideloading? Is an MDM available, which decides D8-C and D7-C?
3. **CDN specifics** (owned by the portal design, but they affect the TV): **partly answered.**
   - Which CDN? **Answered: Cloudflare R2**, public bucket behind `cdn.<domain>` (the domain itself is still open in #2).
   - Who uploads to it and computes `sha256`, `codecs` and dimensions? Owned by #2. Its accepted design has the portal's media service compute them server side from the stored bytes.
   - Does the portal transcode to the baseline H.264 rendition, or reject non-conforming files? Owned by #2. Its accepted design rejects first, then transcodes out-of-spec video before the pilot.
   - Can the URLs be public and content-addressed, or must they be **signed**? Signed URLs need a URL-minting function, and the TV needs a resolver. Owned by #2. Its accepted design uses public, content-addressed URLs, so no resolver is needed.
4. **Firestore plan and cost.** Every queue change costs one read per TV listening to it. With N sites × M boxes, is Spark enough, or do we move to Blaze? **Answered: stay on Spark.** The TV follows C5 (600 s heartbeat, no per-item status writes). Reads still grow with queue changes × boxes per site, plus the rules' `get()` of the device doc. If those reads become material, C7 (TV scoping by claim) is the contingency.
5. **YouTube.** Do current screens use YouTube URLs? Is it OK to drop it (no offline playback, caching not allowed)?
6. **Chime.** Every queue on the screen or only some? A sound per queue? Spoken announcements (TTS) later?
7. **Ticker and clock.** Is one feed per site OK, with `rtp.pt` as the default? Static text messages too? Should the clock show the date (Flutter formats one but hides it)?
8. **Portrait screens.** Are any displays mounted vertically? That would need root rotation and portrait templates.
9. **Scheduling.** Do screens need day-parting (different playlists by time or day) soon? The model has one `playlistId` per screen. A `schedule[]` field could be added later without breaking anything.
10. **Tenancy.** Is this single-organisation? If several customers will share the project, add `orgId` above `sites` now rather than later. **Answered: several customers, each with its own org admins.** The org layer is C10: `orgs/{orgId}` plus an `orgId` field on config docs, with no path changes (see [Interface with the admin portal](#interface-with-the-admin-portal)).
11. **Security timeline.** Must rules hardening (admin/operator claims, Flutter operator login) ship before any box goes into a public space, given the repo is public and anonymous users can write everything today? *Still open here.* The portal decision requires #6 before the portal holds real customer data or any box goes into a public space.
12. **Naming.** Is `android-tv/` and `pro.joaosousa.queuedisplay.tv` OK? What app label and launcher banner should it use?
13. **Number range.** Is 0–99 with wrap a business rule the display can assume?

## Decision

**Accepted on 2026-11-15.**

- **Picks:** every pick in [Summary of picks](#summary-of-picks) (D1–D10) is accepted as written.
- **Delivery plan:** milestones M1–M10 are adopted as the delivery plan.
- **Open questions:** at acceptance, questions 1–13 stayed open (revision 3 since answered some; see below). None of them blocks M1, and each answer will be recorded in this doc as it comes in. The most pressing are:
  - Q1: D-pad advancing
  - Q2: fleet and MDM
  - Q3: CDN specifics
  - Q10: tenancy
  - Q11: security timing
- **Handed off to the admin portal design (#2):** Q3 (CDN specifics) and the [Interface with the admin portal](#interface-with-the-admin-portal) contract. That design owns them from here.
- **Revision 3 (2026-11-15):** the portal's contract feedback C1–C10 is accepted and written into this doc. Q3 is partly answered, and Q4 and Q10 are answered. The status stays **Accepted**. Points that still need a decision are under [Contract conflicts](#contract-conflicts).
- **Rules hardening** (admin/operator claims, Flutter operator login, device-scoped reads) is tracked as its own issue and isn't part of this design's milestones.
