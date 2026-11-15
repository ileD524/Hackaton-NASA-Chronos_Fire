#!/usr/bin/env bash
# Frontend dev server. El backend Java debe estar corriendo en :8080.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
if [[ ! -d node_modules ]]; then
  npm ci
fi
echo "Backend Java esperado en http://127.0.0.1:8080"
exec npm run dev
