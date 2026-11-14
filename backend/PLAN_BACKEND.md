# Plan de implementación — Backend Chronos Fire

**Proyecto:** Hackaton NASA Chronos Fire  
**Base de datos:** PostgreSQL 17, `chronos_fire`  
**Stack:** Java 21+, Spring Boot 3.x, JDBC (`NamedParameterJdbcTemplate`)  
**Estado:** planificación. El backend todavía no está implementado.

---

## 1. Objetivo

Construir una API de **solo lectura** sobre la base `chronos_fire` para:

- Consultar el calendario histórico de actividad de fuego por AOI.
- Comparar la actividad observada contra la climatología.
- Detectar períodos críticos y condiciones inusuales.
- Exponer alertas tempranas basadas en NRT.
- Devolver detecciones y celdas en formato JSON y GeoJSON para el frontend.

El backend **no carga datos**: la carga sigue siendo responsabilidad del ETL con DuckDB (`etl/load_pg.py`).

---

## 2. Estado de partida

| Componente | Estado |
|---|---|
| Base `chronos_fire` | Creada en PostgreSQL 17 (5432) |
| Esquema `ChronosFireDB.sql` | Aplicado |
| `detections` | Cargada casi en su totalidad; verificar cobertura |
| `grid_daily` | Verificar que esté construida |
| `calendar_weekly` | Verificar que esté construida |
| Backend Java | No implementado |
| `application.properties` | Existe y apunta a `chronos_fire` |
| ETL DuckDB | `etl/load_pg.py` |

### Modelo de datos disponible

| Tabla / vista | Grano | Uso |
|---|---|---|
| `detections` | Una fila por sensor + píxel + instante | Detalle, filtros, GeoJSON |
| `grid_daily` | Celda 0,05° × día | Calendario diario, heatmap |
| `calendar_weekly` | Celda × semana ISO | Climatología y anomalías |
| `ingest_runs` | Ejecución de carga | Auditoría (no se expone en el MVP) |
| `v_latest_nrt` | Detecciones NRT de 7 días | Alertas |
| `v_vegetation_detections` | Detecciones de vegetación | Análisis |

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
| GET | `/api/calendar/weekly` | `grid_daily` + `calendar_weekly` | Alta |
| GET | `/api/calendar/daily` | `grid_daily` | Alta |
| GET | `/api/calendar/heatmap` | `grid_daily` | Alta |
| GET | `/api/alerts` | `detections` NRT | Alta |
| GET | `/api/detections` | `detections` | Media |
| GET | `/api/detections/geojson` | `detections` | Media |

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
  "week": 35,
  "detections": 1234,
  "fireCells": 87,
  "frpSumMw": 4567.8,
  "climatologyProbability": 0.12,
  "expectedFireCells": 62.4,
  "anomalyRatio": 1.39,
  "reliable": true
}
```

Cálculo:

```text
expectedFireCells = climatologyProbability × n_cells_del_AOI
anomalyRatio      = observedFireCells / expectedFireCells
reliable          = n_years >= 5
```

Si `expectedFireCells` es 0 o `n_years` es bajo, devolver `null` en anomalía y `reliable = false`.

---

## 6. Fases de implementación

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
  1. **Weekly**: `grid_daily` + `calendar_weekly`, agrupado por semana.
  2. **Daily**: `grid_daily` agrupado por día.
  3. **Heatmap**: `grid_daily` agrupado por celda.
- `CalendarService`:
  - Defaults de fechas.
  - Validación de bbox.
  - Cálculo de anomalía.
  - `reliable` según `n_years`.
- `CalendarController`.
- Cache opcional por `(bbox, year)` si las consultas tardan.

**Criterio de aceptación:**
- `/api/calendar/weekly?year=2020` en el Delta devuelve la anomalía de 2020.
- Semanas sin datos devuelven `null`, no `0`.
- Tiempo de respuesta < 1 s para un AOI típico.

---

### Fase 4 — Detecciones y GeoJSON (3–4 h)

- `DetectionRepository` con SQL dinámico y parámetros nombrados.
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

### Fase 5 — Alertas (2 h)

- `GET /api/alerts?days=7&bbox=...`:
  ```sql
  SELECT *
  FROM detections
  WHERE source = 'NRT'
    AND acquired_at >= now() - make_interval(days => :days)
    AND latitude BETWEEN :minLat AND :maxLat
    AND longitude BETWEEN :minLon AND :maxLon
    AND confidence_class IN ('nominal', 'high')
    AND (is_vegetation IS NULL OR is_vegetation)
  ORDER BY acquired_at DESC
  LIMIT :limit;
  ```
- `GET /api/alerts/geojson`.
- Cuando exista la máscara industrial, aplicarla aquí.

**Criterio de aceptación:** devuelve NRT reciente, sin industriales cuando la máscara esté lista.

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
| Anomalía | Observado vs climatología; `reliable` por `n_years` |
| SP/NRT | Exponer `source`; no sumar ambos en la misma serie |
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

### Mitigación

1. **Política “SP gana”:** al cargar SP, borrar NRT solapado por sensor/fecha.
2. **Vista canónica:** consultar SP si existe, si no NRT.
3. **No mezclar:** calendario histórico con SP; alertas con NRT.
4. **Health check:**
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

En el backend, exponer `source` como filtro y documentar que los agregados no distinguen fuente.

---

## 9. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| `grid_daily` vacío | Reconstruir agregados antes de probar el calendario |
| SP/NRT solapados | Vista canónica o política “SP gana” |
| Consultas lentas | Índices existentes; filtros de fecha/bbox; cache |
| `n_years` bajo | Campo `reliable`; no mostrar anomalía |
| Semanas faltantes | Devolver `null` y explicar en la UI |
| Clave en el repo | `DB_PASSWORD` por entorno |
| Bbox enorme | Limitar área o pedir confirmación |
| NRT industrial | Máscara industrial pendiente |
| Cobertura incompleta | `/api/meta` muestra rangos; la UI debe avisar |

---

## 10. Definition of Done del MVP

- [ ] `mvn spring-boot:run` funciona.
- [ ] `/api/health` devuelve `database: true`.
- [ ] `/api/meta` devuelve cobertura por sensor/fuente.
- [ ] `/api/calendar/weekly` devuelve calendario + anomalía.
- [ ] `/api/calendar/daily` devuelve serie diaria.
- [ ] `/api/calendar/heatmap` devuelve celdas.
- [ ] `/api/alerts` devuelve NRT reciente.
- [ ] `/api/detections` pagina y filtra.
- [ ] `/api/detections/geojson` es válido.
- [ ] Errores 400/500 controlados.
- [ ] CORS configurado.
- [ ] Sin credenciales en el repo.
- [ ] README con ejemplos.

---

## 11. Estimación

| Fase | Tiempo |
|---|---|
| Esqueleto + health | 2–3 h |
| Meta | 2 h |
| Calendario | 4–6 h |
| Detecciones/GeoJSON | 3–4 h |
| Alertas | 2 h |
| Tests + hardening | 4–6 h |
| **Total MVP** | **~1,5–2 días** |

---

## 12. Próximo paso inmediato

1. Confirmar que `grid_daily` y `calendar_weekly` tienen datos.
2. Crear el rol `chronos_reader`.
3. Crear el esqueleto Maven + `ChronosFireApplication`.
4. Implementar `/api/health` y `/api/meta`.
5. Recién después, el calendario.

---

## 13. Fuera de alcance del MVP

- Escritura de datos desde el backend.
- Carga incremental automática desde la API de FIRMS.
- Autenticación de usuarios.
- Frontend.
- PostGIS y geometrías.
- Máscara industrial completa.
- Notificaciones por email/Telegram.

Estos puntos se pueden agregar después sin cambiar el núcleo de la API.
