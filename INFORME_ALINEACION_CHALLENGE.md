# Informe de alineación con el challenge — Chronos Fire

**Proyecto:** Hackaton NASA Chronos Fire  
**Fecha del análisis:** 14/11/2026  
**Alcance:** planes de armonización y backend, ETL, esquema SQL y estado real de la base `chronos_fire`.  
**Método:** revisión documental y consultas **de solo lectura** a PostgreSQL 17 (`127.0.0.1:5432`). No se modificó ningún archivo, tabla ni dato durante el análisis.

---

## 1. Veredicto ejecutivo

| Dimensión | Estado | Comentario |
|---|---|---|
| Armonización de datos | 🟢 **Sólida** | Es la parte más fuerte del proyecto, muy por encima del nivel habitual de un hackatón. |
| Calendario / climatología | 🟠 **Implementado con un defecto metodológico grave** | La `fire_day_prob` está mal condicionada y las anomalías pueden dar resultados absurdos. |
| Backend / API | 🔴 **No implementado** | Solo existe `application.properties`. No hay `pom.xml` ni código Java. |
| Aplicación web | 🔴 **No existe** | No hay frontend, mapa, calendario visual ni selector de AOI. |
| Descarga CSV | 🔴 **No planificada** | El plan solo contempla GeoJSON; el challenge pide explícitamente CSV. |
| Validación (eventos, MODIS vs VIIRS) | 🟠 **Pendiente** | Es la validación que sostiene la promesa central del challenge. |

**Síntesis:** el proyecto resolvió bien la “parte 1” del challenge (armonización), pero todavía no construyó la “parte 2” (aplicación web). Además, la métrica pensada para el calendario (probabilidad por celda) tiene un sesgo estructural que debe corregirse antes de exponerla.

---

## 2. Alineación con los objetivos del challenge

| Requisito del challenge | Estado | Evidencia |
|---|---|---|
| Combinar la serie histórica de MODIS con la continuidad/detalle de VIIRS | 🟢 Hecho | 4 sensores, 8 pares sensor/fuente, 7.082.474 detecciones, 2000-11-01 → 2026-11-14. |
| Armonización estructural (esquema, confianza, tipos, UTC) | 🟢 Hecho | `detections` unificada, `confidence_class`, `fire_type`, `quality_flags`, deduplicación. |
| Que el sensor de origen “deje de ser relevante” | 🟠 **Parcial / no demostrado** | `fire_day` es el intento correcto, pero la serie anual sigue mostrando saltos por sensor (§5.4). |
| Patrones, tendencias y anomalías como foco | 🟠 | La estacionalidad está bien documentada; la anomalía implementada tiene el problema de denominador (§5.1). |
| Calendario de actividad desde principios de los 2000 | 🟠 | Existe `calendar_weekly`, pero no tiene 53 semanas por celda ni una climatología confiable. |
| Aplicación web integrada | 🔴 | No hay frontend. |
| Seleccionar región y rango de fechas | 🟠 | La API planificada acepta `bbox` y fechas; no hay UI ni selección por polígono. |
| Descargar análisis y visualizaciones | 🔴 | No hay endpoint CSV ni export de imágenes. |
| Calendario con “número de focos detectados” | 🟠 | Hay `n_detections`, pero mezcla sensores y no equivale a “número de incendios” (§5.3). |
| Escalas de color accesibles | 🔴 | No planificado. |
| Mapa interactivo con AOI dibujada o lat/lon | 🔴 | Solo `bbox` en la API; sin mapa. |
| Comparaciones históricas y anomalías vs promedio multianual | 🟠 | La fórmula existe en el plan, pero no está implementada y su baseline es débil. |
| Público objetivo definido | 🟠 | El informe todavía pregunta “¿cuál es el objetivo?”; no hay personas ni user journeys. |

---

## 3. Estado real de los datos (snapshot verificado)

Consultas de solo lectura sobre `chronos_fire`:

| Métrica | Valor |
|---|---:|
| Detecciones totales | 7.082.474 |
| Pares sensor/fuente | 8 |
| Rango temporal | 2000-11-01 → 2026-11-14 |
| `grid_daily` (filas) | 2.103.125 |
| Celdas distintas en `grid_daily` | 70.176 |
| `grid_daily.fire_day = true` | 1.828.769 (87%) |
| `grid_daily.fire_day = false` | 274.356 (13%) |
| `calendar_weekly` (filas) | 782.826 |
| Celdas distintas en `calendar_weekly` | 70.176 |
| Semanas promedio por celda | 11,16 (de 53) |
| Filas de semana 53 | 1.537 |
| Filas con `n_years >= 5` | 63.498 (**8,1%**) |
| `industrial_cells` | 885 |
| Solapamientos SP/NRT por sensor/fecha | **0** |
| Celdas-día con más de un sensor | 581.943 |
| Celdas-día con MODIS + VIIRS | 306.073 |

**Cobertura por sensor/fuente:**

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

---

## 4. Fortalezas

1. **La armonización es conceptualmente correcta.** No sumar conteos entre sensores, usar `fire_day` binario, homologar confianza y separar SP/NRT son decisiones maduras.
2. **La política SP/NRT está bien resuelta para el MVP.** Hoy hay 0 solapamientos SP/NRT por sensor/fecha, por lo que la vista canónica no oculta datos.
3. **La máscara industrial está implementada y auditada.** 885 celdas, 240.141 detecciones `type=2` en SP, con impacto medido sobre `fire_day` y FRP.
4. **Hay honestidad documental.** Los planes marcan explícitamente lo que falta y lo que no se puede afirmar.
5. **El modelo de datos es razonable.** Capas separadas (cruda, canónica, agregada, calendario), índices y volúmenes controlados.

---

## 5. Brechas críticas

### 5.1. La climatología está condicionada a que haya detecciones (P0)

`calendar_weekly.fire_day_prob` se calcula como:

```sql
avg(fire_day::int)
FROM grid_daily
GROUP BY grid_id, week_of_year
```

Pero `grid_daily` **solo tiene filas para celdas/días donde hubo al menos una detección**. El denominador no es “días observados”, sino “días con detección”. La probabilidad mide:

> “de los días en que se detectó algo, ¿en cuántos había fuego de vegetación confiable?”

y no:

> “¿qué probabilidad hay de que haya fuego en esta celda en esta semana?”

Evidencia:

- **613.373 celdas-semana (78%)** se calculan con **1 a 3 días con detecciones**, con probabilidad media **0,886**.
- **625.633 filas (80%)** tienen `fire_day_prob >= 0,9`.
- Hay celdas con `n_years = 1` y `fire_day_prob = 1,0`.

Ejemplo en el Delta del Paraná (celda `0.05:-660:-1205`):

| Semana | `n_years` | `fire_day_prob` |
|---:|---:|---:|
| 35 | 4 | 0,875 |
| 36 | 2 | 0,667 |
| 37 | 3 | **1,0** |
| 40 | 3 | **1,0** |
| 41 | 2 | **1,0** |

Decir “probabilidad 1,0” con 2–3 años de datos es una sobreafirmación.

**Impacto en la anomalía.** Fórmula del plan: `expected = fire_day_prob × n_cells_del_AOI`. Prueba sobre el caso estrella: **Delta del Paraná, semana 40 de 2020** (año récord).

| Cálculo | Resultado |
|---|---:|
| Celdas en el AOI | 331 |
| Celdas “reliable” (`n_years >= 5`) | 13 |
| `expected` usando todas las celdas | **280,6** |
| `expected` usando solo reliable | **8,2** |
| Celdas observadas con fuego en 2020 | **78** |
| Celdas observadas con fuego en 2019 | **18** |
| Anomalía resultante (todas) | **0,28** (“muy por debajo de lo normal”) |
| Anomalía resultante (reliable) | **9,5** (“9,5× lo normal”, con 13 celdas) |

Ninguno de los dos números es defendible. El primero dice que 2020 fue un año bajo; el segundo se apoya en 13 celdas.

**Recomendación:** cambiar el baseline a nivel AOI, que además es lo que pide el challenge (“anomalías con respecto a promedios multianuales”):

```text
para cada AOI y semana ISO:
  observed(year) = celdas con fire_day en esa semana/año
  baseline       = media de observed en los años de referencia (excluyendo el año evaluado)
  anomaly        = observed / baseline
```

No necesita denominador por día ni cobertura nubosa, y es robusto. La probabilidad por celda puede quedar como capa secundaria, renombrada a `fire_day_rate_given_detection`, sin llamarla “probabilidad de fuego”.

### 5.2. `n_years` mide años con fuego, no años con datos (P0)

`n_years = count(DISTINCT year)` sobre `grid_daily` cuenta solo los años en que esa celda/semana tuvo al menos una detección. Consecuencias:

- Solo **8,1%** de las filas de `calendar_weekly` tiene `n_years >= 5`.
- Una celda con 5 años de una sola detección cada uno sería “reliable”.
- Una celda con 4 años de incendios intensos sería “no reliable”.

El umbral está invertido respecto de la idea de cobertura. Para que `reliable` signifique algo, `n_years` debería contar **años con datos disponibles** en esa celda/semana, no años con fuego.

### 5.3. “Número de focos” no está resuelto de forma sensor-agnóstica (P0)

El challenge pide un calendario con **número de focos detectados**. El proyecto, correctamente, evita sumar conteos entre sensores. Pero eso deja un vacío:

- `grid_daily.n_detections` cuenta **todas** las detecciones, de todos los sensores, incluyendo industriales, baja confianza y `type` 1/3.
- Hay **581.943 celdas-día con más de un sensor**, de las cuales **306.073 mezclan MODIS con VIIRS**. En esos días, `n_detections` y `frp_sum_mw` están inflados por el mismo fuego visto por varios satélites.
- El plan de API devuelve `"detections": 1234` en el calendario semanal. Si el frontend lo muestra como “focos”, el usuario ve una mezcla no comparable.
- **274.356 celdas-día (13%)** tienen `fire_day = false` pero `n_detections > 0`: son detecciones industriales o de baja confianza.

**Opciones defendibles:**

1. Mostrar como titular **“celdas con actividad”** (`fire_day`), que sí es sensor-agnóstico.
2. Mostrar “detecciones” **solo con un sensor seleccionado** (MODIS / SNPP / NOAA-20 / NOAA-21).
3. Implementar `fire_events` (clustering < 5 km, mismo día o consecutivos) y contar **eventos**, lo más cercano a “número de incendios” y más estable entre sensores.

Hoy el clustering está como opcional/fuera del MVP, pero es justamente lo que permitiría cumplir el objetivo sin violar el principio de no sumar sensores.

### 5.4. La promesa “el sensor deja de ser relevante” no está demostrada (P0)

Serie anual de celdas-día con `fire_day`:

| Año | Celdas-día con fuego | Cambio |
|---:|---:|---:|
| 2011 | 34.403 | — |
| 2012 | **80.365** | **×2,3** (entra VIIRS SNPP) |
| 2017 | 70.498 | — |
| 2018 | **98.876** | **×1,4** (entra NOAA-20) |
| 2020 | 197.756 | año récord real |

El salto de 2012 es demasiado grande para ser solo clima. La grilla de 0,05° y el `fire_day` reducen el problema, pero no lo eliminan: VIIRS sigue detectando fuegos pequeños que MODIS no ve. La validación **MODIS-only vs VIIRS-only** del plan (Spearman, ranking de semanas) es exactamente lo que falta para poder afirmar que la serie es coherente. Sin eso, el calendario 2000–2026 tiene una discontinuidad en 2012.

**Recomendación:** correr esa validación **antes** de construir la UI. Si el ranking de semanas coincide (Spearman > 0,8), presentar el calendario como “actividad relativa”. Si no:

- calendario 2000–2011 basado en MODIS y 2012–2026 basado en VIIRS, con banda de transición explícita;
- índice de actividad por celda calibrado contra MODIS;
- conteos por sensor con selector, nunca un total único.

### 5.5. No se distingue “sin datos” de “sin fuego” (P1)

El principio 6 del plan dice “No interpretar `NULL` como cero”, pero el modelo no lo implementa:

- `grid_daily` **no tiene filas** para celdas/días sin detecciones. La ausencia de fila puede ser “no hubo fuego” o “no hubo overpass / nubes”.
- `calendar_weekly` tiene en promedio **11,16 semanas por celda**, no 53. Solo hay **1.537 filas de semana 53**.
- `grid_daily` no guarda `sources` ni un indicador de NRT: **2026 (NRT provisional) no se distingue de 2010 (SP archivado)** en el calendario.
- No hay tabla de cobertura ni banderas de cobertura, aunque el alcance del plan las menciona.

**Recomendación mínima:**

- Agregar `sources TEXT[]` o `n_nrt` / `is_provisional` a `grid_daily`.
- Agregar `coverage_daily` (sensor, fecha, ¿hubo datos?) construida a partir de los días con cualquier detección en la región.
- Completar `calendar_weekly` a 53 semanas con `NULL` donde no hay datos (≈ 3,7 M de filas, manejable).

### 5.6. El FRP se suma entre sensores, contradiciendo el propio principio (P1)

El plan dice “no sumar FRP entre sensores”, pero `grid_daily.frp_sum_mw` hace `sum(frp_mw)` sobre todos los sensores, y `calendar_weekly.frp_p50/p90` se calculan sobre esa suma. En **306.073 celdas-día** hay MODIS + VIIRS mezclados. El FRP máximo también se calcula sobre todos.

No es fatal porque el FRP es secundario, pero **la implementación no cumple el principio 8** del plan. Solución: guardar FRP por sensor (columnas separadas o JSONB) y calcular percentiles solo dentro de un sensor; o excluir del cálculo los días multi-sensor.

### 5.7. Semana ISO con año calendario (P2)

`calendar_weekly` agrupa por `extract(week FROM day)` (semana ISO) pero cuenta años con `extract(year FROM day)` (año calendario). En PostgreSQL, para la semana ISO hay que usar `extract(isoyear FROM day)`. Hay **25.819 detecciones** en fechas donde `isoyear <> year`, típicamente alrededor del 1 de enero. Eso desalinea las semanas 1, 52 y 53 y puede duplicar o perder años en `n_years`.

### 5.8. Reproducibilidad y seguridad (P1/P2)

- El ETL usa rutas absolutas de Windows y, para **NOAA-20 archive** y **MODIS NRT**, lee CSV desde `C:\Users\HP\AppData\Local\Temp\opencode\firms\`, que **no está en el repo**. Hoy funciona porque esos archivos existen; en otra máquina o si se limpia el temp, la carga queda incompleta.
- Los scripts del informe (pandas/shapely) están en temp, no versionados. El informe dice “scripts... en Temp”.
- `application.properties` tiene `spring.datasource.password=postgres` hardcodeado, contradiciendo el propio DoD (“Sin credenciales en el repo”).
- No hay `pom.xml`, `docker-compose`, tests ni CI. El jurado no puede reproducir el proyecto solo con el repo.

### 5.9. Detalles menores

- `frp_zero` con confianza nominal/alta: **1.458 detecciones**; no se filtran en `fire_day`. Impacto bajo, pero el informe recomendaba descartarlas.
- `low_brightness` con confianza nominal/alta: **0**; el flag no tiene impacto práctico.
- `confidence_max` de `grid_daily` se calcula sobre todas las detecciones, incluidas industriales.
- `n_detections` incluye `type` 1/2/3 y baja confianza; si la UI muestra “detecciones”, hay que explicarlo.

---

## 6. Cumplimiento de los planes

### 6.1. Plan de armonización

| Criterio de aceptación | Estado real |
|---|---|
| Las 8 fuentes están presentes o su ausencia está documentada | ✅ 8/8 presentes |
| No hay duplicados exactos | ✅ 0 reportados |
| No hay solapamiento SP/NRT sin resolver | ✅ 0 por sensor/fecha |
| `v_detections_canonical` devuelve una sola fuente por sensor/fecha | ✅ |
| `industrial_cells` cubre los focos conocidos | ✅ 885 celdas |
| NRT industrial no marca `fire_day` | ✅ |
| `calendar_weekly` tiene 53 semanas por celda | ❌ 11,16 promedio; 1.537 filas de semana 53 |
| `fire_day_prob` y `n_years` son consistentes | ❌ ver §5.1 y §5.2 |
| Los eventos 2020, 2022, 2013 y 2017 aparecen en el calendario | ❌ no validado formalmente |
| MODIS-only y VIIRS-only coinciden en períodos críticos | ❌ pendiente |
| Las limitaciones están documentadas | 🟡 parcialmente (informe y planes) |

### 6.2. Plan de backend

| Componente | Estado |
|---|---|
| `pom.xml` / esqueleto Maven | ❌ no existe |
| `ChronosFireApplication` | ❌ no existe |
| `HealthController` / `/api/health` | ❌ planificado |
| `/api/meta` | ❌ planificado |
| `/api/calendar/*` | ❌ planificado |
| `/api/detections` + GeoJSON | ❌ planificado |
| `/api/alerts` | ❌ planificado |
| Exportación CSV | ❌ **no planificada** |
| Rol de solo lectura | ❌ no creado |
| Tests | ❌ no existen |

**Conclusión:** el plan de backend está bien diseñado, pero es solo un plan. El README lo confirma: “la implementación todavía no está hecha”.

---

## 7. Riesgos y prioridades

### P0 — Para que el challenge se cumpla

1. **Corregir la climatología/anomalía** (§5.1 y §5.2). Cambio de SQL acotado: baseline semanal por AOI.
2. **Implementar el backend MVP**:
   - `/api/health`, `/api/meta`
   - `/api/calendar/weekly`, `/daily`, `/heatmap`
   - `/api/detections` + `/geojson`
   - `/api/alerts`
3. **Agregar exportación CSV** (`/api/detections/export.csv` y, si se puede, `/api/calendar/export.csv`). El challenge lo pide explícitamente.
4. **Frontend mínimo viable**:
   - mapa (Leaflet/MapLibre) con rectángulo o polígono dibujable + inputs lat/lon;
   - selector de rango de fechas;
   - calendario semana × año con rampa accesible (viridis/magma, no rojo-verde);
   - línea de anomalía vs baseline;
   - botón “Descargar CSV”.
5. **Validación mínima**: 4 eventos conocidos + comparación MODIS-only vs VIIRS-only.

### P1 — Para que sea defendible

6. Banderas de cobertura y de NRT en `grid_daily` / `calendar_weekly`.
7. Completar 53 semanas con `NULL`.
8. Corregir `isoyear`.
9. Separar FRP por sensor.
10. Definir la métrica de “número de focos”: celdas con actividad, conteo por sensor o eventos.

### P2 — Para que sea reproducible

11. Mover los CSV de temp al repo (o documentar la descarga) y los scripts de análisis a `analisis/`.
12. Quitar la password del `application.properties`.
13. `docker-compose` con PostgreSQL + dump, y README con pasos exactos.

---

## 8. Encuadre recomendado para la demo

No conviene decir “armonización completa” ni “calendario de número de incendios”. La frase defendible es:

> “Construimos un calendario de **actividad de fuego por celda de ~5 km**, sensor-agnóstico a nivel de presencia diaria (`fire_day`), con detecciones por sensor como capa de detalle, máscara de focos industriales, política SP/NRT y anomalías contra un baseline multianual por AOI. La calibración física y la comparación fina MODIS–VIIRS quedan documentadas como trabajo futuro.”

Eso es exactamente lo que el proyecto **puede** sostener hoy, y es más valioso que prometer un conteo de incendios que los datos no permiten afirmar sin calibración.

---

## 9. Anexo — Consultas de verificación

Consultas de solo lectura usadas para este informe. Se pueden reproducir tal cual.

```sql
-- Cobertura por sensor/fuente
SELECT sensor, source, count(*) AS n, min(acq_date) AS desde, max(acq_date) AS hasta
FROM detections
GROUP BY 1, 2
ORDER BY 1, 2;

-- Volúmenes
SELECT (SELECT count(*) FROM detections)      AS detections,
       (SELECT count(*) FROM grid_daily)      AS grid_daily,
       (SELECT count(*) FROM calendar_weekly) AS calendar_weekly,
       (SELECT count(*) FROM industrial_cells) AS industrial_cells;

-- Semanas por celda
SELECT count(*) AS celdas,
       min(n) AS min_semanas,
       max(n) AS max_semanas,
       round(avg(n), 2) AS avg_semanas
FROM (SELECT grid_id, count(*) AS n FROM calendar_weekly GROUP BY 1) t;

-- Confiabilidad de la climatología
SELECT count(*) AS filas,
       count(*) FILTER (WHERE n_years >= 5) AS ge5,
       round(100.0 * count(*) FILTER (WHERE n_years >= 5) / count(*), 1) AS pct_ge5,
       min(n_years) AS min_ny,
       max(n_years) AS max_ny
FROM calendar_weekly;

-- Días con detecciones por celda-semana vs probabilidad
WITH obs AS (
  SELECT grid_id, extract(week FROM day)::int AS w, count(*) AS dias_obs
  FROM grid_daily
  GROUP BY 1, 2
)
SELECT CASE
         WHEN dias_obs <= 3  THEN '1-3'
         WHEN dias_obs <= 7  THEN '4-7'
         WHEN dias_obs <= 14 THEN '8-14'
         WHEN dias_obs <= 28 THEN '15-28'
         ELSE '>28'
       END AS dias_observados,
       count(*) AS celdas_semana,
       round(avg(cw.fire_day_prob)::numeric, 3) AS prob_media
FROM obs
JOIN calendar_weekly cw
  ON cw.grid_id = obs.grid_id AND cw.week_of_year = obs.w
GROUP BY 1
ORDER BY 1;

-- Anomalía Delta del Paraná, semana 40 de 2020
WITH aoi AS (
  SELECT grid_id, fire_day_prob, n_years
  FROM calendar_weekly
  WHERE week_of_year = 40
    AND cell_lat BETWEEN -34 AND -32.5
    AND cell_lon BETWEEN -61 AND -59.5
),
obs20 AS (
  SELECT count(DISTINCT grid_id) AS n
  FROM grid_daily
  WHERE fire_day
    AND extract(week FROM day) = 40
    AND extract(isoyear FROM day) = 2020
    AND cell_lat BETWEEN -34 AND -32.5
    AND cell_lon BETWEEN -61 AND -59.5
),
obs19 AS (
  SELECT count(DISTINCT grid_id) AS n
  FROM grid_daily
  WHERE fire_day
    AND extract(week FROM day) = 40
    AND extract(isoyear FROM day) = 2019
    AND cell_lat BETWEEN -34 AND -32.5
    AND cell_lon BETWEEN -61 AND -59.5
)
SELECT (SELECT count(*) FROM aoi) AS celdas_aoi,
       (SELECT count(*) FROM aoi WHERE n_years >= 5) AS celdas_reliables,
       round((SELECT sum(fire_day_prob) FROM aoi)::numeric, 1) AS esperado_todas,
       round((SELECT sum(fire_day_prob) FROM aoi WHERE n_years >= 5)::numeric, 1) AS esperado_reliable,
       (SELECT n FROM obs19) AS obs_2019,
       (SELECT n FROM obs20) AS obs_2020;

-- Celdas-día con múltiples sensores
SELECT count(*) AS multi_sensor_cellday,
       count(*) FILTER (
         WHERE 'MODIS' = ANY(sensors)
           AND sensors && ARRAY['VIIRS_SNPP','VIIRS_NOAA20','VIIRS_NOAA21']
       ) AS modis_mas_viirs
FROM grid_daily
WHERE array_length(sensors, 1) > 1;

-- Semana ISO vs año calendario
SELECT count(*) AS fechas_isoyear_distinto
FROM detections
WHERE extract(isoyear FROM acq_date) <> extract(year FROM acq_date);
```

---

## 10. Conclusión

Chronos Fire tiene un **pipeline de datos serio y bien pensado**, con decisiones que resisten una defensa técnica. Lo que falta no es más armonización: es **convertir esa base en el producto que pide el challenge** y **corregir el baseline de la anomalía** para que el calendario diga la verdad.

Con los cinco P0 resueltos —anomalía, backend, CSV, frontend mínimo y validación MODIS/VIIRS— el proyecto pasa de “muy buen pipeline” a “solución completa del challenge”.
