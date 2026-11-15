# Contract fixtures

Golden Firestore documents for every collection in the contract between the admin portal and
the Android TV app (`docs/design/2026-11-15-android-tv-display.md`, "Interface with the admin
portal", plus C1–C10 from `docs/design/2026-11-15-admin-portal.md`). They are:

- validated by the zod schemas in `../src` (`pnpm test`);
- written by the seed script into the Firestore emulator (`pnpm seed`);
- read by the TV's mapper tests (TV milestone M2), through a Kotlin loader that mirrors
  `../src/fixtures.ts` and the encoding below.

A change here is a contract change: the portal CI (`admin-portal.yml`) and the TV CI
(`android-tv.yml`) both run when these files change.

## Files

One file per collection, plus the legacy queues:

| File                 | Collection                    | Notes                                                             |
| -------------------- | ----------------------------- | ----------------------------------------------------------------- |
| `orgs.json`          | `orgs/{orgId}`                | Portal-only (C10).                                                |
| `sites.json`         | `sites/{siteId}`              |                                                                   |
| `screens.json`       | `sites/{siteId}/screens/{id}` | One screen per template.                                          |
| `queues.json`        | `queues/{queueId}`            | As the portal writes them: every legacy field + `orgId`/`siteId`. |
| `queues.legacy.json` | `queues/{queueId}`            | Flutter-era docs before the backfill. Valid for readers only.     |
| `playlists.json`     | `playlists/{playlistId}`      | One org-wide (`siteId: null`), one site playlist.                 |
| `media.json`         | `media/{mediaId}`             | Portal-only. `ready`, `processing` and `failed` (C1).             |
| `devices.json`       | `devices/{uid}`               | Claimed with a screen, claimed on the default screen, unclaimed.  |
| `pairingCodes.json`  | `pairingCodes/{code}`         | The unclaimed box's code, `K7M3QX`.                               |
| `users.json`         | `users/{uid}`                 | Portal-only. Platform admin, two org admins, one operator.        |

Each file is a JSON object keyed by the **full document path**:

```json
{
  "sites/site_porto_3fKq/screens/scr_lobby_left_Q2mz": { "orgId": "org_lojas_7Hq2", "...": "..." }
}
```

## Encoding

JSON cannot tell an integer from a double, a timestamp from a string, or a `serverTimestamp()`
placeholder from a value. Every fixture, the schemas, the fixture loader, the seed script and
the TV's loader use this one encoding. The schemas validate the **encoded** form, so a doc
that passes the schema is ready to seed.

### Timestamps

```json
{ "__ts": "2026-11-15T09:58:12.123456Z" }
```

- A Firestore `Timestamp`.
- The value is ISO-8601 in **UTC with a `Z` suffix**, with **0 to 6** fraction digits.
  Firestore stores microseconds, so a 7th digit could not survive a round trip.
- Offsets (`+01:00`), date-only values and plain strings are rejected.
- Kotlin: `Timestamp(Instant.parse(value))`.

### Server timestamps

```json
{ "__sentinel": "serverTimestamp" }
```

- The `serverTimestamp()` placeholder of a **write**. Fixtures use it where the real writer does:
  `updatedAt`, `devices.lastSeenAt`, `pairingCodes.createdAt`, `orgs.createdAt`.
- The seed script writes it as `FieldValue.serverTimestamp()`, so the server fills in the time.
  This is what makes the seeded pairing code `K7M3QX` claimable for 15 minutes after seeding.
- A loader that simulates a **read** (TV mapper tests) replaces it with one fixed "now" for the
  whole load (`resolveSentinels(doc, now)` in `../src/encoding.ts`).
- Fields that accept it on write accept a `{"__ts"}` on read (`writeTimestampSchema`).

### Numbers

- A JSON number written **without a `.` or exponent is an integer** (a `Long` on Android).
- Any other number is a **double**.
- Integer-valued doubles such as `1.0`, `2.5e1` or `-0.0` are **rejected** by the loader. JavaScript
  parses `1.0` as `1`, and the portal's JS SDK then stores it as an integer, so the two sides
  would disagree. Write `1` instead.
- Readers must accept either kind for any numeric field (TV invariant 11). The fixtures do both
  on purpose: for example `sites.chime.volume` is `1` in one site and `0.6` in the other, and
  `screens.zones.media.volume` is `0.3`, `0` and `1`.
- ARGB colours are unsigned 32-bit values, for example `4282339765`, so they are **above
  `Int.MAX_VALUE`** and arrive as `Long`. `Color(value.toInt())` keeps the bit pattern.

### Reserved keys

Map keys starting with `__` are reserved for the two shapes above. Anything else, such as a typo
like `{"__tz": "..."}`, an extra key next to `__ts`, or another sentinel, fails to load. Real
documents never use `__` keys.

## The data set

There are **two orgs**, so isolation is testable. Ids are globally unique and carry a readable
prefix (C10).

| Org                                  | Site                                  | Screens (template)                                                                          | Queues                                | Playlist                       | Boxes                                                           |
| ------------------------------------ | ------------------------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------- | ------------------------------ | --------------------------------------------------------------- |
| `org_lojas_7Hq2` "Lojas Exemplo"     | `site_porto_3fKq` "Loja Porto Centro" | `scr_lobby_left_Q2mz` (`queues_media`), `scr_lobby_right_Vb8n` (`fullscreen_media`)         | `q_balcao_a_P4tc`, `q_balcao_b_Lr6w`  | `pl_promo_nov_Zx81` (org-wide) | `tvPortoLeft01uidAAAAAAAAAA` on the left screen                 |
| `org_clinica_K9xP` "Clínica Exemplo" | `site_lisboa_Wd2e` "Clínica Lisboa"   | `scr_waiting_Hn5r` (`queues_only`, all queues), `scr_reception_Tj3u` (`media_ticker_clock`) | `q_consultas_Ma9v`, `q_analises_Ce7y` | `pl_saude_Rk42` (site)         | `tvLisboa01uidBBBBBBBBBBBBB`, `screenId: null` (default screen) |

There is also one **unclaimed** box, `tvUnclaimed01uidCCCCCCCCCC`, with pairing code `K7M3QX`.

### Test media

There is no R2 CDN until portal A7, so the playlists point at public stand-ins. Their `sha256`,
size, `codecs` and dimensions were measured from the served bytes on 2026-11-15. The TV
verifies `sha256` after download, so if a host ever changes the bytes the TV will reject the
file. In that case, re-measure the file and update the fixture.

| Media                    | URL                                                                                                          | Notes                                                                            |
| ------------------------ | ------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------- |
| Big Buck Bunny 10 s      | `https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_1MB.mp4`                  | H.264 High 4.2, 1920×1080, 60 fps, no audio, faststart                           |
| Jellyfish 10 s           | `https://test-videos.co.uk/vids/jellyfish/mp4/h264/1080/Jellyfish_1080_10s_1MB.mp4`                          | H.264 High 4.2, 1920×1080, 29.97 fps, no audio, faststart                        |
| Fronalpstock (both orgs) | `https://upload.wikimedia.org/wikipedia/commons/thumb/3/3f/Fronalpstock_big.jpg/1920px-Fronalpstock_big.jpg` | JPEG 1920×863. One object shared by two orgs, which is the cross-org dedupe case |

To re-measure, run `curl -sL <url> | shasum -a 256` and
`ffprobe -show_entries stream=profile,level,width,height <url>`.

## Changing the contract

- Fields are **additive-only** (TV invariant 9).
- To add a field, add it to the schema, the fixtures and the TV doc in the same PR.
- A TV-read field must not be renamed or removed before an app release that no longer reads it.
