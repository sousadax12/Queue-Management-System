# Design: native Android TV display app

Refs #1

## Status

Proposed (2026-11-15)

## Context

### Goal

We want a native Android TV app to replace the **display** part of the Flutter app. It should play videos and images and offer a few built-in screen templates. Each screen picks its template remotely. Decisions made before this doc:

- **Scope:** TV display only. The operator flow, which advances queue numbers, stays in Flutter for now.
- **Backend:** Firebase stays. Firestore holds queues and screen config, and Firebase Storage holds media. Screens keep playing from a local cache when the network drops.
- **Templates:** a fixed set of layouts built into the app. Data-defined layouts are not a goal.
- **Repo:** the native project lives in a new directory beside the Flutter app until cutover.

### What the Flutter app does today

| Concern | Current behaviour | Where |
|---|---|---|
| Start-up and auth | `Firebase.initializeApp`, then **always** `signInAnonymously()`. The initial route is hard-coded to `/setup`, so the email/password `LoginScreen` at `/` can't be reached from the app. | `lib/main.dart:62-91`, `lib/pages/login_page.dart` |
| Queue selection | `/setup` does a one-off `get()` of `queues` and lists them. The user picks one with the D-pad (left/right/select), and it becomes `QueuesProvider.selectedQueue`. | `lib/pages/setup_page.dart:40-127` |
| Queue tiles | `/home` listens to the **whole** `queues` collection and shows every queue in a column on the left (flex 1 of 4). Each tile shows its name and number on the queue's background colour. | `lib/pages/home_page.dart:86-122`, `lib/widgets/queue_widget.dart` |
| Queue document | `number` (int), `icon` (int, a Font Awesome Solid code point), `name`, `backgroundColor` (int ARGB), `videoURL`, `videoVolume` (int), `soundVolume` (int), `velocity` (int). `foregroundColor` is hard-coded to white and isn't in Firestore. Every field is read with `document['x']`, which **throws if the field is missing**. | `lib/mappers/queue_doc_mapper.dart`, `lib/models/queue_model.dart` |
| Icon | It's mapped into `QueueModel.icon`, but `QueueWidget` **never renders it**. | `lib/widgets/queue_widget.dart` |
| Number changes | D-pad left/right on `/home` decrements or increments the **selected** queue's number, wrapping 0↔99. The app writes the absolute value with `update({'number': n})` and plays `pixiedust.mp3` at `soundVolume`. | `lib/pages/home_page.dart:59-79`, `lib/providers/queues_provider.dart` |
| Sound | It plays **only on the device whose key was pressed**, not when a number changes in Firestore. A second display showing the same queue stays silent. | same |
| Video | One looping video from the selected queue's `videoURL`, streamed with no cache. YouTube URLs go to `youtube_player_flutter`. Volume is `videoVolume` (0..1 for `video_player`, ×100 for YouTube). Since it's stored as an int, it's in practice either muted or full volume. | `lib/widgets/video_player_widget.dart` |
| RSS ticker | A hard-coded feed (`https://www.rtp.pt/noticias/rss`) as a marquee over the bottom of the video, with speed from the selected queue's `velocity`. The feed is fetched again on every rebuild and isn't cached offline. | `lib/pages/home_page.dart:135-142`, `lib/widgets/rss_feed_widget.dart` |
| Clock | Top-right, shows `HH:mm` only. A date gets formatted but isn't used. It updates on every frame via a `Ticker`. | `lib/widgets/current_date_widget.dart` |
| Firebase config | Project `santos-1b6f6`, with a default bucket `santos-1b6f6.appspot.com` that the app doesn't use. `firebase.json` only deploys Firestore rules, and there are no Storage rules. | `lib/firebase_options.dart`, `firebase.json` |
| Rules | Any authenticated user, anonymous included, can read **and write** every document. | `firestore.rules` |
| Android host | `minSdk 23`, `compileSdk 35`, AGP 8.1.1, Kotlin 1.9.22, Gradle 8.0.2. The manifest already declares `LEANBACK_LAUNCHER` and makes touch optional. Cleartext traffic is allowed. | `android/app/build.gradle`, `android/gradle.properties`, `android/app/src/main/AndroidManifest.xml` |
| CI | One workflow runs on push to `main` only, with no PR checks. It sets up Java 17 and Flutter, runs `flutter build apk --debug`, and uploads the APK. | `.github/workflows/build-apk.yml` |

### Things this changes or exposes

1. **The "display" and the "operator" are the same screen today.** `/home` renders the display, and its D-pad arrows advance the number. With "operator stays in Flutter", someone has to keep running the Flutter app (on a phone, tablet or a second TV) to advance numbers. Otherwise the native app must keep the D-pad shortcut. See Open question 1.
2. **Screen config currently lives on the queue document.** The video, video volume, sound volume and ticker speed all come from the selected `queues/{id}`. The new model moves screen config into its own collections and leaves `queues` alone (see "Compatibility with the Flutter operator").
3. **"Sound on number change" isn't how it works today.** The native app will play the chime when it **observes** a change in Firestore. That's what the issue asks for, and it's better for multiple displays.
4. **Security.** The repository is **public** and includes `google-services.json`/`firebase_options.dart`. Anonymous auth is enabled and the rules allow any signed-in user to write anything. Anyone can mint an anonymous user and overwrite queues today, and the same will apply to screens, playlists and media once they exist. Hardening the rules is a separate topic, but the data model below is shaped so that per-device rules become possible (each device reads only its own screen).
5. **Storage needs the Blaze plan.** Since 3 Feb 2026, Cloud Storage for Firebase needs Blaze. That includes the `*.appspot.com` default bucket, and Spark projects get 402/403 errors. Check the plan of `santos-1b6f6` before milestone M5.

## Options

Each decision lists the realistic options. The pick and its reasoning are under [Recommendation](#recommendation).

### D1. UI stack

| | A. Kotlin + Jetpack Compose with Compose for TV (`androidx.tv:tv-material`) | B. Kotlin + Leanback (Views/Fragments) | C. Keep Flutter, rewrite the display page |
|---|---|---|---|
| Fit for signage | Good. Free-form layouts, easy proportional zones, animation, marquee (`Modifier.basicMarquee`). | Poor. Leanback is built for browse/details/playback catalogue UIs, not free-form signage. | OK. It's what we have. |
| Status | `tv-material` 1.1.0 stable (May 2026), with the standard Compose BOM. Google's recommended TV toolkit. | **Deprecated** in favour of Compose for TV. | Supported, but plugin-based media and caching is where the current pain is (YouTube plugin, no offline). |
| Media | Media3 has first-party Compose UI (`PlayerSurface`; the `Player` composable since 1.10). | Media3 `PlayerView`. | Plugins wrap ExoPlayer with less control over cache, surfaces and decoders. |
| Cheap sticks | Compose costs a bit more than Views at start-up, but a static signage layout is light. Use Baseline Profiles. | Lightest. | Flutter engine overhead, plus platform-view cost for video. |
| Team cost | New native codebase. | New codebase on a dead toolkit. | No new language, but it contradicts the decision already made. |

**minSdk and target devices.** The targets are Android TV / Google TV boxes and TVs (Chromecast with Google TV, Google TV Streamer, Nvidia Shield, Philips/Sony/TCL Google TV sets) and cheap AOSP-based signage sticks/boxes (Rockchip/Amlogic, usually Android 9–12, sometimes 7.1).

- `minSdk 23` matches the Flutter app and is the current AndroidX floor. It reaches the oldest sticks but means testing old WebView, codec and boot behaviour.
- `minSdk 26` (Android 8.0) covers every Android TV OS 8+ device and drops the API-23–25 quirks (`java.time` without desugaring, simpler notification channels and background limits).
- `targetSdk 35` meets the Play requirement for new **TV** apps, which is API 34+. TV is exempt from the API-36 phone rule. Move to 36 when convenient.

### D2. Video playback

| A. Media3 ExoPlayer, one player reused across items | B. Media3 ExoPlayer with the whole mixed playlist in one timeline (images through ExoPlayer's image support, `MediaItem.Builder().setImageDurationMs`) | C. Two alternating ExoPlayer instances for gapless pre-roll |
|---|---|---|
| Our own scheduler drives items. Video items go to ExoPlayer, and when a video ends (or hits its cap) we advance. A ~100–300 ms gap between videos is hidden behind a crossfade or the last frame. | Gapless and preloaded by ExoPlayer itself. But images go through the player's image output, layout/scale control is less flexible, the feature is newer, and it's less tested on cheap SoCs. | Gapless video→video. But many cheap SoCs only allow **one** hardware decoder instance, so the second player fails or falls back to software decoding. |

Media3 is the only serious choice for the player. Media3 1.10 (Mar 2026) is current. The real choice is about orchestration.

### D3. Image loading

| A. Coil 3 (`coil-compose`) | B. Glide | C. Raw `BitmapFactory` |
|---|---|---|
| Kotlin-first and Compose-native. Downsamples to the display size (important on 1–2 GB RAM sticks). Loads local files directly. | Mature, but its Compose integration is secondary. | No dependency, but we'd have to write our own downsampling, caching and crossfade. |

### D4. Media pre-caching and eviction

| A. Our own content-addressed file store, synced by WorkManager | B. Media3 `DownloadManager` with `SimpleCache` + `CacheDataSource` | C. Cache only during playback (`CacheDataSource`) |
|---|---|---|
| A sync worker downloads every item that any active playlist references into `filesDir/media/<key>` via the Firebase Storage SDK (`getFile`). A small Room table (or JSON manifest) tracks the files. The player and Coil both read local `file://` URIs. One cache for images and videos, easy to check from a diagnostics screen. | Built for video, handles HLS/DASH segments, resumable. But it's a separate cache from images (Coil keeps its own disk cache), keys must be pinned to storage paths because download-URL tokens rotate, and "is the whole playlist ready?" is harder to answer. | Least code, but **first play needs the network**, and a cold start offline plays nothing new. That fails the offline requirement. |

### D5. Firestore data model

| A. New collections `screens`, `playlists` (items embedded), `devices`, `media`. `queues` unchanged. | B. Extend `queues` docs with screen/playlist fields (today's pattern). | C. A single `screens/{id}` doc embedding everything, playlists included. |
|---|---|---|
| Clean separation: one screen can show several queues, and several screens can share one playlist. Flutter is unaffected. | No new collections, but it keeps conflating "a queue" with "a screen" and doesn't scale to screens with no queue or several queues. | Fewest reads, but playlists can't be shared between screens. |

Within A, playlist items could either embed media metadata or use a `mediaItems` subcollection that references `media/{id}`. Embedding makes a playlist one atomic doc with one listener and no N+1 reads. A subcollection makes the media library the single source of truth. Both are covered under Recommendation.

### D6. Template architecture

| A. Sealed set of Kotlin templates; each composes typed **zones** | B. One generic grid layout driven by data (rows, cols, spans in Firestore) | C. One composable per template with the zone UI inlined |
|---|---|---|
| A template is a small composable that places zone slots. Zones (queues, media, ticker, clock) are reusable components with their own state. Adding a template means one file, a registry entry and a screenshot test. | It's the data-defined layout we decided **not** to build. | Fastest for the first template, but each new template copies the zone code. |

### D7. Device provisioning / pairing

| A. Pairing code | B. On-device picker (today's `/setup` UX) | C. Managed config / provisioning payload |
|---|---|---|
| On first boot the device signs in anonymously, writes `devices/{uid}` with a 6-character code, and shows it full-screen. An admin enters the code somewhere, which sets `devices/{uid}.screenId`. Works well once rules are hardened. **Needs an admin UI**, which doesn't exist yet, so for now it'd be the Firebase console or a script. | A hidden menu lists `screens` and the installer picks one with the D-pad. The device writes its own `devices/{uid}.screenId`. No admin UI needed, but every device must be able to read every screen, which conflicts with strict rules later. | EMM/MDM app restrictions (`RestrictionsManager`) or a QR/`adb` extra carry the `screenId`. Great for fleets that are already MDM-managed. Pointless without an MDM. |

### D8. Kiosk behaviour (launch on boot, stay on, D-pad)

| A. `BOOT_COMPLETED` receiver + "display over other apps" | B. App is the **HOME** launcher | C. Device owner + lock-task mode (or an MDM kiosk policy) |
|---|---|---|
| Android 10+ blocks starting an activity from the background unless the app holds `SYSTEM_ALERT_WINDOW`. Grant it once at install (`adb shell appops set <pkg> SYSTEM_ALERT_WINDOW allow`), because Google TV hides the settings toggle. Works on any device. | A `HOME` intent filter means the box boots straight into the app. Works on AOSP signage boxes. Google TV doesn't let you replace its launcher. | The strongest option: survives crashes, blocks exits, allows silent updates. Needs a factory-reset device and `dpm set-device-owner`, or an MDM. |

Common to all three: `FLAG_KEEP_SCREEN_ON` on the window, `Back` consumed while in kiosk, and a deployment checklist that sets Google TV "Energy saver → turn off display: Never" and turns off the screensaver.

### D9. Project structure, DI and architecture

| Option | Shape |
|---|---|
| **Modules** A. A few modules: `:app`, `:domain` (pure Kotlin/JVM), `:data`, `:ui` | B. One `:app` module with packages | C. Fine-grained feature modules (`:feature:queues`, `:feature:media`, …) |
| **DI** A. Hilt (+ `hilt-work` for workers) | B. Koin | C. Manual `AppContainer` |
| **Pattern** A. Single activity, unidirectional data flow: repositories expose `Flow`s, a `ScreenViewModel` combines them into `StateFlow<ScreenState>`, zones are stateless composables | B. MVI with reducers/intents | |

### D10. CI

| A. A second workflow, `android-tv.yml`, scoped by path filter; the Flutter workflow ignores `android-tv/**` | B. One workflow with two jobs and `dorny/paths-filter` | C. One job that always builds both |
|---|---|---|
| Independent, easy to read, each has its own caching. Runs on PRs as well as on `main`. | One file, but more conditional logic. | Simple but slow, and a Flutter break blocks native PRs and vice versa. |

## Recommendation

### Summary of picks

| Decision | Pick | What would change our mind |
|---|---|---|
| D1 UI stack | **Kotlin + Compose with Compose for TV** (`tv-material` 1.1.x, Compose BOM, Kotlin 2.x with the Compose compiler plugin, AGP 8.x/9.x). **`minSdk 26`, `targetSdk 35`.** Directory **`android-tv/`**, new application ID **`pro.joaosousa.queuedisplay.tv`** so both apps can run side by side during the pilot. | Real devices on Android 7.x → drop to `minSdk 23` (no other change). Profiling on the cheapest target stick shows Compose jank we can't fix with Baseline Profiles → render the media zone in a `SurfaceView` / Views island. |
| D2 Video | **Media3 ExoPlayer, one reused instance, driven by our own playlist scheduler (A).** | Product needs truly gapless video→video → try B (single ExoPlayer timeline, images included) on the target hardware first. Never C on cheap sticks. |
| D3 Images | **Coil 3**, loading local files only, `size` = zone size, crossfade. | — |
| D4 Caching | **Own content-addressed file store + WorkManager sync (A).** Playlists switch only when all their items are on disk. | We need HLS/DASH or adaptive streams, or libraries bigger than device storage → Media3 `DownloadManager` (B) for video, keeping A for images. |
| D5 Data model | **A: new `screens`, `playlists` (items embed media metadata), `devices`, `media` collections. `queues` unchanged, additive fields only.** | Screens turn out to be 1:1 with queues for good → B would be simpler. Unlikely, since templates without queues are already in scope. |
| D6 Templates | **A: sealed template registry + reusable zones.** | We need customer-defined layouts → revisit B (explicitly out of scope). |
| D7 Pairing | **A + B behind one field.** Each device registers `devices/{uid}` with a pairing code. Assignment is via the **on-device picker (B) for the MVP**, and via code entry once an admin tool exists. Both write the same `devices/{uid}.screenId`, so nothing forks. | An MDM is already in place → C, and the picker is hidden. Strict rules are needed before an admin UI exists → ship pairing-code-only and assign through the console/script. |
| D8 Kiosk | **A as the baseline on every device**, B added on AOSP boxes (the manifest declares both), and **C recommended for unattended public screens** when an MDM or factory-reset setup is possible. | MDM chosen → use its kiosk policy and drop the overlay grant. |
| D9 Structure | **Modules A (`:app`, `:domain`, `:data`, `:ui`), Hilt, single-activity UDF.** | Team strongly prefers no annotation processing → manual DI. The app is small enough. |
| D10 CI | **A: separate `android-tv.yml` + path filters; PR checks for both.** | — |

Why Compose for TV over Leanback: Leanback is deprecated and built for media catalogues. Signage is free-form layout plus media, which is what Compose does well. Why native at all: the decision is made, and the hard parts here (offline media cache, decoder control, kiosk/boot behaviour) are native APIs that Flutter would reach only through plugins.

### Data model

All new collections are additive. Nothing in `queues` is renamed or removed, because the Flutter mapper reads every field with `document['x']` and throws on a missing one.

```text
queues/{queueId}        (existing, written by the Flutter operator; read-only for the TV app)
screens/{screenId}      (screen config: template + zone params)
playlists/{playlistId}  (ordered items with embedded media metadata)
media/{mediaId}         (media library: one doc per uploaded file)
devices/{uid}           (one per TV, keyed by the anonymous auth uid)
```

`queues/{queueId}` (unchanged, shown for reference):

```json
{
  "name": "Balcão A",
  "number": 42,
  "icon": 61713,
  "backgroundColor": 4282339765,
  "videoURL": "https://…/promo.mp4",
  "videoVolume": 0,
  "soundVolume": 1,
  "velocity": 60,
  "foregroundColor": 4294967295
}
```

- `foregroundColor` is **new and optional** (default white). Flutter ignores it.
- `icon` is a Font Awesome **Solid** code point. The TV app bundles the Font Awesome Free 6.x Solid font that matches `font_awesome_flutter` 10.9.1 (`pubspec.lock`) and draws the code point as a glyph, so it shows the same icon (and actually renders it, unlike `QueueWidget` today).
- Colours are ARGB ints that Firestore returns as `Long`. Convert with `Color(value.toInt())`, which keeps the bit pattern.
- `videoURL`, `videoVolume`, `soundVolume` and `velocity` are **ignored** by the TV app except in legacy mode (below). Screen config replaces them.

`screens/{screenId}`:

```json
{
  "name": "Lobby TV 1",
  "template": "queues_media",
  "zones": {
    "queues": { "queueIds": ["balcao-a", "balcao-b"], "chime": true, "chimeVolume": 1.0 },
    "media":  { "playlistId": "lobby-default", "volume": 0.3 },
    "ticker": { "feedUrls": ["https://www.rtp.pt/noticias/rss"], "speedDpPerSec": 60, "refreshMinutes": 15 },
    "clock":  { "pattern": "HH:mm", "showDate": false }
  },
  "locale": "pt-PT",
  "timeZone": "Europe/Lisbon",
  "safeAreaPercent": 0,
  "updatedAt": "<serverTimestamp>"
}
```

- `queueIds` omitted means **all queues**, which matches today's `collection('queues').snapshots()`. Up to 30 ids are fetched with one `whereIn` listener.
- Any zone the template doesn't use is ignored. If a zone the template needs is missing, it gets defaults (e.g. no ticker feed → hide the ticker).
- An unknown `template` value falls back to `queues_media` and reports the error in `devices/{uid}.status`. That way an older app build never shows a blank screen when a newer template id is set.

`playlists/{playlistId}`:

```json
{
  "name": "Lobby default",
  "items": [
    { "mediaId": "m_8f2c", "type": "video", "storagePath": "media/8f2c…e1.mp4", "md5": "jL1x…==", "sizeBytes": 48211022, "durationSec": null },
    { "mediaId": "m_19ab", "type": "image", "storagePath": "media/19ab…77.jpg", "md5": "Qm9v…==", "sizeBytes": 812004, "durationSec": 10 }
  ],
  "updatedAt": "<serverTimestamp>"
}
```

- Items are an **array in one doc**: ordered, atomic, one listener, well under the 1 MiB limit.
- Media metadata is **denormalised** into each item, so the device needs no extra reads. `media/{id}` stays the library the admin edits. Whoever edits it (console, script, later an admin UI) copies the fields into the item.
- **Storage objects are immutable.** Replacing a file means a new `storagePath`, ideally named by content hash. The cache key is `storagePath`, and `md5` (Firebase Storage's `md5Hash` metadata) verifies the download. Download URLs and their tokens are never stored or used as keys.
- `durationSec`: required for images (default 10 s). For videos, `null` = play to the end, a number = cap.
- Item types: `image` and `video` for the MVP. YouTube is **not** supported: it can't be cached (and YouTube's terms forbid it), and it needs a WebView. See Open question 5.

`media/{mediaId}`: `{ "type", "storagePath", "md5", "sizeBytes", "contentType", "width", "height", "durationMs", "name", "uploadedAt" }`.

`devices/{uid}` (written by the device itself):

```json
{
  "pairingCode": "K7Q2PX",
  "screenId": "lobby-tv-1",
  "model": "Chromecast HD", "sdkInt": 33, "appVersion": "0.4.0 (12)",
  "lastSeenAt": "<serverTimestamp>",
  "status": { "nowPlaying": "m_8f2c", "cacheBytes": 812004112, "freeBytes": 3210000000, "lastSyncAt": "…", "lastError": null }
}
```

- The heartbeat runs every 5 minutes, which is about 290 writes per device per day. That's well inside the free tier for a small fleet.
- Anonymous uids survive restarts and updates, but **not** "clear data" or a reinstall. In that case the device shows a new pairing code and gets assigned again.

### Compatibility with the Flutter operator

- No Flutter change is needed for the MVP. The TV app reads `queues` and writes nothing to it, unless Open question 1 says to keep D-pad advancing.
- **Legacy mode (optional, for cutover):** `zones.media.legacyQueueId` makes the media zone play that queue's `videoURL` (looping, downloaded through the same file store if it's a direct http(s) file) at `videoVolume`, and use its `velocity` for the ticker. Existing setups can then move to the TV app before anyone uploads media to Storage. Drop it once screens use playlists.
- If the TV app keeps D-pad advancing, it writes with `FieldValue`/a transaction that applies the same 0–99 wrap as `queues_provider.dart`. Flutter's absolute-value write would race against it, so a matching Flutter change to a transaction is worth making, but it isn't blocking.

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
  - `ClockZone`: ticks aligned to the minute.
- `ZoneSlots` is built once per screen config in `ScreenViewModel`, so a zone stays alive when only another zone's config changes.
- Initial templates:
  1. `queues_media`: parity with today's `/home`. Queue column ¼, media ¾, ticker over the bottom, clock top-right. This is the default.
  2. `fullscreen_media`: media only.
  3. `media_ticker_clock`: media, a ticker bar under it (not overlaid), clock.
  4. `queues_only`: a grid of large tiles, for counters without media.
- **Sizing.** A 1080p TV runs at 960×540 dp (xhdpi), and some sticks report 1280×720 at tvdpi. Templates use weights, not fixed dp, and text sizes scale with the zone height (e.g. queue number = 45% of the tile height), so layouts look the same at either density. `safeAreaPercent` adds padding for TVs that overscan.

### Playback and media pipeline

```text
Firestore (screens, playlists) ──► ScreenRepository ──► SyncPlanner (pure) ──► MediaSyncWorker (WorkManager)
                                         │                    │ wanted set           │ Storage getFile → tmp → md5 check → atomic rename
                                         ▼                    ▼                      ▼
                                   ScreenViewModel ◄── MediaStore (Room: key, path, size, md5, lastUsedAt, state)
                                         │
                                         ▼
                                MediaZone: PlaylistScheduler (pure, clock-injected)
                                   ├─ image → Coil AsyncImage(file) for durationSec, crossfade
                                   └─ video → shared ExoPlayer(file://…), advance on STATE_ENDED / cap / error
```

- **Ready-set rule.** The scheduler plays only items whose files are verified on disk. When a playlist changes, the old one keeps playing until **every** item of the new one is cached, then it switches at the next item boundary. If a new playlist can never be complete (out of space), it plays the ready subset and reports the error.
- **Sync triggers.** Sync runs as unique work (`KEEP` + chaining): whenever the screen/playlist snapshot changes, on network-available (`NetworkType.CONNECTED`), and every 6 h. Downloads go to a temp file and are checked against `md5`, then renamed into place.
- **Eviction.** After each sync, files no playlist on this device references are deleted after a 24 h grace period, so quick back-and-forth edits don't cause re-downloads. Then LRU by `lastUsedAt` until the store is under budget. The budget is min(configured cap, 70 % of free space at install), checked with `StatFs`. Files in the active playlist are never evicted.
- **Errors.** A decode error skips the item, marks it `bad` until its `storagePath` changes, and reports it in `devices/{uid}.status`. Several errors in a row with nothing playable → show the bundled fallback image.
- **Volume** comes from `zones.media.volume` (0.0–1.0) and is applied with `player.volume`. Two content guidelines go in the README: 1080p H.264/AAC MP4 and ≤ 4K JPEG/PNG/WebP, because cheap sticks often can't decode 4K HEVC.
- **Chime.** `SoundPool` with the bundled `pixiedust.mp3`. `NumberChangeDetector` (pure, in `:domain`) compares consecutive `queues` snapshots per id. It ignores the first emission and cache→server catch-up (`metadata.isFromCache` transitions) and fires once per real change, wrap 99→0 included. The media volume ducks to 30 % for the length of the chime.
- **Ticker.** OkHttp + the platform `XmlPullParser`, so no RSS library is needed. Titles are stored in DataStore to survive offline restarts, and the feed refreshes every `refreshMinutes`. HTTPS only (no cleartext config).
- **Clock.** Ticks aligned to the minute (not every frame), honouring `timeZone`/`locale`. Shows `HH:mm`, plus the date only if `showDate`.

### Start-up, offline and provisioning flow

1. **Boot.** The `BOOT_COMPLETED` receiver (or the HOME intent) launches `DisplayActivity`, which shows a splash with the app version.
2. **Firebase.** Init, then sign in anonymously **only if `currentUser == null`**. A cached user works offline. The Firestore Android SDK caches to disk by default, so listeners return the last-known docs immediately.
3. **Identity.** Observe `devices/{uid}`. If there's no doc or no `screenId`, show the **pairing screen**: a large code, plus "press MENU to choose a screen" (the on-device picker). If there's no network and no cache, show "Waiting for network" with the code.
4. **Render.** Observe `screens/{screenId}` and render the template right away from cache. Zones fill in as their data arrives: the queues zone shows cached numbers, and the media zone plays cached items or the fallback image.
5. **Online again.** Listeners catch up, the chime detector ignores the catch-up burst, and sync resumes.
6. **Watchdogs.** An uncaught-exception handler schedules a relaunch with `AlarmManager` (or lock-task mode keeps the app up under D8-C). An optional daily restart at a quiet hour, set by an optional `maintenanceHour` field on the screen doc.

**D-pad.** The display ignores input by default. Back is consumed. **Long-press OK (3 s) or MENU** opens a hidden diagnostics overlay with:

- device code, uid suffix, screen, template
- cache usage and per-item status
- last sync and last error
- buttons for "Re-pair / choose screen", "Sync now" and "Exit kiosk" (this one behind a fixed PIN set in screen config)

If Open question 1 keeps it, Left/Right advance `zones.queues.remoteQueueId`.

### Project layout

```text
android-tv/
  settings.gradle.kts            # includes :app :domain :data :ui
  gradle/libs.versions.toml      # version catalog
  app/                           # DisplayActivity, Hilt setup, BootReceiver, kiosk, diagnostics overlay
    google-services.json         # for the NEW Android app id registered in santos-1b6f6
  domain/                        # pure Kotlin/JVM: models, TemplateId, ScreenConfig parsing rules,
                                 # PlaylistScheduler, SyncPlanner, NumberChangeDetector, Clock abstraction
  data/                          # Firestore mappers + repositories, Storage downloads, Room MediaStore,
                                 # DataStore, MediaSyncWorker, RSS client
  ui/                            # theme, templates, zones, Compose previews
```

- **Firestore mapping is manual** (`DocumentSnapshot` → domain), with defaults for every optional field, rather than `toObject()`. That way one bad field degrades a zone instead of crashing the screen, unlike Flutter's `document['x']`.
- **`:domain` has no Android dependency**, so the logic that matters (scheduling, ready-set switching, chime detection, eviction planning, config defaults) runs as fast JVM tests.

### Testing

| Layer | Tooling | What |
|---|---|---|
| `:domain` | JUnit 5, kotlinx-coroutines-test, Turbine | Scheduler with a fake clock (durations, loop, ready-set switch, error skip); chime detector (first emission, cache catch-up, wrap 99→0); eviction planner; config defaults/unknown template. |
| `:data` | Robolectric + fakes; **Firebase Emulator Suite** (Firestore + Storage) in CI from M5 | Mappers against real-shaped docs (Long colours, missing fields); sync worker happy path, md5 mismatch, out of space. |
| `:ui` | Roborazzi screenshot tests (Robolectric) at 1920×1080 and 1280×720 | One golden per template × representative data; queue tile with long names. |
| Device | Manual checklist on 2–3 real devices (one Google TV, one cheap AOSP stick); optional nightly emulator job | Boot launch, offline cold start, 24 h soak (memory, decoder leaks). |

### CI

- **`build-apk.yml` (Flutter):** add `paths-ignore: ['android-tv/**', 'docs/**']` and a `pull_request` trigger. Otherwise unchanged.
- **New `android-tv.yml`:** runs on `push` to `main` and on `pull_request` when `android-tv/**` or the workflow itself changes.
  - Steps: `actions/setup-java` (17, or 21 if the AGP version requires it), `gradle/actions/setup-gradle` (cache), then `./gradlew -p android-tv lint testDebugUnitTest verifyRoborazziDebug assembleDebug`. Upload `app-debug.apk` and the test reports.
  - From M9, a tag-triggered release job: `assembleRelease` signed with a keystore from GitHub secrets, `versionCode` from `github.run_number`, APK attached to a GitHub Release.
- `google-services.json` for the new app id is committed, as the Flutter one already is. It isn't a secret, but see the rules note in Context.

## Delivery plan

Each milestone is one PR, roughly in dependency order. M3–M4 and M5 can run in parallel once M2 lands.

| # | PR | Done when |
|---|---|---|
| M0 | This design doc | Decision recorded below. |
| M1 | **Scaffold `android-tv/` + CI.** Gradle KTS, version catalog, 4 modules, Hilt, Compose for TV "hello" activity with `LEANBACK_LAUNCHER`, `android-tv.yml`, path filters on the Flutter workflow. Register `pro.joaosousa.queuedisplay.tv` in Firebase and add its `google-services.json`. | Green CI on a PR. The APK installs and launches on a TV emulator. |
| M2 | **Firebase bootstrap + queues read path.** Anonymous auth (reuse cached user), `QueueMapper` (Long ARGB, FA glyph, optional `foregroundColor`), `QueueRepository`, debug screen listing live queues. | Unit tests for the mapper. Numbers update live when changed from the Flutter app. |
| M3 | **Template framework + `queues_media` parity.** `ScreenConfig` parsing with defaults, template registry, queue/clock/ticker zones (RSS with offline cache), media zone placeholder. Screen chosen by a debug setting. | Roborazzi goldens. Side by side with Flutter `/home`, minus the video. |
| M4 | **Chime on number change.** `NumberChangeDetector` + `SoundPool`, per-screen volume, media ducking hook. | Detector tests. Changing a number in Flutter chimes on the TV once, and not on start-up or reconnect. |
| M5 | **Media sync + storage.** `storage.rules` + `firebase.json` storage entry, Room `MediaStore`, `SyncPlanner`, `MediaSyncWorker`, md5 verification, eviction. **Prerequisite:** confirm the Blaze plan. | Emulator-backed tests. A playlist's files appear on the device and stay through a reboot in airplane mode. |
| M6 | **Playlist playback + remaining templates.** `PlaylistScheduler`, ExoPlayer + Coil media zone, ready-set switching, `fullscreen_media`, `media_ticker_clock`, `queues_only`. Optional legacy `videoURL` mode. | Mixed image/video playlist loops offline. Goldens for all 4 templates. |
| M7 | **Provisioning.** `devices/{uid}` registration + pairing code screen, on-device screen picker, heartbeat/status, diagnostics overlay. | Fresh install → pick screen → renders, with no code change or console edit to `screens`. |
| M8 | **Kiosk.** Boot receiver + HOME filter, keep-screen-on, Back handling, crash relaunch, optional daily restart. `docs/android-tv/DEPLOY.md` covers the `adb` overlay grant, energy-saver settings, and the device-owner path. | Power-cycle test on a Google TV device and an AOSP stick. 24 h soak with no leaks. |
| M9 | **Release + pilot.** Signing, versioning, release workflow, update path per Open question 2. Pilot 1–2 screens beside Flutter. | Pilot runs a week. Cutover checklist signed off. |
| Later | Separate issues: **Firestore/Storage rules hardening** (per-device read scope, operator accounts), **admin UI** for screens/playlists/media upload and pairing-code entry, D-pad advancing (if wanted), removal of the Flutter display route after cutover. | — |

## Open questions

1. **Who advances numbers after cutover?** Today the display TV itself does it with D-pad left/right on `/home`. Should the native app keep that (configurable per screen), or is advancing only done by the Flutter app on another device?
2. **Device fleet and distribution.** Which models and Android versions (this decides `minSdk 26` vs 23 and the 4K/codec guidance)? Google TV or AOSP boxes? Will updates go through Play (closed track / managed Google Play), an MDM, or sideloading? Is an MDM available, which decides between D8 A/B and C?
3. **Who manages content, and with what?** Until an admin UI exists, screens, playlists and media are edited in the Firebase console and uploaded by hand, with someone copying media metadata into playlist items. Is that acceptable for the pilot? Should the admin UI be part of the Flutter app or a separate web page?
4. **Firebase plan.** Is `santos-1b6f6` on Blaze? Storage stopped working on Spark after 3 Feb 2026, and M5 depends on it.
5. **YouTube.** Do any current screens use YouTube URLs? Is it OK to drop YouTube (no offline playback, no caching allowed), or do we need an online-only "web/YouTube" item type later?
6. **Chime semantics.** Chime for every queue on the screen or only some? A different sound per queue? Spoken announcements (TTS, e.g. "Número 42, balcão A") in a later phase?
7. **Ticker.** Is one feed per screen enough, and should `rtp.pt` stay the default? Should static text messages be possible as well as RSS?
8. **Clock.** Show the date as well? (Flutter formats one but hides it.) Should the time zone and locale be fixed (`Europe/Lisbon`, `pt-PT`) or set per screen?
9. **Portrait screens.** Are any displays mounted vertically? Android TV has no portrait mode, so this would need rotation in the root composable plus portrait templates.
10. **Security timeline.** Must rules hardening ship before any device goes into a public space, given the repo is public and anonymous users can write everything today?
11. **Naming.** Is `android-tv/` and `pro.joaosousa.queuedisplay.tv` OK? What app label and launcher banner should it use?
12. **Number range.** Is 0–99 with wrap a business rule the display should assume (for example for tile width), or could numbers grow?

## Decision

<!-- Left empty until the team decides. -->
