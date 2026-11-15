# Informe del módulo estadístico de riesgo — Chronos Fire

**Fecha:** 15/11/2026  
**Módulo:** capa de riesgo histórico por celda-semana (`risk-v1-low`)  
**Alcance:** scripts SQL, backend Java/Spring Boot, contrato API y UI Vue.  
**Método:** revisión de código y documentación; consultas de solo lectura a PostgreSQL 17 (`chronos_fire`), al endpoint `/api/alerts` y a los scripts de validación.  
**Estado:** implementado, materializado y verificado en datos. Calibración **baja** declarada (`BSS = 0,0286 < 0,05`): no se presenta como predicción ni como riesgo oficial.

---

## 1. Resumen ejecutivo

El módulo agrega al producto una **frecuencia histórica estimada de actividad de fuego para cada celda de 0,05° y semana ISO**, con:

- un valor puntual suavizado (`p_fire`);
- un intervalo de confianza del 90 % (Wilson);
- la cantidad de años de referencia (`n`);
- un nivel de confiabilidad (`high`, `low`, `insufficient`);
- un percentil de actividad reciente para las alertas;
- una calibración temporal publicada (`Brier`, `BSS`, `AUC`) y una etiqueta de versión (`risk-v1-low`).

La idea central: responder *“¿con qué frecuencia esta celda tuvo actividad en esta misma semana del año?”*, en lugar de afirmar *“esta celda va a tener fuego”*. El cálculo pesado se hace **offline en SQL**; el backend solo lee y aplica aritmética cerrada en Java.

| Aspecto | Estado |
|---|---|
| Tabla de riesgo por celda-semana | ✅ 7.438.232 filas (70.172 celdas × 53 semanas × 2 eras) |
| Prior empírico por era y semana | ✅ 106 filas (53 × 2) |
| Exposición en alertas | ✅ `fireProbability`, IC, `n`, `reliability`, `activityPercentile` |
| Exposición en mapa | ✅ Probabilidad agregada del rango por celda |
| Publicación de calibración | ✅ `risk_backtest` + bloque `risk` en `/api/meta` |
| Puerta de calidad | ⚠️ BSS 0,0286 < 0,05 → se publica como `risk-v1-low` |
| Predicción / alerta oficial | ❌ Fuera de alcance por diseño |

**Lectura correcta:** es una **capa descriptiva de recurrencia histórica**, útil para priorizar el triage y para comparar una celda con su propia historia. No incorpora meteorología, combustible, propagación ni calibración física de sensores.

---

## 2. Qué agrega al producto

Antes del módulo, la comparación histórica disponible era la **anomalía de AOI**: `observado / mediana multianual` a escala de área y mes/semana. Eso responde “cómo está el área contra su historia”, pero no “qué tan recurrente es esta celda en esta semana”.

El módulo agrega tres piezas:

1. **Probabilidad por celda-semana** (`risk_cell_week`): frecuencia histórica con suavizado bayesiano y intervalo.
2. **Percentil de actividad** en las alertas: cuán inusual es el volumen observado en los últimos 7 días frente a los años de referencia de esa celda-semana.
3. **Calibración publicada**: Brier, BSS y AUC de un holdout temporal, con una puerta de calidad que decide la etiqueta del modelo (`risk-v1-low`).

La UI lo usa en:

- **Alertas NRT:** valor `%`, años de referencia, IC 90 %, barra de intervalo y percentil.
- **Mapa:** tooltip/popup con la probabilidad histórica del rango consultado.
- **Metodología:** fórmula, cobertura proxy, límites y bloque de calibración.

---

## 3. Arquitectura y flujo

```text
ETL (detecciones FIRMS)
        │
        ▼
detections / grid_daily / grid_weekly / grid_weekly_sensor   (tablas canónicas, no se tocan)
        │
        ├── populate_coverage.sql ──► coverage_daily   (proxy regional: día con ≥1 detección)
        │
        ▼
rebuild_risk.sql
        ├── risk_prior_week   (prior Beta por era y semana ISO)
        └── risk_cell_week    (k, n, p_fire, IC Wilson, det_p50/p90, reliability)
        │
        ▼
Backend de solo lectura
        ├── RiskService / RiskMath  ──► /api/alerts
        ├── CalendarRepository      ──► /api/calendar/heatmap
        └── MetaService             ──► /api/meta (bloque risk)
        │
        ▼
Frontend Vue: alertas, mapa y panel de metodología

rebuild_risk_backtest.sql ──► risk_backtest ──► /api/meta (calibración)
```

Restricción de arquitectura vigente: **el backend es de solo lectura**. Todo el cálculo pesado se materializa con scripts SQL aplicados a mano después de cada carga del ETL.

---

## 4. Modelo estadístico

### 4.1 Unidad de análisis

- **Celda:** 0,05° (~5 km), identificada por `grid_id` con formato `0.05:lat:lon`.
- **Semana:** semana ISO (`week_of_year`, 1–53).
- **Era:** `MODIS` o `SNPP`. No se mezclan.

| Era | Años de referencia | Sensores |
|---|---|---|
| `MODIS` | 2003–2011 (9 años) | MODIS Terra/Aqua |
| `SNPP` | 2013–2025 (13 años) | VIIRS Suomi-NPP |

No se construyen tablas de riesgo para NOAA-20 ni NOAA-21 en el MVP: su historia es demasiado corta (2019–2025 y 2024–2025). El mapa con sensor NOAA no recibe campos de riesgo.

### 4.2 Variables

| Símbolo | Significado |
|---|---|
| `k` | Años de referencia en que la celda tuvo al menos un `fire_day` en esa semana ISO. |
| `n` | Años de referencia con cobertura para esa semana ISO. |
| `p` (`p_fire`) | Media posterior Beta-Binomial: `(k + α) / (n + α + β)`. |
| `α`, `β` | Prior empírico por semana: `μ_w · κ` y `(1 − μ_w) · κ`, con `κ = 8`. |
| `μ_w` | Tasa AOI de la semana: celdas-año con fuego / celdas-año con cobertura. |
| `p_lo`, `p_hi` | Intervalo de Wilson al 90 % sobre la proporción cruda `k/n` (`z = 1,6449`). |
| `reliability` | `high` con `n ≥ 10`; `low` con `5 ≤ n < 10`; `insufficient` con `n < 5`. |
| `det_p50`, `det_p90` | Percentiles históricos de `n_detections` semanal de la celda (solo años con actividad). |

### 4.3 Fórmulas

```text
p        = (k + α) / (n + α + β)                [media posterior, suavizada]

α        = μ_w · κ
β        = (1 − μ_w) · κ
κ        = 8

Wilson 90 % sobre p̂ = k / n:
  centro = (p̂ + z²/(2n)) / (1 + z²/n)
  mitad  = z · √(p̂(1−p̂)/n + z²/(4n²)) / (1 + z²/n)
  [p_lo, p_hi] = [centro − mitad, centro + mitad]
```

Decisiones metodológicas:

- **Denominador de cobertura, no de detección.** `n` sale de `coverage_daily`, no de “años en que hubo detección”. Evita el sesgo *“dado que hubo detección”*, que saturaría la probabilidad cerca de 1.
- **Suavizado débil.** `κ = 8` evita que una celda con `k = 0` quede en `p = 0` exacto y que una con `k = n` quede en `1`. El dato local domina a partir de 3–4 años con fuego.
- **Intervalo sobre `k/n`, no sobre `p`.** Se guardan ambos y se documenta la diferencia. Con `n = 13` el intervalo es ancho a propósito.
- **Semanas 53.** Solo hay 2 años con semana ISO 53 en cada era; por eso su `reliability` es `insufficient` y la UI debe mostrar `—`.
- **Eras separadas.** No se mezcla MODIS con VIIRS en el denominador ni en el prior.

### 4.4 Confiabilidad y regla de producto

| `reliability` | Condición | UI |
|---|---|---|
| `high` | `n ≥ 10` | Muestra `p`, IC y `n` |
| `low` | `5 ≤ n < 10` | Muestra `p`, IC y `n` con la salvedad |
| `insufficient` | `n < 5` o celda sin fila | Muestra `—`; `p` viaja como `null` |

**Regla:** nunca mostrar `p` sin `n` y sin intervalo. Si `insufficient`, no se publica `p`, aunque exista un valor materializado.

---

## 5. Datos de entrada: cobertura proxy

`coverage_daily` es la pieza que hace auditable el denominador. Se llena con `migraciones/populate_coverage.sql`:

- una fila por `(region, sensor, source, acq_date)` con al menos una detección;
- `has_data = true`, `coverage_type = 'proxy'`;
- volumen actual: **19.260 filas** (8 combinaciones sensor/fuente × ~2.400 días).

Propiedades y límites:

- Es una **cota inferior**: un día sin detecciones queda como “sin cobertura”, no como “sin fuego”.
- La cobertura es **regional**, no por celda. Una celda nublada puede tener `fire_days = 0` sin estar libre de fuego.
- No descuenta nubosidad ni overpass.
- Reemplazarla por cobertura real (`coverage_type = 'observed'`) no requiere cambiar el esquema.

Días cubiertos por año dentro de las eras usadas:

- MODIS 2003–2011: 99,7–100 %.
- SNPP 2013–2025: 94,5–100 % (2022: 95,9 %; 2024: 94,5 %).
- Hueco conocido: SNPP 2022 semana 31 no tiene días cargados; esa semana queda con `n = 12` en vez de 13.

---

## 6. Construcción offline

### 6.1 Orden de ejecución

| # | Script | Escribe | Propósito |
|---|---|---|---|
| 1 | `backend/db/migraciones/add_risk_layer.sql` | DDL | Crea `risk_prior_week`, `risk_cell_week`, `risk_backtest` e índices |
| 2 | `backend/db/migraciones/populate_coverage.sql` | `coverage_daily` | Denominador auditable por sensor/día |
| 3 | `backend/db/rebuild_risk.sql` | `risk_*` | Calcula prior y tabla de riesgo |
| 4 | `backend/db/validacion/06_riesgo.sql` | No | Validación R0–R14 |
| 5 | `backend/db/rebuild_risk_backtest.sql` | `risk_backtest` | Holdout 2025: Brier, BSS, AUC, deciles |

`add_risk_layer.sql` y `rebuild_risk.sql` son idempotentes. El orden importa: si `coverage_daily` está vacía, `rebuild_risk.sql` aborta con un mensaje explícito.

### 6.2 Esquema

**`risk_prior_week`** — prior Beta por era y semana ISO:

| Columna | Tipo | Descripción |
|---|---|---|
| `era` | TEXT | `MODIS` o `SNPP` |
| `week_of_year` | SMALLINT | 1–53 |
| `mu` | REAL | Tasa AOI de la semana |
| `kappa` | REAL | Fuerza del prior (8) |
| `alpha` | REAL | `mu · kappa` |
| `beta` | REAL | `(1 − mu) · kappa` |

**`risk_cell_week`** — resultado materializado:

| Columna | Tipo | Descripción |
|---|---|---|
| `grid_id` | TEXT | Celda 0,05° |
| `cell_lat`, `cell_lon` | DOUBLE | Centro de la celda |
| `week_of_year` | SMALLINT | Semana ISO |
| `era` | TEXT | `MODIS` o `SNPP` |
| `ref_years` | INTEGER | `n` |
| `fire_years` | INTEGER | `k` |
| `fire_days` | INTEGER | Intensidad acumulada en la era |
| `p_fire` | REAL | Media posterior suavizada |
| `p_lo`, `p_hi` | REAL | Wilson 90 % sobre `k/n` |
| `det_p50`, `det_p90` | REAL | Percentiles históricos de detecciones |
| `reliability` | TEXT | `high`, `low`, `insufficient` |
| `model_version` | TEXT | `risk-v1-low` |
| `built_at` | TIMESTAMPTZ | Fecha de construcción |

Clave primaria: `(grid_id, week_of_year, era)`.

**`risk_backtest`** — calibración publicada:

| Columna | Descripción |
|---|---|
| `model_version` | `risk-v1` o `risk-v1-t2019-2024` |
| `era` | `SNPP` |
| `holdout_year` | 2025 |
| `brier`, `brier_baseline` | Error cuadrático medio y baseline |
| `bss` | Brier Skill Score |
| `auc` | Área bajo la curva ROC |
| `n` | Celdas-semana evaluadas |

### 6.3 Volumen y tiempos medidos

| Objeto | Volumen |
|---|---|
| `risk_prior_week` | 106 filas (53 × 2 eras) |
| `risk_cell_week` | **7.438.232 filas** = 70.172 celdas × 53 semanas × 2 eras |
| `risk_backtest` | 2 filas (holdout principal + control de estabilidad) |
| `coverage_daily` | 19.260 filas |

Distribución de confiabilidad:

| Era | `high` | `low` | `insufficient` |
|---|---:|---:|---:|
| MODIS | — | 3.648.944 | 70.172 (semana 53) |
| SNPP | 3.648.944 | — | 70.172 (semana 53) |

Tiempos en la máquina local:

| Paso | Tiempo |
|---|---:|
| `risk_prior_week` | ~25 s |
| `risk_cell_week` (INSERT) | ~8 min 54 s |
| PK + índices (bulk) | ~1 min 12 s |
| Rebuild total | **~10 min 30 s** |
| `06_riesgo.sql` | ~40 s |

El objetivo del plan (< 5 min) **no se cumple en esta máquina**. Queda medido, no supuesto. Para acelerar, el rebuild suelta PK e índices antes del `INSERT` y los reconstruye después, dentro de la misma transacción: ningún lector ve la tabla sin índices.

---

## 7. Exposición en la API

### 7.1 `/api/alerts`

Cada alerta recibe campos **aditivos y nullable**:

| Campo | Tipo | Descripción |
|---|---|---|
| `fireProbability` | number \| null | `p` de la celda para la semana ISO actual |
| `probabilityLow` | number \| null | Límite inferior Wilson 90 % |
| `probabilityHigh` | number \| null | Límite superior Wilson 90 % |
| `probabilityRefYears` | integer \| null | `n` (años con cobertura) |
| `probabilityReliability` | string | `high`, `low`, `insufficient` |
| `activityPercentile` | number \| null | Percentil de actividad reciente; solo con `days = 7` |

Detalles de implementación:

- La semana usada es la **semana ISO de hoy** (`LocalDate.now(UTC)`), y la era es `SNPP` si el año actual es ≥ 2013 (hoy: SNPP).
- `k`/`n`/`p` salen de `risk_cell_week`; el percentil se calcula en Java con el histórico semanal de `grid_weekly_sensor`.
- Si la celda no tiene fila o `reliability = insufficient`, todos los campos de probabilidad van en `null`, pero `probabilityRefYears` puede informarse.
- El percentil cuenta los años sin fila como `0` detecciones; por eso el denominador es `ref_years`, no la cantidad de filas históricas.

Niveles (`level`):

```text
warning : p ≥ 0,50
       o percentil ≥ 0,90 con (p ≥ 0,10 o ≥ 10 detecciones)
watch   : p ≥ 0,25
       o percentil ≥ 0,75 con (p ≥ 0,10 o ≥ 3 detecciones)
info    : resto
```

- Sin percentil (`days != 7`): ≥ 10 detecciones → `warning`; ≥ 3 → `watch`; resto `info`.
- Con `insufficient`: siempre `info` y `p` en `null`.
- El percentil solo escala el nivel si hay base histórica (`p ≥ 0,10`) **o** volumen suficiente. Así un evento intenso y sin precedente no queda sepultado por el histórico.

### 7.2 `/api/calendar/heatmap`

Para el rango consultado, el repositorio:

- identifica las semanas ISO del rango;
- suma `fire_years` (`k`) y `ref_years` (`n`) de esas semanas por celda;
- usa `min(ref_years)` como `probabilityRefYears` (no la suma), para no inflar la confiabilidad;
- recalcula `p = (k + α) / (n + α + β)` con los priors sumados;
- calcula Wilson 90 % sobre el `k/n` agregado;
- calcula `anomaly = p / (α/(α+β))`, es decir, la probabilidad de la celda relativa a la tasa AOI de las mismas semanas.

La era se resuelve con la fecha final del rango:

| Sensor | Era |
|---|---|
| `MODIS` | `MODIS` |
| `VIIRS_SNPP` | `SNPP` |
| `ALL` | `SNPP` si `to.year ≥ 2013`; si no, `MODIS` |
| `VIIRS_NOAA20`, `VIIRS_NOAA21` | sin riesgo (`null`) |

Advertencia de lectura: la probabilidad del mapa es una **probabilidad semanal agregada del rango**, no una probabilidad mensual ni diaria. Para un mes se promedian las semanas incluidas.

### 7.3 `/api/meta`

El bloque `risk` publica:

| Campo | Descripción |
|---|---|
| `modelVersion` | Versión publicada, hoy `risk-v1-low` |
| `calibrationStatus` | `high` o `low` según BSS ≥ 0,05 |
| `holdoutYear` | 2025 |
| `brier` | Brier del holdout principal |
| `bss` | BSS contra la tasa AOI (`mu_w`) |
| `auc` | AUC |
| `cellsWeek` | Celdas-semana evaluadas |
| `stabilityBss` | BSS de la ventana corta 2019–2024 |
| `bssThreshold` | 0,05 |
| `note` | Texto que declara la etiqueta y los límites |

Si `risk_backtest` no existe o está vacío, el bloque es `null` y la UI no muestra la calibración.

### 7.4 Caché

- `RiskService.alertProbabilities` está cacheado por `semana | era | días | detecciones observadas`.
- `AlertService.getAlerts` cachea por `bbox | days | limit`.
- `MetaService.getMeta` cachea el bloque completo.

La tabla de riesgo es estática hasta el próximo rebuild; no hay escrituras desde la API.

---

## 8. Uso en la interfaz

| Componente | Qué muestra |
|---|---|
| `AlertsPanel.vue` | `p` en %, años de referencia, barra con IC 90 %, punto de `p`, percentil, tooltip accesible y ayuda “¿Qué significa la frecuencia histórica?” |
| `FireMap.vue` | Probabilidad histórica en el popup de cada celda, con IC y `n`; `—` si no hay respaldo |
| `MethodologyPanel.vue` | Fórmula, cobertura proxy, límites, niveles y bloque de calibración con Brier/BSS/AUC |
| `useChronos.ts` | Mapea el bloque `risk` de `/api/meta` y lo expone a la UI |

La UI declara explícitamente que **no es predicción, no incorpora meteorología ni combustible y no es alerta oficial**.

---

## 9. Calibración y puerta de calidad

### 9.1 Diseño del backtest

`rebuild_risk_backtest.sql` no usa `risk_cell_week` para predecir. Recalcula `k`, `n` y el prior **solo con la ventana de entrenamiento** y evalúa contra `fire_days > 0` del año holdout. Evita fuga de información del futuro.

- Unidad: celda × semana ISO.
- Era evaluada: `SNPP` (la única con 13 años de historia).
- Holdout: 2025.
- Entrenamiento principal: 2013–2024.
- Control de estabilidad: 2019–2024.

Métricas:

```text
Brier      = mean((p̂ − y)²)
Brier base = mean((μ_w − y)²)
BSS        = 1 − Brier / Brier_base
AUC        = ranking de p̂ contra y
```

### 9.2 Resultados medidos

| Entrenamiento | Holdout | Brier | Brier base | BSS | AUC | Celdas-semana |
|---|---|---|---:|---:|---:|---:|
| 2013–2024 | 2025 | 0,011480 | 0,011818 | **0,0286** | 0,740 | 3.719.116 |
| 2019–2024 | 2025 | 0,011543 | 0,011823 | **0,0236** | 0,713 | 3.719.116 |

Curva de fiabilidad (2013–2024 → 2025):

- decil superior: `p` media 0,0852 vs observado 0,0604 → desvío **0,0249**;
- deciles 1–9: desvío entre −0,0033 y +0,0078.

### 9.3 Puerta de calidad

- Umbral: `BSS ≥ 0,05`.
- Resultado: **0,0286 < 0,05** → se publica como **`risk-v1-low`**.
- El desvío del decil superior (0,0249) cumple el límite de 0,10, pero el poder discriminante global es bajo.
- `risk_cell_week.model_version` y `risk_backtest` quedan etiquetados; `/api/meta` lo declara.
- **No se promete calibración.** El modelo sirve como frecuencia histórica y para ordenar celdas, no como probabilidad calibrada de ocurrencia.

---

## 10. Validación de la capa

`backend/db/validacion/06_riesgo.sql` es de solo lectura y devuelve **0 filas** cuando el chequeo está bien. Cubre:

| Chequeo | Qué valida |
|---|---|
| R0 | Existencia de objetos y volumen |
| R1 | `coverage_daily` sin filas inválidas |
| R2 / R2b | Semanas y `n` alineados con la cobertura; huecos conocidos |
| R3 | Parámetros del prior (`mu`, `kappa`, `alpha + beta = kappa`) |
| R4 | 53 semanas por era |
| R5 | Toda celda-semana tiene prior |
| R6 | Rangos y relaciones (`k ≤ n`, `p ∈ [0,1]`, `p_lo ≤ p_hi`) |
| R7 | `reliability` coherente con `ref_years` |
| R8 | Sin claves duplicadas |
| R9 | Toda celda de `grid_weekly` tiene riesgo |
| R10 | Distribución de confiabilidad (informativo) |
| R11 | Desvío esperado `p_fire` vs Wilson (informativo) |
| R12 | Invariantes de tablas canónicas |
| R13 | Índices requeridos |
| R14 | Resumen de fallas |

Resultados de la corrida documentada:

- R1–R9 y R13: **0 filas**.
- R14: todo en 0.
- R12: `detections` 7.082.474, `grid_daily` 2.103.053, `calendar_weekly` 3.719.116 (sin cambios).
- R11: **1.506 filas (0,02 %)** con `p_fire` fuera del intervalo de Wilson. Es esperado por el suavizado: con `k = 0` el prior empuja `p` por encima de `p_lo = 0`; con `k = n`, por debajo. No es un error de datos.

---

## 11. Desvíos respecto del plan

El plan original era la especificación. Estos puntos se corrigieron porque la SQL de ejemplo daba resultados incorrectos o incompletos:

1. **Volumen de cobertura.** El criterio `count(*) > 200.000` era incompatible con la clave `(region, sensor, source, acq_date)`: hay 8 combinaciones y ~2.400 días cada una, o sea ~19.260 filas. La aceptación correcta es “~365 días por año y sensor”.
2. **Denominador del prior.** Calcular `cell_years` sobre `grid_weekly_sensor` (solo filas con detecciones) reproducía el sesgo “dado que hubo detección” y daba `μ ≈ 0,79–0,98`. El denominador correcto es `n_cells × n_ref` (cobertura), que da `μ ≈ 0,0014–0,0397`.
3. **Ventana de eras.** Sin restringir la cobertura a la ventana de cada era, MODIS tendría `n = 27` y SNPP `n = 15`. Se restringe a 2003–2011 y 2013–2025.
4. **Semana 53.** El plan esperaba `n = 12`; el calendario ISO solo da 2 años con semana 53 en cada ventana. Se deja `insufficient`, que es el comportamiento correcto.
5. **Hueco SNPP 2022-31.** No estaba previsto: esa semana tiene un año de referencia menos (`n = 12`) y así se reporta.
6. **Tiempo de rebuild.** 10,5 min medidos contra < 5 min del plan. Queda documentado como objetivo no cumplido en esta máquina.

---

## 12. Límites y uso responsable

El módulo **no** afirma ni permite afirmar:

- que `p` sea una predicción de fuego;
- que sea un riesgo oficial o una alerta oficial;
- que la celda esté libre de fuego cuando no hay detecciones;
- que la cobertura sea real por celda (es un proxy regional);
- que el modelo esté calibrado (BSS 0,0286, etiqueta `low`);
- cantidad de incendios únicos, superficie quemada o calibración física de FRP;
- comparabilidad absoluta MODIS/VIIRS entre eras.

Reglas de producto:

- Nunca mostrar `p` sin `n`, sin IC y sin `reliability`.
- Con `insufficient` mostrar `—`.
- No sumar sensores como si fueran el mismo instrumento.
- No presentar la probabilidad del mapa como probabilidad mensual o diaria.
- No usar la etiqueta `risk-v1-low` como si implicara calibración alta.

---

## 13. Ejemplo real verificado

Celda del Delta del Paraná, semana ISO 40, era SNPP (consulta de solo lectura):

| Campo | Valor |
|---|---|
| `grid_id` | `0.05:-667:-1203` |
| Centro | −33,35 / −60,15 |
| `ref_years` (`n`) | 13 |
| `fire_years` (`k`) | 5 |
| `fire_days` | 6 |
| `p_fire` | **0,2474** (24,7 %) |
| Wilson 90 % | 0,2016 – 0,6074 |
| `reliability` | `high` |

Lectura: en 5 de los 13 años de referencia (2013–2025) esa celda tuvo actividad en la semana 40. El valor suavizado es 24,7 %, con un intervalo ancho (20,2 %–60,7 %) coherente con `n = 13`. No significa que “vaya a haber fuego”: es la frecuencia histórica de esa celda en esa semana.

Rango global verificado para SNPP: `p_fire` entre 0,0019 y 0,6317; media 0,0137; 436.164 celdas-semana con `fire_years > 0`.

---

## 14. Operación y reproducción

Desde la raíz del repo, con PostgreSQL 17:

```powershell
$env:PGPASSWORD = '<password>'
$psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
$db = 'chronos_fire'

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\migraciones\add_risk_layer.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\migraciones\populate_coverage.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\rebuild_risk.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\06_riesgo.sql

# Holdout principal 2013-2024 -> 2025
& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -v model_version=risk-v1 `
  -f backend\db\rebuild_risk_backtest.sql

# Control de estabilidad 2019-2024 -> 2025
& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -v model_version=risk-v1-t2019-2024 -v train_from=2019 -v train_to=2024 `
  -f backend\db\rebuild_risk_backtest.sql
```

Rollback:

```sql
DROP TABLE IF EXISTS risk_cell_week;
DROP TABLE IF EXISTS risk_prior_week;
DROP TABLE IF EXISTS risk_backtest;
DROP INDEX IF EXISTS grid_weekly_sensor_cell_week_idx;
-- Opcional: TRUNCATE coverage_daily;
```

`coverage_daily` ya existía antes del módulo (estaba vacía); no se elimina.

---

## 15. Estado verificado al 15/11/2026

Consultas de solo lectura a la base local:

| Verificación | Resultado |
|---|---|
| `risk_cell_week` | 7.438.232 filas |
| `risk_prior_week` | 106 filas |
| `risk_backtest` | 2 filas |
| `model_version` | `risk-v1-low` |
| Backtest principal | Brier 0,011480 · BSS 0,0286 · AUC 0,740 · n 3.719.116 |
| Control de estabilidad | Brier 0,011543 · BSS 0,0236 · AUC 0,713 |
| `coverage_daily` | 19.260 filas |
| `/api/alerts` | Devuelve `fireProbability`, IC, `n`, `reliability` y `activityPercentile` |

**Observación operativa:** la instancia del backend que responde en `:8080` se inició a las 15:12, antes del build de la fase 4 (commit de las 15:35). Por eso `/api/alerts` ya expone los campos de probabilidad, pero `/api/meta` todavía no incluye el bloque `risk` ni las definiciones `fire_probability` / `probability_reliability`. **Reiniciar el backend** publica la calibración sin cambios de código ni de datos.

---

## 16. Mapa de archivos

**SQL y documentación**

- `backend/db/migraciones/add_risk_layer.sql` — DDL de las tres tablas e índices.
- `backend/db/migraciones/populate_coverage.sql` — cobertura proxy.
- `backend/db/rebuild_risk.sql` — prior y tabla de riesgo.
- `backend/db/rebuild_risk_backtest.sql` — holdout, métricas y puerta de calidad.
- `backend/db/validacion/06_riesgo.sql` — validación R0–R14.
- `backend/db/README.md` — resultados, tiempos y desvíos.
- `backend/planes/PLAN_RIESGO_ESTADISTICO.md` — especificación original.
- `docs/DECISIONES.md` — definiciones y límites del producto.

**Backend Java**

- `dto/Alert.java`, `dto/HeatmapCell.java`, `dto/MetaResponse.java` — campos nuevos.
- `repository/RiskRepository.java` — lectura de `risk_cell_week` y del histórico semanal.
- `repository/CalendarRepository.java` — agregación de riesgo para el mapa.
- `repository/MetaRepository.java` — versión del modelo y backtests.
- `service/RiskMath.java` — Wilson, posterior, confiabilidad, percentil y niveles.
- `service/RiskService.java` — orquestación y caché.
- `service/AlertService.java` — enriquecimiento de alertas y `level`.
- `service/MetaService.java` — bloque `risk` y puerta de calidad.
- `service/CalendarService.java` — propagación de campos al heatmap.
- `config/CacheConfig.java` — caché de `risk`.
- `tests`: `RiskMathTest.java` (Wilson, prior, percentil, niveles) y `RiskServiceTest.java` (mapeo, `insufficient`, ventana ≠ 7 días).

**Contrato y frontend**

- `backend/openapi/chronosfire-api.yaml` — `/api/alerts`, `/api/calendar/heatmap`, `/api/meta`, esquemas `Alert`, `HeatmapCell`, `RiskMethodology`.
- `frontend/ChronosFire/src/components/AlertsPanel.vue` — tarjetas con `p`, IC, `n` y percentil.
- `frontend/ChronosFire/src/components/FireMap.vue` — probabilidad en el popup.
- `frontend/ChronosFire/src/components/MethodologyPanel.vue` — metodología y calibración.
- `frontend/ChronosFire/src/composables/useChronos.ts` y `src/data/types.ts` — mapeo de tipos.

---

## 17. Pendientes y próximos pasos

1. **Reiniciar el backend** para publicar el bloque `risk` en `/api/meta` y la calibración en la UI.
2. **Evaluar si se modelan eras NOAA-20/21** o se documenta explícitamente que las alertas usan la era SNPP como referencia. Hoy el mapa con esos sensores no recibe riesgo.
3. **Reemplazar la cobertura proxy** por cobertura observada de FIRMS si se consigue; no cambia el esquema, pero sí la solidez de `n`.
4. **Mejorar la calibración**: el BSS 0,0286 está por debajo del umbral. Opciones: más años de holdout, features estacionales o un modelo con regularización; mientras tanto, mantener la etiqueta `low`.
5. **Agregar frescura y monitoreo**: exponer `built_at` y la versión en `/api/meta` para detectar tablas desactualizadas.
6. **Unidad “incendio”**: si se necesita cantidad de incendios, implementar `fire_events` con clustering < 5 km y mismo día/días consecutivos. No es parte de este módulo.

---

**Conclusión:** el módulo cumple el objetivo de convertir la historia en una frecuencia comparable por celda-semana, con incertidumbre explícita, cobertura auditable y una puerta de calidad que impide vender como calibrado lo que no lo está. Su valor es el **triage y la comparación histórica**, no la predicción.
