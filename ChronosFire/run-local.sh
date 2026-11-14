#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
if [[ -z "${CHRONOS_DATA_DIR:-}" ]]; then
  export CHRONOS_DATA_DIR="$PWD/.data/armonizacion"
  CHRONOS_DOWNLOAD=1
fi
CHRONOS_RUNTIME="${CHRONOS_PYTHON:-$PWD/.venv/bin/python}"
if [[ ! -x "$CHRONOS_RUNTIME" ]]; then
  echo 'Indicá CHRONOS_PYTHON con un Python que tenga DuckDB instalado.' >&2
  exit 1
fi
if [[ "${CHRONOS_DOWNLOAD:-}" == 1 ]]; then
  "$CHRONOS_RUNTIME" server/fetch_data.py
fi
if [[ ! -d node_modules ]]; then
  npm ci
fi
npm run build
exec "$CHRONOS_RUNTIME" server/api.py --port "${PORT:-8765}"
