#!/usr/bin/env python3
"""Download the pinned public DSv2 snapshot, validating before replacing files."""
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
from urllib.error import URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
BASE_URL = 'https://pub-b7bfbf4d16184b77a4bfe898adb15963.r2.dev/dsv2/v1/'
MANIFEST = json.loads((ROOT / 'server/data-manifest.json').read_text())
DEST = Path(os.environ.get('CHRONOS_DATA_DIR', ROOT / '.data' / 'armonizacion')).expanduser().resolve()


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def main():
    for name, info in MANIFEST.items():
        target = DEST / name
        if target.is_file() and target.stat().st_size == info['size'] and digest(target) == info['sha256']:
            print(f'Verificado: {name}', flush=True)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        print(f'Descargando {name} ({info["size"] / 1048576:.1f} MiB)...', flush=True)
        tmp = None
        try:
            with tempfile.NamedTemporaryFile(dir=target.parent, prefix='.chronos-', delete=False) as out:
                tmp = Path(out.name)
                h = hashlib.sha256()
                request = Request(BASE_URL + name, headers={'User-Agent': 'Mozilla/5.0 ChronosFire/1.0'})
                with urlopen(request, timeout=90) as response:
                    for chunk in iter(lambda: response.read(1024 * 1024), b''):
                        h.update(chunk)
                        out.write(chunk)
            if tmp.stat().st_size != info['size'] or h.hexdigest() != info['sha256']:
                raise ValueError(f'Integridad incorrecta: {name}')
            os.replace(tmp, target)
        finally:
            if tmp is not None:
                tmp.unlink(missing_ok=True)
    print(f'Datos listos en {DEST}', flush=True)


if __name__ == '__main__':
    try:
        main()
    except (OSError, URLError, ValueError) as exc:
        print(f'No se pudieron preparar los datos: {exc}', file=sys.stderr)
        sys.exit(1)
