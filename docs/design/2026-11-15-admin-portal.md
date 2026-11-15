# Design: web admin portal

Refs #2

## Status

**Proposed (2026-11-15).** Builds on the accepted Android TV design (#1, PR #3: `docs/design/2026-11-15-android-tv-display.md` on branch `orch/design-1-android-tv-display`). That doc is called "the TV design" below. Rules hardening (#6) takes its auth and role model from this doc.

## Context

### Goal

A web portal where staff manage the signage fleet:

- sites, screens and queues
- devices, including pairing and fleet status
- template assignment per screen
- playlists
- CDN media

It is the **main writer** of the Firestore contract in the TV design's "Interface with the admin portal". It also owns the questions the TV design handed over in its open question 3: which CDN, who uploads and computes `sha256`, codecs and dimensions, and whether to transcode or reject.

### Fixed inputs

| Topic | Fixed by | What it means here |
|---|---|---|
| Backend | Issue #2, TV design | Firebase Auth + Firestore. No Firebase Storage. |
| Data model | TV design D5 | `sites/{siteId}` + `sites/{siteId}/screens/{screenId}`; top-level `devices`, `pairingCodes`, `playlists`, `media` (nullable `siteId`); `queues` top-level with a new `siteId`. |
| Media | TV design D4 and "What the TV app assumes about the CDN" | Public HTTPS URLs at content-addressed paths (`/m/<sha256>.<ext>`), immutable bytes, `Range` + `Content-Length`, correct `Content-Type`, progressive MP4. |
| Media format | TV design "Video and image format guidance" | Every item needs an H.264 High@4.1 ≤ 1080p, CFR, ≤ 10 Mbps, 2 s closed GOP, `faststart`, 8-bit SDR MP4 with AAC-LC 48 kHz audio (or none). Images are JPEG/WebP/PNG, sRGB, ≤ 3840×2160. |
| Pairing | TV design D7-A | The TV shows a 6-character code. The admin enters it in the portal, which sets `devices/{uid}.siteId`/`screenId`/`name` and deletes `pairingCodes/{code}` in one transaction. |
| Templates | TV design D6 | A fixed enum: `queues_media`, `fullscreen_media`, `media_ticker_clock`, `queues_only`. |
| Hosting preference | Brief | The user self-hosts on **Coolify**. |

### What exists today

| Concern | Today | Where |
|---|---|---|
| Firebase project | `santos-1b6f6`. `firebase.json` only deploys Firestore rules. No Hosting or Functions config. | `firebase.json`, `lib/firebase_options.dart` |
| Rules | Any signed-in user, anonymous included, can read and write every document. | `firestore.rules` |
| Auth | The Flutter app always calls `signInAnonymously()` and starts at `/setup`. An email/password `LoginScreen` exists at `/` but can't be reached. | `lib/main.dart`, `lib/pages/login_page.dart` |
| Queue writes | `update({'number': n})` with an absolute value, wrapping 0↔99. That can race between two operators. | `lib/providers/queues_provider.dart` |
| Queue reads | `document['x']` for every field, which throws if a field is missing. Portal-created queues must therefore carry every legacy field (TV invariant 6). | `lib/mappers/queue_doc_mapper.dart` |
| Web | `web/` is the stock Flutter web shell. The app isn't built or deployed for web. | `web/index.html` |
| CI | One workflow, push to `main` only: `flutter build apk --debug`. No PR checks and no path filters. | `.github/workflows/build-apk.yml` |
| Team signal | Small history (7 commits) from one author. Dart/Flutter today, Kotlin for the TV app next. No JS/TS in the repo. | `git log`, `pubspec.yaml` |

### Constraints that shape the design

1. **Spark plan.** The project looks like it is on Spark. That matters three ways:
   - **Cloud Functions need Blaze.** Anything that needs a server (presigning uploads, setting custom claims, probing media) can't be a Cloud Function without upgrading.
   - **Daily quotas.** 50k reads and 20k writes a day. A TV heartbeat every 5 minutes is about 288 writes per box per day, so about 70 boxes use the whole write quota on heartbeats alone (see [Contract feedback](#contract-feedback) C5).
   - **Hosting.** Firebase Hosting is still free on Spark.
2. **Public repo, public Firebase config.** Anyone can call Auth and Firestore with the project's API key. Every protection has to live in **rules** and in **server-held secrets**: the CDN keys and the service account.
3. **Anonymous auth must stay on**, because TVs use it. So rules must never grant anything to "any signed-in user" again.
4. **The portal is used by few people and rarely.** It doesn't need SSR, SEO or global edge hosting. It needs correct, safe writes and a usable media workflow.

## Options

Each decision lists the realistic options. The picks and reasoning are under [Recommendation](#recommendation).

### P1. Frontend stack

| | A. **Vite + React + TypeScript SPA**, Firebase JS SDK (modular) | B. **Next.js** (App Router) on a Node server | C. **Flutter web** reusing Dart code |
|---|---|---|---|
| How it works | Static bundle. Auth and Firestore run in the browser. Rules are the security boundary. A separate small backend handles media (P4). | Server components and route handlers. The media endpoints could live in the same deployable. | The same toolchain as the operator app. Firestore through `cloud_firestore` web. |
| Reuse from this repo | None directly. But the **Firestore rules tests** (`@firebase/rules-unit-testing`), the **seed script** the TV design wants, and the media backend are all most natural in TS. One language covers portal, backend, seed and rules tests. | Same as A. | `QueueModel` and `QueueDocMapper`, about 40 lines, and the mapper is the part we'd rather not copy (it throws on missing fields). The **templates are Kotlin/Compose**, so Flutter gives the preview no reuse. |
| Fit for an admin UI | Strong: tables, forms, drag-and-drop ordering, file upload with progress, many component libraries (shadcn/ui, Mantine). | Strong UI, but SSR and server auth (session cookies, Admin SDK on the server) add complexity an internal tool doesn't need. | Weak for this job. Large first load (CanvasKit), no native text selection or browser form behaviour, and hashing/probing multi-GB files in Dart web is slow. Desktop-style data tables are a known weak spot. |
| Effort | Medium. A new language for the team, but the most-trodden path for Firebase web apps. | Medium-high. | Low to start, higher once uploads, tables and preview land. |
| Risks | A third language in the repo (Dart, Kotlin, TS). | Same as A, plus server-rendering pitfalls with client-side Firebase Auth. | Fighting the framework on web-specific UX. |

Also considered: **Compose Multiplatform for web (Wasm)**, to reuse the TV's `:domain` and maybe its templates for preview. The TV templates depend on `androidx.tv` and Media3, which aren't multiplatform, and Compose for web is still the least mature target. Rejected for now. It's listed under "what would change our mind".

### P2. Hosting the portal

| | A. **Coolify**: static build served by Caddy/nginx in a container on the user's server | B. **Firebase Hosting** | C. Cloudflare Pages |
|---|---|---|---|
| How it works | Coolify builds from the repo, or pulls an image from GHCR, and serves it behind its proxy with automatic Let's Encrypt. SPA fallback to `index.html`. | `firebase deploy --only hosting`, global CDN, free SSL, preview channels per PR via a GitHub Action. | Static deploys from Git, preview URLs. |
| Cost | Server we already run. | Free on Spark for this traffic. | Free. |
| Co-location with the media backend | **Same box and same proxy** as the media backend (P4). One domain, e.g. `admin.example.com` and `admin.example.com/api`, so no CORS between portal and API. | Portal on Firebase, API elsewhere: two origins, CORS, two deploy targets. | Same as B. |
| Auth gotcha | `signInWithRedirect` from a domain other than `<project>.firebaseapp.com` breaks in browsers that block third-party storage. Use `signInWithPopup` (fine for an admin tool), or proxy `/__/auth/*` to the Firebase auth domain. | Redirect sign-in works out of the box when the custom domain is the `authDomain`. | Same as A. |
| Ops | We own uptime, TLS renewal (automatic) and backups. | Zero ops. | Zero ops. |

Either way the portal's domain goes into Firebase Auth's **authorized domains**.

### P3. CDN and storage

Scale check first. A box downloads each file **once** (TV design D4). With 20 boxes and 2 GB of new media a month, that's about **40 GB of egress a month**. At that volume every option below costs a few dollars or less, so the choice comes down to operations, upload support and failure modes, not price.

| | A. **Cloudflare R2 + Cloudflare CDN** (public bucket on a custom domain) | B. **Bunny Storage + Bunny CDN** (pull zone) | C. **S3 + CloudFront** |
|---|---|---|---|
| Price (list, 2026) | Storage $0.015/GB-month; writes $4.50/M; reads $0.36/M; **egress free**. Free tier: 10 GB-month, 1M writes, 10M reads. | Storage about $0.01/GB-month per region; CDN egress from about $0.01/GB in EU/NA; $1/month minimum. | S3 about $0.023/GB-month; CloudFront has a perpetual 1 TB/month free egress tier, then about $0.085/GB. Flat-rate plans exist too. |
| Browser upload | S3-compatible API with **presigned PUT and multipart**. CORS is configured per bucket. | S3-compatible API with presigning (added 2026-03; docs say beta while the changelog says GA in July 2026, so check). The classic Storage API needs the zone password, so it can't be used from a browser. | The reference implementation of presigned PUT/POST and multipart. |
| Immutable headers | Set `Cache-Control` per object at upload. Cloudflare caches on the custom domain. | Set on the pull zone or per object. | Per object or via a CloudFront cache policy. |
| `Range` / 206 | Yes. | Yes. | Yes. |
| Failure mode if a TV bug re-downloads in a loop | **Costs nothing** (egress is free; read ops are cheap). | Costs egress. | Costs egress after 1 TB. |
| Staging + lifecycle | Object lifecycle rules (e.g. delete `staging/` after 7 days). | Manual clean-up. | Lifecycle rules. |
| Requirements and caveats | The custom domain's DNS must be on Cloudflare. Cloudflare's free plan doesn't edge-cache objects over 512 MB, so those come straight from R2 (still free egress, just slower to first byte). `r2.dev` URLs are not for production. | The S3 API is new. Storage zones have per-zone RPS and bandwidth caps (fine at this scale). | IAM and bucket policies are the most complex of the three. Two AWS bills to watch. |
| Built-in transcoding | Cloudflare Stream exists, but it's HLS-first and priced per minute, and we can't pin our baseline profile. | Bunny Stream: same issues (HLS-first, its own encoding ladder). | MediaConvert works but is heavy and a separate product. |

Prices checked on 2026-11-15 against [R2 pricing](https://developers.cloudflare.com/r2/pricing/), [Bunny Storage S3 API](https://docs.bunny.net/storage/s3) and its [changelog](https://bunny.net/docs/storage/changelog.md), the [CloudFront free tier announcement](https://aws.amazon.com/blogs/aws/aws-free-tier-data-transfer-expansion-100-gb-from-regions-and-1-tb-from-amazon-cloudfront-per-month) and [Firebase pricing plans](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans). Bunny and CloudFront rates vary by region; re-check before committing.

### P4. Upload and processing pipeline

Who presigns, who computes `sha256`/codecs/duration/dimensions, and where ffmpeg runs.

| | A. **Small media service on Coolify** (`media-api`: Node + Firebase Admin SDK + ffmpeg/ffprobe + sharp) | B. **Cloud Functions / Cloud Run** (needs Blaze) | C. **Browser computes everything**; a thin presigner (Cloudflare Worker) only signs |
|---|---|---|---|
| Presign | API checks the caller's Firebase ID token and claims, then returns a presigned PUT/multipart for a **private staging** key. | Callable function does the same. | Worker verifies the ID token (Google's public JWKs) and presigns. |
| Metadata | **Server side, from the bytes actually stored:** stream-hash `sha256`; `ffprobe` for duration, dimensions, frame rate, bit rate, GOP, pixel format and audio; **`mp4box.js` for the exact RFC 6381 `codecs` string** (read from `avcC`/`hvcC`, e.g. `avc1.640029`), since ffprobe gives profile and level names, not that string; `sharp` for images. | Same, in the function. Cloud Run is needed for long ffmpeg jobs. | `hash-wasm` (streaming SHA-256), `mp4box.js` and `mediainfo.js` in the browser. **The server can't trust any of it**, and a mistake breaks TV invariant 1. |
| Normalise | Remux (`-c copy -movflags +faststart`) when only `faststart` is missing. **Transcode** to the baseline when codec, resolution, bit rate, GOP or frame rate are out of spec. Re-encode images: auto-orient, **strip EXIF/GPS**, fit ≤ 3840×2160, sRGB. Make a poster JPEG for the portal UI. | Same. | None; reject non-conforming files. Or ffmpeg.wasm in the browser, which is slow, memory-hungry and fragile on multi-GB files. |
| Publish | Writes the output to the public bucket at `m/<sha256>.<ext>` with `Content-Type` and `Cache-Control: public, max-age=31536000, immutable`. Skips the write if the key already exists (natural dedupe). Writes `media/{id}` with the Admin SDK. | Same. | The browser PUTs straight to `m/<sha256>.<ext>`. Rules must then let clients write `renditions`, so a manager could forge them. |
| Plan | Works on **Spark**. | Needs **Blaze** (TV design Q4 may force it anyway). | Spark + a Cloudflare Worker (free tier). |
| Ops | One container we run. Transcoding uses the Coolify host's CPU (a 1080p minute of x264 `medium` takes roughly 0.5–2 CPU-minutes). | Managed, scales to zero, per-use billing. | Least infrastructure, least integrity. |

**Transcode vs reject** isn't really a separate decision. The real choice is when transcoding arrives:

- **Reject + remux only.** Lowest effort. Staff must export correct files: we'd document an ffmpeg command or a HandBrake preset.
- **Transcode when out of spec.** Staff can upload phone videos or agency exports as-is. Costs CPU time and one more failure path.
- **Always transcode.** Predictable output, but it re-encodes files that were already fine and loses quality for nothing.

### P5. Auth, roles and rules

**Identities.**

| Who | Sign-in | Proposed token claims |
|---|---|---|
| Portal admin | Google sign-in, or email/password with verified email | `role: "admin"` |
| Site manager | Same | `role: "manager"`, `sites: ["porto-centro", …]` |
| Flutter operator | Email/password through the existing `LoginScreen`. That's a Flutter change, tracked in #6. | `role: "operator"`, `sites: [...]` |
| TV device | Anonymous (TV design) | None (options below) |

**Where roles live.**

| | A. **Custom claims** set by the Admin SDK | B. **Role docs** (`users/{uid}`) read by rules with `get()` | C. Claims, plus a `users/{uid}` mirror for the UI |
|---|---|---|---|
| Rules cost | Free: claims are in the token. | One extra billed read per rule evaluation that checks a role. | Free. |
| Change takes effect | When the ID token refreshes (≤ 1 h, or immediately after `getIdToken(true)`). Revoking needs `revokeRefreshTokens` plus claim removal. | Immediately. | Same as A. |
| Who can grant | Only code with the service account: the media service or a local script. | Any admin through rules. No backend needed. | Media service writes both. |
| Listing users in the portal | Not possible from the client (no Admin SDK). | Yes. | Yes, from the mirror. |
| Size limit | 1000 bytes of claims (fine for a few dozen site ids). | None. | 1000 bytes. |

**How TV reads are scoped.**

| | A. **Rules `get()` the device doc** (as the TV design sketches): `get(/devices/$(request.auth.uid)).data.siteId == s` | B. **`siteId` claim on the anonymous TV user**, set by the media service when the box is claimed. The TV refreshes its token when it sees `siteId` appear. |
|---|---|---|
| Works without a backend | Yes; claiming stays a client-side transaction. | No; claiming goes through the media service. |
| Cost | One extra billed read per evaluation of a TV read. With long-lived listeners that mostly happens at listen time and on changes, so it's modest but not zero. | None. |
| Moving a box between sites | Instant. | Needs a token refresh on the TV. |
| Contract impact | None. | The TV must call `getIdToken(true)` when `siteId`/`screenId` change. Small, but a contract change. |

**Shared hardening, whatever the options above:**

- Treat `request.auth.token.firebase.sign_in_provider == "anonymous"` as "device only". Never grant a staff role to it, even if a claim were present.
- Turn on **email enumeration protection**. Leave self-registration on (needed for Google sign-in), but **an account without a role can read and write nothing**.
- **App Check** with reCAPTCHA Enterprise for the portal is possible. It **can't be enforced for TVs**: generic AOSP boxes have no Play Integrity. So it's a "nice to have" for portal paths only.

### P6. Screen preview

How faithful must "preview this screen" be?

| | A. **Schematic live preview** | B. **High-fidelity web re-implementation** of the templates | C. **Real screenshot from a box** |
|---|---|---|---|
| How it works | A TS layout table per template (the same weights as the Kotlin templates: queues 1 : media 3, ticker at the bottom, clock at the top right). Zones render live data: queue tiles with colours, names, numbers and Font Awesome icons; the media zone shows the current playlist's posters cycling with their durations; the ticker shows the latest feed titles; the clock uses the site's time zone. 16:9 frame with `safeAreaPercent`. | The same zones drawn to match the TV's typography and sizes, with the baseline rendition actually playing in a `<video>`. | The portal sets `devices/{uid}.screenshotRequestedAt`. The TV captures the screen and uploads it through a presigned URL from the media service. The portal shows the image. |
| Answers | "Is this screen configured right?": which queues, which playlist, which template, ticker on or off. | "Will it look right?" | "What is this box showing right now?" |
| Cost | Low: about one zone component per zone type plus four layout entries. | High, and it **drifts** from the Compose templates unless we compare against the TV's Roborazzi goldens. | Needs a TV feature (contract addition), the media service, and an online box. Can't preview before assignment. |

The template picker can also show each template's **Roborazzi golden** (the TV design renders one per template at 1920×1080) as a static thumbnail, at no runtime cost.

### P7. Repo placement and CI/deploy

| | A. **`admin-portal/` as a small pnpm workspace** (`apps/web`, `apps/media-api`, `packages/contract`) | B. Two top-level dirs (`admin-portal/`, `media-api/`) | C. A separate repo |
|---|---|---|---|
| Shared contract types | One `packages/contract` (zod schemas + TS types + JSON fixtures) used by web, media service, seed and rules tests. | Copy, or a relative import across dirs. | Published package or copy. |
| CI | One path-filtered `admin-portal.yml`. | Two workflows. | Separate. |
| Visibility of the Firestore contract change | Same PR as the TV app and the rules. | Same. | Split across repos, so contract drift is easier to miss. |

**Deploy options**, if hosting is Coolify:

1. Coolify builds from the repo with **watch paths** (Coolify's monorepo support) and Dockerfiles in each app.
2. **GitHub Actions builds images and pushes them to GHCR**, then calls Coolify's deploy webhook.

Option 2 keeps image builds off the server that also transcodes, and leaves the exact image digest in CI.

## Recommendation

### Summary of picks

| Decision | Pick | What would change our mind |
|---|---|---|
| P1 Stack | **A: Vite + React + TypeScript SPA**, Firebase JS SDK (modular), TanStack Router, React Hook Form + zod, shadcn/ui (or Mantine), `@dnd-kit` for playlist ordering, Uppy (`@uppy/aws-s3`) for resumable multipart uploads. Firestore through thin typed hooks over `onSnapshot`, with no extra data library. | The team strongly prefers one language over the right tool → Flutter web for the CRUD screens is workable, but the media service, seed script and rules tests stay TS anyway. Compose Multiplatform web matures and the TV templates move off `androidx.tv` → reconsider, to get a pixel-exact preview. |
| P2 Hosting | **A: Coolify**, portal and media service behind one domain (`admin.<domain>` and `admin.<domain>/api`). Sign-in with `signInWithPopup`. | We move to Blaze and put the media service on Cloud Run (P4-B) → Firebase Hosting with a rewrite to Cloud Run gives the same single origin with zero ops. |
| P3 CDN | **A: Cloudflare R2**: a private `staging` bucket with a 7-day lifecycle, and a public `media` bucket on `cdn.<domain>` (Cloudflare-proxied) holding `m/<sha256>.<ext>` and `p/<sha256>.jpg` posters. | The domain's DNS can't move to Cloudflare → **Bunny** (pull zone in front of Bunny Storage, or of R2) once its S3 API is confirmed GA. An AWS account and team already exist → S3 + CloudFront within the 1 TB free tier. |
| P4 Pipeline | **A: `media-api` on Coolify.** Server-side probe, hash and publish. **Ships in two steps:** first validate + remux `faststart` + **reject** out-of-spec video with a clear reason and a ready-made ffmpeg command; then **transcode out-of-spec video** to the baseline in a background worker. Images are always re-encoded (cheap; removes EXIF/GPS). HEVC 2160p second renditions only on request, later. | The Coolify host is too small for x264, or uploads are large and frequent → move the worker (not the API) to Cloud Run jobs on Blaze. |
| P5 Roles | **C: custom claims** (`role`, `sites`) granted by the media service, mirrored to `users/{uid}`, plus a one-off `grant-admin` CLI script for the first admin. **TV scoping: A (rules `get()` of the device doc)**, so the contract stays as accepted. | Firestore read costs from rules `get()` show up as material on the fleet → move TVs to B (`siteId` claim) as a contract change (see C7). |
| P6 Preview | **A: schematic live preview**, plus golden thumbnails in the template picker. **C (real screenshot)** as a later milestone. Not B. | Staff keep being surprised by how screens look → do C first, not B. |
| P7 Repo/CI | **A: `admin-portal/` pnpm workspace.** `admin-portal.yml` path-filtered like `android-tv.yml`; images to GHCR; Coolify webhook deploy on `main`. | — |
| Queue advancing | **Yes, eventually**: an **operator console** route in the portal (phone-friendly, `operator` claim scoped to sites) that advances numbers in a **transaction** (wrap 99→0 rules out `increment`). Then the Flutter app can retire completely. Not before the TV pilot. | TV open question 1 decides the TV keeps D-pad advancing and staff are happy with it → leave the Flutter operator as is. |

### Why these picks

- **Stack.** The portal is forms, tables, uploads and drag-and-drop: the web's home ground. Everything the portal needs besides the UI is naturally TypeScript: the media service, the rules unit tests (`@firebase/rules-unit-testing`), the seed script the TV design asks for, and contract fixtures. So TS is one new language that buys four things. Flutter web would save about 40 lines of mapper code and nothing on preview (the templates are Kotlin).
- **Hosting and backend together.** Staying on Spark means the trusted code (presigning, claims, probing) can't be a Cloud Function. The user already runs Coolify, so the media service goes there, and putting the static portal next to it gives one origin, one deploy story and no CORS. Firebase Hosting's advantages (zero ops, preview channels) matter less for a low-traffic internal tool.
- **R2.** All three CDNs are cheap at our scale. R2 wins on **failure mode**: egress is free, so a TV prefetch bug or a cache wipe across the fleet can't create a bill. It also has the most mature S3-compatible presigning of the cheap options, plus lifecycle rules for staging.
- **Server-side metadata.** `sha256` and `codecs` are what the TV trusts for cache keys and decoder choice (TV invariants 1 and 3). They must come from the bytes we publish, not from the browser. Only the backend can write `renditions` to `media/{id}`, so a compromised or careless portal user can't forge them in the library.
- **Reject first, transcode second.** Rejecting with a precise reason ("HEVC 10-bit; needs H.264 8-bit ≤ 1080p") plus a copy-paste ffmpeg command unblocks the TV pilot quickly. Transcoding is the right end state for non-technical staff, and it slots in as a worker step without changing the API or the contract.

### Architecture

```text
 Browser (admin-portal/apps/web, static, on Coolify)
   │  Firebase Auth (popup) ─► ID token with {role, sites}
   │  Firestore SDK ─────────► sites, screens, queues, playlists, devices, pairingCodes, media (read)
   │  fetch /api/* (Bearer ID token)
   ▼
 media-api (admin-portal/apps/media-api, Node + Admin SDK + ffmpeg + sharp, on Coolify)
   ├─ POST /api/uploads                 → checks role/site, creates media/{id} {status:"uploading"}, presigns staging/{id}
   │       ▲ browser PUTs bytes straight to R2 staging (multipart for large files)
   ├─ POST /api/uploads/{id}/complete   → HEAD staging object, status:"processing", enqueue
   │       worker: stream → temp file → sha256 + ffprobe + mp4box.js codecs / sharp
   │              → conforming? remux faststart : (step 1) reject / (step 2) transcode
   │              → PUT media bucket m/<sha256>.<ext> (immutable), poster p/<sha256>.jpg
   │              → media/{id} {renditions[], durationMs, status:"ready" | "failed", error}
   ├─ POST /api/users/{uid}/role         → set custom claims, mirror users/{uid}, revoke tokens on downgrade
   └─ (later) POST /api/devices/{uid}/screenshot-upload → presign for TV screenshots
 Cloudflare R2: bucket staging (private, CORS for the portal origin, 7-day lifecycle)
                bucket media (public via cdn.<domain>, Cloudflare cache)
 TV boxes ─► cdn.<domain>/m/<sha256>.mp4   (read only, never through media-api)
```

- **Job durability without a queue.** The worker's queue is Firestore itself. On start-up, and every few minutes, the service picks up `media` docs with `status in ["uploaded", "processing"]` and a stale `processingAt`, so a restart never loses a job. One worker process, concurrency 1, is enough at this scale.
- **Limits.** Configurable max upload size (e.g. 4 GB) and max duration. The presigned URL is bound to the key and content length and expires in 15 minutes.
- **Secrets.** R2 keys and the Firebase service account JSON live only in Coolify env vars. Nothing secret is in the web bundle.

### Media processing spec

**Video:**

- **Conforms** when all of these hold: H.264 High, level ≤ 4.1, ≤ 1920×1080, CFR at 24–60 fps, ≤ 10 Mbps, yuv420p 8-bit, BT.709/SDR, AAC-LC (or no audio).
- **Conforms but no `faststart`:** remux.
- **Otherwise:** step 1 rejects; step 2 transcodes with `libx264 -profile:v high -level 4.1 -pix_fmt yuv420p -vf scale=…:force_original_aspect_ratio=decrease -r <25|30> -g <2×fps> -keyint_min <2×fps> -sc_threshold 0 -maxrate 8M -bufsize 16M -crf 20 -c:a aac -ar 48000 -ac 2 -b:a 160k -movflags +faststart`.
- **Interlaced** sources are deinterlaced.
- **HDR/10-bit** sources are tone-mapped or, in step 1, rejected.

**Image:**

- `sharp().rotate()`, then resize to fit 3840×2160 (no upscaling), convert to sRGB and strip metadata.
- Encode JPEG q85, or keep PNG when it has alpha or text-like content (the user picks "graphic").
- Animated GIF/WebP is rejected (TV design: not supported).

**Recorded on `media/{id}`:**

- In the contract: `renditions[]` (`url`, `sha256`, `sizeBytes`, `mimeType`, `codecs`, `width`, `height`) and `durationMs`.
- Portal-only additions (see C1): `status`, `error`, `source` (original name, size, probe summary) and `posterUrl`.

### Rules shape (input to #6)

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    function authed()   { return request.auth != null; }
    function anon()     { return authed() && request.auth.token.firebase.sign_in_provider == 'anonymous'; }
    function role()     { return authed() && !anon() ? request.auth.token.get('role', '') : ''; }
    function mySites()  { return request.auth.token.get('sites', []); }
    function isAdmin()  { return role() == 'admin'; }
    function manages(s) { return isAdmin() || (role() == 'manager' && s in mySites()); }
    function operates(s){ return manages(s) || (role() == 'operator' && s in mySites()); }
    function deviceSite() { return get(/databases/$(db)/documents/devices/$(request.auth.uid)).data.get('siteId', null); }
    function isDeviceOf(s){ return anon() && s != null && deviceSite() == s; }
    function onlyKeys(ks) { return request.resource.data.diff(resource.data).affectedKeys().hasOnly(ks); }

    match /sites/{s} {
      allow read: if operates(s) || isDeviceOf(s);
      allow create, delete: if isAdmin();
      allow update: if manages(s);
      match /screens/{screen} {
        allow read: if operates(s) || isDeviceOf(s);
        allow write: if manages(s);
      }
    }
    match /queues/{q} {
      allow read:   if operates(resource.data.siteId) || isDeviceOf(resource.data.siteId);
      allow create: if manages(request.resource.data.siteId);           // contract checks the legacy fields too
      allow delete: if manages(resource.data.siteId);
      allow update: if (manages(resource.data.siteId) && request.resource.data.siteId == resource.data.siteId)
                    || (operates(resource.data.siteId) && onlyKeys(['number'])
                        && request.resource.data.number is int
                        && request.resource.data.number >= 0 && request.resource.data.number <= 99);
    }
    function editsScope(s) { return (s == null && isAdmin()) || (s != null && manages(s)); }   // null = shared
    match /playlists/{p} {
      allow read:   if (resource.data.siteId == null && (role() != '' || (anon() && deviceSite() != null)))
                    || manages(resource.data.siteId) || isDeviceOf(resource.data.siteId);
      allow create: if editsScope(request.resource.data.siteId);
      allow update: if editsScope(resource.data.siteId) && request.resource.data.siteId == resource.data.siteId;
      allow delete: if editsScope(resource.data.siteId);
    }
    match /media/{m} {                       // TVs never read media; renditions are written by media-api only
      allow read:   if role() != '' && (resource.data.siteId == null || manages(resource.data.siteId));
      allow update: if manages(resource.data.siteId) && onlyKeys(['name', 'tags', 'updatedAt', 'updatedBy']);
      allow create, delete: if false;        // media-api (Admin SDK) only
    }
    match /devices/{uid} {
      allow get:    if request.auth.uid == uid
                   || (role() in ['admin', 'manager'] && (resource.data.get('siteId', null) == null || manages(resource.data.siteId)));
      allow list:   if isAdmin() || (role() == 'manager' && resource.data.siteId in mySites());
      allow create: if anon() && request.auth.uid == uid
                   && !request.resource.data.keys().hasAny(['siteId', 'screenId', 'name', 'identifyUntil']);
      allow update: if (request.auth.uid == uid && onlyKeys(['status', 'info', 'lastSeenAt']))
                   || (onlyKeys(['siteId', 'screenId', 'name', 'identifyUntil', 'updatedAt', 'updatedBy'])
                       && (resource.data.get('siteId', null) == null || manages(resource.data.siteId))
                       && (request.resource.data.siteId == null || manages(request.resource.data.siteId)));
      allow delete: if isAdmin();
    }
    match /pairingCodes/{code} {
      allow create: if anon() && request.resource.data.uid == request.auth.uid
                    && request.resource.data.createdAt == request.time
                    && request.resource.data.keys().hasOnly(['uid', 'createdAt', 'expiresAt']);
      allow get:    if role() in ['admin', 'manager'];
      allow delete: if role() in ['admin', 'manager'] || resource.data.uid == request.auth.uid;
      allow list, update: if false;
    }
    match /users/{uid} { allow read: if isAdmin() || request.auth.uid == uid; allow write: if false; }
  }
}
```

This is a **shape**, not the final file. #6 owns the details, the field validation and the emulator tests (`@firebase/rules-unit-testing` in `admin-portal/packages/contract`, or a root `firestore-tests/`). Moving a playlist between sites is a delete plus a create. Two things the rules can't enforce and the portal must:

- screens reference queues and playlists of their own site;
- playlist item renditions are copied from a `ready` media doc.

The TV re-verifies `sha256` either way.

### Portal features (MVP scope)

| Area | Features |
|---|---|
| Sites | List and create (admin). Edit name, time zone, locale, ticker feeds, chime, maintenance hour and default screen. |
| Queues | CRUD per site. New queues get **every legacy field** with neutral defaults (TV invariant 6). One-off "backfill `siteId`" tool for existing queues. |
| Screens | CRUD per site. Template picker showing only templates supported by the box's app version, with golden thumbnails. Zone forms (queue multi-select from the same site, playlist, volume, ticker on/off, clock pattern, `maxVideoHeight`, `safeAreaPercent`). **Schematic live preview** beside the form. |
| Devices | Fleet table: name, site, screen, online/offline (from `lastSeenAt`), app version, model, decoders, cache and free space, pending items, last error, now playing. **Claim by code** dialog (code → site → screen → name). **Identify** (sets `identifyUntil` = now + 60 s). Rename, move, unassign, **replace device** (move the assignment from an old uid to a new one). |
| Media | Upload (drag and drop, resumable, progress), processing status, rejection reasons, poster grid, filters by site, type and tag. "Used in" list. Soft-delete. |
| Playlists | Create per site or shared (admin). Drag to reorder. Image durations. Totals for duration and **download size vs the smallest free space** among the boxes using the playlist. Publish is **one atomic doc write** (TV invariant 4). |
| Users | Admin only: list (from `users/{uid}`), grant or revoke `admin`/`manager`/`operator` and sites. |

**Cost hygiene in the portal.** The fleet table only keeps `onSnapshot` listeners while the tab is visible (Page Visibility API) and detaches after 10 minutes idle. Otherwise one forgotten tab reads every heartbeat of every box all day (about 288 reads per box per day).

## Contract feedback

All proposals are **additive** unless marked otherwise, so they respect TV invariant 9. Each needs a nod from the TV side before M2 freezes mappers.

| # | Proposal | Why | TV impact |
|---|---|---|---|
| C1 | **`media/{id}` gains portal-only fields**: `status` (`uploading`/`processing`/`ready`/`failed`/`deleted`), `error`, `source`, `posterUrl`, `processingAt`. | The pipeline needs a job state the portal can listen to. | None (the TV never reads `media`). |
| C2 | **`playlists/{id}` gains `mediaIds: [..]`** (portal-maintained, mirrors `items[].mediaId`) and optional `publishedBy`. | Firestore can't query inside arrays of maps. We need `array-contains` to (a) fan out when a rendition is **added** to a media item (TV invariant 2), (b) show "used in" and (c) block deleting media still in use. | None; the TV ignores unknown fields. |
| C3 | **CDN objects are never deleted while referenced, plus a grace period.** Deleting media is a soft delete. A GC job removes `m/<sha256>.*` only when no playlist **and no media doc** references that `sha256`, and only after 30 days. Several media docs can share a `sha256` because of dedupe. | A TV may still be finishing an old playlist version, or be offline for days with that version pending (TV design: the old version keeps playing until the new one is fully cached). | None; it makes TV invariant 1 safe in practice. |
| C4 | **Pairing expiry uses the server's `createdAt`, not the TV's `expiresAt`.** Rules require `createdAt == request.time`, and the portal treats a code as expired at `createdAt + 15 min`. `expiresAt` stays as written by the TV, for a **Firestore TTL policy** that cleans up old codes. Also, the rules sketch's `!exists(...)` is redundant: `allow create` only applies when the doc doesn't exist, and a `set()` on an existing code is an update, which is denied. That already gives the collision signal. | Cheap boxes often boot with a wrong clock until NTP syncs, so a client-side `expiresAt` can be hours off. | The TV writes `createdAt: serverTimestamp()` (it already plans to) and must handle `PERMISSION_DENIED` on create as "collision, retry". |
| C5 | **Write budget.** (a) The TV writes `status` only at heartbeat cadence and on sync-complete or error. **Never per played item** (`status.nowPlaying` updates are coalesced into the heartbeat). (b) The heartbeat interval becomes a contract value: `info.heartbeatSec`, default **600**, which the portal uses for "offline after 2.5 × heartbeat". | At 5 minutes plus per-item status writes, a Spark project hits 20k writes/day at fewer than 70 boxes. A 10 s image playlist alone would be about 8,600 writes per box per day. | Changes the TV design's "heartbeat every 5 min" default to 10 min unless we move to Blaze (TV Q4). |
| C6 | **Template support is machine-readable.** The TV repo publishes `android-tv/contract/templates.json`: `[{ "id": "queues_media", "minVersionCode": 1, "zones": ["queues", "media", "ticker", "clock"] }, …]`. `devices/{uid}.info` gains **`appVersionCode` (int)**. | TV invariant 5 asks the portal to offer only supported templates. Parsing `"0.6.0 (21)"` is fragile, and the zone list drives which forms the portal shows. | One JSON file and one extra `info` field. |
| C7 | *(Contingency, not proposed now.)* **TV scoping by claim.** If rules `get()` reads become material, the media service sets `siteId` as a custom claim on the TV's anonymous user when it's claimed. The TV calls `getIdToken(true)` when `devices/{uid}.siteId` changes. | Removes a billed read from every TV rule evaluation. | A small TV change (token refresh). The pairing transaction moves server-side. |
| C8 | **Shared fixtures.** Golden JSON docs for every collection live in `admin-portal/packages/contract/fixtures/` and are validated by the zod schemas. TV mapper tests (M2) read the same files, and **one seed script** (TS, in the same package) serves the emulator, the dev project and both apps' tests, instead of `android-tv/tools/seed.ts`. | Both sides test against the same examples, so contract drift fails CI on whichever side changed. | M2 points its mapper tests at those fixtures. |
| C9 | **Audit fields.** Portal writes add `updatedAt` (already in the contract) and `updatedBy` (uid) on sites, screens, queues, playlists and devices. | Who changed what, with no extra collection. | None. |

## Delivery plan

Each milestone is one PR. Milestones marked ⇢ unblock TV milestones from the TV design.

| # | PR | Done when | Unblocks |
|---|---|---|---|
| A0 | This design doc | Decision recorded. Contract feedback C1–C9 accepted or rejected by the TV side. | Lets TV **M2** freeze mappers |
| A1 | **Contract package + seed.** `admin-portal/` pnpm workspace skeleton; `packages/contract` with zod schemas and TS types for every collection in the TV contract (plus C1, C2, C5, C6, C9); golden fixtures; a seed script (emulator + dev project); a one-off `queues.siteId` backfill script with dry-run. CI: typecheck + schema tests. | Fixtures validate. Seeding the emulator produces a site, 2 screens, 2 queues, a playlist with CDN test files, and an unclaimed device. | ⇢ **M2** (contract frozen, fixtures and seed to test against) |
| A2 | **Portal scaffold + auth + deploy.** Vite/React app, Google + email sign-in (popup), route guard on `role`, `grant-admin` CLI script, Coolify app (static), `admin-portal.yml` (lint, typecheck, Vitest, build, GHCR image, Coolify webhook on `main`), Flutter workflow gets `paths-ignore: admin-portal/**`. | A signed-in admin sees an empty dashboard at `admin.<domain>`. A user without a role sees "no access". | — |
| A3 | **Rules v1** (this is #6, or lands with it). Claims-based rules from [Rules shape](#rules-shape-input-to-6), emulator tests for each role (admin, manager, operator, device, anonymous-without-device, signed-in-without-role), `firebase deploy --only firestore:rules` from CI. The **Flutter operator login** change ships in the same release window. | Every allowed and denied path has a test. The Flutter operator can still advance numbers with an `operator` account. | ⇢ **M7** (the device-path rules M7 drafts become the real ones) |
| A4 | **Devices + pairing.** Claim-by-code dialog (transaction: read code, check `createdAt` age, set the device fields, delete the code), fleet table with online status, identify, rename, move, unassign, replace. | A box running TV M7 shows a code. Claiming it in the portal switches the box within 5 s. Expired and unknown codes give clear errors. | ⇢ **M7** (replaces the seed script's "claim" step) |
| A5 | **Sites, queues, screens + preview.** CRUD, template picker filtered by `appVersionCode`, zone forms, schematic live preview, queue backfill tool in the UI. | A screen built in the portal renders on a TV with the same template and queues as the preview. | Real config for TV **M3/M6** testing |
| A6 | **Media service + R2, step 1.** R2 buckets, custom domain, CORS, lifecycle; `media-api` container (Admin SDK, ffprobe, mp4box.js, sharp); presign, complete, worker; validate, remux `faststart`, **reject** out-of-spec video with a reason; images re-encoded; posters; media library UI. | A conforming MP4 and a phone JPEG upload and reach `ready` with correct `sha256` (checked against `shasum -a 256` of the CDN object), `codecs`, dimensions and duration. A HEVC 10-bit file is rejected with a readable reason. | Real CDN media for TV **M5** |
| A7 | **Playlists.** Editor with drag-and-drop, durations, size and free-space check, shared vs site playlists, atomic publish, `mediaIds` mirror, "used in". | A playlist published in the portal plays on a TV offline after prefetch (with TV M6). | ⇢ TV **M6** end to end, **M9** pilot |
| A8 | **Transcoding, step 2.** Worker transcodes out-of-spec video to the baseline; progress in `media.status`; concurrency 1; time limit. | An iPhone HEVC clip and a 4K ProRes export both end `ready` as conforming H.264 1080p. | Self-service media for the pilot |
| A9 | **Users and site managers.** Users page; grant and revoke roles and sites; `users/{uid}` mirror; token revocation on downgrade. | A manager can edit only their sites, verified against the A3 rules tests. | — |
| A10 | **Clean-up and GC.** Soft-delete media; CDN GC job (C3); stale `pairingCodes` TTL; staging lifecycle check. | Unreferenced objects older than 30 days are removed; referenced ones never are. | — |
| Later | **Operator console** (queue advancing in a transaction, if TV Q1 lets the Flutter app retire). **Real screenshots** from boxes (P6-C). HEVC 2160p renditions. Day-parting schedules (TV Q9). Bulk enrolment UI for TV **M10** (site enrolment tokens). | — | **M10** |

**Order and parallelism.** A1 first. A2 and A3 can then run in parallel. A4 and A5 need A2. A6 needs A2 and the CDN decision. A7 needs A6. **A3 must land before the portal holds real data and before any box goes into a public space** (TV Q11).

## Open questions

1. **Firebase plan.** Stay on Spark, which drives the Coolify media service, the 10-minute heartbeat (C5) and portal listener hygiene? Or move to Blaze now, which makes Cloud Run/Functions and Firebase Hosting the simpler path? This is also TV Q4.
2. **Domain and DNS.** Which domain hosts `admin.` and `cdn.`, and can its DNS be on Cloudflare (needed for R2 custom domains)? If not, Bunny becomes the pick.
3. **Coolify capacity.** CPU and RAM of the Coolify host, and is it fine to transcode there? A rough answer decides whether A8 runs on Coolify or on Cloud Run.
4. **Who are the users?** How many admins? Are **per-site managers** needed in the MVP, or can A9 wait? Google accounts, email/password, or both?
5. **Uploads.** Typical and maximum file sizes and lengths. Do staff export correct files (so reject-only is enough for a while), or upload raw phone and agency videos (so A8 moves up)?
6. **Media scope.** Are most playlists **shared org-wide** or per site? This decides how prominent shared playlists and the admin-only rules are.
7. **Preview fidelity.** Is a schematic preview (right layout, real queues and posters, not pixel-exact) acceptable? Or do you want "what is this box showing" screenshots (P6-C) soon?
8. **Queue advancing.** Should the portal eventually replace the Flutter operator (operator console + `operator` role), so the Flutter app can be retired? This depends on TV Q1.
9. **Contract feedback.** Does the TV side accept C1–C9, especially **C5** (heartbeat 10 min, no per-item status writes) and **C8** (shared fixtures and seed in `admin-portal/packages/contract`)?
10. **Tenancy.** Same as TV Q10. If several organisations will share this, `orgId` and an `org` claim should go in before A1 freezes the contract.
11. **Third language.** Is the team happy to add TypeScript next to Dart and Kotlin? If not, which part (portal UI vs media service vs tests) should change?

## Decision

<!-- Left empty until a decision is made. -->
