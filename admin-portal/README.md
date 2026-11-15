# Admin portal workspace

This pnpm workspace holds the web admin portal for the Queue Management System. The design is in
[`docs/design/2026-11-15-admin-portal.md`](../docs/design/2026-11-15-admin-portal.md).

| Package                                        | What                                                                                                                                                                             |
| ---------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [`packages/contract`](packages/contract)       | The Firestore contract shared with the Android TV app: zod schemas and TS types, golden fixtures, the seed and claim script, and the queue backfill.                             |
| [`packages/rules-tests`](packages/rules-tests) | Emulator tests for the root [`firestore.rules`](../firestore.rules): every role, TV devices, and cross-org denial (A3). Run with `pnpm --filter @qms/rules-tests test:emulator`. |
| `apps/web`, `apps/media-api`                   | Later milestones (A2, A4).                                                                                                                                                       |

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
pnpm test:emulator    # seed, claim and backfill against a throwaway Firestore emulator
pnpm check            # all of the above
```

- `pnpm test:emulator` runs `firebase emulators:exec --only firestore --project demo-qms`.
  It starts the emulator, runs the tests and stops it.
- `demo-` project ids only exist in the emulator, so these tests can't touch a real project.
- The first run downloads the emulator jar into `~/.cache/firebase/emulators`.

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
