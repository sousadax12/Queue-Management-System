#!/bin/sh
# Writes the container's VITE_* env vars to /env.js (window.__QMS_ENV__), which index.html
# loads before the app. Runs from the nginx image's /docker-entrypoint.d/ at start.
set -eu

out_dir=/tmp/qms-env
mkdir -p "$out_dir"
{
  printf 'window.__QMS_ENV__ = {\n'
  env | grep '^VITE_[A-Z0-9_]*=' | sort | while IFS='=' read -r key value; do
    escaped=$(printf '%s' "$value" | sed 's/\\/\\\\/g; s/"/\\"/g; s/</\\u003c/g')
    printf '  "%s": "%s",\n' "$key" "$escaped"
  done
  printf '};\n'
} >"$out_dir/env.js"

echo "40-qms-env.sh: wrote $(grep -c '^  "' "$out_dir/env.js" || true) VITE_* settings to /env.js"
