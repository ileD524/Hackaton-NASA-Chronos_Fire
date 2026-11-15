# Contrato de API - Chronos Fire

**Fase:** 0-6 implementadas: backend completo y frontend adaptado al contrato Java.  
**Fuente de verdad:** PostgreSQL local `chronos_fire`.  
**Restriccion:** el backend no modifica la base. Solo `SELECT`. No crea tablas, columnas, indices, vistas ni datos.

El contrato completo esta en:

```text
backend/openapi/chronosfire-api.yaml
```

Swagger UI (cuando la app corre):

```text
http://127.0.0.1:8080/swagger-ui.html
```

---

## 1. Estado de implementacion

| Endpoint | Estado |
|---|---|
| `GET /api/health` | ✅ implementado |
| `GET /api/meta` | ✅ implementado |
| `GET /api/calendar/weekly` | ✅ implementado |
| `GET /api/calendar/daily` | ✅ implementado |
| `GET /api/calendar/heatmap` | ✅ implementado |
| `GET /api/calendar/monthly` | ✅ implementado |
| `GET /api/calendar/trend` | ✅ implementado |
| `GET /api/calendar/export.csv` | ✅ implementado |
| `GET /api/alerts` | ✅ implementado |
| `GET /api/alerts/geojson` | ✅ implementado |
| `GET /api/detections` | ✅ implementado |
| `GET /api/detections/geojson` | ✅ implementado |
| `GET /api/detections/export.csv` | ✅ implementado |
| `GET /api/aois` | ✅ implementado |
| `GET /api/validation` | ✅ implementado |

### Fase 2 - `/api/calendar/weekly`

Parametros:

```text
bbox=oeste,sur,este,norte   (opcional; default: extension completa)
year=2020                    (requerido)
week=40                      (requerido)
sensor=AUTO                  (AUTO, MODIS, VIIRS_SNPP, VIIRS_NOAA20, VIIRS_NOAA21)
```

Reglas:

- `AUTO` usa MODIS 2003-2011 y VIIRS SNPP 2013-2026.
- 2012 devuelve `anomaly = null` y `confidence = insufficient`.
- 2000-2002 no se usan para la anomalia.
- `ALL` se rechaza en weekly.
- `sensor` y baseline son el mismo para evitar mezclar eras.
- `baselineFireCells` es la mediana leave-one-out.
- `anomaly` solo se calcula con `nRefYears >= 7`, `baseline >= 3` y `observed >= 3`.
- `provisional = true` para 2026 o semanas con NRT.
- `nDataYears` queda `null` porque `coverage_daily` esta vacia.
- `fireCellsBySensor`, `detectionsBySensor` y `frpBySensor` incluyen todos los sensores presentes.
- `fireDayRateGivenDetection = fireDays / detectionCellDays`.

Ejemplo verificado (Delta 2020, semana 40):

```json
{
  "year": 2020,
  "week": 40,
  "observedFireCells": 66,
  "baselineFireCells": 10.0,
  "anomaly": 6.6,
  "referenceYears": 12,
  "referenceWindow": "2013-2025",
  "observedSensor": "VIIRS_SNPP",
  "baselineSensor": "VIIRS_SNPP",
  "nCellsAoi": 935,
  "confidence": "high",
  "provisional": false,
  "dataStatus": "observed",
  "nDataYears": null,
  "fireCellsBySensor": { "MODIS": 36, "VIIRS_SNPP": 66, "VIIRS_NOAA20": 67 },
  "detectionsBySensor": { "MODIS": 258, "VIIRS_SNPP": 1242, "VIIRS_NOAA20": 855 },
  "fireDayRateGivenDetection": 0.865
}
```

### Fase 3 - diario, heatmap, mensual y tendencia

| Endpoint | Parametros | Semantica |
|---|---|---|
| `GET /api/calendar/daily` | `bbox`, `from`, `to`, `sensor`, `limit` | Filas observadas de `grid_daily` con `hotspots` armonizados; `fireDay` sigue siendo all-sensor; `sensor` filtra `sensors` |
| `GET /api/calendar/heatmap` | `bbox`, `from`, `to`, `sensor`, `limit` | Agrega celdas 0,05°; `anomaly` queda `null` porque la anomalía es por AOI |
| `GET /api/calendar/monthly` | `bbox`, `from`, `to`, `sensor` | Espina de meses; celdas, hotspots, detecciones, baseline/anomalia y desglose por sensor; `ALL` no es promedio |
| `GET /api/calendar/trend` | `bbox`, `from`, `to`, `sensor` | Serie anual; `AUTO` usa la era de cada año; baseline anual leave-one-out |

Reglas:

- `daily` y `heatmap` aceptan rangos de hasta 366 días.
- `monthly` y `trend` aceptan 2000-2026.
- `limit` por defecto 5.000; máximo 50.000.
- `partial = true` cuando el rango corta el mes.
- `dataStatus = unknown` cuando no hay filas para ese mes.
- `trend` devuelve `baselineFireCells = null` en 2000-2002 y 2012.
- `trend` usa `provisional = true` para 2026.
- `sensor=AUTO` se acepta como `ALL` en daily/heatmap/monthly.
- En `/api/calendar/trend`, `ALL` no es válido; usar `AUTO` o un sensor específico.
- En `daily`, `source` y `frpDensityMaxWm2` quedan `null` porque `grid_daily`
  no guarda esos campos.
- En `heatmap`, `anomaly` queda `null`: la anomalía es por AOI, no por celda.
- En `monthly` y `daily`, `fireCells`, `fireDays`, `hotspots`,
  `hotspotsBySensor` y `detectionsBySensor` respetan el filtro de sensor.
  `fire_day` sigue siendo all-sensor: `fireCells(sensor)` cuenta celdas con
  actividad en dias en que ese sensor tuvo al menos una deteccion, no celdas
  atribuibles en exclusiva a ese sensor. `AUTO` se trata como `ALL` y `ALL` no
  es un promedio: la serie esta dominada por VIIRS.
- `hotspots` cuenta detecciones de vegetacion efectiva, confianza
  nominal/alta y fuera de la mascara industrial. Es la misma definicion que
  `fire_day`, pero a nivel de deteccion. `nDetections`/`detectionsBySensor`
  son el conteo crudo; no son la metrica del calendario.
- En `monthly`, `baselineFireCells` es la mediana leave-one-out de
  `fireCells` con la era del sensor: MODIS 2003-2011, SNPP 2013-2025,
  NOAA-20 2019-2025, NOAA-21 2024-2025. Para `ALL` se excluyen 2000-2002,
  2012 (transicion) y 2026 (NRT).
- `anomaly = fireCells / baselineFireCells`. Es `null` si el mes es parcial,
  no tiene datos, tiene menos de 7 anos de referencia, `observed < 3` o
  `baseline < 3`. `confidence = high` con 10+ anos y baseline >= 10; `low` en
  el resto de los casos validos; `insufficient` cuando no se puede calcular.

Ejemplo (Delta 2020-09, SNPP, rango 2013-2026):

```json
{
  "year": 2020,
  "month": 8,
  "fireCells": 154,
  "hotspots": 3313,
  "baselineFireCells": 49.0,
  "anomaly": 3.14,
  "referenceYears": 12,
  "confidence": "high"
}
```

El baseline depende del rango solicitado: 2013-2023 da otra mediana y otra
anomalia que 2013-2026. La UI lo declara como "otros anos completos de la
seleccion".

Verificado:

- Delta 2020 septiembre: `daily` y `heatmap` responden en <0,2 s.
- Delta 2020 enero-marzo: `monthly` responde en ~0,2 s.
- Delta 2020-09 `monthly` por sensor: `ALL` 189 celdas / 618 dias /
  6.628 hotspots; `MODIS` 119 / 313 / 704; `VIIRS_SNPP` 154 / 496 / 3.313;
  `AUTO` se comporta como `ALL`.
- Delta 2020-09-15 `daily`: 20 filas de celda-dia, 76 hotspots; una celda
  puede mezclar sensores (`hotspotsBySensor` conserva el desglose).
- Delta 2020-09 anomalia mensual 2013-2026: SNPP `154 / 49,0 / 3,14` con
  12 referencias y `high`; `ALL` `189 / 62,0 / 3,05` con 12 y `high`.
  2012 queda `insufficient` aunque tenga 17 referencias; 2020 solo tambien
  queda `insufficient` por falta de referencias.
- Delta 2019-2020: `trend` devuelve 2019 = 0,96 y 2020 = 6,22.
- Sin bbox, `monthly` 2020 tarda ~5,6 s la primera vez y queda cacheado.

### Fase 4 - detecciones y exportaciones

| Endpoint | Parametros | Semantica |
|---|---|---|
| `GET /api/detections` | `bbox`, `from`, `to`, `sensor`, `confidence`, `type`, `source`, `vegetation`, `limit`, `offset` | Pagina sobre `v_detections_analysis`; usa la vista canonica SP/NRT |
| `GET /api/detections/geojson` | igual, sin `offset` | `FeatureCollection` con `Point` |
| `GET /api/detections/export.csv` | igual, sin `offset` | CSV UTF-8 con BOM |
| `GET /api/calendar/export.csv` | `bbox`, `from`, `to`, `sensor`, `limit` | CSV semanal desde `grid_weekly_sensor` |

Reglas:

- `detections` devuelve `total`, `limit` y `offset`.
- `limit` por defecto 1.000; máximo 50.000.
- Exportaciones: máximo 50.000 filas.
- `confidence` acepta `low`, `nominal`, `high`; se puede repetir o separar por coma.
- `vegetation=true` usa la misma logica que `v_vegetation_detections`.
- `source` acepta `SP` o `NRT`.
- `type` acepta 0-3.
- `bbox` por defecto es Argentina para proteger la consulta.
- `calendar/export.csv` incluye una fila por celda/semana/sensor.
- Fechas de exportacion semanal usan la semana ISO que contiene `from` y `to`.

Verificado:

- Detecciones MODIS Delta septiembre 2020: 200, 708 filas, 0,35 s.
- GeoJSON con `limit=1`: 200, 0,11 s.
- CSV de detecciones: `Content-Disposition` y cabecera correcta.
- CSV semanal: 200, 0,08 s.

### Fase 5 - alertas, AOIs y validacion

| Endpoint | Parametros | Semantica |
|---|---|---|
| `GET /api/alerts` | `bbox`, `days`, `limit` | NRT agrupado por celda 0,05° |
| `GET /api/alerts/geojson` | `bbox`, `days`, `limit` | Alertas como `FeatureCollection` |
| `GET /api/aois` | — | Catalogo de `validation_aoi` con ventanas |
| `GET /api/validation` | — | Eventos conocidos calculados con `grid_weekly_sensor` |

Reglas de alertas:

- Solo `source='NRT'`, últimos `days` (1-30).
- Excluye `industrial_mask`.
- Solo confianza `nominal` o `high`.
- Solo vegetación efectiva o NRT con confianza nominal/alta.
- `limit` por defecto 1.000; máximo 5.000.
- `asOf` es el instante de la consulta; `latestAvailable` es la última detección NRT.
- `aoiAnomaly` es la anomalía semanal del AOI para la semana ISO actual; puede ser `null`.
- Niveles:
  - `info`: 1-2 detecciones y sin anomalía relevante.
  - `watch`: >=3 detecciones o `aoiAnomaly >= 1,5`.
  - `warning`: >=10 detecciones o `aoiAnomaly >= 2,0`.
- `provisional = true` porque es NRT.
- `industrialMasked = false` porque los focos industriales se excluyen.

Validacion:

- Cada AOI usa su `observed_sensor` y su ventana `expected_peak`.
- El baseline y la anomalía salen de `grid_weekly_sensor` con leave-one-out.
- `verdict = pass` si `anomaly > 1` y la confianza no es insuficiente.
- La primera llamada tarda ~3 s; queda cacheada 10 minutos.

Verificado:

- Delta, 7 días: 5 alertas agrupadas; niveles `watch`/`info`; `aoiAnomaly = null` porque la semana actual no tiene baseline suficiente.
- `/api/aois`: 5 AOIs y 6 ventanas.
- `/api/validation`: 6 eventos, todos `pass`:
  - Cordoba 2013: 7,67
  - Corrientes 2022: 17,62
  - Delta 2020: 6,60
  - La Pampa 2016: 8,00
  - La Pampa 2017: 25,08
  - Salta 2013: 3,73

### Fase 6 - frontend y rendimiento

El frontend ya consume este contrato:

- Vite proxy `/api` → `http://127.0.0.1:8080`.
- Tipos y composables adaptados a `monthly`, `daily` y `heatmap`.
- Calendario de celdas de 0,05°, sin calibración.
- Paneles de alertas NRT y validación de eventos.
- Vista inicial: Delta del Paraná.
- `npm run build`, `npm run test:api` y `npm run test:browser` pasan.
- Backend Python/DuckDB retirado.

Rendimiento medido:

| Consulta | Primera | Cacheada |
|---|---:|---:|
| `alerts` Delta | 1,55 s | 0,01 s (60 s) |
| `alerts` nacional | 7,46 s | 0,01 s (60 s) |
| `validation` | 8,43 s | 0,02 s (10 min) |
| `monthly` Delta 2012–2026 | 0,70 s | 0,009 s |
| `daily` Delta agosto 2020 | 0,03 s | — |
| `heatmap` Delta agosto 2020 | 0,01 s | — |

La carga inicial del frontend con caches calientes fue ~1,3 s:
`meta` 107 ms, `monthly` 66 ms, `daily` 136 ms.

La consulta nacional mensual puede tardar ~14 s la primera vez; por eso la
demo inicia en el Delta.

---

## 2. Endpoints

| Metodo | Ruta | Fuente SQL | Uso |
|---|---|---|---|
| GET | `/api/health` | `SELECT 1`, ultima `acquired_at`, `pg_class` | Estado de la demo |
| GET | `/api/meta` | `detections`, `grid_*`, `industrial_cells` | Cobertura y definiciones |
| GET | `/api/calendar/weekly` | `grid_weekly_sensor`, `grid_weekly` | Titular semanal y anomalia |
| GET | `/api/calendar/daily` | `grid_daily` | Serie diaria |
| GET | `/api/calendar/heatmap` | `grid_daily` | Mapa de celdas 0,05° |
| GET | `/api/calendar/monthly` | `grid_daily`, `v_detections_analysis` | Agregacion mensual con celdas y hotspots |
| GET | `/api/calendar/trend` | `grid_weekly` | Serie anual |
| GET | `/api/calendar/export.csv` | `grid_weekly` | Descarga semanal |
| GET | `/api/alerts` | `v_detections_analysis` | Alertas NRT por celda |
| GET | `/api/alerts/geojson` | `v_detections_analysis` | Alertas en mapa |
| GET | `/api/detections` | `v_detections_analysis` | Detalle paginado |
| GET | `/api/detections/geojson` | `v_detections_analysis` | Puntos del mapa |
| GET | `/api/detections/export.csv` | `v_detections_analysis` | Detalle descargable |
| GET | `/api/aois` | `validation_aoi`, `validation_aoi_window` | Catalogo de AOIs |
| GET | `/api/validation` | `validation_aoi`, `grid_weekly_sensor` | Eventos validados |

---

## 3. Adaptacion del frontend

El frontend actual (`frontend/ChronosFire`) usa un modelo de 1 km² y calibracion que la base no tiene. Debe adaptarse a este contrato.

| Frontend actual | Nuevo contrato |
|---|---|
| celda-dia de 1 km² | celda de 0,05° (~5 km) |
| `active_cells_modis/viirs/union` | `fireCells` + `fireCellsBySensor` |
| `calibrated` | **se elimina** |
| `modis_equivalent_cells` | **se elimina** |
| `paired_days`, `paired_modis`, `paired_viirs`, `paired_calibrated` | **se eliminan** |
| `/api/calendar` mensual | `/api/calendar/weekly` + `/api/calendar/monthly` |
| `/api/map` | `/api/calendar/heatmap` + `/api/detections/geojson` |
| `/api/export` | `/api/calendar/export.csv` |
| 2003-2025 | 2000/2003-2026, con 2026 `provisional` |
| mediana en cliente | `baselineFireCells`, `referenceYears`, `confidence` desde el backend |
| `coverage_known: false` | `dataStatus` y `nDataYears` (hoy `null`/`unknown`) |

### 3.1. Mapeo por componente

| Componente | Cambio |
|---|---|
| `src/data/types.ts` | Reemplazar `Metric`, `MonthCell`, `DailyRow`, `MapData` por los DTOs del OpenAPI |
| `src/composables/useChronos.ts` | Nuevos endpoints y parametros |
| `FireCalendar.vue` | Semanas ISO 1-53 o meses con `fireCells`; quitar calibracion |
| `KpiStrip.vue` | `fireCells`, `nDetectionsBySensor`; no “celda-dia 1 km²” |
| `MonthComparison.vue` | `baselineFireCells`, `anomaly`, `referenceYears`, `confidence` |
| `DailyDetail.vue` | `fireDay`, `nDetections`, `sensors`, `frp` |
| `FireMap.vue` | `/api/calendar/heatmap` o `/api/detections/geojson` |
| `AreaOfInterest.vue` | `/api/aois`, bbox, fechas hasta 2026 |
| `MethodologyPanel.vue` | Nueva metodologia: 0,05°, sin calibracion, sin cobertura real |
| `tests/` | Reescribir contra el contrato Java |

### 3.2. Funciones que se eliminan

- `calibrated` y `serie_calibrada_2012_2025.csv`.
- `modis_equivalent_cells`, `modis_pi90_low`, `modis_pi90_high`.
- Comparacion pareada MODIS/VIIRS/calibrado.
- Celdas-dia de 1 km².
- `/api/map` con grupos de 5×5 km y 6.000 puntos.
- `firms.ts` si no se usa; contiene una `MAP_KEY` hardcodeada.

---

## 4. Semantica de datos

- `fire_day`: celda de 0,05° con al menos una deteccion de vegetacion nominal/alta en el dia UTC.
- `fire_cells`: celdas con actividad en el periodo.
- `n_detections`: detecciones FIRMS; no es cantidad de incendios.
- `hotspots`: detecciones de vegetacion efectiva, confianza nominal/alta y
  fuera de la mascara industrial; no es cantidad de incendios.
- `hotspotsBySensor`: el mismo conteo desglosado por sensor; no se suma entre
  sensores para presentar un promedio.
- `baselineFireCells`: mediana multianual del mismo mes, misma metrica y era
  del sensor, excluyendo el ano evaluado.
- `anomaly`: cociente observado/baseline; no es un porcentaje ni una
  prediccion.
- `referenceYears` y `confidence`: cantidad de anos y calidad del baseline.
- `anomaly = observedFireCells / baselineFireCells`.
- `baselineFireCells`: mediana multianual por AOI, leave-one-out.
- `confidence`: `high`, `low` o `insufficient`.
- `dataStatus`: `observed`, `no_data` o `unknown`.
- `nDataYears`: `null` porque `coverage_daily` esta vacia.
- `provisional`: `true` para 2026/NRT.
- `null` no es `0`.
- No sumar FRP ni detecciones entre sensores.

---

## 5. Limitaciones conocidas

| Limitacion | Efecto en la API |
|---|---|
| `coverage_daily` vacia | `nDataYears = null`, `dataStatus = unknown` |
| `n_data_years` NULL | No usar como confianza |
| `frp_p50/p90` NULL | No exponer percentiles |
| Sin calibracion | No hay `calibrated` |
| Grilla 0,05° | No hay celdas de 1 km² |
| 2026 NRT | `provisional = true` |
| Sin escritura | No hay endpoints de carga |

---

## 6. Criterios de aceptacion de fase 0 y 1

- [x] OpenAPI con los 15 endpoints.
- [x] `pom.xml` con Spring Boot, JDBC, PostgreSQL, Caffeine y Springdoc.
- [x] `GET /api/health` devuelve estado, version, latencia y ultima deteccion.
- [x] `GET /api/meta` devuelve sensores, fuentes, rangos, conteos, definiciones y limitaciones.
- [x] CORS configurable.
- [x] Errores estandarizados `{code, message, requestId}`.
- [x] Tests de controladores.
- [x] Sin DDL ni DML: solo `SELECT`.
- [x] Fase 2: `/api/calendar/weekly`.
- [x] Delta 2020 semana 40: 66 / 10 / 12 / 6,60 con SNPP-only.
- [x] `sensor=AUTO` selecciona MODIS 2003-2011 y SNPP 2013-2026.
- [x] `sensor=ALL` se rechaza en weekly porque no es una anomalía homogénea.
- [x] Semanas ISO inválidas y bbox inválidos devuelven 400.

---

## 7. Como correr

```powershell
$env:DB_PASSWORD = 'postgres'
mvn spring-boot:run
```

Abrir:

```text
http://127.0.0.1:8080/api/health
http://127.0.0.1:8080/api/meta
http://127.0.0.1:8080/swagger-ui.html
```
