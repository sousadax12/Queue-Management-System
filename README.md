# queue_management_system

Queue Management System

## Getting Started

This project is a starting point for a Flutter application.

## CI

Every pull request and every push to `main` runs [`.github/workflows/ci.yml`](.github/workflows/ci.yml).
Its `Detect changes` job looks at the changed paths and calls only the suites that cover them:

| Suite | Workflow | Runs when these change |
| --- | --- | --- |
| Admin portal | `admin-portal.yml` | `admin-portal/**`, `android-tv/contract/**` |
| Android TV | `android-tv.yml` | `android-tv/**`, the shared contract fixtures and schemas |
| Flutter app | `build-apk.yml` | anything outside `android-tv/`, `admin-portal/` and `docs/` |
| Firestore rules | `firestore-rules.yml` | `firestore.rules`, `firebase.json`, rules tests, the contract package, the pnpm lockfile |

A change to a suite's own workflow file, or to `ci.yml`, also runs it. Running CI by hand
(`workflow_dispatch`) runs every suite.

The last job, **`CI gate`**, always runs. It fails if any suite failed or was cancelled and
passes when the rest succeeded or were skipped. Branch protection on `main` requires only
`CI gate`, so add new suites to `ci.yml` (and to the gate's `needs`) rather than as
standalone workflows. Deploys (the admin web image to GHCR, the Coolify webhook and the
Firestore rules) still run only on `main`, and only when their secrets are set.
