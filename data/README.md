# Datos FIRMS - Chronos Fire

Esta carpeta contiene los archivos de entrada del ETL y los `Readme.txt` de cada
descarga. Los JSON/CSV completos **no se versionan**: `data/**` está en
`.gitignore` excepto este README y los `Readme.txt`.

## Demo rápida (sin descargar datos)

La demo reproducible usa un dump acotado y real en
`docker/demo/chronos_fire_demo.sql.gz`:

```powershell
docker compose up -d
.\run-app.ps1 -Demo
```

No hace falta descargar FIRMS para la demo. Ver `docker/README.md`.

## Dataset completo

Para reconstruir la base completa se necesitan las descargas de FIRMS:

- Descarga manual: <https://firms.modaps.eosdis.nasa.gov/download/>
- API de área: <https://firms.modaps.eosdis.nasa.gov/api/area/>
- Productos: MODIS C6.1, VIIRS SNPP, VIIRS NOAA-20 y VIIRS NOAA-21, en
  variante SP (archivo) y NRT.

Rutas que usa `etl/load_pg.py`:

```text
CHRONOS_DATA/
  DL_FIRE_SV-C2_848443/
    fire_archive_SV-C2_848443.json
    fire_nrt_SV-C2_848443.json
  DL_FIRE_J2V-C2_848419/
    fire_archive_J2V-C2_848419.json
    fire_nrt_J2V-C2_848419.json
  DL_FIRE_M-C61_848444/
    fire_archive_M-C61_848444.json
    fire_nrt_M-C61_848444.json

CHRONOS_TMP/
  fire_archive_J1V-C2_848256.csv
  fire_nrt_J1V-C2_848256.csv
  fire_nrt_M-C61_848249.csv
```

Los CSV de `CHRONOS_TMP` completan NOAA-20 y MODIS NRT (el JSON de NOAA-20
solo cubre una parte de 2026). Si faltan, el ETL avisa `FALTA` y continúa con
el resto. La carpeta `DL_FIRE_J1V-C2_848426/` del repo es una muestra, no la
fuente que usa el ETL.

## Variables de entorno

```powershell
$env:CHRONOS_DATA = "C:\ruta\a\data"
$env:CHRONOS_TMP  = "C:\ruta\a\data\tmp"
$env:CHRONOS_PG   = "host=127.0.0.1 port=5432 dbname=chronos_fire user=postgres"
```

`CHRONOS_DATA` y `CHRONOS_TMP` tienen defaults relativos al repo (`data/` y
`data/tmp/`). `CHRONOS_PG` tiene default local.

## Carga

```powershell
python etl/load_pg.py --truncate
python etl/load_pg.py --only SV_ARCHIVE
python etl/load_pg.py --aggregates
```

Después de cargar:

```powershell
psql -h 127.0.0.1 -U postgres -d chronos_fire -v ON_ERROR_STOP=1 `
  -f backend\db\migraciones\fix_calendar.sql
psql -h 127.0.0.1 -U postgres -d chronos_fire -v ON_ERROR_STOP=1 `
  -f backend\db\migraciones\add_grid_weekly_sensor.sql
psql -h 127.0.0.1 -U postgres -d chronos_fire -v ON_ERROR_STOP=1 `
  -f backend\db\rebuild_calendar.sql
psql -h 127.0.0.1 -U postgres -d chronos_fire -v ON_ERROR_STOP=1 `
  -f backend\db\build_industrial_mask.sql
```

La secuencia exacta y el contrato están en `backend/README.md`,
`backend/CONTRATO_API.md` y `backend/PLAN_ARMONIZACION.md`.

## Checksums de las muestras locales

Los JSON de muestra no están en Git, pero si se conservan las descargas
originales se pueden verificar con SHA256:

| Archivo | Bytes | SHA256 |
|---|---:|---|
| `DL_FIRE_J1V-C2_848426/fire_archive_J1V-C2_848426.json` | 9.733.948 | `461581E2AA797DCD9D989D2EEBC44692790ADBF7ECA79D37165A8A211276C763` |
| `DL_FIRE_J1V-C2_848426/fire_nrt_J1V-C2_848426.json` | 18.788.052 | `DC82C9D42F17C4506655E7E1D50D3961356F016CAEDF7E72FEB8378AAE546B33` |
| `DL_FIRE_J2V-C2_848419/fire_archive_J2V-C2_848419.json` | 207.184.259 | `1FA76C0400DA0586050D37DFD79C9578162D53F05BE971C3554FC5F097BE2390` |
| `DL_FIRE_J2V-C2_848419/fire_nrt_J2V-C2_848419.json` | 17.859.136 | `1F7013AD6BCBE5D1B3C005233064346CF07D7572F0E47DDCFD3EBD330E753237` |
| `DL_FIRE_M-C61_848444/fire_archive_M-C61_848444.json` | 718.870.378 | `07B1CC132331AA8FA5EDE79D9BE68294456D0425454466E47A32752F709F6F01` |
| `DL_FIRE_M-C61_848444/fire_nrt_M-C61_848444.json` | 4.479.168 | `CCCC89441F0ACED068581F86014F6CF725BE8556BF90B0F895D608B670A86C7D` |
| `DL_FIRE_SV-C2_848443/fire_archive_SV-C2_848443.json` | 1.228.154.847 | `52FBC3EA69E6460C153132FC75023BC861C4A225AD397D542F1F1DDF3FD9085F` |
| `DL_FIRE_SV-C2_848443/fire_nrt_SV-C2_848443.json` | 16.764.451 | `A040031C4126E1CC63C04B660DDFC75D9645C4D4E09096AFA166C6E1B1891B87` |

El dump de demo tiene SHA256
`2DF40E5F9D8E794C819D189982B35D29EF636AC6AB2F77CA4190CF4FDD4A7D5B`.

## Nota

Los JSON completos pesan más de 2 GB. No se deben commitear. Para compartir el
proyecto, usar el dump de `docker/demo/` o almacenamiento externo con los
checksums de arriba.
