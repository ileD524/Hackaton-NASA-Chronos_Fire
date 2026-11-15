# Plan de implementación — Subida de archivos y armonización

**Proyecto:** Chronos Fire  
**Base:** PostgreSQL 17, `chronos_fire`  
**Backend:** Java 21 + Spring Boot 3.3.5 (JdbcTemplate, sin JPA)  
**Frontend:** Vue 3 + Vite + Leaflet  
**Fecha:** 2026-11-15  
**Estado:** plan aprobado para implementación; **no implementado**  
**Regla dura:** no se modifican las tablas canónicas (`detections`, `grid_daily`, `grid_weekly`, `grid_weekly_sensor`, `calendar_weekly`) ni las vistas oficiales.

---

## 0. Resumen ejecutivo

Se implementa una **capa de importación aislada** en dos etapas:

1. **Cliente primero:** el usuario sube un CSV/JSON, el navegador lo parsea, valida y muestra una vista previa local. No toca la base.
2. **Persistencia y armonización autoritativa:** al confirmar, el archivo viaja al backend, se guarda como fila cruda en `import_raw`, se armoniza con una función SQL y se materializa en `import_detections` + `import_grid_daily`. La UI consulta `/api/imports/{id}/...`.

La armonización oficial sigue siendo la única fuente de verdad. El cliente es solo UX: **nunca se confía en los datos normalizados del navegador**.

**Esfuerzo estimado:** 8 días de desarrollo (MVP funcional en 4–5 días).  
**Riesgo principal:** mezclar datos de usuario con el dataset oficial. Se elimina por diseño con `import_id` y una PK que lo incluye.

---

## 1. Objetivos y no objetivos

### Objetivos

- Permitir que un usuario cargue un archivo FIRMS crudo (CSV/JSON) o un export de Chronos Fire.
- Validar estructura, tipos, rangos y límites antes de escribir.
- Mostrar una vista previa (tabla, mapa, conteos) antes de confirmar.
- Persistir la carga en tablas de importación aisladas.
- Aplicar exactamente las mismas reglas de armonización que el ETL oficial.
- Consultar los datos cargados con endpoints equivalentes a los oficiales.
- Poder borrar la carga y volver a los datos oficiales sin reiniciar la app.
- Mantener el backend oficial en modo lectura.

### No objetivos (fuera de alcance)

- Escribir en `detections` o en los agregados oficiales.
- Recalcular el calendario global por cada carga.
- Calibrar eficiencia de detección o FRP.
- Área quemada, perímetros, QA por píxel o nubosidad.
- Multiusuario con cuentas y permisos finos.
- Cargas maiores a 10 MB o 50.000 filas.
- Anomalía multianual confiable para una carga de una sola temporada.

---

## 2. Restricciones del sistema actual

| Restricción | Evidencia | Consecuencia para el plan |
|---|---|---|
| Backend de solo lectura | `backend/README.md` L22; `PLAN_BACKEND.md` §5 | Se agrega una capa de escritura separada, no se cambia el contrato de lectura |
| CORS solo `GET, OPTIONS` | `CorsConfig.java` L25 | Hay que habilitar `POST/DELETE` solo para `/api/imports/**` |
| Sin autenticación | No hay Spring Security | Se agrega token opcional + rate limit + modo local; documentar el riesgo |
| PK canónica sin `import_id` | `ChronosFireDB.sql` L46 | No se puede aislar una carga dentro de `detections` |
| Vistas globales | `v_detections_canonical`, `v_detections_analysis` | Una fila subida entraría al calendario y a las alertas |
| Agregados globales y costosos | `grid_daily`, `calendar_weekly` | No se reconstruyen por carga |
| Armonización en DuckDB | `etl/load_pg.py` | Se replica en SQL dentro de PostgreSQL, no en Java |
| Multipart por defecto 1 MB | Spring Boot | Configurar `max-file-size` y `max-request-size` |
| UI usa `sensor=ALL` por defecto | `useChronos.ts` L58 | La vista de import debe etiquetar “todos” y no calcular anomalía |
| `n_fire_years`/`n_activity_years` = 0 | `rebuild_calendar.sql` | En import se usa `NULL` para “sin datos”, no `0` |

---

## 3. Arquitectura propuesta

```text
Archivo CSV/JSON
      │
      ▼
┌──────────────────────────────┐
│ CLIENTE (Vue)                │
│ parseo → validación → preview│
│ (no escribe nada)            │
└──────────────┬───────────────┘
               │ POST multipart (confirmación)
               ▼
┌──────────────────────────────┐
│ BACKEND Spring Boot          │
│ ImportController             │
│ ImportService                │
│ Parser CSV/JSON + validador  │
└──────────────┬───────────────┘
               │ INSERT (rol ingest)
               ▼
┌──────────────────────────────┐
│ PostgreSQL                   │
│ import_batches               │
│ import_raw                   │
│ import_rejects               │
│ harmonize_import(uuid)       │
│ import_detections            │
│ import_grid_daily            │
└──────────────┬───────────────┘
               │ SELECT (rol reader)
               ▼
┌──────────────────────────────┐
│ /api/imports/{id}/...        │
│ detections, geojson, daily,  │
│ monthly, heatmap, export.csv │
└──────────────┬───────────────┘
               ▼
        UI (mapa, tabla, calendario)
```

### Separación de planos

| Plano | Datos | Escritura | Lectura |
|---|---|---|---|
| Oficial | `detections`, `grid_daily`, `grid_weekly`, `calendar_weekly` | Solo ETL DuckDB | `chronos_reader` |
| Importación | `import_*` | `chronos_ingest` | `chronos_reader` |
| Vistas oficiales | `v_detections_*` | — | `chronos_reader` |

No hay JOIN entre el plano oficial y el de importación para construir métricas. Solo se reutiliza `industrial_cells` como referencia de lectura.

---

## 4. Decisiones de arquitectura (ADR)

### ADR-01 — Aislamiento por `import_id`

Cada fila de import lleva `import_id` en la PK. Nunca se inserta en las tablas canónicas.  
**Motivo:** evita colisiones de PK, contaminación del calendario y pérdida de reproducibilidad.  
**Consecuencia:** los endpoints de lectura de import son un namespace aparte.

### ADR-02 — El cliente previsualiza, el servidor armoniza

El cliente parsea y valida para UX, pero el backend vuelve a parsear y armonizar.  
**Motivo:** el navegador no es confiable; además el preview debe coincidir con lo que se guarda.  
**Consecuencia:** la lógica de armonización vive en SQL y se prueba por separado.

### ADR-03 — Armonización en función SQL

`harmonize_import(uuid)` replica las reglas de `etl/load_pg.py` en PostgreSQL.  
**Motivo:** una sola fuente de verdad, testeable con SQL, sin duplicar reglas en Java.  
**Consecuencia:** Java solo parsea, valida tipos y llena `import_raw`.

### ADR-04 — Dos datasources, dos roles

- `chronos_reader`: `SELECT` sobre todo.
- `chronos_ingest`: `INSERT/UPDATE/DELETE` solo sobre `import_*` y `EXECUTE` de `harmonize_import`.

**Motivo:** preservar el contrato de solo lectura del backend oficial.  
**Consecuencia:** `IngestDataSourceConfig` con `@Qualifier("ingestJdbc")`.

### ADR-05 — Límites duros

10 MB, 50.000 filas, 40 columnas, 3 importaciones por hora por IP, 1 import activo por sesión, TTL 24 h.  
**Motivo:** proteger memoria, pool de conexiones y tiempos de agregación.  
**Consecuencia:** validación en cliente y servidor; rechazo atómico si > 10% de filas inválidas.

### ADR-06 — Sin anomalía multianual en importaciones

La UI muestra actividad observada, no anomalía, salvo que la carga cubra ≥ 7 años distintos.  
**Motivo:** una temporada no tiene baseline; mostrar “anomalía” sería engañoso.  
**Consecuencia:** `anomaly=null`, `confidence="insufficient"` por defecto en import.

### ADR-07 — Feature flag

`app.import.enabled=false` por defecto en producción; `true` en demo local.  
**Motivo:** permitir desactivar la escritura sin desplegar.  
**Consecuencia:** el endpoint devuelve `503` si está deshabilitado; el frontend oculta el botón.

---

## 5. Fase 1 — Cliente primero

### 5.1 Flujo de usuario

1. Botón **“Cargar datos”** en el header.
2. Modal con drag & drop o selector de archivo.
3. El usuario elige perfil:
   - **FIRMS crudo** (CSV/JSON de FIRMS).
   - **Export Chronos Fire** (CSV generado por la app).
4. Opcional: sensor y fuente (`SP`/`NRT`) si el archivo no los trae.
5. El cliente muestra:
   - nombre, tamaño, filas detectadas;
   - columnas reconocidas y faltantes;
   - primeros 100 registros normalizados;
   - conteos por sensor, confianza y fecha;
   - errores por fila/campo (máx. 100);
   - aviso “vista previa local, todavía no se guardó”.
6. Botón **“Confirmar carga”** (deshabilitado si hay errores bloqueantes).
7. Barra de progreso de subida.
8. Estado “procesando” con polling.
9. Al quedar `ready`: banner “Datos cargados por el usuario — no oficiales”, mapa/tabla/calendario de la carga.
10. Botón **“Volver a datos oficiales”** y **“Eliminar carga”**.

### 5.2 Parseo

**CSV**
- Detectar BOM (`\uFEFF`) y delimitador `,` o `;`.
- Soportar comillas, comas internas y saltos de línea dentro de campos.
- Máximo 40 columnas y 50.000 filas.
- Cabecera obligatoria; alias de columnas normalizados a minúsculas.

**JSON**
- Debe ser un array de objetos planos (formato FIRMS).
- Rechazar objetos anidados > 3 niveles.
- Límite de 2 KB por string y 50.000 elementos.
- Si el JSON es un objeto con `data: []`, aceptar solo esa forma.

**Dependencia recomendada:** `papaparse` para CSV (robusto y pequeño). Si no se quiere agregar dependencia, implementar parser propio con tests; no se recomienda.

### 5.3 Validación en cliente

| Campo | Regla | Acción |
|---|---|---|
| `latitude` | numérico, −90..90 | rechazar fila |
| `longitude` | numérico, −180..180 | rechazar fila |
| `acq_date` | `YYYY-MM-DD`, 2000-01-01..hoy+1 | rechazar fila |
| `acq_time` | `HHMM` o `HH:MM` | si falta, `0000` + warning |
| `confidence` | MODIS 0–100 o VIIRS `l/n/h` | rechazar si no se reconoce |
| `frp` | numérico ≥ 0 | si falta, NULL; si 0, flag |
| `scan`, `track` | numérico > 0 | si falta, área NULL; si 0, flag |
| `type` | 0..3 o vacío | vacío → NULL |
| `sensor` | allowlist | inferir o rechazar |
| `source` | `SP` / `NRT` | inferir o rechazar |

La validación del cliente es **preview**. El servidor repite todas las reglas.

### 5.4 Archivos y componentes

| Archivo | Tipo | Responsabilidad |
|---|---|---|
| `frontend/ChronosFire/src/components/ImportPanel.vue` | nuevo | Modal, drag & drop, formulario, preview, progreso |
| `frontend/ChronosFire/src/composables/useImport.ts` | nuevo | Estado `idle → parsing → preview → uploading → processing → ready/error`, polling, cleanup |
| `frontend/ChronosFire/src/utils/parseFirms.ts` | nuevo | CSV/JSON → `NormalizedRow[]` + errores |
| `frontend/ChronosFire/src/utils/harmonizePreview.ts` | nuevo | Reglas de preview (no autoritativas) |
| `frontend/ChronosFire/src/data/types.ts` | modificar | `ImportBatch`, `ImportPreview`, `ImportError`, `ImportStatus` |
| `frontend/ChronosFire/src/App.vue` | modificar | Montar panel, banner, dataset activo |
| `frontend/ChronosFire/src/composables/useChronos.ts` | modificar | `datasetId` y `baseUrl`; endpoints de import; anomalía deshabilitada |
| `frontend/ChronosFire/package.json` | modificar | Agregar `papaparse` (y `@types/papaparse` si aplica) |

### 5.5 Límites en cliente

- Antes de leer: `file.size > 10 * 1024 * 1024` → error inmediato.
- Antes de parsear: contar saltos de línea; si > 50.000, abortar.
- Después de parsear: si filas inválidas > 10%, no habilitar “Confirmar”.
- No cargar más de 5.000 puntos en el mapa de preview; avisar.

### 5.6 Criterios de aceptación — Fase 1

- [ ] Un CSV FIRMS de 1.000 filas se previsualiza sin tocar la base.
- [ ] Un JSON FIRMS de 1.000 filas se previsualiza correctamente.
- [ ] Un archivo de 11 MB se rechaza antes de leerlo.
- [ ] Un archivo con 50.001 filas se rechaza.
- [ ] Un CSV sin `latitude` muestra el error de columna faltante.
- [ ] Una latitud 95 muestra error por fila.
- [ ] El preview muestra conteos por sensor/confianza.
- [ ] “Confirmar” está deshabilitado con errores bloqueantes.
- [ ] No hay llamadas de escritura en esta fase.

---

## 6. Fase 2 — Backend y base de datos

### 6.1 Modelo de datos

Migración nueva: `backend/db/migraciones/add_imports.sql`.

```sql
-- PostgreSQL 17: gen_random_uuid() es nativo; no hace falta pgcrypto.
CREATE TABLE IF NOT EXISTS import_batches (
    import_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename         TEXT NOT NULL,
    content_type     TEXT,
    size_bytes       BIGINT NOT NULL,
    checksum_sha256  TEXT,
    profile          TEXT NOT NULL CHECK (profile IN ('firms_raw','chronos_export')),
    declared_sensor  TEXT,
    declared_source  TEXT CHECK (declared_source IN ('SP','NRT')),
    status           TEXT NOT NULL DEFAULT 'received'
                     CHECK (status IN ('received','validating','harmonizing','ready','error','expired')),
    rows_total       INTEGER,
    rows_valid       INTEGER,
    rows_rejected    INTEGER,
    rows_deduplicated INTEGER,
    warnings         JSONB NOT NULL DEFAULT '[]'::jsonb,
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '24 hours'
);

CREATE TABLE IF NOT EXISTS import_raw (
    import_id      UUID NOT NULL REFERENCES import_batches(import_id) ON DELETE CASCADE,
    row_no         INTEGER NOT NULL,
    sensor         TEXT,
    source         TEXT,
    latitude       DOUBLE PRECISION,
    longitude      DOUBLE PRECISION,
    acq_date       DATE,
    acq_time       SMALLINT,
    brightness     REAL,
    bright_t31     REAL,
    scan_km        REAL,
    track_km       REAL,
    frp_mw         REAL,
    confidence_raw TEXT,
    fire_type      SMALLINT,
    daynight       CHAR(1),
    satellite      TEXT,
    instrument     TEXT,
    reject_reason  TEXT,
    raw            JSONB NOT NULL,
    PRIMARY KEY (import_id, row_no)
);

CREATE TABLE IF NOT EXISTS import_rejects (
    import_id   UUID NOT NULL REFERENCES import_batches(import_id) ON DELETE CASCADE,
    row_no      INTEGER NOT NULL,
    field       TEXT,
    message     TEXT NOT NULL,
    raw         JSONB,
    PRIMARY KEY (import_id, row_no, field)
);

CREATE TABLE IF NOT EXISTS import_detections (
    import_id              UUID NOT NULL REFERENCES import_batches(import_id) ON DELETE CASCADE,
    sensor                 TEXT NOT NULL,
    source                 TEXT NOT NULL CHECK (source IN ('SP','NRT')),
    product_version        TEXT,
    satellite              TEXT,
    instrument             TEXT,
    acquired_at            TIMESTAMPTZ NOT NULL,
    acq_date               DATE NOT NULL,
    acq_time_utc           SMALLINT,
    latitude               DOUBLE PRECISION NOT NULL,
    longitude              DOUBLE PRECISION NOT NULL,
    brightness             REAL,
    bright_t31             REAL,
    scan_km                REAL,
    track_km               REAL,
    pixel_area_km2         REAL,
    frp_mw                 REAL,
    frp_density_wm2        REAL,
    confidence_raw         TEXT,
    confidence_norm        SMALLINT,
    confidence_class       TEXT CHECK (confidence_class IS NULL OR confidence_class IN ('low','nominal','high')),
    daynight               CHAR(1),
    fire_type              SMALLINT,
    is_vegetation          BOOLEAN,
    industrial_mask        BOOLEAN NOT NULL DEFAULT FALSE,
    is_vegetation_effective BOOLEAN,
    quality_flags          TEXT,
    harmonization_ver      TEXT NOT NULL DEFAULT 'upload-v1',
    PRIMARY KEY (import_id, sensor, source, latitude, longitude, acquired_at)
);

CREATE TABLE IF NOT EXISTS import_grid_daily (
    import_id      UUID NOT NULL REFERENCES import_batches(import_id) ON DELETE CASCADE,
    grid_id        TEXT NOT NULL,
    cell_lat       DOUBLE PRECISION NOT NULL,
    cell_lon       DOUBLE PRECISION NOT NULL,
    day            DATE NOT NULL,
    n_detections   INTEGER NOT NULL,
    fire_day       BOOLEAN NOT NULL,
    frp_sum_mw     REAL,
    frp_max_mw     REAL,
    sensors        TEXT[],
    confidence_max SMALLINT,
    PRIMARY KEY (import_id, grid_id, day)
);
```

Índices:

```sql
CREATE INDEX IF NOT EXISTS import_detections_date_idx
    ON import_detections (import_id, acq_date);
CREATE INDEX IF NOT EXISTS import_detections_geo_idx
    ON import_detections (import_id, latitude, longitude);
CREATE INDEX IF NOT EXISTS import_grid_daily_day_idx
    ON import_grid_daily (import_id, day);
CREATE INDEX IF NOT EXISTS import_grid_daily_fire_idx
    ON import_grid_daily (import_id, grid_id, day) WHERE fire_day;
CREATE INDEX IF NOT EXISTS import_batches_expires_idx
    ON import_batches (expires_at);
```

**No se materializa** `import_calendar_weekly` en el MVP: con ≤ 50.000 filas, la agregación mensual/semanal se calcula en lectura. Si se necesita, se agrega en una fase 2b.

### 6.2 Función de armonización

`backend/db/migraciones/harmonize_import.sql`:

```sql
CREATE OR REPLACE FUNCTION harmonize_import(p_import_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    -- 1. Marcar en proceso
    UPDATE import_batches
       SET status = 'harmonizing'
     WHERE import_id = p_import_id
       AND status IN ('received','validating');

    -- 2. Insertar rechazos desde import_raw
    INSERT INTO import_rejects (import_id, row_no, field, message, raw)
    SELECT import_id, row_no, 'row', reject_reason, raw
      FROM import_raw
     WHERE import_id = p_import_id
       AND reject_reason IS NOT NULL;

    -- 3. Insertar detecciones válidas, deduplicadas por píxel/instante
    INSERT INTO import_detections (...)
    WITH norm AS (
        SELECT
            r.*,
            CASE
                WHEN r.sensor = 'MODIS' THEN
                    CASE
                        WHEN r.confidence_raw ~ '^[0-9]+$' THEN r.confidence_raw::int
                        ELSE NULL
                    END
                ELSE
                    CASE lower(r.confidence_raw)
                        WHEN 'h' THEN 90
                        WHEN 'n' THEN 50
                        WHEN 'l' THEN 20
                        ELSE NULL
                    END
            END AS confidence_norm,
            (r.acq_date + make_time(r.acq_time / 100, r.acq_time % 100, 0))
                AT TIME ZONE 'UTC' AS acquired_at
        FROM import_raw r
        WHERE r.import_id = p_import_id
          AND r.reject_reason IS NULL
    ),
    dedup AS (
        SELECT *,
               row_number() OVER (
                   PARTITION BY sensor, source, latitude, longitude, acquired_at
                   ORDER BY frp_mw DESC NULLS LAST, brightness DESC NULLS LAST
               ) AS rn
        FROM norm
    )
    SELECT
        p_import_id,
        sensor,
        source,
        NULL,
        satellite,
        instrument,
        acquired_at,
        acq_date,
        acq_time,
        latitude,
        longitude,
        brightness,
        bright_t31,
        scan_km,
        track_km,
        scan_km * track_km,
        frp_mw,
        frp_mw / NULLIF(scan_km * track_km, 0),
        confidence_raw,
        confidence_norm,
        CASE
            WHEN confidence_norm >= 80 THEN 'high'
            WHEN confidence_norm >= 50 THEN 'nominal'
            ELSE 'low'
        END,
        daynight,
        fire_type,
        CASE
            WHEN fire_type = 0 THEN TRUE
            WHEN fire_type IS NULL THEN NULL
            ELSE FALSE
        END,
        FALSE,
        CASE
            WHEN fire_type = 0 THEN TRUE
            WHEN fire_type IS NULL THEN NULL
            ELSE FALSE
        END,
        NULLIF(concat_ws(',',
            CASE WHEN frp_mw = 0 THEN 'frp_zero' END,
            CASE WHEN brightness <= 250 THEN 'low_brightness' END,
            CASE WHEN fire_type IN (2,3) THEN 'industrial_or_offshore' END,
            CASE WHEN scan_km * track_km = 0 THEN 'bad_geometry' END
        ), ''),
        'upload-v1'
    FROM dedup
    WHERE rn = 1;

    -- 4. Aplicar máscara industrial solo a NRT
    UPDATE import_detections d
       SET industrial_mask = TRUE,
           is_vegetation_effective = FALSE
      FROM industrial_cells ic
     WHERE d.import_id = p_import_id
       AND d.source = 'NRT'
       AND ic.cell_lat_idx = round(d.latitude / 0.01)::int
       AND ic.cell_lon_idx = round(d.longitude / 0.01)::int;

    -- 5. Completar is_vegetation_effective para el resto
    UPDATE import_detections
       SET is_vegetation_effective = is_vegetation
     WHERE import_id = p_import_id
       AND is_vegetation_effective IS NULL;

    -- 6. Grid diario 0,05°
    INSERT INTO import_grid_daily (...)
    SELECT
        import_id,
        '0.05:' || CAST(round(latitude / 0.05) AS BIGINT) || ':' ||
                   CAST(round(longitude / 0.05) AS BIGINT),
        round(latitude / 0.05) * 0.05,
        round(longitude / 0.05) * 0.05,
        acq_date,
        count(*)::int,
        bool_or(
            confidence_class IN ('nominal','high')
            AND (is_vegetation_effective IS TRUE OR is_vegetation_effective IS NULL)
        ),
        sum(frp_mw) FILTER (
            WHERE confidence_class IN ('nominal','high')
              AND (is_vegetation_effective IS TRUE OR is_vegetation_effective IS NULL)
        ),
        max(frp_mw) FILTER (
            WHERE confidence_class IN ('nominal','high')
              AND (is_vegetation_effective IS TRUE OR is_vegetation_effective IS NULL)
        ),
        array_agg(DISTINCT sensor),
        max(confidence_norm)::smallint
    FROM import_detections
    WHERE import_id = p_import_id
    GROUP BY 1,2,3,4,5;

    -- 7. Cerrar batch
    UPDATE import_batches b
       SET status = 'ready',
           rows_total = (SELECT count(*) FROM import_raw WHERE import_id = p_import_id),
           rows_valid = (SELECT count(*) FROM import_detections WHERE import_id = p_import_id),
           rows_rejected = (SELECT count(DISTINCT row_no) FROM import_rejects WHERE import_id = p_import_id),
           rows_deduplicated = (
               SELECT count(*) FROM import_raw WHERE import_id = p_import_id AND reject_reason IS NULL
           ) - (SELECT count(*) FROM import_detections WHERE import_id = p_import_id),
           finished_at = now()
     WHERE b.import_id = p_import_id;

EXCEPTION WHEN OTHERS THEN
    UPDATE import_batches
       SET status = 'error',
           error_message = SQLERRM,
           finished_at = now()
     WHERE import_id = p_import_id;
    RAISE;
END;
$$;
```

**Notas de implementación:**

- El pseudocódigo anterior es para `profile='firms_raw'`. Para `chronos_export` debe existir una rama que use `confidence_class`, `fire_type` e `is_vegetation` provistos por el archivo, revalidando rangos y recalculando `pixel_area_km2` y `frp_density_wm2`.
- `sensor` y `source` deben llegar no nulos desde `ImportValidator`; si no, la fila se rechaza antes de insertar en `import_raw`.
- `acq_time` se normaliza a HHMM en Java; la función asume 0..2359 y minutos 0..59. El validador rechaza `2400` o minutos inválidos.
- La función se ejecuta en una transacción propia. El bloque `EXCEPTION` actualiza `status='error'`, pero si el caller hace rollback ese update se pierde; `ImportService` debe volver a marcar el error en una transacción separada.
- `POST /api/imports` responde `202` y encola `process(importId)` en un `TaskExecutor` acotado. El cliente hace polling a `GET /api/imports/{id}`. Si la app se reinicia, un job de arranque marca como `error` los batches en `harmonizing` con más de 10 minutos.
- La función se prueba con SQL y no se expone al cliente; `GRANT EXECUTE` solo a `chronos_ingest`.

### 6.3 Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/imports` | Sube y armoniza. `multipart/form-data`: `file`, `profile`, `sensor?`, `source?` |
| `GET` | `/api/imports/{id}` | Estado, conteos, warnings, errores |
| `DELETE` | `/api/imports/{id}` | Borra la carga y sus agregados |
| `GET` | `/api/imports/{id}/meta` | Rango de fechas, sensores, conteos |
| `GET` | `/api/imports/{id}/detections` | Paginado, con bbox/fechas/sensor/confianza |
| `GET` | `/api/imports/{id}/detections.geojson` | FeatureCollection |
| `GET` | `/api/imports/{id}/calendar/daily` | Serie diaria desde `import_grid_daily` |
| `GET` | `/api/imports/{id}/calendar/monthly` | Agregación mensual |
| `GET` | `/api/imports/{id}/heatmap` | Celdas 0,05° |
| `GET` | `/api/imports/{id}/export.csv` | CSV de detecciones o celdas |

**Request `POST`:**

```http
POST /api/imports
Content-Type: multipart/form-data

file=@fire_nrt_SV-C2.csv
profile=firms_raw
sensor=VIIRS_SNPP
source=NRT
```

**Response `202`:**

```json
{
  "importId": "5f0c2e6a-...",
  "status": "received",
  "profile": "firms_raw",
  "sensor": "VIIRS_SNPP",
  "source": "NRT",
  "createdAt": "2026-11-15T12:00:00Z",
  "expiresAt": "2026-11-16T12:00:00Z"
}
```

**Response `GET /api/imports/{id}`:**

```json
{
  "importId": "5f0c2e6a-...",
  "status": "ready",
  "filename": "fire_nrt_SV-C2.csv",
  "profile": "firms_raw",
  "sensor": "VIIRS_SNPP",
  "source": "NRT",
  "rowsTotal": 12000,
  "rowsValid": 11840,
  "rowsRejected": 160,
  "rowsDeduplicated": 12,
  "warnings": ["time_missing: 12 filas", "industrial_mask: 4 filas"],
  "errors": [
    { "row": 42, "field": "latitude", "message": "fuera de rango (−90..90)" }
  ],
  "dateMin": "2026-01-01",
  "dateMax": "2026-03-31",
  "createdAt": "2026-11-15T12:00:00Z",
  "finishedAt": "2026-11-15T12:00:04Z",
  "expiresAt": "2026-11-16T12:00:00Z"
}
```

**Códigos de error:**

| Código | Cuándo |
|---:|---|
| `400` | multipart inválido o falta `file` |
| `413` | archivo > 10 MB |
| `415` | extensión/MIME no soportado |
| `422` | columnas faltantes, > 50.000 filas, > 10% rechazos |
| `429` | rate limit por IP |
| `503` | `app.import.enabled=false` |

### 6.4 Configuración

`backend/src/main/resources/application.properties`:

```properties
# Multipart
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=12MB
server.tomcat.max-swallow-size=12MB

# Importación
app.import.enabled=${IMPORT_ENABLED:false}
app.import.max-file-size=10MB
app.import.max-rows=50000
app.import.max-columns=40
app.import.max-errors=100
app.import.ttl-hours=24
app.import.rate-limit-per-hour=3
app.import.token=${IMPORT_TOKEN:}
```

### 6.5 Roles y datasources

```sql
-- Rol de lectura (existente o a crear)
CREATE ROLE chronos_reader LOGIN PASSWORD '...';
GRANT CONNECT ON DATABASE chronos_fire TO chronos_reader;
GRANT USAGE ON SCHEMA public TO chronos_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO chronos_reader;

-- Rol de importación
CREATE ROLE chronos_ingest LOGIN PASSWORD '...';
GRANT CONNECT ON DATABASE chronos_fire TO chronos_ingest;
GRANT USAGE ON SCHEMA public TO chronos_ingest;
GRANT INSERT, UPDATE, DELETE ON import_batches, import_raw, import_rejects,
      import_detections, import_grid_daily TO chronos_ingest;
GRANT SELECT ON industrial_cells TO chronos_ingest;
GRANT EXECUTE ON FUNCTION harmonize_import(UUID) TO chronos_ingest;
```

En Java: `IngestDataSourceConfig` con `@Bean @Qualifier("ingestDataSource")` y `@Bean @Qualifier("ingestJdbc")`, leyendo `app.import.datasource.url/username/password`. El datasource principal sigue con `chronos_reader`.

### 6.6 Limpieza y rate limit

- `ImportCleanupJob` con `@Scheduled(fixedDelay = 3600000)`: borra `import_batches` con `expires_at < now()`; el `ON DELETE CASCADE` limpia todo.
- `ImportRateLimiter` con Caffeine (ya está en `pom.xml`): clave IP + ventana de 1 hora; 3 intentos.
- `ImportTokenFilter` opcional: si `app.import.token` no está vacío, exigir header `X-Import-Token` en `POST` y `DELETE`.

### 6.7 Endpoints de lectura

Reutilizar los DTOs existentes (`Detection`, `DetectionPage`, `DailyRow`, `MonthlyCell`, `HeatmapCell`, `GeoJsonFeatureCollection`) y agregar un `WHERE import_id = ?` en repositorios nuevos:

- `ImportDetectionRepository`: mismas columnas que `v_detections_analysis`, pero desde `import_detections`.
- `ImportCalendarRepository`: `import_grid_daily` para daily/heatmap/monthly.
- `ImportExportService`: CSV con `\uFEFF`, escape de comillas y **sanitización de fórmulas** (`=`, `+`, `-`, `@` al inicio de celda).

La anomalía mensual:

```text
Si el import tiene < 7 años distintos con datos → anomaly = null, confidence = insufficient.
Si tiene >= 7 → misma fórmula que el oficial, pero baseline propio del import.
Nunca mezclar baseline oficial con observado del import.
```

### 6.8 Archivos a crear/modificar

| Archivo | Acción |
|---|---|
| `backend/db/migraciones/add_imports.sql` | crear |
| `backend/db/migraciones/harmonize_import.sql` | crear |
| `backend/src/main/java/.../controller/ImportController.java` | crear |
| `backend/src/main/java/.../service/ImportService.java` | crear |
| `backend/src/main/java/.../service/ImportParser.java` | crear |
| `backend/src/main/java/.../service/ImportValidator.java` | crear |
| `backend/src/main/java/.../repository/ImportRepository.java` | crear |
| `backend/src/main/java/.../repository/ImportDetectionRepository.java` | crear |
| `backend/src/main/java/.../repository/ImportCalendarRepository.java` | crear |
| `backend/src/main/java/.../config/IngestDataSourceConfig.java` | crear |
| `backend/src/main/java/.../config/ImportCleanupJob.java` | crear |
| `backend/src/main/java/.../web/ImportExceptionHandler.java` | crear |
| `backend/src/main/java/.../web/ImportTokenFilter.java` | crear (opcional, si `app.import.token` no está vacío) |
| `backend/src/main/java/.../dto/ImportBatch.java` y `ImportStatus.java` | crear |
| `backend/src/main/resources/application.properties` | modificar |
| `backend/src/main/java/.../config/CorsConfig.java` | modificar |
| `backend/pom.xml` | modificar (`commons-csv`) |
| `backend/openapi/chronosfire-api.yaml` | modificar |
| `backend/CONTRATO_API.md` | modificar |
| `backend/README.md` | modificar |
| `README.md` | modificar |
| `docs/DECISIONES.md` | modificar |

### 6.9 Criterios de aceptación — Fase 2

- [ ] `POST /api/imports` con 1.000 filas devuelve `202` y termina en `ready`.
- [ ] `import_detections` tiene exactamente las filas válidas y deduplicadas.
- [ ] `import_grid_daily` tiene `fire_day` correcto.
- [ ] La máscara industrial se aplica solo a NRT.
- [ ] `n_fire_years`/`n_activity_years` usan `NULL` cuando no hay datos, no `0`.
- [ ] `GET /api/imports/{id}/detections` devuelve paginado y filtrado.
- [ ] `GET /api/imports/{id}/calendar/monthly` devuelve meses con `anomaly=null` si < 7 años.
- [ ] `DELETE /api/imports/{id}` borra todo en cascada.
- [ ] Los conteos de las tablas oficiales no cambian antes/después.
- [ ] El rol `chronos_reader` no puede escribir.
- [ ] El rol `chronos_ingest` no puede escribir en `detections`.
- [ ] Un archivo de 11 MB devuelve `413`.
- [ ] Un archivo con 50.001 filas devuelve `422`.
- [ ] Un archivo con 20% de filas inválidas se rechaza completo.
- [ ] La limpieza automática borra imports vencidos.

---

## 7. Reglas de armonización (fuente de verdad)

| Regla | Implementación en import |
|---|---|
| Sensor | `declared_sensor` o inferido de `satellite`/nombre; allowlist |
| Fuente | `declared_source` o `SP` si hay `type`, si no `NRT` con warning |
| Tiempo | `acquired_at = acq_date + acq_time` en UTC; `acq_date` UTC |
| Confianza MODIS | numérica 0–100 → `high >= 80`, `nominal >= 50`, resto `low` |
| Confianza VIIRS | `h→90/high`, `n→50/nominal`, `l→20/low` |
| Área de píxel | `scan_km * track_km`; NULL si falta |
| FRP | `frp_mw`; `frp_density_wm2 = frp / área` |
| Tipo | `type` 0..3; NRT sin `type` → NULL |
| Vegetación | `type=0 → TRUE`; `type` NULL → NULL; resto FALSE |
| Industrial | Solo NRT; join a `industrial_cells` 0,01°; no se borra la detección |
| `quality_flags` | `frp_zero`, `low_brightness`, `industrial_or_offshore`, `bad_geometry` |
| Dedup | `(sensor, source, lat, lon, acquired_at)`, conservar mayor FRP |
| Grilla | 0,05° con la misma fórmula oficial |
| `fire_day` | `bool_or(conf_ok AND veg_ok)` |
| Semanas | ISO con `isoyear`; 53 semanas solo si se materializa calendario |
| Sin datos | `NULL`, nunca `0`, en agregados de import |
| FRP agregado | Por sensor; no sumar sensores en una métrica única |
| Anomalía | Solo con ≥ 7 años; nunca mezclar baseline oficial |

---

## 8. Límites y configuración

| Límite | Valor | Cliente | Servidor |
|---|---:|---|---|
| Tamaño de archivo | 10 MB | sí | `max-file-size` |
| Request total | 12 MB | — | `max-request-size` |
| Filas | 50.000 | sí | validador |
| Columnas | 40 | sí | validador |
| Errores mostrados | 100 | sí | validador |
| Rechazos permitidos | 10% | sí | validador |
| Imports por IP/hora | 3 | — | Caffeine |
| TTL | 24 h | — | job |
| Timeout de parseo | 30 s | — | `statement_timeout` + futuro |
| Timeout de armonización | 60 s | — | `statement_timeout` |

---

## 9. Seguridad

- **No hay autenticación actual.** El plan no la resuelve completa; agrega:
  - `app.import.enabled=false` por defecto;
  - token opcional `X-Import-Token`;
  - rate limit por IP;
  - CORS restringido a orígenes configurados.
- **Nunca confiar en el cliente:** el servidor reparsea y revalida.
- **Sin SQL dinámico:** todo parametrizado; la función SQL no recibe nombres de tabla.
- **Sin path traversal:** el archivo se procesa en memoria/stream; no se guarda en disco con el nombre del usuario.
- **Sniffing:** validar extensión y contenido (primeros bytes `{`/`[` para JSON; cabecera CSV).
- **CSV injection:** al exportar, prefijar `'` a celdas que empiecen con `=`, `+`, `-`, `@`.
- **Timeouts:** `SET LOCAL statement_timeout` en la transacción de armonización.
- **Límites de memoria:** no cargar el archivo completo en un `String`; usar streaming.
- **Auditoría:** `import_batches` guarda checksum, tamaño, perfil, fechas y estado.

---

## 10. Integración con el frontend existente

### `useChronos.ts`

- Agregar `const datasetId = ref<string | null>(null)`.
- `const apiBase = computed(() => datasetId.value ? \`/api/imports/${datasetId.value}\` : '/api')`.
- Reemplazar las rutas fijas:
  - `/api/calendar/monthly` → `${apiBase}/calendar/monthly`.
  - `/api/calendar/daily` → `${apiBase}/calendar/daily`.
  - `/api/detections` → `${apiBase}/detections`.
  - `/api/meta` → para import, `GET /api/imports/{id}/meta`.
- Para import:
  - `coverage_known = false`.
  - `anomaly = null`, `confidence = 'insufficient'`, `reference_years = 0` salvo ≥ 7 años.
  - `seriesLabel` incluye “datos de usuario”.
- `initialize()`: si hay `datasetId`, cargar meta del import y fijar `from/to`.
- `exportURL` y `hotspotsExportURL` usan `apiBase`.

### `App.vue`

- Botón “Cargar datos” en `AppHeader` o `HeroSection`.
- `<ImportPanel @ready="onImportReady" @close="..." />`.
- Banner fijo cuando `datasetId != null`: “Datos cargados por el usuario — no oficiales”.
- Botón “Volver a datos oficiales”: `datasetId = null; initialize()`.
- Mantener la vista oficial intacta si no hay import.

### `FireMap.vue`

- Aceptar `geojsonUrl` o `points` genéricos; no hardcodear `/api/detections.geojson`.
- Para import, usar `/api/imports/{id}/detections.geojson`.

### `MonthComparison` / `KpiStrip`

- Mostrar “— / sin baseline” en import si no hay anomalía.
- No mostrar “0” donde no hay datos.

---

## 11. Tests

### Backend

- `ImportParserTest`: CSV con BOM, `;`, comillas, campos vacíos, JSON array, JSON inválido, nesting.
- `ImportValidatorTest`: lat/lon, fechas, confianza, FRP, scan/track, sensor/source.
- `HarmonizationSqlTest` (Testcontainers PostgreSQL):
  - mapeo de confianza MODIS/VIIRS;
  - `acquired_at` UTC;
  - `pixel_area_km2` y `frp_density_wm2`;
  - dedup por píxel/instante;
  - máscara industrial solo NRT;
  - `fire_day` con `veg_ok AND conf_ok`;
  - `NULL` en agregados sin datos.
- `ImportControllerTest` (MockMvc):
  - `202` con archivo válido;
  - `413` con 11 MB;
  - `422` con columnas faltantes / 50.001 filas / >10% rechazos;
  - `415` con `.txt`;
  - `429` con rate limit;
  - `503` con feature flag off.
- `ImportCorsTest`: preflight `OPTIONS` con `Access-Control-Request-Method: POST`.
- `ImportReadTest`: detections/geojson/daily/monthly/heatmap/export.
- `ImportCleanupTest`: batch vencido se borra.
- `OfficialDataRegressionTest`: counts de `detections`, `grid_daily`, `calendar_weekly` iguales antes/después.

### Frontend

- `parseFirms` unit tests (Vitest o tests de Node).
- `tests/import.spec.mjs` (Playwright):
  - abrir panel;
  - subir fixture;
  - ver preview;
  - confirmar;
  - ver banner y mapa;
  - volver a oficiales;
  - eliminar carga.
- `tests/api-contract.mjs`: agregar endpoints de import (si el backend está activo).

### Fixtures

- `backend/src/test/resources/imports/firms_sample.csv` (100 filas reales anonimizadas).
- `backend/src/test/resources/imports/firms_sample.json`.
- `backend/src/test/resources/imports/chronos_export.csv`.
- `backend/src/test/resources/imports/bad_lat.csv`.
- `backend/src/test/resources/imports/bad_columns.csv`.
- `frontend/ChronosFire/tests/fixtures/firms_sample.csv`.

### 11.1 Baseline de regresión

Estos valores se midieron en `chronos_fire` el 2026-11-15 y **deben quedar idénticos** después de implementar y usar la importación:

| Tabla | Filas |
|---|---:|
| `detections` | 7.082.474 |
| `grid_daily` | 2.103.053 |
| `grid_weekly` | 1.575.089 |
| `grid_weekly_sensor` | 2.265.477 |
| `calendar_weekly` | 3.719.116 |
| `industrial_cells` | 885 |
| `coverage_daily` | 0 |

Chequeo obligatorio en CI/local:

```sql
SELECT 'detections' t, count(*) n FROM detections
UNION ALL SELECT 'grid_daily', count(*) FROM grid_daily
UNION ALL SELECT 'grid_weekly', count(*) FROM grid_weekly
UNION ALL SELECT 'grid_weekly_sensor', count(*) FROM grid_weekly_sensor
UNION ALL SELECT 'calendar_weekly', count(*) FROM calendar_weekly
UNION ALL SELECT 'industrial_cells', count(*) FROM industrial_cells
UNION ALL SELECT 'coverage_daily', count(*) FROM coverage_daily;
```

---

## 12. Plan de trabajo

| Hito | Tareas | Archivos | Esfuerzo | Depende de |
|---|---|---|---:|---|
| **M0 — Contrato y DDL** | Definir perfiles, columnas, errores; escribir migración; feature flag | `add_imports.sql`, `application.properties` | 0,5 d | — |
| **M1 — Cliente preview** | Parser CSV/JSON, validador, preview, modal, límites | `ImportPanel.vue`, `useImport.ts`, `parseFirms.ts`, `harmonizePreview.ts` | 1,5 d | M0 |
| **M2 — Backend y armonización** | Parser servidor, validador, `ImportService`, `ImportRepository`, función SQL, endpoints de escritura/estado | `ImportController`, servicios, repos, `harmonize_import.sql` | 2,5 d | M0 |
| **M3 — Lectura y frontend** | Endpoints de lectura, `datasetId`, banner, mapa/tabla/calendario, export | `ImportDetectionRepository`, `ImportCalendarRepository`, `useChronos.ts`, `App.vue` | 1,5 d | M2 |
| **M4 — Tests y hardening** | Unitarios, Testcontainers, MockMvc, Playwright, CORS, rate limit, limpieza | tests, fixtures | 1,5 d | M3 |
| **M5 — Docs y demo** | OpenAPI, README, `DECISIONES.md`, guion de demo | docs | 0,5 d | M4 |

**Total:** 8 días.  
**MVP recortado:** M0 + M1 + M2 básico + M3 mínimo = 4–5 días (sin rate limit fino, sin tests de Testcontainers, sin export).

### Recorte recomendado si hay poco tiempo

| Must | Should | Could |
|---|---|---|
| CSV FIRMS, 10 MB/50k, preview, persistencia, mapa/tabla | JSON, export CSV, delete, TTL | Anomalía con ≥7 años, edición de filas, múltiples imports simultáneos |

---

## 13. Criterios de aceptación global

- [ ] El usuario puede subir un CSV/JSON y ver una vista previa sin escribir en la base.
- [ ] Al confirmar, la carga queda en `import_*`, nunca en `detections`.
- [ ] La UI muestra mapa, tabla, serie diaria y mensual de la carga.
- [ ] La UI marca claramente “datos de usuario, no oficiales”.
- [ ] La UI puede volver a los datos oficiales sin recargar la página.
- [ ] Los conteos oficiales no cambian.
- [ ] Se aplican las mismas reglas de confianza, dedup, grilla, `fire_day` y máscara industrial.
- [ ] Los agregados de import usan `NULL` para “sin datos”, no `0`.
- [ ] Los límites se cumplen en cliente y servidor.
- [ ] Los errores se muestran por fila/campo.
- [ ] La carga se puede borrar y expira a las 24 h.
- [ ] El backend oficial sigue funcionando con `chronos_reader`.
- [ ] La documentación y el OpenAPI están actualizados.

---

## 14. Riesgos y mitigaciones

| Riesgo | Impacto | Prob. | Mitigación |
|---|---|---|---|
| Contaminar el dataset oficial | Alto | Alta | `import_id` + tablas separadas + regression test |
| Colisión de PK | Alto | Media | PK con `import_id` |
| Agregados costosos | Medio | Media | 50k filas + `import_grid_daily` + sin calendario materializado |
| Memoria por archivos grandes | Medio | Media | 10 MB + streaming |
| Sin autenticación | Alto | Alta | feature flag + token + rate limit + local-only |
| CORS bloquea POST | Medio | Alta | mapping específico + test de preflight |
| Multipart 1 MB por defecto | Medio | Alta | configuración explícita |
| NRT sin `type` | Medio | Alta | máscara industrial + warning |
| Falsos positivos industriales | Medio | Media | marcar, no borrar; mostrar flag |
| Zona horaria | Bajo | Media | UTC obligatorio; warning si falta hora |
| `0` vs `NULL` | Alto | Alta | usar NULL en import; test explícito |
| “Todos” dominado por VIIRS | Medio | Alta | etiquetar; permitir filtro por sensor |
| Anomalía engañosa | Alto | Alta | deshabilitar salvo ≥7 años |
| CSV injection | Medio | Media | sanitizar export |
| JSON anidado malicioso | Medio | Baja | límite de profundidad/tamaño |

---

## 15. Rollback y operación

- **Feature flag:** `app.import.enabled=false` desactiva `POST/DELETE` y oculta el botón.
- **Borrar una carga:** `DELETE /api/imports/{id}` + `ON DELETE CASCADE`.
- **Borrar todo el plano de importación:**
  ```sql
  DROP TABLE IF EXISTS import_grid_daily, import_detections, import_rejects, import_raw, import_batches CASCADE;
  DROP FUNCTION IF EXISTS harmonize_import(UUID);
  ```
- **TTL:** job cada hora; `expires_at`.
- **Logs:** `import_id`, estado, filas, duración, errores.
- **Métricas sugeridas:** imports/día, filas aceptadas, rechazos, duración p95, tamaño promedio.
- **No hay migración destructiva:** las tablas oficiales no se tocan.

---

## 16. Entregables

1. Migración `add_imports.sql`.
2. Función `harmonize_import.sql`.
3. Backend: controller, servicios, repositorios, datasource, job, exception handler.
4. Frontend: panel, composable, parser, tipos, integración.
5. Tests backend y frontend + fixtures.
6. OpenAPI actualizado.
7. Documentación:
   - `backend/CONTRATO_API.md`;
   - `backend/README.md`;
   - `README.md`;
   - `docs/DECISIONES.md`;
   - este plan.
8. Guion de demo:
   - subir un CSV FIRMS de 1.000 filas;
   - mostrar preview;
   - confirmar;
   - mostrar mapa/tabla;
   - volver a oficiales;
   - borrar.

---

## 17. Anexos

### A. Columnas aceptadas — perfil `firms_raw`

```text
latitude, longitude, acq_date, acq_time, confidence, frp,
scan, track, brightness, bright_t31, type, daynight,
satellite, instrument
```

### B. Columnas aceptadas — perfil `chronos_export`

```text
sensor, source, acquired_at, acq_date, latitude, longitude,
confidence_raw, confidence_norm, confidence_class, fire_type,
is_vegetation, frp_mw, frp_density_wm2, pixel_area_km2,
quality_flags, product_version
```

### C. Ejemplo `curl`

```bash
curl -X POST http://127.0.0.1:8080/api/imports \
  -H "X-Import-Token: demo" \
  -F "file=@fire_nrt_SV-C2.csv" \
  -F "profile=firms_raw" \
  -F "sensor=VIIRS_SNPP" \
  -F "source=NRT"
```

```bash
curl http://127.0.0.1:8080/api/imports/5f0c2e6a-.../detections?limit=100
```

```bash
curl http://127.0.0.1:8080/api/imports/5f0c2e6a-.../calendar/monthly?from=2026-01-01&to=2026-03-31&sensor=ALL
```

### D. Definición de terminado (DoD)

- [ ] Código compila (`mvn test`, `npm run build`).
- [ ] Tests nuevos pasan.
- [ ] Conteos oficiales sin cambios.
- [ ] Documentación actualizada.
- [ ] Demo reproducible con un fixture.
- [ ] Feature flag documentado.
- [ ] Plan de rollback probado.

---

**Fin del plan.**
