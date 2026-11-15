# Informe de brechas y plan de implementación — Chronos Fire

**Fecha:** 15/11/2026  
**Alcance:** backend Java/Spring Boot, frontend Vue 3, contrato API y datos.  
**Método:** revisión de código, documentación, artefactos de build y consultas de solo lectura a PostgreSQL 17 (`chronos_fire`).  
**Restricción:** este documento es un plan. No modifica código, esquema ni datos.

---

## 1. Resumen ejecutivo

El proyecto ya cumple el núcleo del challenge: armonización MODIS + VIIRS, calendario web, anomalías contra una mediana multianual, mapa, alertas NRT y exportación CSV. Las brechas que quedan no son de pipeline, sino de **producto** y **reproducibilidad**.

### 1.1. Brechas que impiden cumplir el texto del challenge al pie de la letra

1. **El calendario no muestra “número de hotspots”.** Muestra celdas de 0,05° con actividad (`fire_cells`). Es una decisión metodológica defendible, pero el challenge pide un calendario con el número de hotspots detectados.
2. **No se pueden descargar visualizaciones.** Solo existe la descarga del CSV semanal. El objetivo pide “download analytics and visualizations”.
3. **El CSV de hotspots no está expuesto en la interfaz.** El endpoint existe, pero no hay botón que lo use.
4. **No hay selector de sensor.** La UI fija `metric: 'all'` y muestra una serie dominada por VIIRS. El backend sí soporta el filtro.
5. **El calendario arranca en 2012.** El rango por defecto oculta la serie 2003–2011, aunque el challenge pide “from the early 2000s”.

### 1.2. Brechas de calidad que afectan la defensa

6. **“Sin datos” no se distingue de “sin fuego”**: `coverage_daily` está vacía y `n_data_years` es `NULL` en el 100% de `calendar_weekly`.
7. **Conviven dos metodologías de anomalía**: el backend calcula leave-one-out semanal/anual; la UI calcula una mediana mensual en el cliente.
8. **El mapa suma detecciones entre sensores**, contradiciendo el principio de no sumar.
9. **La rampa de anomalía usa verde/rojo**, poco accesible para daltonismo.
10. **Documentación desactualizada**: `frontend/ChronosFire/docs/FUNCIONALIDAD.md` describe el backend Python/DuckDB y la calibración de 1 km², ya retirados.

### 1.3. Esfuerzo estimado

| Bloque | Alcance | Esfuerzo |
|---|---|---:|
| A. Quick wins | CSV de hotspots, rango 2003, etiquetas, público objetivo, documentación | 0,5 persona-día |
| B. Sensor + hotspots | Selector de sensor, filtro mensual, modo “hotspots”, tests | 1,5–2 persona-días |
| C. Accesibilidad y export | Rampa accesible, export SVG/PNG, tests | 1 persona-día |
| D. Robustez opcional | Anomalía mensual server-side, desglose en mapa, cobertura, reproducibilidad | 1–2 persona-días |
| **Total MVP (A+B+C)** | **Cumple el challenge sin salvedades** | **3–3,5 persona-días** |
| **Total con D** | **Defensa técnica reforzada** | **5–6 persona-días** |

---

## 2. Línea base verificada

| Elemento | Estado actual |
|---|---|
| Detecciones | 7.082.474 |
| Pares sensor/fuente | 8 |
| Rango | 2000-11-01 → 2026-11-14 |
| `grid_weekly_sensor` | 2.265.477 filas |
| `calendar_weekly` | 3.719.116 filas = 70.172 celdas × 53 semanas |
| `coverage_daily` | 0 filas |
| `n_data_years` | `NULL` en 3.719.116 filas (100%) |
| Delta 2020 semana 40, SNPP | 66 observadas / baseline 10 / 12 refs / anomalía 6,60 |
| Endpoints | 15 implementados |
| Tests backend | 19 aprobados, 0 fallos |
| Build | JAR y `dist/` generados |
| Frontend | Vue 3 + Leaflet, calendario mensual, mapa, alertas, validación |

---

## 3. Definiciones para no confundir métricas

Antes de implementar, el equipo debe fijar estas definiciones y usarlas en la UI, la API y la documentación:

| Término | Definición propuesta | No confundir con |
|---|---|---|
| **Hotspot / detección** | Registro FIRMS de un píxel con anomalía térmica. | Un incendio único. |
| **Celda con actividad (`fire_day`)** | Celda de 0,05° con al menos una detección de vegetación nominal/alta en un día UTC. | Número de hotspots. |
| **Hotspot armonizado** | Detección de vegetación efectiva, confianza nominal/alta y fuera de la máscara industrial. | Detección cruda (`detections`). |
| **Incendio / evento** | Agrupación de hotspots cercanos (<5 km) en el mismo día o días consecutivos. **No implementado.** | Hotspot o celda. |
| **“Todos los sensores”** | Unión de sensores, **no un promedio**. Está dominada por VIIRS. | Serie neutral. |

**Regla de oro:** nunca sumar detecciones ni FRP entre sensores en un mismo titular. Si se muestra un total, debe decir “todas las fuentes” y explicar que no es comparable.

---

## 4. Brechas priorizadas

| ID | Brecha | Impacto en el challenge | Prioridad | Dónde se resuelve |
|---|---|---|---|---|
| B-01 | `/api/calendar/monthly` no filtra `fire_cells` por sensor | El selector de sensor mostraría un titular inconsistente | P0 | Backend |
| B-02 | El conteo mensual de detecciones es crudo (incluye industriales y baja confianza) | “Número de hotspots” no sería el armonizado | P0 | Backend |
| B-03 | La UI no expone el CSV de hotspots | Requisito explícito de descarga | P0 | Frontend |
| B-04 | No hay selector de sensor en la UI | La serie visible no permite aislar la era | P0 | Frontend |
| B-05 | El calendario no tiene modo “hotspots” | Requisito literal del calendario | P0 | Backend + Frontend |
| B-06 | El calendario inicia en 2012 | “From the early 2000s” queda oculto | P0 | Frontend |
| B-07 | No hay export de visualizaciones | Objetivo de “download visualizations” | P0 | Frontend |
| B-08 | Rampa de anomalía verde/rojo | Accesibilidad | P1 | Frontend |
| B-09 | El mapa suma detecciones entre sensores | Coherencia metodológica | P1 | Backend + Frontend |
| B-10 | Anomalía mensual calculada en el cliente | Dos metodologías conviviendo | P1 | Backend |
| B-11 | `coverage_daily` vacía y `n_data_years` NULL | “Sin datos” ≠ “sin fuego” | P2 | Datos |
| B-12 | ETL con rutas absolutas y datos fuera del repo | Reproducibilidad para el jurado | P2 | Datos / DevOps |
| B-13 | Documentación desactualizada y código muerto | Riesgo de contradicción en la demo | P1 | Docs / Frontend |
| B-14 | CSV truncado silenciosamente a 50.000 filas | Descarga incompleta sin aviso | P2 | Backend + Frontend |

---

## 5. Plan de backend

### B-01. Filtrar `fire_cells` por sensor en `/api/calendar/monthly`

**Estado:** ✅ implementado el 15/11/2026.

**Problema:** `CalendarService.getMonthly()` llama a `repository.monthlyFire(bbox, from, to)` sin sensor. `monthlyDetections()` sí filtra, pero el titular `fireCells` no. Si el frontend agrega un selector, el calendario seguiría mostrando todos los sensores.

**Cambio propuesto:**

- Agregar `sensor` a `CalendarRepository.monthlyFire(...)`.
- Si `sensor != ALL`, agregar `AND CAST(? AS TEXT) = ANY(sensors)` sobre `grid_daily`.
- Documentar la semántica: `fireCells(sensor)` = “celdas con `fire_day` en días en que ese sensor tuvo al menos una detección”. Es la misma semántica que ya usan `/daily` y `/heatmap`.
- No cambiar el comportamiento de `ALL`.

**Alternativa más pura (no recomendada para el MVP):** calcular `fire_cells` por sensor desde `grid_weekly_sensor` y asignar cada semana al mes de su jueves. Introduce un corte semanal en un calendario mensual y complica la lectura.

**Contrato:**

```http
GET /api/calendar/monthly?bbox=-61,-34,-59.5,-32.5&from=2020-01-01&to=2020-12-31&sensor=VIIRS_SNPP
```

```json
{
  "year": 2020,
  "month": 8,
  "fireCells": 66,
  "fireDays": 78,
  "hotspots": 1242,
  "hotspotsBySensor": { "VIIRS_SNPP": 1242 },
  "detections": 1242,
  "detectionsBySensor": { "VIIRS_SNPP": 1242 },
  "partial": false,
  "dataStatus": "observed",
  "provisional": false
}
```

**Aceptación:**

- `sensor=ALL` devuelve exactamente los valores actuales.
- `sensor=VIIRS_SNPP` en Delta 2020-09 devuelve 66 celdas.
- Un test de controlador cubre `sensor` inválido y `sensor=ALL`.
- OpenAPI actualizado.

**Implementación:** `monthlyFire(..., sensor)` en `CalendarRepository`, `getMonthly` pasa el sensor normalizado, 4 tests nuevos (23 en total, 0 fallos), OpenAPI y `CONTRATO_API.md` actualizados. Verificado en vivo sobre Delta 2020-09: `ALL` 189/618, `MODIS` 119/313, `VIIRS_SNPP` 154/496; `AUTO` se comporta como `ALL`.

**Archivos:** `CalendarController.java`, `CalendarService.java`, `CalendarRepository.java`, `MonthlyCell.java`, `openapi/chronosfire-api.yaml`, tests.

---

### B-02. Agregar el conteo de hotspots armonizados

**Estado:** ✅ implementado el 15/11/2026.

**Problema:** `monthlyDetections()` cuenta todas las filas de `detections`, incluidas industriales, baja confianza y tipos 1/2/3. Ese número no es “hotspots de vegetación confiable”.

**Cambio propuesto:**

- Nueva consulta `monthlyHotspots(bbox, from, to, sensor)` sobre `v_detections_analysis`:

```sql
SELECT extract(year FROM acq_date)::int AS year,
       (extract(month FROM acq_date)::int - 1) AS month,
       sensor,
       count(*)::int AS hotspots
FROM v_detections_analysis
WHERE acq_date BETWEEN ? AND ?
  AND latitude BETWEEN ? AND ?
  AND longitude BETWEEN ? AND ?
  AND coalesce(is_vegetation_effective,
               confidence_class IN ('nominal', 'high'))
  AND NOT industrial_mask
  AND (? = 'ALL' OR sensor = ?)
GROUP BY 1, 2, 3;
```

- Agregar `hotspots` y `hotspotsBySensor` a `MonthlyCell`.
- Mantener `detections`/`detectionsBySensor` como capa cruda, pero renombrarlos en la UI.
- Extender también `/api/calendar/daily` con `hotspots` y `hotspotsBySensor` para que el gráfico diario pueda cambiar de métrica.

**Aceptación:**

- `hotspots <= detections` siempre.
- Delta 2020-09 con SNPP: `hotspots` coincide con la suma de detecciones de vegetación nominal/alta no industriales.
- La diferencia entre `detections` y `hotspots` se explica en la UI.
- Tests de servicio y controlador.

**Implementación:** `monthlyHotspots` y `dailyHotspots` en `CalendarRepository`, enriquecimiento en `CalendarService`, campos nuevos en `MonthlyCell` y `DailyRow`. Filtro: vegetación efectiva, confianza nominal/alta y fuera de máscara industrial. 25 tests, 0 fallos. Verificado en vivo sobre Delta 2020-09: `ALL` 6.628 hotspots; `MODIS` 704; `VIIRS_SNPP` 3.313. Delta 2020-09-15: 76 hotspots en 20 celdas-día.

**Archivos:** `CalendarService.java`, `CalendarRepository.java`, `MonthlyCell.java`, `DailyRow.java`, `openapi/chronosfire-api.yaml`.

---

### B-03. Anomalía mensual calculada en el backend (P1)

**Problema:** `MonthComparison.vue` y `useCalendar.ts` calculan la mediana de otros años en el cliente. El backend ya tiene leave-one-out en `/weekly` y `/trend`. Hay dos metodologías.

**Cambio propuesto:**

- Extender `MonthlyCell` con:

```text
baselineFireCells   (mediana leave-one-out de la misma métrica)
referenceYears      (cantidad de años usados)
confidence          (high | low | insufficient)
anomaly             (observed / baseline, o null)
```

- Calcularlo en Java después de construir la serie mensual, sin SQL adicional:
  - para cada `(year, month)`, tomar los valores de la misma métrica en los demás años del rango;
  - mediana;
  - `referenceYears >= 7`, `baseline >= 3`, `observed >= 3`;
  - `high` si `referenceYears >= 10` y `baseline >= 10`.
- Si el usuario selecciona un sensor, la serie observada y el baseline son de ese sensor. Si es `ALL`, ambos son `ALL` y se advierte que la serie no es neutral.
- El frontend deja de calcular la mediana y solo consume estos campos.

**Aceptación:**

- Delta 2020-09 con SNPP: baseline y anomalía coinciden con el cálculo actual del cliente (o se documenta la diferencia por años de referencia).
- `confidence=insufficient` cuando el rango tiene menos de 7 años.
- Test unitario con una serie sintética.

**Estado:** ✅ implementado el 15/11/2026.

**Implementación:** `withMonthlyAnomaly` en `CalendarService` calcula mediana leave-one-out con la métrica y la era del sensor: MODIS 2003-2011, SNPP 2013-2025, NOAA-20 2019-2025, NOAA-21 2024-2025; para `ALL` excluye 2000-2002, 2012 y 2026. `MonthlyCell` expone `baselineFireCells`, `anomaly`, `referenceYears` y `confidence`. El frontend dejó de calcular la mediana en `useCalendar.ts` y `MonthComparison.vue`, y muestra años de referencia, confianza y el aviso de que `ALL` no es neutral. 31 tests backend, 0 fallos; `npm run build`, `npm run test:api` y `npm run test:browser` pasan. Verificado en vivo: Delta 2020-09 SNPP `154 / 49,0 / 3,14 / 12 / high`; `ALL` `189 / 62,0 / 3,05 / 12 / high`; 2012 y un rango de un solo año quedan `insufficient`.

**Archivos:** `CalendarService.java`, `MonthlyCell.java`, `CalendarServiceTest.java`, `MonthComparison.vue`, `useCalendar.ts`.

**Nota:** si el tiempo aprieta, esta tarea se puede posponer sin bloquear el challenge. La opción mínima es mantener el cálculo en el cliente y agregar `referenceYears` y `confidence` calculados también en el cliente.

---

### B-04. Desglose de hotspots por sensor en `/api/calendar/heatmap` (P1)

**Problema:** `HeatmapCell.nDetections` se calcula con `sum(n_detections)` sobre `grid_daily`, que mezcla sensores. Con un selector de sensor, el total del mapa seguiría siendo incorrecto.

**Cambio propuesto:**

- Agregar `hotspots` y `hotspotsBySensor` a `HeatmapCell`.
- Query sobre `v_detections_analysis` agrupada por celda y sensor para el mes/AOI:

```sql
SELECT '0.05:' || round(latitude / 0.05)::bigint || ':' || round(longitude / 0.05)::bigint AS grid_id,
       sensor,
       count(*)::int AS hotspots
FROM v_detections_analysis
WHERE acq_date BETWEEN ? AND ?
  AND latitude BETWEEN ? AND ?
  AND longitude BETWEEN ? AND ?
  AND coalesce(is_vegetation_effective,
               confidence_class IN ('nominal', 'high'))
  AND NOT industrial_mask
GROUP BY 1, 2;
```

- Cachear por `bbox|from|to|sensor` con Caffeine.
- Mientras no se implemente, el frontend debe mostrar **“días con actividad”** y no “detecciones” cuando haya más de un sensor.

**Aceptación:**

- El mapa nunca muestra una suma cross-sensor sin etiqueta.
- Tiempo de respuesta < 1 s para un AOI y un mes con cache caliente.
- Test de contrato para la forma del JSON.

**Archivos:** `HeatmapCell.java`, `CalendarRepository.java`, `CalendarService.java`, `FireMap.vue`.

---

### B-05. Aviso de truncamiento en exportaciones CSV (P2)

**Problema:** `/api/detections/export.csv` y `/api/calendar/export.csv` tienen un tope de 50.000 filas y hoy no avisan si se alcanzó.

**Cambio propuesto:**

- Agregar headers:

```text
X-Row-Limit: 50000
X-Returned-Rows: 50000
X-Truncated: true|false
```

- En el frontend, mostrar “Descarga limitada a 50.000 filas” y un aviso si `X-Truncated` es verdadero. El `fetch` debe exponer los headers (hoy el link directo no los lee). Alternativa: agregar `?limit=` visible en la UI y un texto fijo.
- Opcional: `GET /api/detections/count` con los mismos filtros para mostrar “N resultados” antes de descargar.

**Aceptación:**

- Una búsqueda con más de 50.000 filas muestra el aviso.
- Una búsqueda menor descarga todas las filas.
- Tests de controlador para los headers.

**Archivos:** `DetectionController.java`, `CalendarController.java`, `AreaOfInterest.vue`.

---

### B-06. Actualizar contrato y documentación

- `backend/openapi/chronosfire-api.yaml`: nuevos campos y parámetros.
- `backend/CONTRATO_API.md`: semántica de `hotspots`, `fireCells` por sensor, `confidence`, truncamiento.
- `backend/README.md`: tabla de endpoints actualizada.
- `frontend/ChronosFire/docs/FUNCIONALIDAD.md`: **reescribir**. Hoy describe Python/DuckDB, 1 km² y calibración 2012–2025.
- `README.md`: rango 2003–2026, métricas y límites.
- Nuevo `docs/DECISIONES.md`: definiciones de hotspot, celda, evento, “todos los sensores”, baseline y eras.

**Aceptación:** ningún documento del repo menciona backend Python, DuckDB ni calibración como parte del producto actual.

---

### B-07. Tests backend

Agregar o ampliar:

| Test | Qué cubre |
|---|---|
| `CalendarControllerTest` | `monthly` con sensor, `weekly` con AUTO, `trend`, parámetros inválidos |
| `CalendarServiceTest` | baseline leave-one-out, confidence, semana ISO, límites de rango |
| `DetectionControllerTest` | CSV, headers de truncamiento, filtros |
| `openapi` smoke | que el YAML parsee y tenga los 15 paths + los nuevos parámetros |
| Script SQL de validación | Delta 2020 semana 40 = 66/10/12/6,60 |

No hace falta Testcontainers para el MVP: los tests de controlador usan `@WebMvcTest`. Para SQL, mantener el script `backend/db/validacion/` reproducible con `psql`.

---

### B-08. Reproducibilidad (P2)

- `docker-compose.yml` con PostgreSQL 17 y un volumen para el dump.
- Dump de demostración acotado (Delta + 4 AOIs + 2012–2026) para que el jurado pueda levantar la app sin 7 millones de filas.
- `etl/load_pg.py`: reemplazar `C:\Users\HP\...` y `C:\Users\HP\AppData\Local\Temp\opencode\firms` por variables de entorno (`CHRONOS_DATA`, `CHRONOS_TMP`).
- `data/README.md` con URLs de descarga, checksums y pasos.
- `.env.example` ya existe; mantenerlo sin secretos.

**Aceptación:** un clon limpio + `docker compose up` + `.\run-app.ps1` levanta la app con datos de demo.

---

## 6. Plan de frontend

### F-01. Selector de sensor

**Archivo:** `src/components/AreaOfInterest.vue`, `src/composables/useChronos.ts`.

- Agregar un `<select>` o segmented control:

```text
Todos los sensores (dominado por VIIRS)
MODIS
VIIRS SNPP
VIIRS NOAA-20
VIIRS NOAA-21
```

- Al cambiar, llamar `load({ ...filters.value, metric })`.
- `params()` ya envía `sensor`; no hay que cambiar el contrato.
- Mostrar una nota cuando `metric === 'all'`: “No es un promedio; la serie está dominada por VIIRS”.
- Persistir la elección en el estado global; opcional: en la URL (`?sensor=`).

**Aceptación:**

- Cambiar de `ALL` a `MODIS` cambia calendario, mapa y detalle diario.
- El selector no ofrece sensores sin datos para el rango (opcional, recomendado).
- Test de navegador: el badge de serie cambia.

---

### F-02. Modo “Celdas” / “Hotspots” en el calendario

**Archivos:** `src/data/types.ts`, `src/composables/useChronos.ts`, `src/composables/useCalendar.ts`, `FireCalendar.vue`, `CalendarLegend.vue`, `KpiStrip.vue`, `MonthComparison.vue`, `DailyDetail.vue`, `FireMap.vue`.

- Agregar `metricMode: 'cells' | 'hotspots'` a `useChronos`.
- Extender `MonthCell` con `hotspots` y `hotspots_by_sensor`.
- En `useCalendar`, exponer `metricValue(cell)` y `metricLabel()`:

```text
cells    → "celdas con actividad"
hotspots → "hotspots detectados"
```

- El calendario, el KPI, la comparación y el detalle diario deben usar `metricValue` en lugar de `value`.
- El toggle va arriba del calendario, junto a las rampas:

```text
[ Celdas con actividad ] [ Hotspots detectados ]
```

- Leyenda y pie deben cambiar de unidad.
- En modo hotspots, la rampa y los cortes se recalculan sobre `hotspots`.

**Aceptación:**

- El número de cada celda cambia al alternar el modo.
- El título y la leyenda dicen la unidad correcta.
- No se muestra “hotspots” como “incendios”.
- Test de navegador: el KPI cambia de “celdas-mes” a “hotspots”.

**Dependencia:** B-02.

---

### F-03. Descarga de hotspots CSV

**Archivo:** `AreaOfInterest.vue`, `useChronos.ts`.

- Agregar un segundo botón:

```text
↓ Descargar hotspots CSV
```

- URL:

```text
/api/detections/export.csv?bbox=...&from=...&to=...&sensor=...&vegetation=true&confidence=nominal,high&limit=50000
```

- `download` y `aria-label` explícitos.
- Nota: “Hasta 50.000 filas. Solo vegetación nominal/alta, sin focos industriales”.
- Si el modo es “celdas”, el botón sigue descargando hotspots; el nombre lo aclara.

**Aceptación:**

- El archivo se descarga con BOM UTF-8 y cabecera correcta.
- El bbox y las fechas coinciden con los filtros activos.
- Test de contrato: `Content-Disposition` y primera línea.

**Estado:** ✅ implementado el 15/11/2026.

**Implementación:** `hotspotsParams` en `useChronos.ts` agrega `vegetation=true`, `confidence=nominal,high` y `limit=50000` a los filtros activos (bbox, rango y sensor). `AreaOfInterest.vue` expone un segundo botón con `download`, `aria-label` y la nota de alcance. Verificado en vivo: Delta 2020-09 `ALL` devuelve 6.628 filas, idéntico al conteo de hotspots del backend, con BOM UTF-8 y cabecera correcta. `npm run build`, `npm run test:api` y `npm run test:browser` pasan.

---

### F-04. Exportar visualizaciones

**Archivo:** `FireCalendar.vue`, nuevo `src/utils/exportCalendarSvg.ts`.

- Generar un SVG a partir de la matriz `months`:
  - título “Calendario de actividad de fuego”;
  - subtítulo con AOI, rango, sensor y métrica;
  - grilla año × mes con los mismos colores y valores;
  - leyenda con cortes;
  - `<title>` y `<desc>` para accesibilidad.
- Descargar con `Blob` + `URL.createObjectURL` + `<a download>`.
- Nombre: `chronos-fire-calendario-<area>-<from>-<to>.svg`.
- Opcional: convertir el SVG a PNG con `<canvas>` y `toBlob('image/png')`. Si se agrega, cuidar la carga de fuentes y el tamaño.

**Aceptación:**

- El SVG abre en un navegador y en Inkscape.
- Los valores y colores coinciden con la pantalla.
- El archivo incluye la nota metodológica.
- Test de navegador: el click dispara una descarga.

---

### F-05. Rampa accesible

**Archivo:** `src/composables/useColorRamp.ts`, `CalendarLegend.vue`, `FireMap.vue`.

- Reemplazar la rampa de anomalía verde/rojo por una divergente segura:
  - propuesta: `PuOr` (púrpura ↔ gris ↔ naranja) o `RdBu` invertida;
  - mantener los símbolos ▼/▲ y las etiquetas ±15% / ±50%.
- Para la escala secuencial, evaluar `Viridis` o `Cividis` para los datos, dejando la paleta de marca para la interfaz.
- Verificar contraste de texto con la función `contrast()` existente; apuntar a WCAG AA (4,5:1) para texto pequeño.
- En el mapa, codificar intensidad con color además del tamaño (doble codificación) o mantener un solo color y explicar que el tamaño codifica días.

**Aceptación:**

- La rampa pasa una simulación de deuteranopia/protanopia.
- La leyenda sigue siendo legible sin color (símbolos + texto).
- No se pierde la identidad visual en el resto de la UI.

---

### F-06. Rango inicial y acceso a los 2000

**Archivo:** `useChronos.ts`, `AreaOfInterest.vue`.

- Cambiar el valor inicial a `from: '2003-01-01'` (mínimo recomendado) o `2000-11-01` si el rendimiento lo permite.
- Cambiar el `min` de los inputs a `2000-11-01`.
- Agregar una nota: “2000–2002: cobertura parcial; no se usa para el baseline”.
- Mantener el enlace “Ver todo el archivo 2003–2026”.

**Aceptación:**

- La carga inicial muestra 2003–2026.
- El calendario incluye 2003–2011 sin acción adicional.
- La consulta nacional sigue siendo usable con cache.

---

### F-07. Público objetivo y caso de uso

**Archivo:** `MethodologyPanel.vue` o `HeroSection.vue`.

- Agregar un bloque breve:

```text
Para quién
- Respuesta a emergencias: detectar semanas críticas y comparar el mes actual.
- Científicos: explorar patrones estacionales y anomalías.
- Gestión del territorio: priorizar monitoreo en áreas recurrentes.
```

- Un caso de uso concreto: “Delta del Paraná, septiembre de 2020: 6,6× la mediana de la semana 40”.

**Aceptación:** el bloque es visible sin abrir un modal y no promete predicción ni riesgo.

---

### F-08. Etiquetas del mapa y coherencia de métricas

**Archivo:** `FireMap.vue`.

- Cambiar “N detecciones en X celdas” por:
  - “X celdas con actividad · Y hotspots (todas las fuentes)”, o
  - “X celdas · Y días con actividad” si no se implementa B-04.
- Agregar un tooltip: “Las detecciones de distintos sensores no se suman”.
- Si hay selector de sensor, mostrar el sensor activo.

**Aceptación:** ningún total cross-sensor aparece sin la aclaración.

---

### F-09. Alertas: refresco y estado

**Archivo:** `AlertsPanel.vue`, `useAlerts.ts` (opcional, P2).

- Botón “Actualizar” y refresco automático cada 5 minutos.
- Mostrar “Actualizado hace X”.
- Cancelar el intervalo al desmontar.

**Aceptación:** el panel no queda con datos viejos sin aviso.

---

### F-10. Tests frontend

Ampliar `frontend/ChronosFire/tests/api-contract.mjs`:

- `/api/calendar/weekly` Delta 2020 semana 40 → 66 / 10 / 6,60.
- `/api/calendar/trend` 2019–2020.
- `/api/calendar/monthly` con `sensor=VIIRS_SNPP` y `sensor=ALL` devuelve valores distintos.
- `/api/detections/export.csv` → 200, `text/csv`, primera línea con cabecera.
- Campos `hotspots` y `hotspotsBySensor` presentes.

Ampliar `tests/browser.mjs`:

- El selector de sensor cambia el badge de serie.
- El toggle “Hotspots” cambia el KPI.
- El botón de CSV tiene el `href` correcto.
- El botón de SVG dispara una descarga.

**Aceptación:** `npm run build`, `npm run test:api` y `npm run test:browser` pasan.

---

### F-11. Limpieza

- Eliminar `src/data/firms.ts` (sin uso y con `MAP_KEY` hardcodeada) y `src/data/sampleCalendar.ts`.
- Sacar `run-app.err` y `frontend/ChronosFire/vite.err` del control de versiones.
- Revisar que `.env` siga ignorado.

**Aceptación:** `npm run build` sin imports rotos; `git ls-files` sin logs ni claves.

---

## 7. Contrato API propuesto

### 7.1. `/api/calendar/monthly` (extendido)

```json
{
  "year": 2020,
  "month": 8,
  "fireCells": 66,
  "fireDays": 78,
  "hotspots": 1242,
  "hotspotsBySensor": { "VIIRS_SNPP": 1242 },
  "detections": 1242,
  "detectionsBySensor": { "VIIRS_SNPP": 1242 },
  "baselineFireCells": 10.0,
  "anomaly": 6.6,
  "referenceYears": 12,
  "confidence": "high",
  "partial": false,
  "dataStatus": "observed",
  "provisional": false
}
```

### 7.2. `/api/calendar/heatmap` (extendido)

```json
{
  "gridId": "0.05:-640:-1190",
  "cellLat": -32.0,
  "cellLon": -59.5,
  "fireCells": 1,
  "fireDays": 4,
  "hotspots": 18,
  "hotspotsBySensor": { "MODIS": 3, "VIIRS_SNPP": 15 },
  "dataStatus": "observed"
}
```

### 7.3. `/api/detections/export.csv` (headers)

```text
Content-Type: text/csv; charset=utf-8
Content-Disposition: attachment; filename="chronosfire-detecciones.csv"
X-Row-Limit: 50000
X-Returned-Rows: 50000
X-Truncated: false
```

---

## 8. Plan de datos y ETL (P2)

### D-01. Cobertura real

- Poblar `coverage_daily` desde `data_availability` de FIRMS o desde un proxy documentado.
- Calcular `n_data_years` en `calendar_weekly`.
- Exponer `nDataYears` y `dataStatus = no_data` en los endpoints.
- En la UI, marcar celdas sin cobertura con trama y no con cero.

**Aceptación:** una semana sin datos no se muestra como “sin fuego”.

### D-02. Reproducibilidad

- Dump de demo y `docker-compose`.
- ETL con variables de entorno.
- `data/README.md` con URLs y checksums.

### D-03. Eventos (opcional, fuera del MVP)

- Clustering de hotspots <5 km en el mismo día o días consecutivos.
- Tabla `fire_events` y endpoint `/api/events`.
- Solo si se quiere afirmar “número de incendios”. Mientras tanto, no usarlo en la demo.

---

## 9. Roadmap y estimación

### Fase A — Quick wins (0,5 persona-día)

| Tarea | Resultado |
|---|---|
| F-03 | Botón de CSV de hotspots |
| F-06 | Calendario inicia en 2003 |
| F-08 | Etiquetas del mapa sin suma cross-sensor |
| F-07 | Bloque de público objetivo |
| B-06 | Documentación actualizada |
| F-11 | Limpieza de código muerto y logs |

**Salida:** el challenge se cumple mejor sin tocar el modelo de datos. Se puede presentar.

### Fase B — Sensor + hotspots (1,5–2 persona-días)

| Orden | Tarea |
|---:|---|
| 1 | B-02 (hotspots mensuales y diarios) |
| 2 | B-01 (filtro de `fireCells` por sensor) |
| 3 | F-01 (selector de sensor) |
| 4 | F-02 (modo celdas/hotspots) |
| 5 | B-07 / F-10 (tests) |

**Salida:** el calendario muestra hotspots y se puede aislar el sensor. Es el bloque que cierra la brecha literal del challenge.

### Fase C — Accesibilidad y export (1 persona-día)

| Tarea | Resultado |
|---|---|
| F-04 | Export SVG del calendario |
| F-05 | Rampa accesible |
| F-10 | Tests de descarga y contraste |

**Salida:** “download visualizations” y accesibilidad.

### Fase D — Robustez opcional (1–2 persona-días)

| Tarea | Resultado |
|---|---|
| B-03 | Anomalía mensual server-side |
| B-04 | Desglose de hotspots en el mapa |
| B-05 | Aviso de truncamiento |
| D-01/D-02 | Cobertura y reproducibilidad |

**Salida:** defensa técnica reforzada y reproducibilidad para el jurado.

---

## 10. Plan de pruebas y definición de terminado

### 10.1. Pruebas funcionales

| Caso | Resultado esperado |
|---|---|
| Delta 2020 semana 40, SNPP | 66 observadas / baseline 10 / 12 refs / anomalía 6,60 |
| Delta 2020-09, `sensor=ALL` vs `SNPP` | Valores distintos y trazables |
| Modo “Hotspots” en 2020-09 | Número > 0 y ≤ detecciones crudas |
| Cambio de sensor | Calendario, mapa y detalle cambian |
| Descarga CSV de hotspots | Archivo con BOM, cabecera y filas del AOI |
| Descarga SVG | Archivo válido, con valores y leyenda |
| Rango 2003–2026 | Carga sin error y sin bloqueo |
| Rango sin baseline (ej. 1 año) | `confidence=insufficient`, sin inventar anomalía |

### 10.2. Pruebas técnicas

- `mvn test` verde.
- `npm run build` sin errores de tipos.
- `npm run test:api` verde.
- `npm run test:browser` verde.
- OpenAPI parsea y documenta los campos nuevos.
- Sin secretos en el repo.
- Consultas de AOI < 2 s con cache caliente.

### 10.3. Definición de terminado (DoD)

- [ ] Cada tarea tiene test o verificación manual documentada.
- [ ] La UI usa la misma palabra que la API (`hotspots`, `celdas`, `detecciones`).
- [ ] Ningún total cross-sensor se muestra sin aclaración.
- [ ] La documentación no menciona backend Python, DuckDB ni calibración.
- [ ] El README permite levantar la app en una máquina limpia.
- [ ] La demo se puede hacer en 5 minutos sin console.log ni datos mock.

---

## 11. Guion de demo actualizado (5 minutos)

1. **Abrir** la app en el Delta del Paraná, rango 2003–2026. Mostrar el calendario completo y la estacionalidad.
2. **Alternar** “Celdas” → “Hotspots”. Explicar la diferencia: una celda puede tener varios hotspots.
3. **Cambiar sensor** a MODIS y a VIIRS SNPP. Mostrar que la serie cambia y explicar que “todos” está dominada por VIIRS.
4. **Seleccionar septiembre de 2020**. Mostrar mapa, detalle diario y comparación: ~6,6× la mediana de la semana 40.
5. **Cambiar el AOI** con lat/lon o “usar vista como filtro”, y el rango de fechas. Mostrar que todo se recalcula.
6. **Descargar** el CSV de hotspots y el SVG del calendario.
7. **Mostrar** alertas NRT y eventos de validación. Cerrar con los límites: cobertura real, calibración física y “hotspot ≠ incendio”.

---

## 12. Riesgos y no-objetivos

### Riesgos

| Riesgo | Mitigación |
|---|---|
| La consulta mensual nacional es lenta | Cache, inicio en un AOI y límites de rango |
| El modo hotspots confunde con “incendios” | Etiqueta explícita y nota metodológica |
| El filtro por sensor no es “puro” porque `fire_day` es all-sensor | Documentar la semántica y ofrecer el endpoint semanal para la versión rigurosa |
| El CSV se trunca a 50.000 filas | Headers de truncamiento y aviso en la UI |
| Cambiar la paleta rompe la identidad de marca | Mantener la marca en la UI y usar la paleta accesible solo para datos |
| El jurado no puede reproducir la base | Dump de demo + docker-compose (P2) |

### No-objetivos

- **No** afirmar “número de incendios” sin clustering.
- **No** afirmar comparabilidad absoluta MODIS/VIIRS sin calibración física.
- **No** prometer cobertura real sin `coverage_daily`.
- **No** implementar dibujo libre de polígonos si el bbox + lat/lon alcanza; es una consideración opcional.
- **No** reemplazar la mediana por media: la mediana es más robusta y ya está validada.

---

## 13. Checklist de cierre

### Cumplimiento del challenge

- [ ] Calendario muestra hotspots además de celdas.
- [ ] Rango visible desde 2003 (o 2000) por defecto.
- [ ] Selector de sensor disponible.
- [x] Descarga de hotspots CSV desde la UI.
- [ ] Descarga de visualización (SVG/PNG).
- [ ] Rampa accesible.
- [ ] Público objetivo explícito.
- [ ] Comparación histórica y anomalía visibles.
- [ ] Mapa interactivo con AOI/lat-lon.

### Calidad

- [ ] Tests backend y frontend verdes.
- [ ] OpenAPI y documentación actualizados.
- [ ] Sin secretos ni logs versionados.
- [ ] Sin sumas cross-sensor sin aclaración.
- [ ] Límites metodológicos visibles en la app.

### Demo

- [ ] Guion de 5 minutos ensayado.
- [ ] Datos y cache precalentados.
- [ ] Frase de cierre alineada con lo que la app realmente muestra.

---

**Conclusión:** con las fases A, B y C —aproximadamente 3 a 3,5 persona-días— el proyecto pasa de “cumple el núcleo con salvedades” a “cumple el challenge completo”. La fase D no es necesaria para el jurado, pero convierte la demo en una solución defendible fuera del hackatón.
