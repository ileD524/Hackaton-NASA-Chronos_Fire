# Chronos Fire - Backend

Backend Java 21 + Spring Boot de **solo lectura** sobre PostgreSQL 17
(`chronos_fire`). Implementa la API del calendario de actividad de fuego
armonizado (MODIS + VIIRS).

**Estado:** fases 0-6 completadas, más las mejoras B-01 a B-05.

- Fase 0: contrato OpenAPI de los 15 endpoints.
- Fase 1: esqueleto Spring Boot, `/api/health` y `/api/meta`.
- Fase 2: `/api/calendar/weekly` con baseline y anomalía.
- Fase 3: `/api/calendar/daily`, `heatmap`, `monthly` y `trend`.
- Fase 4: detecciones, GeoJSON, CSV de detecciones y CSV semanal.
- Fase 5: alertas NRT, GeoJSON de alertas, AOIs y validación.
- Fase 6: frontend adaptado al contrato Java; proxy Vite a `:8080`.
- B-01: `monthly` filtra celdas por sensor.
- B-02: `hotspots` y `hotspotsBySensor` en mensual y diario.
- B-03: anomalía mensual server-side con baseline y confianza.
- B-04: `hotspots` y `hotspotsBySensor` en el heatmap.
- B-05: headers de truncamiento en las exportaciones CSV.

El backend **no modifica la base**: solo ejecuta `SELECT`. No crea tablas,
columnas, índices, vistas ni datos.

## Requisitos

- Java 21 o superior.
- Maven 3.9 o superior.
- PostgreSQL 17 con la base `chronos_fire` ya cargada.

Para una demo sin dataset completo:

```powershell
docker compose up -d
.\run-app.ps1 -Demo
```

Ver `../docker/README.md`. El ETL usa `CHRONOS_DATA` y `CHRONOS_TMP`; ver
`../data/README.md`.

## Configuracion

Copiar `backend/.env.example` a un archivo local o definir las variables:

```text
SERVER_PORT=8080
DB_URL=jdbc:postgresql://127.0.0.1:5432/chronos_fire
DB_USER=postgres
DB_PASSWORD=...
DB_POOL_SIZE=10
CORS_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:4173,http://localhost:8080
```

No hay contrasena hardcodeada en `application.properties`.

## Correr solo el backend

```powershell
$env:DB_PASSWORD = 'postgres'
mvn spring-boot:run
```

## Correr backend + frontend

Desde la raíz del repo:

```powershell
Copy-Item backend\.env.example backend\.env
# editar DB_PASSWORD en backend\.env
.\run-app.ps1
```

También está la task de VS Code `app: dev`.

O compilar y ejecutar el JAR:

```powershell
mvn clean package
java -jar target/chronos-fire-backend-0.0.1-SNAPSHOT.jar
```

## Endpoints implementados

| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | `/api/health` | Estado de la app, PostgreSQL, latencia y ultima deteccion |
| GET | `/api/meta` | Sensores, fuentes, rangos, conteos, definiciones y limitaciones |
| GET | `/api/calendar/weekly` | Actividad semanal, baseline, anomalia y desglose por sensor |
| GET | `/api/calendar/daily` | Serie diaria por celda con hotspots y desglose por sensor |
| GET | `/api/calendar/heatmap` | Celdas 0,05° con hotspots por celda y sensor |
| GET | `/api/calendar/monthly` | Agregacion mensual con celdas, hotspots, baseline y anomalia |
| GET | `/api/calendar/trend` | Serie anual con baseline |
| GET | `/api/calendar/export.csv` | CSV semanal por celda y sensor, con headers de truncamiento |
| GET | `/api/detections` | Detecciones paginadas |
| GET | `/api/detections/geojson` | Detecciones como GeoJSON |
| GET | `/api/detections/export.csv` | CSV de detecciones o hotspots, con headers de truncamiento |
| GET | `/api/alerts` | Alertas NRT agrupadas por celda |
| GET | `/api/alerts/geojson` | Alertas como GeoJSON |
| GET | `/api/aois` | Catálogo de AOIs |
| GET | `/api/validation` | Eventos conocidos validados |

Ejemplos:

```text
http://127.0.0.1:8080/api/health
http://127.0.0.1:8080/api/meta
http://127.0.0.1:8080/api/calendar/weekly?bbox=-61,-34,-59.5,-32.5&year=2020&week=40&sensor=AUTO
http://127.0.0.1:8080/api/calendar/monthly?bbox=-61,-34,-59.5,-32.5&from=2020-01-01&to=2020-03-31&sensor=ALL
http://127.0.0.1:8080/api/calendar/trend?bbox=-61,-34,-59.5,-32.5&from=2019-01-01&to=2020-12-31&sensor=AUTO
http://127.0.0.1:8080/api/detections?bbox=-61,-34,-59.5,-32.5&from=2020-09-01&to=2020-09-30&sensor=MODIS&limit=10
http://127.0.0.1:8080/api/calendar/export.csv?bbox=-61,-34,-59.5,-32.5&from=2020-01-01&to=2020-01-31&sensor=ALL
http://127.0.0.1:8080/api/alerts?bbox=-61,-34,-59.5,-32.5&days=7
http://127.0.0.1:8080/api/aois
http://127.0.0.1:8080/api/validation
http://127.0.0.1:8080/swagger-ui.html
http://127.0.0.1:8080/v3/api-docs
```

### Rendimiento

- `/api/health` responde en decenas de milisegundos.
- `/api/meta` tarda unos 3-4 s la primera vez porque cuenta tablas grandes;
  luego queda cacheado 10 minutos.
- `/api/calendar/weekly` tarda menos de 1 s la primera vez para un AOI y
  queda cacheado 10 minutos.
- `daily`, `heatmap` y `trend` responden en menos de 1 s para un AOI.
- `monthly` con AOI responde en ~0,2 s; sin bbox, la primera llamada puede
  tardar ~5 s y luego queda cacheada.
- `detections` con AOI y un mes responde en ~0,3 s; GeoJSON y CSV en ~0,1 s.
- `alerts` con AOI responde en ~1,5 s la primera vez; nacional ~7,5 s.
  Queda cacheada 60 s.
- `aois` responde en milisegundos.
- `validation` tarda ~8 s la primera vez y queda cacheada 10 minutos.
- `monthly` para el Delta tarda ~0,7 s; toda la extensión puede tardar
  varios segundos. El frontend inicia en el Delta para evitar esa espera.
- `detectionsApprox` en `/api/health` sale de `pg_class.reltuples`; es una
  aproximacion, no un `count(*)`.

## Contrato

- OpenAPI: `openapi/chronosfire-api.yaml`
- Guia de adaptacion del frontend: `CONTRATO_API.md`

El contrato completo incluye:

- calendario semanal, diario, mensual, heatmap y tendencia;
- alertas NRT;
- detecciones y GeoJSON;
- exportacion CSV;
- catalogo de AOIs;
- validacion de eventos.

Los 15 endpoints estan implementados. La semantica de `hotspots`, baseline y
exportaciones esta en `CONTRATO_API.md` y `../docs/DECISIONES.md`.

## Exportaciones

- `X-Row-Limit`: tope aplicado (50.000).
- `X-Returned-Rows`: filas incluidas.
- `X-Truncated`: `true` si habia mas filas que el tope.
- Detecciones: rango de hasta 10.000 dias. Paginado y GeoJSON: 3.660 dias.
- CSV en UTF-8 con BOM.

## Tests

```powershell
mvn test
```

48 tests: controladores (`@WebMvcTest`), servicios, contrato OpenAPI y smoke de
datos. No requieren una base activa. El smoke SQL se corre aparte con `psql`:

```powershell
$env:PGPASSWORD = 'postgres'
psql -h 127.0.0.1 -U postgres -d chronos_fire -v ON_ERROR_STOP=1 `
  -f db/validacion/05_smoke_delta_2020.sql
```

## Base de datos y ETL

- Esquema base: `db/ChronosFireDB.sql`
- Correccion del calendario: `db/migraciones/fix_calendar.sql`
- Actividad por sensor: `db/migraciones/add_grid_weekly_sensor.sql`
- Rebuild del calendario: `db/rebuild_calendar.sql`
- Mascara industrial: `db/build_industrial_mask.sql`
- Validacion §6.8: `db/validacion/`
- Carga con DuckDB: `../etl/load_pg.py` (ETL, no API)
- Demo Docker: `../docker/README.md`

El ETL lee `CHRONOS_DATA` (JSON de FIRMS) y `CHRONOS_TMP` (CSV NRT de NOAA-20
y MODIS). Los defaults son `data/` y `data/tmp/`.

## Documentos

- Decisiones y definiciones: `../docs/DECISIONES.md`
- Plan de backend: `PLAN_BACKEND.md`
- Plan de armonizacion: `PLAN_ARMONIZACION.md`
- Informe de validacion: `VALIDACION_6_8.md`
- Contrato de API: `CONTRATO_API.md`
- OpenAPI: `openapi/chronosfire-api.yaml`
