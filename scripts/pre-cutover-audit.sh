#!/usr/bin/env bash
#
# Pre-cutover audit: lists the production `queues` docs that will look or behave
# differently once a box moves from the Flutter operator app to the native one.
# READ-ONLY: it reads the `queues` collection once and never writes.
#
# Usage:
#   scripts/pre-cutover-audit.sh --project <firebase-project-id> [--json]
#   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 scripts/pre-cutover-audit.sh [--json]
#
# Options:
#   --project <id>  The Firebase project to read. Required unless
#                   FIRESTORE_EMULATOR_HOST is set (then it reads the emulator).
#   --json          Machine-readable output instead of the table.
#   -h, --help      Show this help.
#
# Credentials (yours; nothing is stored or committed), one of:
#   gcloud auth application-default login
#   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
# A read-only role such as roles/datastore.viewer is enough.
#
# Per queue it reports:
#   - text colour: foregroundColor missing, not white (Flutter always paints
#     white, native paints the field), or low contrast against backgroundColor
#   - videoURL class: empty, youtube, mp4, hls-dash, http, other (or rejected)
#   - missing orgId/siteId (the native app cannot see such a queue)
#   - fields the native app would silently replace with its defaults
#
# Exit code: 0 nothing to do, 1 something needs attention, 2 usage or runtime
# error. How to read the result: docs/operator/DEPLOY.md, section 3.
#
# Needs Node 24 and the admin-portal workspace installed once:
#   cd admin-portal && corepack enable && pnpm install
# The audit itself lives in admin-portal/packages/contract (it reuses that
# package's firebase-admin and queue contract):
#   scripts/pre-cutover-audit.ts, src/audit/, src/admin/audit.ts

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/admin-portal/packages/contract"
TSX="$PKG/node_modules/.bin/tsx"

for arg in "$@"; do
  if [[ "$arg" == "-h" || "$arg" == "--help" ]]; then
    # Print the header comment above, without the shebang and the "# " prefix.
    awk 'NR > 2 && /^#/ { sub(/^# ?/, ""); print; next } NR > 2 { exit }' "${BASH_SOURCE[0]}"
    exit 0
  fi
done

if ! command -v node >/dev/null 2>&1; then
  echo "node is not installed. Install Node 24 (see admin-portal/.nvmrc)." >&2
  exit 2
fi
if [[ ! -x "$TSX" ]]; then
  echo "Dependencies are missing. Run once: cd admin-portal && corepack enable && pnpm install" >&2
  exit 2
fi

# Stay in the caller's directory, so a relative GOOGLE_APPLICATION_CREDENTIALS keeps working.
exec "$TSX" "$PKG/scripts/pre-cutover-audit.ts" "$@"
