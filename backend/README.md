# Chronos Fire - Backend

Backend Java 21 + Spring Boot de **solo lectura** sobre PostgreSQL 17
(`chronos_fire`). Implementa la API del calendario de actividad de fuego
armonizado (MODIS + VIIRS).

**Estado:** fases 0-5 del backend y fase 6 del frontend completadas.

- Fase 0: contrato OpenAPI de los 15 endpoints.
- Fase 1: esqueleto Spring Boot, `/api/health` y `/api/meta`.
- Fase 2: `/api/calendar/weekly` con baseline y anomalia.
- Fase 3: `/api/calendar/daily`, `heatmap`, `monthly` y `trend`.
- Fase 4: detecciones, GeoJSON, CSV de detecciones y CSV semanal.
- Fase 5: alertas NRT, GeoJSON de alertas, AOIs y validación.
- Fase 6: frontend adaptado al contrato Java; proxy Vite a `:8080`.

El backend **no modifica la base**: solo ejecuta `SELECT`. No crea tablas,
columnas, índices, vistas ni datos.

## Requisitos

- Java 21 o superior.
- Maven 3.9 o superior.
- PostgreSQL 17 con la base `chronos_fire` ya cargada.

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
| GET | `/api/calendar/daily` | Serie diaria por celda |
| GET | `/api/calendar/heatmap` | Celdas 0,05° agregadas |
| GET | `/api/calendar/monthly` | Agregacion mensual con `partial` y `dataStatus` |
| GET | `/api/calendar/trend` | Serie anual con baseline |
| GET | `/api/calendar/export.csv` | CSV semanal por celda y sensor |
| GET | `/api/detections` | Detecciones paginadas |
| GET | `/api/detections/geojson` | Detecciones como GeoJSON |
| GET | `/api/detections/export.csv` | CSV de detecciones |
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

Los endpoints de calendario, alertas, detecciones y validacion quedan para las
fases 2 a 5. La fase 1 solo expone `health` y `meta`.

## Tests

```powershell
mvn test
```

Los tests de controladores usan `@WebMvcTest` y no requieren una base activa.

## Base de datos y ETL

- Esquema base: `db/ChronosFireDB.sql`
- Correccion del calendario: `db/migraciones/fix_calendar.sql`
- Actividad por sensor: `db/migraciones/add_grid_weekly_sensor.sql`
- Rebuild del calendario: `db/rebuild_calendar.sql`
- Mascara industrial: `db/build_industrial_mask.sql`
- Validacion §6.8: `db/validacion/`
- Carga con DuckDB: `../etl/load_pg.py`

## Documentos

- Plan de backend: `PLAN_BACKEND.md`
- Plan de armonizacion: `PLAN_ARMONIZACION.md`
- Informe de validacion: `VALIDACION_6_8.md`
- Contrato de API: `CONTRATO_API.md`
- OpenAPI: `openapi/chronosfire-api.yaml`
