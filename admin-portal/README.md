# Admin portal workspace

This pnpm workspace holds the web admin portal for the Queue Management System. The design is in
[`docs/design/2026-11-15-admin-portal.md`](../docs/design/2026-11-15-admin-portal.md).

| Package                                        | What                                                                                                                                                 |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| [`packages/contract`](packages/contract)       | The Firestore contract shared with the Android TV app: zod schemas and TS types, golden fixtures, the seed and claim script, and the queue backfill. |
| [`apps/web`](apps/web)                         | The portal: Vite + React + TypeScript, TanStack Router, Mantine, Firebase Auth (Google + email/password), role-gated routes.                         |
| [`packages/rules-tests`](packages/rules-tests) | Emulator tests for the root [`firestore.rules`](../firestore.rules): every role, TV devices, and cross-org denial (A3).                              |
| `apps/media-api`                               | Later milestone (A4).                                                                                                                                |

## Setup

You need:

- Node 24 (LTS). The version is in `.nvmrc`.
- pnpm 11.25.0. The version is pinned in `packageManager`, and `corepack enable` picks it up.
- Java 21, for the Firestore emulator.

```bash
cd admin-portal
corepack enable
pnpm install
```

`firebase-tools` is a dev dependency, so `pnpm exec firebase` works without a global install.

## Checks

```bash
pnpm typecheck        # tsc in every package
pnpm lint             # ESLint + Prettier
pnpm test             # unit tests: schemas, fixtures, encoding, claim logic, templates.json
pnpm test:emulator    # seed, claim, backfill, grant and web sign-in against throwaway emulators
pnpm check            # all of the above
```

- `pnpm test:emulator` runs `firebase emulators:exec --only auth,firestore --project demo-qms`.
  It starts the emulator, runs the tests and stops it.
- `demo-` project ids only exist in the emulator, so these tests can't touch a real project.
- The first run downloads the emulator jar into `~/.cache/firebase/emulators`.

## Run the portal

Against the emulators (no Firebase project needed):

```bash
pnpm --filter @qms/web dev:emulator   # Auth + Firestore emulators, Vite on http://localhost:5173
```

Create an account on the sign-in page. The emulator prints the verification "email" in its log:
open the link, then press **I've verified**. You land on **No access**. Grant yourself the role
from another terminal, then press **Check again**:

```bash
pnpm grant-platform-admin --email you@example.com
```

Against a real project, copy [`apps/web/.env.example`](apps/web/.env.example) to
`apps/web/.env.local`, fill in the web config, and run `pnpm --filter @qms/web dev`. Enable
**Google** and **Email/Password** sign-in in the Firebase console, and add the portal's domain to
**Authentication > Settings > Authorized domains**.

## Deploy

CI ([`admin-portal.yml`](../.github/workflows/admin-portal.yml)) builds
[`apps/web/Dockerfile`](apps/web/Dockerfile) on every run: the static build behind unprivileged
nginx on port 8080, with SPA fallback and `/healthz`. On `main` it pushes
`ghcr.io/<owner>/qms-admin-web:latest` (and `:sha-<commit>`), then calls the Coolify deploy webhook.

The image is the same for every environment. It reads its config at start from the `VITE_*`
env vars listed in [`.env.example`](apps/web/.env.example), so set them in the Coolify app.

One-time setup:

1. In Coolify, create an application of type **Docker image** from
   `ghcr.io/<owner>/qms-admin-web:latest`, port 8080, health check `/healthz`, and the `VITE_*`
   env vars. Give it registry credentials for GHCR, or make the package public.
2. In GitHub, add the repository secrets `COOLIFY_WEBHOOK` (the app's deploy webhook URL) and
   `COOLIFY_TOKEN` (a Coolify API token with deploy rights). Without `COOLIFY_WEBHOOK` the deploy
   job skips, so CI stays green.

## Seed the emulator

Start an emulator in one terminal. It keeps running until you stop it:

```bash
pnpm exec firebase emulators:start --only firestore --project demo-qms
```

Then seed it from another terminal:

```bash
pnpm seed
```

The seed writes every fixture in [`packages/contract/fixtures`](packages/contract/fixtures):

- 2 orgs, each with a site, 2 screens, 2 queues and a playlist of CDN test media;
- devices: two claimed boxes and **one unclaimed box** with pairing code `K7M3QX`;
- media library docs and users.

Seeding uses `set()`, so it is idempotent: run it again to reset those docs. By default it
targets `FIRESTORE_EMULATOR_HOST`, or `127.0.0.1:8080` if that's unset, with project `demo-qms`.

To seed a **dev** Firebase project instead, pass its id explicitly:

```bash
GOOGLE_APPLICATION_CREDENTIALS=~/keys/dev-sa.json pnpm seed --dev-project <dev-project-id>
```

The script refuses three things:

- the production project (`santos-1b6f6`);
- `demo-` ids;
- a real project while `FIRESTORE_EMULATOR_HOST` is set.

## Claim a box

This runs the same transaction the portal's claim dialog will run (A5):

1. read `pairingCodes/{code}`;
2. reject the code if its server `createdAt` is more than 15 minutes old;
3. check that the site and the screen exist;
4. set `orgId`, `siteId`, `screenId`, `name`, `updatedAt` and `updatedBy` on `devices/{uid}`;
5. delete the code.

The org comes from the site. A box that is already claimed can move within its org, but not to
another org.

```bash
pnpm seed claim K7M3QX site_porto_3fKq scr_lobby_left_Q2mz --name "Box 1 (entrance)"
```

- `--name` defaults to the box's current name, or `TV <code>` if it has none.
- `--by <uid>` sets `updatedBy` (default `script:seed`).
- `--dev-project <id>` works as it does for the seed.

A real TV (TV milestone M7) shows its own code, so claim that code instead of `K7M3QX`. To make a
fresh code for the seeded unclaimed box, for example after the seeded one has expired:

```bash
pnpm seed pairing-code                 # random code for tvUnclaimed01uidCCCCCCCCCC
pnpm seed pairing-code --uid <uid> --code ABC234
```

Errors exit with code 1 and a reason: `code_not_found`, `code_expired`, `site_not_found`,
`screen_not_found`, `device_not_found`, `device_in_other_org` or `invalid_document`.

## Backfill `orgId` + `siteId` on existing queues

Flutter-era `queues` docs have no `orgId` or `siteId`. TVs filter queues by `siteId`, so those
docs must be backfilled before TVs go live (TV invariant 6, C10).

The script only sets `orgId`, `siteId`, `updatedAt` and `updatedBy`. It never touches `number`
or the other legacy fields, and it never overwrites a different existing value: that case is a
conflict, and the script exits with code 1.

It is a **dry run by default**:

```bash
pnpm backfill:queues --org <orgId> --site <siteId>                       # emulator, dry run
pnpm backfill:queues --org <orgId> --site <siteId> --project <id>        # real project, dry run
pnpm backfill:queues --org <orgId> --site <siteId> --project <id> --apply
```

Options:

- `--queue <id>`, repeatable, limits the run to those queues.
- `--allow-missing-site` applies even if `sites/<siteId>` doesn't exist yet. The site still has
  to be created before TVs use it.
- `--by <uid>` sets `updatedBy`.
- `--production` is required on top of `--project santos-1b6f6`.

The report also lists legacy fields that each queue is missing, which make the Flutter mapper throw.

## Grant the first platform admin

Roles are custom claims (`role`, `org`, `sites`), mirrored into `users/{uid}`. Until `media-api`
(A4) manages roles, this one-off script makes an existing user a platform admin. The user must
have signed in to the portal once, so their Auth account exists.

```bash
pnpm grant-platform-admin --email you@example.com                     # Auth + Firestore emulators
FIREBASE_SERVICE_ACCOUNT="$(cat ~/keys/sa.json)" \
  pnpm grant-platform-admin --email you@example.com --project <id>   # a real project
```

- `--uid <uid>` instead of `--email` looks the user up by uid.
- Credentials: the service account JSON in `FIREBASE_SERVICE_ACCOUNT`, or a key file path in
  `GOOGLE_APPLICATION_CREDENTIALS`.
- The production project (`santos-1b6f6`) also needs `--production`.
- The script keeps the user's other claims and is idempotent. The user picks up the role on
  their next token refresh: **Check again** on the portal's "no access" page.

## Contract rules of thumb

- [`packages/contract/fixtures/README.md`](packages/contract/fixtures/README.md) defines the JSON
  encoding for timestamps, `serverTimestamp()` and int vs double. The TV's fixture loader mirrors it.
- The template list the portal offers comes from
  [`android-tv/contract/templates.json`](../android-tv/contract/templates.json) (C6). The TV app
  owns that file, and `pnpm test` validates it.
- Schemas are strict for portal-owned docs (an unknown key is an error) and loose for what the TV
  reports (`devices.info`, `devices.status`) and for reading legacy `queues`.
- `checkReferences()` checks what the rules can't: screens use queues and playlists of their own
  site and org, devices point at screens of their site, and ids are unique across orgs.
