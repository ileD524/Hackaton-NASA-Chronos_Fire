# Plan de armonización de datos — Chronos Fire

**Proyecto:** Hackaton NASA Chronos Fire  
**Objetivo:** armonizar las detecciones MODIS y VIIRS lo suficiente para construir un calendario de actividad de fuego comparable, reproducible y defendible.  
**Base:** PostgreSQL 17, `chronos_fire`  
**Cobertura:** Argentina, 2000–2026  
**Estado:** implementación parcial. 6.2 (SP/NRT), 6.3 (máscara industrial), corrección C0–C2 del calendario y validación §6.8 (V0–V4) implementados en PostgreSQL. Informe: `backend/VALIDACION_6_8.md`.

---

## 1. Alcance

### Incluido

- Estandarización estructural de los 4 sensores.
- Homologación de confianza.
- Política SP/NRT.
- Grilla común de 0,05°.
- `fire_day` binario como unidad del calendario.
- Máscara de focos industriales para NRT.
- Calendario semanal completo (53 semanas).
- Banderas de cobertura y confiabilidad.
- Validación con eventos conocidos.
- Análisis de sensibilidad MODIS vs VIIRS.

### Fuera de alcance

- Calibración física de eficiencia de detección.
- Calibración de FRP por bioma/estación.
- Área quemada.
- Nubosidad/QA por píxel.
- Provincias/departamentos.
- Validación con reportes oficiales.
- Escala global.

La armonización propuesta es **operativa**, no científica completa. Alcanza para el calendario y las alertas, y documenta explícitamente lo que no resuelve.

---

## 2. Qué significa “suficiente para el calendario”

Una armonización es suficiente si permite afirmar:

1. Todos los sensores usan el mismo esquema, unidades y zona horaria.
2. La confianza se puede filtrar de forma equivalente.
3. MODIS y VIIRS no se suman como si fueran lo mismo.
4. Un mismo píxel/instante no se cuenta dos veces.
5. Los focos industriales no se confunden con incendios de vegetación.
6. Las semanas sin datos se distinguen de las semanas sin fuego.
7. Los eventos históricos conocidos aparecen en el calendario.
8. Las limitaciones están documentadas.

No requiere:

- probabilidad real de detección,
- FRP calibrado,
- área quemada,
- incertidumbre formal.

---

## 3. Datos disponibles

| Sensor | Resolución | Período | Filas aprox. | Fuente |
|---|---:|---|---:|---|
| MODIS C6.1 | 1 km | 2000-11 → 2026-08 | 1,73 M | SP + NRT |
| VIIRS SNPP | 375 m | 2012-01 → 2026-11 | 2,98 M | SP + NRT |
| VIIRS NOAA-20 | 375 m | 2018-04 → 2026-11 | 1,83 M | SP + NRT |
| VIIRS NOAA-21 | 375 m | 2023-03 → 2026-11 | 0,54 M | SP + NRT |

### Campos disponibles

```text
latitude, longitude, brightness, bright_t31,
scan, track, frp, confidence, type,
daynight, acq_date, acq_time,
satellite, instrument, version
```

### Lo que no está disponible

- Radiancias crudas.
- QA/cloud mask por píxel.
- Ángulo de visión.
- Uso del suelo/bioma.
- Área quemada.
- Perímetros.
- Incertidumbre de FRP.
- Reportes oficiales.

---

## 4. Principios de armonización

1. **No sumar conteos entre sensores.** VIIRS detecta ~3–3,6× más que MODIS.
2. **Usar `fire_day` binario** por celda/día como unidad del calendario.
3. **Homologar confianza** a `low/nominal/high`.
4. **SP gana sobre NRT** por sensor/fecha.
5. **Excluir focos industriales** del análisis de vegetación.
6. **No interpretar `NULL` como cero.** Sin datos ≠ sin fuego.
7. **Documentar la incertidumbre** y no sobreafirmar la comparabilidad.
8. **FRP es secundario.** Se usa `frp_density_wm2`, no sumas crudas entre sensores.

---

## 5. Arquitectura de datos

```text
Archivos FIRMS (JSON/CSV)
        │
        ▼
detections                 ← estandarización estructural
        │
        ▼
v_detections_canonical     ← política SP/NRT
        │
        ▼
grid_daily                 ← fire_day + FRP + sensores
        │
        ▼
calendar_weekly            ← climatología y confiabilidad
        │
        ▼
API / Vue 3
```

### Capas

| Capa | Contenido | Responsable |
|---|---|---|
| Raw | JSON/CSV originales | Descarga FIRMS |
| Curated | `detections` | ETL DuckDB |
| Canónica | `v_detections_canonical` | SQL |
| Agregada | `grid_daily` | ETL / SQL |
| Calendario | `calendar_weekly` | ETL / SQL |
| Consumo | API Java | Backend |

---

## 6. Componentes de la armonización

### 6.1 Estructural (ya implementado)

- Esquema único para los 4 sensores.
- `sensor`, `source`, `product_version`, `satellite`, `instrument`.
- `acquired_at` en UTC.
- `confidence_norm` 0–100 y `confidence_class`.
- `fire_type` e `is_vegetation`.
- `pixel_area_km2` y `frp_density_wm2`.
- `quality_flags`.
- Deduplicación por píxel + instante.

**Estado:** ✅ hecho.

---

### 6.2 Política SP/NRT

**Regla:** para cada sensor + fecha, si existe SP, se usa SP. Si no, se usa NRT hasta que SP esté disponible.

```sql
CREATE OR REPLACE VIEW v_detections_canonical AS
SELECT d.*
FROM detections d
WHERE d.source = 'SP'
   OR NOT EXISTS (
       SELECT 1
       FROM detections s
       WHERE s.sensor   = d.sensor
         AND s.source   = 'SP'
         AND s.acq_date = d.acq_date
   );
```

**Uso:**

- Detalle y alertas → `v_detections_canonical`.
- Alertas → filtrar `source = 'NRT'` para mostrar solo lo no archivado.
- Agregados → reconstruir desde la vista cuando SP se solape con NRT.

**Caveat:** es una política por sensor/fecha, no por píxel. Si SP cubre parcialmente un día, se oculta el NRT de ese sensor/día. Para MODIS, `sensor` agrupa Terra y Aqua; si se necesita granularidad por plataforma, la política debe usar `satellite` + `acq_date`.

**Estado:** ✅ implementado en PostgreSQL: vista `v_detections_canonical` e índice `detections_sensor_source_date_idx` creados.

---

### 6.3 Máscara de focos industriales

**Problema:** el NRT no trae `type`, así que `fire_type` es `NULL` y `is_vegetation` también. Sin máscara, una antorcha industrial puede marcar `fire_day` y generar alertas.

**Solución implementada:**

1. Construir la máscara desde SP, usando **solo `type = 2`**. `type = 3` (barcos/plataformas) queda fuera de alcance.
2. Agregar por celda de 0,01° (~1 km):
   - `n_industrial` con `type = 2`.
   - `n_total` con todas las detecciones SP de la celda.
   - años distintos con presencia de `type = 2`.
3. Conservar celdas con:
   - `n_industrial >= 10`
   - `n_years >= 3`
   - `industrial_ratio = n_industrial / n_total >= 0,5`
4. **Sin dilatación.**
5. Aplicar a NRT:
   - Si la detección cae en la máscara, marcar `industrial_mask = true`.
   - Excluirla de `fire_day` y de alertas.
   - No borrar la detección y no excluir toda la celda de 0,05°.

**Tabla implementada:**

```sql
CREATE TABLE industrial_cells (
    cell_lat_idx     INTEGER NOT NULL,
    cell_lon_idx     INTEGER NOT NULL,
    cell_lat         DOUBLE PRECISION NOT NULL,
    cell_lon         DOUBLE PRECISION NOT NULL,
    n_industrial     INTEGER NOT NULL,
    n_total          INTEGER NOT NULL,
    industrial_ratio REAL NOT NULL,
    n_years          INTEGER NOT NULL,
    first_seen       DATE,
    last_seen        DATE,
    types            SMALLINT[],
    mask_version     TEXT NOT NULL DEFAULT 'v1',
    built_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (cell_lat_idx, cell_lon_idx)
);
```

Se creó la vista `v_detections_analysis`:

```sql
SELECT d.*,
       (d.source = 'NRT' AND ic.cell_lat_idx IS NOT NULL) AS industrial_mask,
       CASE WHEN d.source = 'NRT' AND ic.cell_lat_idx IS NOT NULL
            THEN FALSE ELSE d.is_vegetation END AS is_vegetation_effective
FROM v_detections_canonical d
LEFT JOIN industrial_cells ic
  ON ic.cell_lat_idx = round(d.latitude / 0.01)::int
 AND ic.cell_lon_idx = round(d.longitude / 0.01)::int;
```

**Resultado:**

- **885 celdas** industriales.
- **4.041 detecciones NRT (2,8%)** marcadas como industriales.
- **108 celdas borderline** con ratio 0,5–0,7, para revisión.
- Focos conocidos cubiertos: San Nicolás, Zárate, La Plata, Neuquén y Olavarría.
- `grid_daily` y `calendar_weekly` reconstruidos desde `v_detections_analysis`.
- `v_latest_nrt` y `v_vegetation_detections` excluyen NRT industrial.
- Impacto: `fire_day` bajó de 1.829.790 a 1.828.769 (−1.021); FRP acumulado −0,19%; alertas NRT −398.
- `n_detections` sigue contando todas las detecciones; solo `fire_day` y el FRP excluyen industriales.

**Estado:** ✅ implementado en PostgreSQL.

**Script reproducible:** `backend/db/build_industrial_mask.sql`.  
**ETL:** `etl/load_pg.py --aggregates` reconstruye `industrial_cells` antes de `grid_daily`.

---

### 6.4 Armonización espacial

**Grilla:** 0,05° (~5 km).

```text
grid_id  = '0.05:' || round(lat/0.05) || ':' || round(lon/0.05)
cell_lat = round(lat/0.05) * 0.05
cell_lon = round(lon/0.05) * 0.05
```

**Por qué 0,05°:**

- Compromiso entre 375 m y 1 km.
- Suficiente para un calendario regional.
- Evita que un píxel de VIIRS domine la celda.

**Limitación:** un incendio pequeño puede quedar diluido; dos incendios cercanos pueden colapsar en una celda.

**Sensibilidad:** comparar 0,05° vs 0,1° en una muestra de AOI. Si el calendario cambia mucho, documentarlo.

**Estado:** ✅ implementado en `grid_daily`.

---

### 6.5 Armonización temporal

- Día UTC (`acq_date`).
- Semana ISO con **`isoyear`**, no año calendario.
- **53 semanas por celda** en `calendar_weekly`.
- `grid_weekly`: actividad observada por celda / isoyear / semana.
- `grid_weekly_sensor`: actividad por celda / isoyear / semana / sensor.
- `n_fire_years`: años con `fire_day`.
- `n_activity_years`: años con alguna detección.
- `n_data_years`: años con cobertura; queda `NULL` hasta cargar `coverage_daily`.
- La confiabilidad de la anomalía se calcula por AOI con `n_ref_years`, no por celda.

**Implementado:** `calendar_weekly` tiene 3.719.116 filas, 53 semanas para 70.172 celdas.  
**Pendiente:** `n_data_years` y `coverage_daily` (proxy de cobertura).

---

### 6.6 Armonización de intensidad

- **No sumar FRP entre sensores.**
- Usar `frp_density_wm2 = frp_mw / pixel_area_km2` como indicador secundario.
- Reportar FRP por sensor y por celda, no un total único.
- No usar `brightness` como intensidad: VIIRS satura en 367 K y MODIS en ~500 K.

**Estado:** ✅ campos calculados; interpretación documentada.

---

### 6.7 Construcción del calendario

**`grid_daily`:**

```sql
fire_day = bool_or(veg_and_conf)
```

**`grid_weekly` / `grid_weekly_sensor`:**

```text
fire_days        = días con fire_day en la celda/semana
fire_cells       = 1 si fire_days > 0
n_detections     = todas las detecciones de la celda/semana
detections_by_sensor = JSONB por sensor
frp_by_sensor    = JSONB por sensor (vegetación confiable)
```

**`calendar_weekly`:**

```text
fire_day_rate_given_detection = sum(fire_days) / sum(detection_days)
n_fire_years                  = años con fire_cells = 1
n_activity_years              = años con alguna detección
n_data_years                  = años con cobertura (NULL hasta coverage_daily)
fire_days_mean                = promedio de fire_days por año con actividad
provisional_share             = proporción de años con NRT
```

`fire_day_rate_given_detection` **no es una probabilidad de fuego**: mide la proporción de días con detección que además tienen fuego de vegetación confiable.

**Anomalía por AOI:**

```text
observed(year, week) = count(distinct grid_id)
                       where fire_cells = 1
                         and isoyear = year
                         and week_of_year = week
                         and bbox

baseline(week) = median(observed(y, week))
                 for y in reference_years, y != evaluated_year

anomaly = observed(year, week) / baseline(week)
```

Reglas:

- Baseline con **mediana**, no media.
- `n_ref_years >= 7` para mostrar; `>= 10` alta confianza.
- `baseline >= 3`; si es menor, no mostrar.
- `observed >= 3`; si es menor, no mostrar.
- Si se evalúa 2026, marcar `provisional = true`.

**Estado:** ✅ base corregida en PostgreSQL (`grid_weekly`, `grid_weekly_sensor`, `calendar_weekly`).  
**Pendiente:** API con baseline por AOI, `coverage_daily` y validación formal MODIS/VIIRS.

---

### 6.7.1 Decisiones C0 para el baseline

| Era | Años de referencia | Sensor del baseline |
|---|---|---|
| MODIS | 2003–2011 | MODIS |
| Transición | — | 2012: sin anomalía |
| VIIRS consistente | 2013–2025 | **VIIRS SNPP solamente** |
| Provisional | 2013–2025 | SNPP; 2026 se evalúa como provisional |

**Exclusiones:**

- 2000–2002: MODIS en spin-up.
- 2012: transición a VIIRS.
- 2026: NRT provisional.
- El año evaluado se excluye del baseline (leave-one-out).
- 2018 y 2023 solo se excluyen si se usa “todos los VIIRS”; con SNPP-only no hace falta.

**Umbrales:**

- `n_ref_years >= 7`; `>= 10` alta confianza.
- `baseline_fire_cells >= 3`; `>= 10` alta confianza.
- `observed_fire_cells >= 3`.
- `n_cells_aoi >= 10`.
- Mediana, no media.

**Validación ya realizada:**

| Caso | Observado | Baseline mediana | n_ref | Anomalía |
|---|---:|---:|---:|---:|
| Delta 2020, semana 40 | 66 | 10 | 12 | **6,60** |
| Corrientes 2022, semanas 2–7 | 193–392 | 11–36,5 | 11–12 | **10,7–20,2** |
| Salta 2013, semana 37 | 164 | 84 | 9 | **1,95** |
| Córdoba 2013, semana 37 | 86 | 7 | 9 | **12,29** |
| La Pampa 2017, semana 1 | 311 | 3,5 | 12 | **88,86** (confianza baja) |

La Pampa queda con confianza baja por baseline 3,5; el valor se muestra con advertencia.

**Nota de consistencia (2026-11-14):** la fila del Delta usa observado y baseline
VIIRS SNPP-only. Con todos los sensores, el observado de la semana 40 es 78 y la
anomalía 7,80, no 6,60. La tabla debe declarar el conjunto de sensores. Las filas
de Corrientes, Salta, Córdoba y La Pampa no tienen AOI registrada: se recalculan
en `backend/VALIDACION_6_8.md` con bboxes preliminares y pueden diferir.

---

### 6.8 Validación

#### Validación interna

- Conteos por sensor/fuente.
- Cobertura por año.
- Duplicados exactos.
- Solapamiento SP/NRT.
- Porcentaje de `fire_day` por semana.
- `n_fire_years` y `n_activity_years` por celda.

#### Validación con eventos conocidos

| Evento | Período esperado | Qué verificar |
|---|---|---|
| Delta del Paraná | Ago–Oct 2020 | Pico semanal y anomalía > 2 |
| Corrientes/Iberá | Ene–Feb 2022 | Pico en semanas 2–6 |
| Salta/Jujuy | Sep 2013 | Pico en semana 37 |
| La Pampa | Dic 2016–Ene 2017 | Pico en semanas 52–2 |

#### Sensibilidad MODIS vs VIIRS

- Construir el calendario con:
  - solo MODIS,
  - solo VIIRS,
  - ambos.
- Comparar:
  - ranking de semanas,
  - correlación de Spearman,
  - diferencias en `fire_cells` y `fire_day_rate_given_detection`.
- Si las tres versiones coinciden en los períodos críticos, la armonización es robusta para el calendario.

#### Coincidencia espacial

- Emparejar MODIS y VIIRS en el período de solapamiento.
- Ventana: mismo día y < 1 km.
- Reportar:
  - % de coincidencia,
  - ratio de detecciones,
  - diferencia de FRP.

**Estado:** 🟡 parcial (V0–V4 implementados el 2026-11-14).

**Implementado:**

- V0: contrato de validación y registro de AOIs en `validation_aoi` y
  `validation_aoi_window`.
- V1: validación interna completa. 0 duplicados, 0 solapamiento SP/NRT,
  3.719.116 filas de calendario, 1.828.709 días-celda consistentes.
- V2: 6/6 ventanas de evento con anomalía > 1 y `n_ref_years >= 7`.
  Delta 2020 reproduce 66/10/12/6,60 con VIIRS SNPP-only.
- V3: sensibilidad nacional Spearman 0,969 y top-5 0,908. En el año del evento,
  el top-5 coincide en 5/6 ventanas.
- V4: coincidencia espacial MODIS/VIIRS. Serie completa SNPP 2013–2025 y
  barrido NOAA-20 (2018–2025) y NOAA-21 (2023–2025): 52–69% de MODIS y
  37–49% de VIIRS con SNPP; mismo día, < 1 km. Sensibilidad ≤ 3 h: 57,3%/40,4%.
- V3b: sensibilidad con `high` only. Nacional Spearman 0,949 y top-5 0,877.
  El ratio SNPP/MODIS cae de 2,26 a 0,85: `high` no es homólogo entre sensores.
  Conclusión: usar `nominal + high` en el calendario.

**Pendiente:**

- Confirmar las AOIs de Corrientes, Salta, Córdoba y La Pampa (hoy
  `provisional`).
- Sensibilidad con grilla 0,1°.
- `coverage_daily` y `n_data_years` siguen vacíos.

**Scripts:** `backend/db/validacion/`.  
**Informe:** `backend/VALIDACION_6_8.md`.

---

### 6.9 Clustering de eventos (opcional)

- Agrupar detecciones por proximidad espacial y temporal:
  - < 5 km,
  - mismo día o días consecutivos.
- Crear `fire_events` con:
  - inicio, fin,
  - centroide,
  - FRP máximo,
  - sensores,
  - confianza.
- Usar para el calendario por evento, no para reemplazar `fire_day`.

**Estado:** ❌ no incluido en el MVP; recomendado como siguiente paso.

---

## 7. Plan de implementación

### H0 — Auditoría (hecho)

- Verificar las 8 fuentes.
- Confirmar `detections`, `grid_daily`, `calendar_weekly`.
- Revisar duplicados y solapamiento SP/NRT.

### H1 — Armonización operativa (1–2 días)

1. ✅ Crear `v_detections_canonical` (hecho).
2. ✅ Construir `industrial_cells` desde SP (hecho: 885 celdas).
3. ✅ Aplicar la máscara a NRT vía `v_detections_analysis` (hecho).
4. ✅ Reconstruir `grid_daily`, `grid_weekly`, `grid_weekly_sensor` y `calendar_weekly` (hecho).
5. ✅ Completar las 53 semanas (hecho: 3.719.116 filas).
6. ✅ Agregar `n_fire_years`, `n_activity_years`, `n_data_years`, `fire_days_mean`, `provisional_share`.
   - `n_data_years` queda `NULL` hasta cargar `coverage_daily`.

**Entregable:** calendario consistente, sin industriales en NRT, sin semanas faltantes.

### H2 — Validación (1–2 días)

1. ✅ Validar eventos conocidos: Delta 2020, Corrientes 2022, Salta/Córdoba 2013 y La Pampa 2017.
2. 🟡 Comparar MODIS-only vs VIIRS-only vs ambos (V3 y V3b implementados; falta cerrar AOIs).
3. ✅ Calcular coincidencia espacial (V4 serie SNPP 2013–2025 + NOAA-20/21).
4. 🟡 Documentar resultados y limitaciones (informe preliminar en `backend/VALIDACION_6_8.md`).

**Entregable:** informe de validación y métricas.

### H3 — Eventos (2–3 días, opcional)

1. Clustering espacio-temporal.
2. Tabla `fire_events`.
3. Calendario por evento.

**Entregable:** conteo de eventos y duración.

### H4 — Futuro

- Provincias/PostGIS.
- Área quemada.
- QA/nubosidad.
- Calibración empírica de eficiencia.
- Calibración de FRP.

---

## 8. Criterios de aceptación

- [x] Las 8 fuentes están presentes o su ausencia está documentada.
- [x] No hay duplicados exactos.
- [x] No hay solapamiento SP/NRT sin resolver.
- [x] `v_detections_canonical` devuelve una sola fuente por sensor/fecha.
- [x] `industrial_cells` cubre los focos industriales conocidos.
- [x] NRT industrial no marca `fire_day`.
- [x] `calendar_weekly` tiene 53 semanas por celda (con `NULL` donde falta).
- [x] `fire_day_prob` renombrada a `fire_day_rate_given_detection`; `n_fire_years` y `n_activity_years` separados.
- [x] Los eventos 2020, 2022, 2013 y 2017 aparecen en el calendario con anomalía > 1.
- [ ] MODIS-only y VIIRS-only coinciden en los períodos críticos.
- [ ] Las limitaciones están documentadas por completo (coverage y validación pendientes).

---

## 9. Métricas de calidad

| Métrica | Objetivo |
|---|---|
| Cobertura de fuentes | 8/8 o justificar faltantes |
| Duplicados exactos | 0 |
| Solapamiento SP/NRT sin resolver | 0 |
| Celdas con 53 semanas | 100% ✅ |
| Celdas-semana con `n_fire_years >= 7` | 18.516 (0,50%); 5.486 celdas (7,82%) |
| Coincidencia MODIS/VIIRS | MODIS 52–69%; VIIRS 37–49% (SNPP, < 1 km, 2013–2025) |
| Eventos conocidos con anomalía > 1 | 4/4 ✅ |
| Baseline mínimo | `n_ref_years >= 7`; `baseline >= 3` |

---

## 10. Riesgos y mitigaciones

| Riesgo | Impacto | Probabilidad | Mitigación |
|---|---|---|---|
| NRT sin `type` → industriales como vegetación | Alto | Alta | `industrial_cells` + exclusión de NRT |
| SP/NRT solapados → duplicados | Alto | Media | `v_detections_canonical` |
| SP parcial oculta NRT del día | Medio | Media | Documentar; evaluar política por píxel |
| Eficiencia MODIS ≠ VIIRS | Alto | Alta | `fire_day` binario; no sumar conteos |
| FRP no comparable | Medio | Alta | `frp_density`; no sumar FRP crudo |
| Grilla 0,05° diluye fuegos pequeños | Medio | Media | Sensibilidad 0,05 vs 0,1 |
| Semanas faltantes | Medio | Alta | Generar 53 semanas con `NULL` |
| `n_ref_years` bajo → anomalía falsa | Alto | Media | `n_ref_years >= 7`; no mostrar anomalía |
| Huecos de cobertura interpretados como cero | Alto | Alta | `NULL` + banderas de cobertura |
| Nubosidad no disponible | Medio | Alta | Documentar; no imputar |
| Zona horaria | Bajo | Baja | UTC en toda la cadena |
| Saturación de `brightness` | Bajo | Alta | No usar como intensidad |
| Vista canónica lenta | Medio | Media | Índice `(sensor, source, acq_date)` o materializar |
| Máscara industrial borra fuegos cercanos | Medio | Media | Umbral conservador; 0,01°; revisar casos |
| Sobreafirmar “armonización completa” | Alto | Media | Documentar alcance y limitaciones |

---

## 11. Decisiones pendientes

1. **Grilla:** 0,05° (actual) o 0,1° para mayor estabilidad.
2. **Confianza:** usar `nominal + high` o solo `high`. **Decidido:** `nominal + high`; `high` only cambia el ratio SNPP/MODIS de 2,26 a 0,85 y no es comparable.
3. **Máscara industrial:** umbral de 10 detecciones/3 años o más conservador.
4. **`n_ref_years` mínimo:** 7 años (alta confianza ≥ 10).
5. **SP/NRT:** política por fecha (actual) o por píxel (futuro).
6. **NRT en calendario:** incluirlo con la vista canónica o solo SP.
7. **Eventos:** clustering en el MVP o después.

---

## 12. Entregables

- `v_detections_canonical`.
- `industrial_cells`.
- `grid_daily` y `calendar_weekly` reconstruidos.
- Calendario con 53 semanas.
- Informe de validación con eventos conocidos.
- Documento de limitaciones.
- Actualización del README del backend con la política SP/NRT.

---

## 13. Conclusión

La armonización suficiente para el calendario es **factible** con los datos actuales y un esfuerzo de **2–4 días** para H1–H2.

No es una armonización científica completa:

- no calibra eficiencia de detección,
- no calibra FRP,
- no incorpora nubosidad,
- no usa área quemada.

Pero sí permite:

- comparar MODIS y VIIRS a nivel de `fire_day`,
- construir un calendario reproducible,
- excluir industriales,
- marcar incertidumbre,
- validar con eventos conocidos.

La frase correcta para la demo es:

> “Armonizamos estructural y operativamente: esquema único, confianza homologada, grilla común, `fire_day` sensor-agnóstico, política SP/NRT, máscara industrial y validación con eventos conocidos. La calibración física queda como trabajo futuro.”
