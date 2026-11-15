# Informe de alineación con el challenge — Chronos Fire

**Actualizado:** 15/11/2026  
**Versión original:** 14/11/2026  
**Alcance:** backend Java/Spring Boot, frontend Vue 3, contrato API, ETL, esquema SQL y estado real de la base `chronos_fire`.  
**Método:** revisión de código y documentación, `mvn test`, `npm run build`, pruebas de contrato/navegador/export y consultas **de solo lectura** a PostgreSQL 17 (`127.0.0.1:5432`). No se modificaron datos ni tablas durante el análisis.

> **Nota de actualización.** El informe del 14/11 describía un estado anterior: en ese momento no existían el backend Java ni el frontend. Entre el 14 y el 15/11 se implementaron la API (15 endpoints), la aplicación Vue/Leaflet, el calendario mensual, los hotspots por sensor, la comparación histórica, los CSV, las exportaciones SVG/PNG, la rampa accesible y la reproducibilidad con Docker. Este documento reemplaza ese diagnóstico; conserva las brechas que siguen abiertas y distingue las que quedaron resueltas.

---

## 1. Veredicto ejecutivo

| Dimensión | Estado | Comentario |
|---|---|---|
| Armonización de datos | 🟢 **Sólida** | 8 fuentes, 4 sensores, 7.082.474 detecciones; deduplicación, confianza homologada, máscara industrial y política SP/NRT. |
| Climatología por celda | 🟠 **Con salvedades** | `fire_day_rate_given_detection` sigue condicionada a que haya detecciones y `n_data_years` está vacío. El producto no usa ese campo para las anomalías. |
| Anomalías del producto | 🟢 **Implementadas** | Baseline leave-one-out por AOI/semana o mes, mediana, años de referencia y confianza (`high`/`low`/`insufficient`). |
| Backend / API | 🟢 **Implementado** | 15 endpoints de solo lectura, OpenAPI, CORS, cache, CSV con aviso de truncamiento y 48 tests aprobados. |
| Aplicación web | 🟢 **Implementada** | Vue 3 + Leaflet: calendario, selector de sensor, modo celdas/hotspots, mapa, comparación, detalle diario, alertas y validación. |
| Descarga de análisis y visualizaciones | 🟢 **Implementada** | CSV de calendario y hotspots; SVG vectorial y PNG a 2× del calendario. |
| Validación | 🟢 **Implementada, con AOIs provisionales** | Scripts V1–V4 ejecutados; 5 AOIs y 6 ventanas. Solo Delta 2020 está confirmada; las otras cuatro tienen bbox preliminar. |
| Cobertura real | 🟠 **Pendiente** | `coverage_daily` tiene 0 filas y `n_data_years` es `NULL` en el 100% de `calendar_weekly`. Una celda sin filas no distingue “sin fuego” de “sin observación”. |
| Reproducibilidad | 🟢 **Mejorada** | `docker compose` con dump real acotado (77 MB), `run-app.ps1 -Demo`, ETL con variables de entorno y `.env.example`. |

**Síntesis:** el proyecto ya cumple el núcleo del challenge y lo hace con un producto web completo, no solo con un pipeline. Las brechas que quedan son de **transparencia de cobertura**, **comunicación de la discontinuidad MODIS/VIIRS** y **robustez de datos secundarios**; no bloquean la demo.

---

## 2. Cambios respecto del informe del 14/11

| Brecha original | Estado actual |
|---|---|
| Backend no implementado | ✅ 15 endpoints, Spring Boot, OpenAPI, 48 tests. |
| Frontend inexistente | ✅ Vue 3 + Leaflet con calendario, mapa, filtros y exportaciones. |
| Sin CSV | ✅ `/api/calendar/export.csv` y `/api/detections/export.csv`, con `X-Row-Limit`, `X-Returned-Rows` y `X-Truncated`. |
| Sin selector de sensor | ✅ Selector en la UI; calendario, mapa y detalle se recalculan. |
| Calendario sin “número de hotspots” | ✅ Modo “Hotspots detectados” con definición armonizada (vegetación, confianza nominal/alta, sin focos industriales). |
| Sin export de visualizaciones | ✅ SVG y PNG a 2× con valores, leyenda y nota metodológica. |
| Rampa verde/rojo | ✅ Viridis para volumen y PuOr para anomalía; contraste y daltonismo verificados. |
| Anomalía mensual calculada en el cliente | ✅ Server-side, con `baselineFireCells`, `anomaly`, `referenceYears` y `confidence`. |
| `calendar_weekly` incompleto | ✅ 3.719.116 filas = 70.172 celdas × 53 semanas. |
| `fire_day_prob` / `n_years` mal nombrados | 🟡 Renombrados a `fire_day_rate_given_detection` y `n_fire_years`; `n_data_years` existe pero está vacío. |
| ETL con rutas absolutas y password en el repo | ✅ Variables `CHRONOS_DATA` / `CHRONOS_TMP` / `CHRONOS_PG`; `DB_PASSWORD` por entorno. |
| Sin forma de reproducir la base | ✅ `docker-compose.yml` con dump de demo y modo `-Demo`. |

---

## 3. Alineación con los objetivos del challenge

| Requisito del challenge | Estado | Evidencia y límites |
|---|---|---|
| Combinar la serie histórica de MODIS con la continuidad/detalle de VIIRS | 🟢 Hecho | 4 sensores, 8 pares sensor/fuente, 7.082.474 detecciones, 2000-11-01 → 2026-11-14. |
| Armonización estructural (esquema, confianza, tipos, UTC) | 🟢 Hecho | `detections` unificada, `confidence_class`, `fire_type`, `quality_flags`, deduplicación y SP/NRT sin solapamientos. |
| Que el sensor de origen “deje de ser relevante” | 🟠 **Parcial, ahora medido** | El producto no suma sensores; hay selector y validación MODIS vs VIIRS (Spearman nacional 0,969). La discontinuidad de 2012 por entrada de VIIRS sigue existiendo. |
| Patrones, tendencias y anomalías como foco | 🟢 Hecho | `/weekly`, `/monthly` y `/trend` con mediana leave-one-out, años de referencia y confianza. |
| Calendario de actividad desde principios de los 2000 | 🟢 Hecho | La UI carga el rango de `/api/meta` (2000-11-01 → 2026-11-14) y la matriz tiene 53 semanas por celda. |
| Aplicación web integrada | 🟢 Hecho | Vue 3 + Leaflet, una sola página con filtros, calendario, mapa y paneles. |
| Seleccionar región y rango de fechas | 🟢 Hecho | Presets (Delta, Córdoba, Corrientes), rectángulo personalizado, `bbox`, lat/lon y “usar vista como filtro”. Sin polígono libre (no objetivo). |
| Descargar análisis y visualizaciones | 🟢 Hecho | CSV de calendario, CSV de hotspots, SVG y PNG. |
| Calendario con “número de focos detectados” | 🟢 Hecho con definición explícita | Modo hotspots; no se llaman “incendios” ni se suman entre sensores. |
| Escalas de color accesibles | 🟢 Hecho | Viridis y PuOr, contraste AA y símbolos ▼/▲ independientes del color. |
| Mapa interactivo con AOI o lat/lon | 🟢 Hecho | Leaflet, presets, rectángulo y coordenadas; el mapa colorea hotspots y codifica días por tamaño. |
| Comparaciones históricas y anomalías vs mediana multianual | 🟢 Hecho | Server-side con `referenceYears` y `confidence`; la UI muestra años de referencia y advierte cuando no alcanza. |
| Público objetivo definido | 🟢 Hecho | Bloque “Para quién” y caso de uso Delta 2020, semana 40. |

---

## 4. Estado real de los datos (snapshot verificado)

### 4.1. Volúmenes

| Métrica | Valor |
|---|---:|
| Detecciones totales | 7.082.474 |
| Pares sensor/fuente | 8 |
| Sensores | 4 |
| Rango temporal | 2000-11-01 → 2026-11-14 |
| `grid_daily` (filas) | 2.103.053 |
| Celdas distintas | 70.172 |
| `grid_daily.fire_day = true` | 1.828.709 (87,0%) |
| `grid_daily.fire_day = false` | 274.344 (13,0%) |
| `calendar_weekly` (filas) | 3.719.116 = 70.172 × 53 |
| Semanas por celda | 53 (mín. 53, máx. 53) |
| Filas de semana 53 | 70.172 |
| `grid_weekly_sensor` (filas) | 2.265.477 |
| `coverage_daily` (filas) | **0** |
| `industrial_cells` | 885 |
| Celdas-día con más de un sensor | 581.995 |
| Celdas-día con MODIS + VIIRS | 306.186 |
| Solapamientos SP/NRT por sensor/fecha | **0** |

### 4.2. Cobertura por sensor/fuente

| Sensor | Fuente | Filas | Desde | Hasta |
|---|---|---:|---|---|
| MODIS | NRT | 11.164 | 2026-09-01 | 2026-11-14 |
| MODIS | SP | 1.726.328 | 2000-11-01 | 2026-08-31 |
| VIIRS_NOAA20 | NRT | 46.823 | 2026-08-01 | 2026-11-14 |
| VIIRS_NOAA20 | SP | 1.780.437 | 2018-04-01 | 2026-07-31 |
| VIIRS_NOAA21 | NRT | 44.390 | 2026-08-01 | 2026-11-13 |
| VIIRS_NOAA21 | SP | 495.292 | 2023-03-01 | 2026-07-31 |
| VIIRS_SNPP | NRT | 41.674 | 2026-08-01 | 2026-11-02 |
| VIIRS_SNPP | SP | 2.936.366 | 2012-01-20 | 2026-07-31 |

### 4.3. Climatología por celda y cobertura

| Métrica | Valor |
|---|---:|
| `calendar_weekly.n_data_years` | 100% `NULL` (3.719.116 filas) |
| `n_fire_years >= 5` | 51.612 filas (1,4% de la matriz; 7,1% de las 730.133 filas con algún año de fuego) |
| `fire_day_rate_given_detection` no nulo | 782.840 filas |
| De esas, `rate >= 0,9` | 625.645 (79,9%) |
| `rate` promedio | 0,887 |
| `provisional_share > 0` | 29.414 filas |
| Semana 53 | 70.172 filas, pero solo 4 años con actividad: no usar para anomalías sin `n_ref >= 7` |
| Fechas crudas con `isoyear <> year` | 25.819 detecciones (los agregados usan `isoyear`, así que no desalinean la semana) |

**Lectura correcta:** `fire_day_rate_given_detection` **no** es “probabilidad de fuego”. Es la proporción de días con fuego entre los días con alguna detección. El nombre actual ya lo aclara. La API de anomalías no usa este campo: usa la mediana de celdas con actividad por AOI y semana/mes.

### 4.4. Validación disponible

| AOI | Estado | Observado | Baseline | Referencia |
|---|---|---|---|---|
| Delta del Paraná 2020 | **confirmed** | VIIRS_SNPP | VIIRS_SNPP | 2013–2025 |
| Corrientes / Iberá 2022 | provisional | VIIRS_SNPP | VIIRS_SNPP | 2013–2025 |
| Salta / Jujuy 2013 | provisional | VIIRS_SNPP | VIIRS_SNPP | 2013–2025 |
| Córdoba 2013 | provisional | VIIRS_SNPP | VIIRS_SNPP | 2013–2025 |
| La Pampa 2016–2017 | provisional | VIIRS_SNPP | VIIRS_SNPP | 2013–2025 |

- 5 AOIs y 6 ventanas; solo Delta 2020 está confirmada.
- Delta 2020, semana 40: **66 observadas / baseline 10 / 12 años de referencia / anomalía 6,60** con SNPP-only. Con todos los sensores: 78 / 10 / 7,80. El titular depende del sensor y debe declararse.
- Sensibilidad nacional MODIS vs VIIRS: Spearman **0,969**, top-5 **0,908**. En las ventanas de evento, el top-5 coincide en 5 de 6 casos.
- Coincidencia espacial: MODIS coincide en 52–69% de sus detecciones y VIIRS en 37–49%, según año y sensor. VIIRS ve más focos y de menor FRP.
- **La cobertura observada es un proxy**: días con al menos una detección, no cobertura real de observación (nubes/overpass).

---

## 5. Fortalezas

1. **Armonización conceptualmente correcta.** No sumar sensores en el titular, usar `fire_day`, homologar confianza y separar SP/NRT.
2. **Producto web completo y coherente.** Selector de sensor, modo celdas/hotspots, mapa, comparación, detalle diario, alertas, validación y exportaciones.
3. **Anomalías defendibles.** Baseline leave-one-out por AOI con años de referencia y confianza; no se inventa anomalía cuando faltan datos.
4. **Transparencia metodológica.** La UI distingue celdas, hotspots, detecciones e incendios; advierte que “todos los sensores” no es neutral.
5. **Accesibilidad.** Viridis/PuOr, contraste AA, símbolos ▼/▲, trama para faltantes y etiquetas accesibles.
6. **Reproducibilidad.** Docker con dump real acotado, ETL portable, `.env.example`, script de arranque y tests.
7. **Validación ejecutada, no solo planificada.** Scripts V1–V4 y reportes en `reports/validacion_6_8_20261114/`.
8. **Honestidad documental.** Los límites (cobertura, calibración, hotspot ≠ incendio) están en la app y en los informes.

---

## 6. Brechas y límites actuales

### 6.1. Cobertura real: “sin datos” todavía no es distinguible de “sin fuego” (P1)

- `coverage_daily` tiene **0 filas**.
- `calendar_weekly.n_data_years` es **NULL en el 100%** de las filas.
- La matriz completa de 53 semanas evita el “faltan semanas”, pero una celda-semana con `n_fire_years = 0` puede significar “no hubo fuego” o “no hubo observación útil”.
- La UI marca `dataStatus = unknown` cuando no hay filas, pero no puede marcar celdas sin cobertura dentro de un AOI con datos.

**Impacto:** no se puede afirmar “no hubo fuego” en una semana sin detecciones. Es la limitación más importante que queda.

**Recomendación (D-01):** poblar `coverage_daily` con disponibilidad por sensor/fecha (FIRMS o proxy documentado), calcular `n_data_years` y exponer `dataStatus = no_data` con trama en la UI.

### 6.2. Climatología por celda todavía condicionada (P2, no P0)

`fire_day_rate_given_detection` sigue midiendo “de los días con detección, cuántos tuvieron fuego”. Es correcto que ya no se llame `fire_day_prob`, pero sigue siendo un campo tentador para leer como probabilidad de fuego.

- 79,9% de las filas con tasa calculada están por encima de 0,9.
- `n_fire_years` cuenta años con fuego, no años con datos.
- `n_data_years` está vacío.

**Recomendación:** mantenerlo como capa secundaria y, si se expone, etiquetarlo explícitamente como “tasa condicionada a detección”. No usarlo para anomalías ni para “riesgo”.

### 6.3. Discontinuidad MODIS/VIIRS (P1 de comunicación)

- La validación nacional es robusta (Spearman 0,969), pero la serie anual sigue mostrando un salto en 2012 por la entrada de VIIRS SNPP.
- El caso Delta cambia según el conjunto: 66 con SNPP-only vs 78 con todos los sensores.
- No hay calibración física de eficiencia de detección ni de FRP.

**Recomendación:** declarar siempre sensor y era; usar el selector; no presentar “todos los sensores” como serie neutral. La validación ya ofrece evidencia para sostener esta advertencia.

### 6.4. FRP entre sensores en la capa cruda (P2)

- `grid_daily.frp_sum_mw` suma todos los sensores (1.828.709 filas con valor).
- `grid_weekly_sensor.frp_sum_mw` y `frp_by_sensor` sí conservan el desglose por sensor.
- `calendar_weekly.frp_p50` y `frp_p90` están **NULL** (0 filas): no deben presentarse como si estuvieran calculados.

**Recomendación:** usar FRP por sensor en la UI o excluir días multi-sensor; no mostrar sumas cross-sensor como titular.

### 6.5. Validación con AOIs provisionales (P2)

- Solo `delta_2020` está confirmada.
- Las otras cuatro AOIs usan bbox preliminar y no deberían citarse como resultados definitivos.
- La coincidencia MODIS/VIIRS está medida, pero no es calibración física.

**Recomendación:** validar los bbox y pasar a `confirmed` antes de usar esos eventos en la demo o en un informe externo.

### 6.6. Alertas sin refresco automático (P3 de producto)

`AlertsPanel` no tiene botón “Actualizar”, refresco cada 5 minutos ni “Actualizado hace X”. El endpoint funciona, pero el panel puede quedar con datos viejos sin aviso. Es una mejora de producto, no un requisito del challenge.

### 6.7. “Hotspot” no es “incendio” y no hay clustering de eventos (fuera del MVP)

- El modo hotspots cuenta detecciones armonizadas; una celda puede contener varias.
- No existe `fire_events` ni clustering <5 km en el mismo día o días consecutivos.
- Por lo tanto, no se debe afirmar “cantidad de incendios”.

**Recomendación:** mantener la etiqueta “hotspots” y dejar eventos para una etapa posterior.

### 6.8. Detalles menores de datos

- `frp_zero` con confianza nominal/alta: 1.458 detecciones; no se filtran en `fire_day`. Impacto bajo.
- `confidence_max` de `grid_daily` se calcula sobre todas las detecciones, incluidas industriales. No se reauditó en esta actualización.
- El conteo crudo de fechas con `isoyear <> year` sigue siendo 25.819, pero los agregados semanales usan `isoyear`; no hay desalineación en `grid_weekly_sensor` ni en `calendar_weekly`.

---

## 7. Cumplimiento de los planes

### 7.1. Plan de armonización

| Criterio | Estado actual |
|---|---|
| 8 fuentes presentes | ✅ 8/8 |
| Sin duplicados exactos | ✅ |
| SP/NRT sin solapamiento | ✅ 0 pares sensor/fecha |
| `v_detections_canonical` devuelve una fuente por sensor/fecha | ✅ |
| Máscara industrial | ✅ 885 celdas |
| NRT industrial no marca `fire_day` | ✅ |
| `calendar_weekly` con 53 semanas por celda | ✅ 3.719.116 filas |
| `n_data_years` calculado | ❌ 100% NULL |
| `coverage_daily` poblada | ❌ 0 filas |
| Eventos 2020, 2022, 2013 y 2017 | ✅ 5 AOIs / 6 ventanas; solo Delta confirmada |
| Sensibilidad y coincidencia MODIS/VIIRS | ✅ scripts V3/V3b/V4 ejecutados |
| Limitaciones documentadas | ✅ app, README, planes e informes |

### 7.2. Plan de backend

| Componente | Estado |
|---|---|
| `pom.xml`, aplicación Spring Boot | ✅ |
| `/api/health`, `/api/meta` | ✅ |
| `/api/calendar/weekly`, `/daily`, `/monthly`, `/heatmap`, `/trend`, `/export.csv` | ✅ |
| `/api/detections`, `/geojson`, `/export.csv` | ✅ |
| `/api/alerts`, `/alerts/geojson` | ✅ |
| `/api/aois`, `/api/validation` | ✅ |
| OpenAPI y contrato | ✅ |
| CORS y cache | ✅ |
| Rol dedicado de solo lectura | 🟡 No hay rol específico; el backend solo ejecuta SELECT |
| Tests | ✅ 48 aprobados, 0 fallos |

**Endpoints:** 15. **Tests:** 48 (`mvn test`, `BUILD SUCCESS`). **Build frontend:** `npm run build` ✅. **Contrato/navegador/export:** `test:api`, `test:browser` y `test:png` ✅.

### 7.3. Plan de frontend

| Elemento | Estado |
|---|---|
| Calendario mensual con celdas y hotspots | ✅ |
| Selector de sensor | ✅ |
| Rango desde el inicio del archivo | ✅ |
| KPIs, comparación y detalle diario | ✅ |
| Mapa con hotspots por sensor | ✅ |
| Alertas y validación | ✅ |
| CSV de calendario y hotspots | ✅ |
| SVG y PNG del calendario | ✅ |
| Rampa accesible | ✅ Viridis/PuOr, contraste mínimo 4,87:1 |
| Público objetivo y caso de uso | ✅ |
| Refresco automático de alertas | ❌ F-09 pendiente |
| Tests de contrato, navegador y export | ✅ |

### 7.4. Reproducibilidad

| Elemento | Estado |
|---|---|
| `docker-compose.yml` con PostgreSQL 17 | ✅ |
| Dump real acotado | ✅ 77 MB |
| `run-app.ps1 -Demo` | ✅ |
| ETL con `CHRONOS_DATA`, `CHRONOS_TMP`, `CHRONOS_PG` | ✅ |
| `.env.example` sin secretos | ✅ |
| `DB_PASSWORD` por entorno | ✅ |
| Documentación de arranque | ✅ README y `docker/README.md` |

---

## 8. Riesgos y prioridades

### P0 — Bloqueantes del challenge

**Ninguno.** El calendario, el mapa, el selector de sensor, los hotspots, la comparación histórica y las descargas están implementados y probados.

### P1 — Para una defensa rigurosa

1. **Cobertura real (D-01):** poblar `coverage_daily`, calcular `n_data_years` y distinguir “sin datos” de “sin fuego”.
2. **Declaración de sensor/era:** mantener visible el sensor activo y advertir que “todos” está dominado por VIIRS.
3. **No presentar la tasa condicionada como probabilidad de fuego.**

### P2 — Robustez y honestidad de datos

4. Confirmar los bbox de las 4 AOIs provisionales.
5. Exponer FRP solo por sensor o documentar que la capa cruda suma.
6. No publicar `frp_p50`/`frp_p90` del calendario hasta poblarlos.
7. Añadir refresco y “actualizado hace X” a las alertas (F-09).
8. Revisar `frp_zero` y `confidence_max` en una auditoría menor.

### P3 — Fuera del MVP

9. `fire_events` con clustering <5 km para poder hablar de “incendios”.
10. Polígono dibujable si se necesita más que bbox.
11. Calibración física MODIS/VIIRS y de FRP.

---

## 9. Encuadre recomendado para la demo

La frase defendible es:

> “Construimos un calendario de **actividad de fuego por celda de ~5 km** con hotspots armonizados de MODIS y VIIRS. Permite aislar el sensor, alternar entre celdas con actividad y hotspots, comparar contra la mediana de otros años con años de referencia y confianza, explorar el mapa y descargar CSV, SVG o PNG. No afirmamos cantidad de incendios, cobertura real ni calibración física: esas son limitaciones declaradas.”

Guion de 5 minutos:

1. Abrir el Delta del Paraná y mostrar el calendario completo.
2. Alternar **Celdas → Hotspots** y explicar la diferencia.
3. Cambiar el sensor a MODIS y a VIIRS SNPP; mostrar el badge y la advertencia de “todos”.
4. Seleccionar septiembre de 2020: mapa, detalle diario y comparación (~6,6× la mediana semanal con SNPP).
5. Descargar el CSV de hotspots y el calendario en SVG/PNG.
6. Mostrar alertas y validación; cerrar con cobertura, calibración y “hotspot ≠ incendio”.

---

## 10. Anexo — Consultas de verificación

Consultas de solo lectura usadas para esta actualización.

```sql
-- Volúmenes
SELECT (SELECT count(*) FROM detections)        AS detections,
       (SELECT count(*) FROM grid_daily)        AS grid_daily,
       (SELECT count(*) FROM calendar_weekly)   AS calendar_weekly,
       (SELECT count(*) FROM grid_weekly_sensor) AS grid_weekly_sensor,
       (SELECT count(*) FROM coverage_daily)    AS coverage_daily,
       (SELECT count(*) FROM industrial_cells)  AS industrial_cells;

-- Cobertura por sensor/fuente
SELECT sensor, source, count(*) AS filas, min(acq_date) AS desde, max(acq_date) AS hasta
FROM detections
GROUP BY 1, 2
ORDER BY 1, 2;

-- Matriz semanal completa
SELECT count(*) AS filas,
       count(DISTINCT grid_id) AS celdas,
       count(*) FILTER (WHERE week_of_year = 53) AS semana_53,
       count(*) FILTER (WHERE n_data_years IS NULL) AS n_data_years_null,
       count(*) FILTER (WHERE n_fire_years >= 5) AS n_fire_years_ge5,
       count(*) FILTER (WHERE fire_day_rate_given_detection IS NOT NULL) AS tasa_calculada,
       count(*) FILTER (WHERE fire_day_rate_given_detection >= 0.9) AS tasa_ge_09
FROM calendar_weekly;

-- Semanas por celda
SELECT round(avg(n), 2) AS promedio, min(n) AS minimo, max(n) AS maximo
FROM (SELECT grid_id, count(*) AS n FROM calendar_weekly GROUP BY 1) t;

-- Multi-sensor
SELECT count(*) AS multi_sensor_cellday,
       count(*) FILTER (WHERE 'MODIS' = ANY(sensors)
         AND sensors && ARRAY['VIIRS_SNPP','VIIRS_NOAA20','VIIRS_NOAA21']) AS modis_mas_viirs
FROM grid_daily
WHERE array_length(sensors, 1) > 1;

-- Fechas crudas con isoyear distinto
SELECT count(*) AS fechas_isoyear_distinto
FROM detections
WHERE extract(isoyear FROM acq_date) <> extract(year FROM acq_date);

-- FRP del calendario
SELECT count(*) FILTER (WHERE frp_p50 IS NOT NULL) AS p50_set,
       count(*) FILTER (WHERE frp_p90 IS NOT NULL) AS p90_set
FROM calendar_weekly;

-- Validación
SELECT aoi_id, name, status, observed_sensor, baseline_sensor, ref_from, ref_to
FROM validation_aoi
ORDER BY aoi_id;
```

---

## 11. Conclusión

Chronos Fire pasó de “pipeline sólido sin producto” a una **solución web completa del challenge**: armoniza MODIS y VIIRS, permite elegir sensor, muestra celdas y hotspots, compara contra la mediana multianual, valida eventos, exporta CSV/SVG/PNG y usa escalas accesibles.

Lo que queda no es implementación de producto, sino **rigor de datos**: cobertura real (`coverage_daily`/`n_data_years`), comunicación de la discontinuidad MODIS/VIIRS y límites del FRP. Si se mantienen esas advertencias y se evita llamar “incendios” a los hotspots, la demo es defendible técnica y metodológicamente.
