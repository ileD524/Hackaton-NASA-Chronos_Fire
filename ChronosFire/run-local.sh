#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
export CHRONOS_DATA_DIR="${CHRONOS_DATA_DIR:-$(realpath ../../armonizacion)}"
CHRONOS_RUNTIME="${CHRONOS_PYTHON:-$CHRONOS_DATA_DIR/.venv/bin/python}"
if [[ ! -x "$CHRONOS_RUNTIME" ]]; then
  echo 'Indicá CHRONOS_PYTHON con un Python que tenga DuckDB instalado.' >&2
  exit 1
fi
if [[ ! -d node_modules ]]; then
  npm ci
fi
npm run build
exec "$CHRONOS_RUNTIME" server/api.py --port "${PORT:-8765}"
