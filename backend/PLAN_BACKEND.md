# Plan de implementación — Backend Chronos Fire

**Proyecto:** Hackaton NASA Chronos Fire  
**Base de datos:** PostgreSQL 17, `chronos_fire`  
**Stack:** Java 21+, Spring Boot 3.x, JDBC (`NamedParameterJdbcTemplate`)  
**Estado:** backend fases 0-5 y frontend fase 6 completados. Contrato OpenAPI, calendario, detecciones, GeoJSON, CSV, alertas, AOIs y validación. Ver `CONTRATO_API.md`.

---

## 1. Objetivo

Construir una API de **solo lectura** sobre la base `chronos_fire` para:

- Consultar el calendario histórico de actividad de fuego por AOI.
- Comparar la actividad observada contra la climatología.
- Detectar períodos críticos y condiciones inusuales.
- Exponer alertas tempranas basadas en NRT, incluidas en el MVP.
- Aplicar la política SP/NRT: SP si está disponible; NRT mientras no lo esté.
- Devolver detecciones y celdas en formato JSON y GeoJSON para el frontend.

El backend **no carga datos**: la carga sigue siendo responsabilidad del ETL con DuckDB (`etl/load_pg.py`).

---

## 2. Estado de partida

| Componente | Estado |
|---|---|
| Base `chronos_fire` | Creada en PostgreSQL 17 (5432) |
| Esquema `ChronosFireDB.sql` | Aplicado |
| `detections` | Cargada casi en su totalidad (~7,08 M de filas); verificar cobertura |
| `grid_daily` | Construida (~2,1 M de filas) |
| `grid_weekly` | Construida (~1,58 M de filas) |
| `grid_weekly_sensor` | Construida (~2,27 M de filas) |
| `calendar_weekly` | Corregida (~3,72 M de filas, 53 semanas/celda) |
| `coverage_daily` | Esquema creado; población pendiente |
| `v_detections_canonical` | Creada en PostgreSQL (política SP/NRT) |
| `industrial_cells` | Creada (885 celdas, type=2) |
| `v_detections_analysis` | Creada (canónica + máscara industrial) |
| Backend Java | Fases 0-5: contrato, calendario, detecciones, GeoJSON, CSV, alertas, AOIs y validación |
| `application.properties` | Existe y apunta a `chronos_fire` |
| ETL DuckDB | `etl/load_pg.py` |

### Modelo de datos disponible

| Tabla / vista | Grano | Uso |
|---|---|---|
| `detections` | Una fila por sensor + píxel + instante | Detalle, filtros, GeoJSON |
| `grid_daily` | Celda 0,05° × día | Calendario diario, heatmap |
| `grid_weekly` | Celda × isoyear × semana | Actividad observada |
| `grid_weekly_sensor` | Celda × isoyear × semana × sensor | Baseline SNPP-only y detalle por sensor |
| `calendar_weekly` | Celda × semana ISO (53) | Climatología corregida |
| `coverage_daily` | Región/sensor/fecha | Cobertura (pendiente de datos) |
| `ingest_runs` | Ejecución de carga | Auditoría (no se expone en el MVP) |
| `v_latest_nrt` | Detecciones NRT de 7 días | Alertas |
| `v_vegetation_detections` | Detecciones de vegetación | Análisis |
| `v_detections_canonical` | SP si existe; NRT si no | Detalle y alertas del MVP |
| `v_detections_analysis` | Canónica + `industrial_mask` | Detalle y alertas del MVP |

---

## 3. Prerrequisitos y verificación

Antes de escribir Java, confirmar en pgAdmin:

```sql
-- Cobertura por sensor y fuente
SELECT sensor, source, count(*) AS n,
       min(acq_date) AS desde, max(acq_date) AS hasta
FROM detections
GROUP BY 1, 2
ORDER BY 1, 2;

-- Agregados
SELECT count(*) FROM grid_daily;
SELECT count(*) FROM calendar_weekly;

-- Muestras
SELECT * FROM grid_daily ORDER BY day DESC LIMIT 5;
SELECT * FROM calendar_weekly LIMIT 5;
```

### Interpretación

- Si `grid_daily` o `calendar_weekly` están vacíos, primero hay que reconstruir los agregados.
- Si falta una fuente (por ejemplo, NOAA-20 archive), el backend puede funcionar, pero los resultados estarán incompletos.
- Si hay solapamiento SP/NRT, definirlo antes de automatizar: ver sección 8.

### Política SP/NRT del MVP

Regla adoptada:

> Para cada **sensor + fecha**, si existe al menos una detección **SP**, se usa **SP**.  
> Si no existe SP para ese sensor/fecha, se usa **NRT** hasta que el archive SP esté disponible.

Esto se implementa con una vista canónica:

```sql
CREATE OR REPLACE VIEW v_detections_canonical AS
SELECT d.*
FROM detections d
WHERE d.source = 'SP'
   OR NOT EXISTS (
       SELECT 1
       FROM detections s
       WHERE s.sensor  = d.sensor
         AND s.source  = 'SP'
         AND s.acq_date = d.acq_date
   );
```

Uso:

- `/api/detections` consulta `v_detections_analysis` por defecto.
- `/api/alerts` consulta `v_detections_analysis` y filtra `source = 'NRT'`: así muestra solo lo que todavía no fue reemplazado por SP y excluye focos industriales.
- `grid_daily` y `calendar_weekly` se reconstruyen desde `v_detections_analysis` cuando SP empiece a solaparse con NRT. Hoy los rangos son disjuntos, así que los agregados actuales son válidos.

**Caveat:** la política es por sensor/fecha, no por píxel. Si el SP cubre solo parte de un día, se oculta todo el NRT de ese sensor/día. Es una simplificación aceptable para el MVP; se documenta en la API.

### Volúmenes y performance

| Tabla | Filas aproximadas | Uso |
|---|---:|---|
| `detections` | ~7.080.000 | Detalle y filtros |
| `grid_daily` | ~2.100.000 | Calendario diario, heatmap |
| `calendar_weekly` | ~780.000 | Climatología y anomalías |

Con estos volúmenes:

- Las consultas de calendario por AOI/año deben resolverse con los índices existentes.
- Conviene revisar `EXPLAIN ANALYZE` de las tres consultas del calendario.
- Cachear `weekly` por `(bbox, year)` si el frontend repite consultas.
- Limitar siempre el `heatmap` y el GeoJSON.
- La vista canónica usa `NOT EXISTS`; verificar `EXPLAIN ANALYZE`. Si resulta lenta, materializarla o agregar un índice `(sensor, source, acq_date)`.

### Rol de solo lectura

Crear un rol para el backend:

```sql
CREATE ROLE chronos_reader LOGIN PASSWORD 'una_clave';
GRANT CONNECT ON DATABASE chronos_fire TO chronos_reader;
GRANT USAGE ON SCHEMA public TO chronos_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO chronos_reader;
```

Configurar en el entorno:

```text
DB_URL=jdbc:postgresql://127.0.0.1:5432/chronos_fire
DB_USER=chronos_reader
DB_PASSWORD=...
```

No hardcodear la clave en `application.properties`.

---

## 4. Arquitectura propuesta

```text
Frontend
   │
   ▼
Spring Boot (Java 21)
   ├─ controller/   → HTTP, validación, DTOs
   ├─ service/      → reglas de negocio, defaults, anomalías
   ├─ repository/   → SQL con NamedParameterJdbcTemplate
   └─ dto/          → records de entrada/salida
   │
   ▼
PostgreSQL 17 (chronos_fire)
   ├─ detections
   ├─ v_detections_canonical
   ├─ industrial_cells
   ├─ v_detections_analysis
   ├─ grid_daily
   ├─ calendar_weekly
   └─ v_latest_nrt
```

### Estructura de paquetes

```text
backend/
├─ pom.xml
├─ src/main/java/com/chronosfire/backend/
│  ├─ ChronosFireApplication.java
│  ├─ config/
│  │  └─ CorsConfig.java
│  ├─ controller/
│  │  ├─ HealthController.java
│  │  ├─ MetaController.java
│  │  ├─ DetectionController.java
│  │  ├─ CalendarController.java
│  │  └─ AlertController.java
│  ├─ service/
│  │  ├─ DetectionService.java
│  │  ├─ CalendarService.java
│  │  └─ AlertService.java
│  ├─ repository/
│  │  ├─ DetectionRepository.java
│  │  ├─ CalendarRepository.java
│  │  ├─ AlertRepository.java
│  │  └─ MetaRepository.java
│  ├─ dto/
│  │  ├─ Bbox.java
│  │  ├─ PageResponse.java
│  │  ├─ DetectionDto.java
│  │  ├─ WeeklyCalendarRow.java
│  │  ├─ DailyCalendarRow.java
│  │  ├─ HeatmapCell.java
│  │  ├─ SensorSummary.java
│  │  ├─ MetaResponse.java
│  │  ├─ AlertCell.java
│  │  ├─ AlertResponse.java
│  │  └─ GeoJsonFeatureCollection.java
│  └─ web/
│     └─ ApiExceptionHandler.java
└─ src/main/resources/
   └─ application.properties
```

**Decisión:** no usar JPA/Hibernate. Las consultas son agregadas y `detections` tiene clave primaria compuesta. `NamedParameterJdbcTemplate` es más simple y controlable.

---

## 5. Contrato de API

### Endpoints

| Método | Ruta | Tabla principal | Prioridad |
|---|---|---|---|
| GET | `/api/health` | `SELECT 1` | Alta |
| GET | `/api/meta` | `detections` | Alta |
| GET | `/api/calendar/weekly` | `grid_weekly` + `grid_weekly_sensor` + `calendar_weekly` | Alta |
| GET | `/api/calendar/daily` | `grid_daily` | Alta |
| GET | `/api/calendar/heatmap` | `grid_daily` | Alta |
| GET | `/api/calendar/monthly` | `grid_daily` + `detections` | Alta |
| GET | `/api/calendar/trend` | `grid_weekly_sensor` | Alta |
| GET | `/api/calendar/export.csv` | `grid_weekly_sensor` | Alta |
| GET | `/api/alerts` | `v_detections_analysis` (NRT) | Alta |
| GET | `/api/alerts/geojson` | `v_detections_analysis` (NRT) | Alta |
| GET | `/api/detections` | `v_detections_analysis` | Media |
| GET | `/api/detections/geojson` | `v_detections_analysis` | Media |
| GET | `/api/detections/export.csv` | `v_detections_analysis` | Media |

### Fuente de datos y política SP/NRT

- `/api/detections` y `/api/detections/geojson` consultan `v_detections_analysis`.
- `/api/alerts` consulta `v_detections_analysis` y filtra `source = 'NRT'`.
- `/api/calendar/*` usan `grid_daily` y `calendar_weekly`.
- Si se necesita depurar, se puede pedir `source=SP` o `source=NRT` explícitamente.

### Parámetros comunes

```text
minLon, minLat, maxLon, maxLat
from, to          (ISO: YYYY-MM-DD)
year              (para weekly)
sensor            (repetible: MODIS, VIIRS_SNPP, VIIRS_NOAA20, VIIRS_NOAA21)
confidence        (repetible: low, nominal, high)
type              (0,1,2,3)
source            (SP, NRT)
vegetation        (true/false)
limit, offset
days              (para alerts)
```

### Defaults

- Bbox: Argentina (`-73.6, -55.1, -53.6, -21.8`).
- `limit`: 1.000.
- `limit` máximo: 50.000.
- `days` de alertas: 7.
- Fechas: si no se envían, últimos 365 días para `daily`/`heatmap`.

### Respuesta semanal esperada

```json
{
  "week": 40,
  "fireCells": 66,
  "baselineFireCells": 10,
  "anomaly": 6.60,
  "referenceYears": 12,
  "baselineSensor": "VIIRS_SNPP",
  "confidence": "high",
  "provisional": false,
  "detectionsBySensor": {
    "MODIS": 0,
    "VIIRS_SNPP": 120,
    "VIIRS_NOAA20": 40
  }
}
```

Cálculo:

```text
observedFireCells = count(distinct grid_id)
                    where fire_cells = 1
                      and isoyear = year
                      and week_of_year = week
                      and bbox

baselineFireCells = median(observedFireCells)
                    for referenceYears, excluding year

anomaly = observedFireCells / baselineFireCells
```

Reglas:

- Baseline con **mediana**, no media.
- `referenceYears >= 7`; `>= 10` alta confianza.
- `baselineFireCells >= 3`; `>= 10` alta confianza.
- `observedFireCells >= 3`.
- Si no cumple, `anomaly = null` y `confidence = "insufficient"`.
- `baselineSensor`: `MODIS` (2003–2011) o `VIIRS_SNPP` (2013–2025).
- `provisional`: `true` para 2026 o datos NRT.
- No devolver `fire_day_prob` como probabilidad.
- `detections` no es titular; si se incluye, va por sensor.

### Respuesta de alertas tempranas

`/api/alerts` devuelve alertas **agrupadas por celda de 0,05°**, no una fila por detección. Así el frontend puede mostrar un panel manejable.

```json
{
  "generatedAt": "2026-11-14T18:00:00Z",
  "days": 7,
  "aoi": {
    "minLon": -60.5,
    "minLat": -33.5,
    "maxLon": -59.5,
    "maxLat": -32.8
  },
  "count": 2,
  "alerts": [
    {
      "cellLat": -33.0,
      "cellLon": -60.25,
      "lastDetection": "2026-11-14T17:40:00Z",
      "detections": 14,
      "frpMaxMw": 52.3,
      "sensors": ["VIIRS_SNPP", "VIIRS_NOAA20"],
      "confidenceMax": "high",
      "aoiAnomaly": 1.8,
      "level": "warning"
    }
  ]
}
```

### Niveles de alerta (MVP)

| Nivel | Regla sugerida |
|---|---|
| `info` | 1–2 detecciones en la celda, sin anomalía de AOI |
| `watch` | ≥ 3 detecciones o `aoiAnomaly >= 1,5` |
| `warning` | ≥ 10 detecciones o `aoiAnomaly >= 2,0` o `frpMaxMw` alto |

Los umbrales son configurables y deben ajustarse con la demo. No son una probabilidad.

### Consulta base de alertas

```sql
WITH recent AS (
    SELECT *
    FROM v_detections_analysis
    WHERE source = 'NRT'
      AND NOT industrial_mask
      AND acquired_at >= now() - make_interval(days => :days)
      AND latitude BETWEEN :minLat AND :maxLat
      AND longitude BETWEEN :minLon AND :maxLon
      AND confidence_class IN ('nominal', 'high')
      AND (is_vegetation_effective IS NULL OR is_vegetation_effective)
)
SELECT
    round(latitude / 0.05) * 0.05  AS cell_lat,
    round(longitude / 0.05) * 0.05 AS cell_lon,
    max(acquired_at)               AS last_detection,
    count(*)                       AS detections,
    max(frp_mw)                    AS frp_max_mw,
    array_agg(DISTINCT sensor)     AS sensors,
    max(confidence_norm)           AS confidence_max
FROM recent
GROUP BY 1, 2
ORDER BY detections DESC
LIMIT :limit;
```

La anomalía de alertas es **a nivel AOI**, calculada con `grid_weekly_sensor` y baseline SNPP-only para la semana actual. No se calcula por celda. Si `n_ref_years < 7`, devolver `aoiAnomaly = null`.

---

## 6. Fases de implementación

### Fase 0 — Vista canónica SP/NRT (1 h)

- Crear `v_detections_canonical` (SQL de la sección 3).
- Verificar que devuelva una sola fuente por sensor/fecha cuando haya solapamiento.
- Confirmar que los agregados actuales siguen siendo válidos (hoy SP y NRT son disjuntos).
- Documentar que `grid_daily` y `calendar_weekly` deben reconstruirse desde la vista cuando SP empiece a solaparse.

**Criterio de aceptación:** la vista existe, el backend puede consultarla y el chequeo de solapamiento no muestra duplicados inesperados.

---

### Fase 1 — Esqueleto del proyecto (2–3 h)

- Crear `pom.xml` con:
  - Spring Boot 3.x
  - `spring-boot-starter-web`
  - `spring-boot-starter-jdbc`
  - `spring-boot-starter-validation`
  - `postgresql`
  - `spring-boot-starter-test`
- Crear `ChronosFireApplication`.
- Usar `application.properties` existente.
- Crear `CorsConfig`.
- Crear `HealthController`.
- Verificar `mvn spring-boot:run`.

**Criterio de aceptación:** la app arranca y `/api/health` devuelve `database: true`.

---

### Fase 2 — Metadatos (2 h)

- `MetaRepository`:
  ```sql
  SELECT sensor, source, count(*) AS n,
         min(acq_date) AS desde, max(acq_date) AS hasta
  FROM detections
  GROUP BY sensor, source
  ORDER BY sensor, source;
  ```
- `MetaController` → `/api/meta`.
- DTOs `SensorSummary`, `MetaResponse`.

**Criterio de aceptación:** devuelve los 8 pares sensor/fuente y el rango de fechas.

---

### Fase 3 — Calendario (4–6 h)

- `CalendarRepository` con tres consultas:
  1. **Weekly**: `grid_weekly` + `grid_weekly_sensor` + `calendar_weekly`.
  2. **Daily**: `grid_daily` agrupado por día.
  3. **Heatmap**: `grid_daily` agrupado por celda.
- Los agregados se usan tal como están. Si se reconstruyen, deben generarse desde `v_detections_analysis` para respetar la política SP/NRT y la máscara industrial.
- `CalendarService`:
  - Defaults de fechas.
  - Validación de bbox.
  - Cálculo de anomalía por AOI: observado vs mediana multianual.
  - `confidence` según `n_ref_years` y `baseline`.
- `CalendarController`.
- Cache opcional por `(bbox, year)` si las consultas tardan.

**Criterio de aceptación:**
- `/api/calendar/weekly?year=2020` en el Delta devuelve anomalía ≈ 6,6 en la semana 40.
- Semanas sin datos devuelven `null`, no `0`.
- Tiempo de respuesta < 1 s para un AOI típico.

---

### Fase 4 — Detecciones y GeoJSON (3–4 h)

- `DetectionRepository` con SQL dinámico y parámetros nombrados, sobre `v_detections_analysis`.
- Filtros: bbox, fechas, sensor, confidence, type, source, vegetation.
- Paginación:
  ```text
  items, total, limit, offset
  ```
- `GET /api/detections` → JSON paginado.
- `GET /api/detections/geojson` → `FeatureCollection`.
- Límite máximo: 50.000; default: 1.000.

**Criterio de aceptación:**
- Se puede pedir un AOI y un rango de fechas.
- El GeoJSON se abre en MapLibre/Leaflet.
- No se devuelven más filas que el límite.

---

### Fase 5 — Alertas tempranas (MVP, 3–4 h)

Incluidas en el MVP, no como opcionales.

- `AlertRepository`:
  - Consulta base de la sección 5, agrupada por celda de 0,05°.
  - Solo `source = 'NRT'` de `v_detections_analysis`.
  - Ventana `days` (default 7, máximo 30).
  - Filtros: bbox, confianza nominal/alta, vegetación.
- `AlertService`:
  - Agrupa por celda.
  - Calcula `detections`, `frpMaxMw`, `lastDetection`, `sensors`, `confidenceMax`.
  - Calcula `aoiAnomaly` contra el baseline SNPP-only de `grid_weekly_sensor`.
  - Asigna `level`: `info`, `watch`, `warning`.
  - Si `n_ref_years < 7`, `aoiAnomaly = null`.
- `AlertController`:
  - `GET /api/alerts?days=7&bbox=...&limit=...`
  - `GET /api/alerts/geojson?days=7&bbox=...` (un `Point` por celda agrupada)
- Usar `v_detections_analysis` y `NOT industrial_mask`. La máscara ya está implementada con 885 celdas.
- No hay push: el frontend consulta por polling.

**Criterio de aceptación:**
- Devuelve alertas agrupadas, no miles de puntos.
- Cada alerta tiene nivel y última detección.
- No muestra NRT cuando ya existe SP para ese sensor/fecha.
- No muestra focos industriales (máscara v1 implementada).
- Tiempo de respuesta < 1 s para un AOI típico y 7 días.

---

### Fase 6 — Calidad y despliegue (4–6 h)

- `ApiExceptionHandler`:
  - 400 para bbox inválido o fechas invertidas.
  - 500 sin filtrar detalles internos.
- Validación con `@Min`, `@Max`, `@DateTimeFormat`.
- Tests:
  - Unitarios de `Bbox` y cálculo de anomalía.
  - `@WebMvcTest` con repositorios mockeados.
  - Smoke test contra base de prueba (opcional Testcontainers).
- Logging básico.
- `README` con ejemplos `curl`.
- Perfiles `dev`/`prod`.
- Rol `chronos_reader` de solo lectura.

**Criterio de aceptación:** el backend no escribe en la base y no expone credenciales.

---

## 7. Decisiones técnicas

| Tema | Decisión |
|---|---|
| Acceso a datos | `NamedParameterJdbcTemplate`, no JPA |
| DTOs | `record` inmutables |
| Fechas | `LocalDate` para `acq_date`, `OffsetDateTime` para `acquired_at` |
| Bbox | Validar y usar default Argentina |
| Paginación | `limit`/`offset` con tope |
| GeoJSON | `FeatureCollection` con `Point` |
| Anomalía | Baseline por AOI: `fire_cells` observado vs **mediana** multianual |
| Baseline | MODIS 2003–2011; VIIRS SNPP 2013–2025; 2012/2026 excluidos |
| Umbral anomalía | `n_ref_years >= 7`; `baseline >= 3`; `observed >= 3` |
| SP/NRT | Vista canónica: SP si existe para sensor/fecha; NRT en caso contrario |
| Alertas | NRT no archivado, agrupado por celda, niveles `info/watch/warning` |
| Volúmenes | `detections` ~7,08 M; `grid_daily` ~2,1 M; `grid_weekly` ~1,58 M; `grid_weekly_sensor` ~2,27 M; `calendar_weekly` ~3,72 M |
| Semanas sin datos | `null`, no `0` |
| Seguridad | Rol de solo lectura; secretos por entorno |
| CORS | Configurable por `CORS_ORIGINS` |
| PostGIS | Opcional; el MVP usa `latitude`/`longitude` |

---

## 8. Riesgo SP/NRT

### Situación actual

Los rangos SP y NRT son disjuntos:

| Sensor | SP hasta | NRT desde |
|---|---|---|
| MODIS | 2026-08-31 | 2026-09-01 |
| VIIRS SNPP | 2026-07-31 | 2026-08-01 |
| VIIRS NOAA-20 | 2026-07-31 | 2026-08-01 |
| VIIRS NOAA-21 | 2026-07-31 | 2026-08-01 |

No hay duplicación hoy.

### Riesgo futuro

Cuando el archive SP cubra fechas que hoy son NRT, el mismo píxel/instante puede aparecer dos veces. La clave primaria incluye `source`, así que **no falla**: duplica en silencio.

Impacto:

- `n_detections` y `frp_sum_mw` se inflan.
- `fire_day` no se duplica (es booleano), pero el FRP del calendario sí.
- `calendar_weekly.frp_p50/p90` se inflan.
- Las alertas NRT podrían mostrar datos ya reemplazados por SP.

### Política adoptada para el MVP

**SP si está disponible; NRT si todavía no hay SP, por sensor y fecha.**

Implementación:

1. **Vista canónica** `v_detections_canonical`:
   - Devuelve SP si existe al menos una detección SP para ese `sensor` + `acq_date`.
   - Si no, devuelve las NRT de ese `sensor` + `acq_date`.
2. **Backend**:
   - `/api/detections` consulta la vista canónica.
   - `/api/alerts` consulta la vista canónica y filtra `source = 'NRT'`: muestra solo lo aún no archivado.
3. **Agregados**:
   - `grid_daily` y `calendar_weekly` deben reconstruirse desde `v_detections_analysis` cuando SP empiece a cubrir fechas que hoy son NRT.
   - Hoy los rangos son disjuntos, así que los agregados actuales son válidos.
4. **Monitoreo**:
   ```sql
   SELECT sensor, acq_date,
          count(*) FILTER (WHERE source = 'SP')  AS sp,
          count(*) FILTER (WHERE source = 'NRT') AS nrt
   FROM detections
   GROUP BY 1, 2
   HAVING count(*) FILTER (WHERE source = 'SP')  > 0
      AND count(*) FILTER (WHERE source = 'NRT') > 0
   LIMIT 20;
   ```

**Caveat:** la política es por sensor/fecha, no por píxel. Si SP cubre parcialmente un día, se oculta el NRT de ese sensor/día. Es una simplificación documentada para el MVP.

En el backend, además, se puede pedir `source=SP` o `source=NRT` explícitamente para depuración.

---

## 9. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| `grid_daily` vacío | Reconstruir agregados antes de probar el calendario |
| SP/NRT solapados | Vista canónica `v_detections_canonical` |
| SP parcial oculta NRT del día | Documentar; evaluar política por píxel en el futuro |
| Alertas con falsos positivos | Confianza nominal/alta + máscara industrial |
| Volumen de agregados | `grid_daily` ~2,1 M; `grid_weekly` ~1,58 M; `calendar_weekly` ~3,72 M: cachear y limitar consultas |
| Consultas lentas | Índices existentes; filtros de fecha/bbox; cache |
| `n_ref_years` bajo | No mostrar anomalía; `confidence` insuficiente |
| Baseline inestable | Mediana; mínimo 3 celdas; excluir 2012/2026 |
| Semanas faltantes | Devolver `null` y explicar en la UI |
| Clave en el repo | `DB_PASSWORD` por entorno |
| Bbox enorme | Limitar área o pedir confirmación |
| NRT industrial | Máscara v1 implementada; revisar 108 celdas borderline |
| Cobertura incompleta | `/api/meta` muestra rangos; la UI debe avisar |

---

## 10. Definition of Done del MVP

- [x] `mvn spring-boot:run` funciona.
- [x] `/api/health` devuelve `database: true`.
- [x] `/api/meta` devuelve cobertura por sensor/fuente.
- [x] `v_detections_canonical` existe y respeta SP > NRT por sensor/fecha.
- [x] `industrial_cells` existe (885 celdas) y cubre los focos conocidos.
- [x] `v_detections_analysis` existe y marca `industrial_mask`.
- [x] `grid_weekly` y `grid_weekly_sensor` construidos.
- [x] `calendar_weekly` corregida, 53 semanas por celda.
- [x] `/api/calendar/weekly` devuelve `fireCells`, `baselineFireCells`, `anomaly`, `baselineSensor`, `confidence` y `detectionsBySensor`.
- [x] `/api/calendar/weekly` no expone `fire_day_prob` como probabilidad.
- [x] `/api/calendar/daily` devuelve serie diaria.
- [x] `/api/calendar/heatmap` devuelve celdas.
- [x] `/api/calendar/monthly` devuelve meses con `partial` y `dataStatus`.
- [x] `/api/calendar/trend` devuelve serie anual con baseline.
- [x] `/api/calendar/export.csv` y `/api/detections/export.csv` funcionan.
- [x] `/api/alerts` devuelve alertas agrupadas por celda, con nivel y última detección.
- [x] `/api/alerts` no muestra NRT si ya existe SP para ese sensor/fecha.
- [x] `/api/alerts` no muestra focos industriales.
- [x] `/api/aois` devuelve el catálogo de AOIs y sus ventanas.
- [x] `/api/validation` devuelve los eventos conocidos con anomalía y veredicto.
- [x] `/api/detections` pagina, filtra y usa `v_detections_analysis`.
- [ ] Consultas de calendario < 1 s para un AOI típico con ~3,7 M de filas en `calendar_weekly`.
- [x] `/api/detections/geojson` es válido.
- [ ] Errores 400/500 controlados.
- [ ] CORS configurado.
- [ ] Sin credenciales en el repo.
- [ ] README con ejemplos.

---

## 11. Estimación

| Fase | Tiempo |
|---|---|
| Vista canónica SP/NRT | 1 h |
| Esqueleto + health | 2–3 h |
| Meta | 2 h |
| Calendario | 4–6 h |
| Detecciones/GeoJSON | 3–4 h |
| Alertas tempranas | 3–4 h |
| Tests + hardening | 4–6 h |
| **Total MVP** | **~2 días** |

---

## 12. Próximo paso inmediato

1. Confirmar que `grid_daily` (~2,1 M) y `calendar_weekly` (~0,78 M) tienen datos.
2. Validar `v_detections_analysis` (canónica + máscara industrial) y la política SP/NRT.
3. Crear el rol `chronos_reader`.
4. Crear el esqueleto Maven + `ChronosFireApplication`.
5. Implementar `/api/health` y `/api/meta`.
6. Implementar calendario.
7. Implementar alertas tempranas agrupadas por celda.
8. Implementar detecciones y GeoJSON.
9. Exponer `industrialMask` en `/api/detections` y excluir industriales en `/api/alerts`.

---

## 13. Fuera de alcance del MVP

- Escritura de datos desde el backend.
- Carga incremental automática desde la API de FIRMS (las alertas del MVP usan los NRT ya cargados).
- Autenticación de usuarios.
- Frontend.
- PostGIS y geometrías.
- Máscara industrial avanzada (actualización automática o por píxel). La máscara v1 ya está implementada.
- Notificaciones push/email/Telegram (las alertas son pull).
- Persistencia de alertas vistas/confirmadas.

Estos puntos se pueden agregar después sin cambiar el núcleo de la API.
