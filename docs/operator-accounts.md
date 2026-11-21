# Operator accounts

The operator app (the Flutter app today, the native `:operator` app from
[the native operator design](design/2026-11-21-native-operator-app.md)) signs in with an email
and password. Under `firestore.rules` v1 an account only works if:

- its email is verified (`emailVerified: true`), and
- it has the custom claims `{role, org, sites}`: `role` is `operator` or `manager`, `org` is
  the org id, and `sites` lists the sites whose queues it may see and advance.

Until the portal manages accounts (media-api, A4), the `grant-operator` CLI creates them. It
lives in [`admin-portal/packages/contract`](../admin-portal/packages/contract), beside
`grant-platform-admin`, and uses the Admin SDK.

## Before you start: backfill the queues

The operator app lists queues with `orgId == <org>` and `siteId in <sites>`. Flutter-era queue
docs have neither field, so the rules hide them and the operator sees an empty list. Backfill
them first, once per site (see the
[backfill section of the portal README](../admin-portal/README.md#backfill-orgid--siteid-on-existing-queues)):

```bash
cd admin-portal
pnpm backfill:queues --org <orgId> --site <siteId> --project <id>            # dry run
pnpm backfill:queues --org <orgId> --site <siteId> --project <id> --apply
```

The org (`orgs/<orgId>`) and its sites (`sites/<siteId>`) must also exist. `grant-operator`
refuses an org or site it can't find.

## One account per site

The default is **one operator account per site** (design question Q5): the boxes at a site share
it. Give it an address you control for that site, such as `porto@<your-domain>`. One account per
person also works, and the CLI doesn't care which you choose: run it once per account.

A `manager` account works like an operator in the app (it advances the queues of its sites).
The portal will give managers more later. Give a manager several sites with repeated `--site`.

## Create an account

From `admin-portal/`:

```bash
# Auth + Firestore emulators (the default)
pnpm grant-operator --email porto@example.com --org <orgId> --site <siteId>

# A real project: check first, then run it
FIREBASE_SERVICE_ACCOUNT="$(cat ~/keys/sa.json)" \
  pnpm grant-operator --email porto@example.com --org <orgId> --site <siteId> --project <id> --dry-run
FIREBASE_SERVICE_ACCOUNT="$(cat ~/keys/sa.json)" \
  pnpm grant-operator --email porto@example.com --org <orgId> --site <siteId> --project <id>
```

It creates the user if it doesn't exist, sets `emailVerified: true`, sets the claims, and writes
the `users/{uid}` mirror the portal reads. By default it generates a 24-character password and
prints it **once**: pass it to whoever sets up the boxes over a private channel. To choose the
password yourself, add `--prompt-password` (it asks twice without echoing; when stdin is not a
terminal it reads the first line).

| Option                       | What it does                                                                      |
| ---------------------------- | --------------------------------------------------------------------------------- |
| `--email <email>`            | Required. The account's sign-in email.                                            |
| `--org <orgId>`              | Required. Must exist.                                                             |
| `--site <siteId>`            | Required, repeatable or comma-separated. Every site must belong to `--org`.       |
| `--role operator \| manager` | Default `operator`.                                                               |
| `--name <display name>`      | Display name, for example `Balcão Porto`.                                         |
| `--prompt-password`          | Ask for the password of a new account instead of generating one.                  |
| `--dry-run`                  | Check the org, the sites and the account, print what would happen, write nothing. |
| `--project <id>`             | A real project. Without it the CLI targets the emulators.                         |
| `--production`               | Also required for the production project (`santos-1b6f6`).                        |
| `--by <who>`                 | `updatedBy` on the mirror. Default `script:grant-operator`.                       |

Credentials for a real project: the service account JSON in `FIREBASE_SERVICE_ACCOUNT`, or a key
file path in `GOOGLE_APPLICATION_CREDENTIALS`. The emulators need none.

## What it refuses

- An org that doesn't exist, a site that doesn't exist, or a site of another org.
- An empty site list, or more than 30 sites (Firestore caps the operator's `siteId in` query at
  30 values).
- An existing account that is a `platformAdmin` or `orgAdmin` (it never demotes an admin), or an
  operator or manager of another org (it never moves an account between orgs). Change those by
  hand in the Firebase console.
- The production project without `--project santos-1b6f6 --production`, and a real project while
  the emulator variables (`FIRESTORE_EMULATOR_HOST`, `FIREBASE_AUTH_EMULATOR_HOST`) are set.

## Change an existing account

Run it again with the new sites (or role). The role and the site list are **replaced**, not
merged, and other custom claims are kept. The password is never changed: reset it in the Firebase
console (**Authentication > Users**). Running it on an existing account also marks its email as
verified, because the rules ignore unverified accounts.

A box that is already signed in picks up new claims on its next ID-token refresh (at most an
hour), or right away when the operator signs out and in again.

## Tests

- `admin-portal/packages/contract/test/emulator/grant-operator.emulator.test.ts`: the CLI and
  its checks, on the Auth and Firestore emulators.
- `admin-portal/packages/rules-tests/test/operator-account.test.ts`: an account the CLI creates
  signs in through the web SDK and runs the operator queue query against the real
  `firestore.rules`. It can advance its own site's queues and is denied another site's or
  org's.
