# Plan de harmonización de datos — Chronos Fire

**Proyecto:** Hackaton NASA Chronos Fire  
**Objetivo:** harmonizar las detecciones MODIS y VIIRS lo suficiente para construir un calendario de actividad de fuego comparable, reproducible y defendible.  
**Base:** PostgreSQL 17, `chronos_fire`  
**Cobertura:** Argentina, 2000–2026  
**Estado:** planificación. No implementado todavía.

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

La harmonización propuesta es **operativa**, no científica completa. Alcanza para el calendario y las alertas, y documenta explícitamente lo que no resuelve.

---

## 2. Qué significa “suficiente para el calendario”

Una harmonización es suficiente si permite afirmar:

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

## 4. Principios de harmonización

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

## 6. Componentes de la harmonización

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

**Caveat:** es una política por sensor/fecha, no por píxel. Si SP cubre parcialmente un día, se oculta el NRT de ese sensor/día.

**Estado:** 🟡 planificado, no implementado.

---

### 6.3 Máscara de focos industriales

**Problema:** el NRT no trae `type`, así que `fire_type` es `NULL` y `is_vegetation` también. Sin máscara, una antorcha industrial puede marcar `fire_day` y generar alertas.

**Solución:**

1. Construir una máscara desde SP, donde sí hay `type`.
2. Agregar por celda de 0,01° (~1 km):
   - `n_detections` con `type IN (2,3)`.
   - años distintos con presencia.
3. Conservar celdas con:
   - `n_detections >= 10`
   - `n_years >= 3`
4. Opcional: dilatación de 1 celda (3×3) para cubrir el footprint del complejo industrial.
5. Aplicar a NRT:
   - Si la detección cae en la máscara, marcar `industrial_mask = true`.
   - Excluirla de `fire_day` y de alertas.
   - No excluir toda la celda de 0,05°, solo la detección NRT.

**Tabla propuesta:**

```sql
CREATE TABLE industrial_cells (
    cell_lat     DOUBLE PRECISION NOT NULL,
    cell_lon     DOUBLE PRECISION NOT NULL,
    n_detections INTEGER NOT NULL,
    n_years      INTEGER NOT NULL,
    last_seen    DATE,
    PRIMARY KEY (cell_lat, cell_lon)
);
```

**Estado:** 🟡 pendiente.

---

### 6.4 Harmonización espacial

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

### 6.5 Harmonización temporal

- Día UTC (`acq_date`).
- Semana ISO (`extract(week from day)`).
- 53 semanas por celda.
- Semanas sin datos → `NULL`, no `0`.
- `n_years` indica sobre cuántos años se calculó la probabilidad.
- `reliable = n_years >= 5`.

**Completar calendario:**

```sql
WITH grids AS (
    SELECT DISTINCT grid_id, cell_lat, cell_lon FROM grid_daily
),
weeks AS (
    SELECT generate_series(1, 53) AS week_of_year
),
observed AS (
    SELECT grid_id,
           extract(week FROM day)::smallint AS week_of_year,
           count(DISTINCT extract(year FROM day))::int AS n_years,
           avg(fire_day::int)::real AS fire_day_prob,
           percentile_cont(0.5) WITHIN GROUP (ORDER BY frp_sum_mw)::real AS frp_p50,
           percentile_cont(0.9) WITHIN GROUP (ORDER BY frp_sum_mw)::real AS frp_p90
    FROM grid_daily
    GROUP BY 1, 2
)
SELECT g.grid_id, g.cell_lat, g.cell_lon,
       w.week_of_year,
       o.n_years,
       o.fire_day_prob,
       o.frp_p50,
       o.frp_p90
FROM grids g
CROSS JOIN weeks w
LEFT JOIN observed o
  ON o.grid_id = g.grid_id
 AND o.week_of_year = w.week_of_year;
```

**Estado:** 🟡 parcial; hoy solo se guardan semanas observadas.

---

### 6.6 Harmonización de intensidad

- **No sumar FRP entre sensores.**
- Usar `frp_density_wm2 = frp_mw / pixel_area_km2` como indicador secundario.
- Reportar FRP por sensor y por celda, no un total único.
- No usar `brightness` como intensidad: VIIRS satura en 367 K y MODIS en ~500 K.

**Estado:** ✅ campos calculados; interpretación documentada.

---

### 6.7 Construcción del calendario

**`grid_daily`:**

```sql
fire_day = bool_or(veg_ok AND conf_ok)
```

donde:

```sql
veg_ok  = (is_vegetation IS TRUE OR is_vegetation IS NULL)
conf_ok = confidence_class IN ('nominal','high')
```

Con la máscara industrial, `veg_ok` debe excluir las detecciones NRT marcadas como industriales.

**`calendar_weekly`:**

```sql
fire_day_prob = avg(fire_day::int)
n_years       = count(DISTINCT year)
frp_p50/p90   = percentiles de frp_sum_mw
```

**Anomalía:**

```text
expected = fire_day_prob * n_cells_del_AOI
anomaly  = observed_fire_cells / expected
reliable = n_years >= 5 AND expected >= 1
```

Si `expected` es muy bajo, no mostrar anomalía.

**Estado:** ✅ base implementada; 🟡 faltan semanas completas y máscara industrial.

---

### 6.8 Validación

#### Validación interna

- Conteos por sensor/fuente.
- Cobertura por año.
- Duplicados exactos.
- Solapamiento SP/NRT.
- Porcentaje de `fire_day` por semana.
- `n_years` por celda.

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
  - diferencias en `fire_day_prob`.
- Si las tres versiones coinciden en los períodos críticos, la harmonización es robusta para el calendario.

#### Coincidencia espacial

- Emparejar MODIS y VIIRS en el período de solapamiento.
- Ventana: mismo día y < 1 km.
- Reportar:
  - % de coincidencia,
  - ratio de detecciones,
  - diferencia de FRP.

**Estado:** 🟡 pendiente.

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

### H1 — Harmonización operativa (1–2 días)

1. Crear `v_detections_canonical`.
2. Construir `industrial_cells` desde SP.
3. Aplicar la máscara a NRT.
4. Reconstruir `grid_daily` y `calendar_weekly` desde la vista canónica.
5. Completar las 53 semanas.
6. Agregar `n_years` y `reliable`.

**Entregable:** calendario consistente, sin industriales en NRT, sin semanas faltantes.

### H2 — Validación (1–2 días)

1. Validar eventos conocidos.
2. Comparar MODIS-only vs VIIRS-only vs ambos.
3. Calcular coincidencia espacial.
4. Documentar resultados y limitaciones.

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

- [ ] Las 8 fuentes están presentes o su ausencia está documentada.
- [ ] No hay duplicados exactos.
- [ ] No hay solapamiento SP/NRT sin resolver.
- [ ] `v_detections_canonical` devuelve una sola fuente por sensor/fecha.
- [ ] `industrial_cells` cubre los focos industriales conocidos.
- [ ] NRT industrial no marca `fire_day`.
- [ ] `calendar_weekly` tiene 53 semanas por celda (con `NULL` donde falta).
- [ ] `fire_day_prob` y `n_years` son consistentes.
- [ ] Los eventos 2020, 2022, 2013 y 2017 aparecen en el calendario.
- [ ] MODIS-only y VIIRS-only coinciden en los períodos críticos.
- [ ] Las limitaciones están documentadas.

---

## 9. Métricas de calidad

| Métrica | Objetivo |
|---|---|
| Cobertura de fuentes | 8/8 o justificar faltantes |
| Duplicados exactos | 0 |
| Solapamiento SP/NRT sin resolver | 0 |
| Celdas con 53 semanas | 100% |
| Celdas con `n_years >= 5` | Reportar % |
| Coincidencia MODIS/VIIRS | Reportar % |
| Eventos conocidos detectados | 4/4 |
| Diferencia de ranking MODIS vs VIIRS | Spearman > 0,8 (deseable) |

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
| `n_years` bajo → anomalía falsa | Alto | Media | `reliable = n_years >= 5` |
| Huecos de cobertura interpretados como cero | Alto | Alta | `NULL` + banderas de cobertura |
| Nubosidad no disponible | Medio | Alta | Documentar; no imputar |
| Zona horaria | Bajo | Baja | UTC en toda la cadena |
| Saturación de `brightness` | Bajo | Alta | No usar como intensidad |
| Vista canónica lenta | Medio | Media | Índice `(sensor, source, acq_date)` o materializar |
| Máscara industrial borra fuegos cercanos | Medio | Media | Umbral conservador; 0,01°; revisar casos |
| Sobreafirmar “harmonización completa” | Alto | Media | Documentar alcance y limitaciones |

---

## 11. Decisiones pendientes

1. **Grilla:** 0,05° (actual) o 0,1° para mayor estabilidad.
2. **Confianza:** usar `nominal + high` o solo `high`.
3. **Máscara industrial:** umbral de 10 detecciones/3 años o más conservador.
4. **`n_years` mínimo:** 5 años.
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

La harmonización suficiente para el calendario es **factible** con los datos actuales y un esfuerzo de **2–4 días** para H1–H2.

No es una harmonización científica completa:

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

> “Harmonizamos estructural y operativamente: esquema único, confianza homologada, grilla común, `fire_day` sensor-agnóstico, política SP/NRT, máscara industrial y validación con eventos conocidos. La calibración física queda como trabajo futuro.”
