# Decisiones de datos y producto - Chronos Fire

**Fecha:** 15/11/2026  
**Alcance:** definiciones que sostienen la API y la UI.  
**Fuente de verdad:** `backend/CONTRATO_API.md` y `backend/openapi/chronosfire-api.yaml`.

Este documento fija qué significan las métricas y qué no se puede afirmar con
ellas. Si cambia una definición, actualizar también el contrato y la UI.

---

## 1. Unidades y metricas

| Termino | Definicion | No es |
|---|---|---|
| **Hotspot / deteccion** | Registro FIRMS de un pixel con anomalia termica. | Un incendio unico. |
| **Hotspot armonizado** | Deteccion de vegetacion efectiva, confianza `nominal` o `high`, fuera de la mascara industrial. | Deteccion cruda o industrial. |
| **Celda con actividad (`fire_day`)** | Celda de 0,05° con al menos un hotspot armonizado en un dia UTC. | Numero de hotspots. |
| **`fireCells`** | Celdas distintas con actividad en el periodo. | Suma de detecciones. |
| **`hotspots`** | Conteo de hotspots armonizados. | Cantidad de incendios. |
| **Incendio / evento** | Agrupacion de hotspots cercanos en el mismo dia o dias consecutivos. | **No implementado.** |
| **`ALL`** | Union de sensores. | Un promedio neutral. |

**Regla:** el calendario titular usa `fireCells` o `hotspots`, nunca una suma de
detecciones entre sensores presentada como promedio.

---

## 2. Grilla y agregacion

- La grilla es de **0,05° (~5 km)**.
- La agregacion diaria es en **UTC**.
- Una celda con actividad en un dia cuenta una vez para `fire_day`.
- `fireCells` cuenta celdas distintas en el periodo.
- `fireDays` cuenta dias-celda con actividad; no es cantidad de incendios.
- El calendario de la UI es **mensual**. La API tambien expone `weekly`,
  `daily`, `heatmap` y `trend`.

---

## 3. "Todos los sensores" no es neutral

La serie `ALL` combina MODIS y tres VIIRS. La validacion muestra que esta
dominada por VIIRS:

- ratio `ALL/MODIS` aproximado: **3,3**;
- ratio `ALL/SNPP` aproximado: **1,45**;
- Spearman nacional MODIS vs VIIRS: **0,969**;
- el ranking relativo es robusto, pero los **niveles absolutos** no son
  comparables entre eras.

Consecuencia: `ALL` sirve para patrones relativos y para la vista por defecto,
pero no debe presentarse como una serie calibrada ni como un promedio.

---

## 4. Baseline y eras

La anomalia mensual usa una mediana **leave-one-out** por mes y AOI:

| Sensor | Anos de referencia |
|---|---|
| MODIS | 2003-2011 |
| VIIRS SNPP | 2013-2025 |
| VIIRS NOAA-20 | 2019-2025 |
| VIIRS NOAA-21 | 2024-2025 |
| `ALL` | 2003-2025, excluyendo 2000-2002, 2012 y 2026 |

Reglas:

- 2000-2002: cobertura MODIS parcial; no se usan como baseline.
- 2012: transicion MODIS/VIIRS; no se usa como baseline y no tiene anomalia.
- 2026: NRT provisional; no se usa como baseline.
- La anomalia de un ano se calcula contra los otros anos del rango solicitado;
  por eso el baseline depende del rango.
- El baseline de un sensor especifico usa solo ese sensor.

---

## 5. Anomalia y confianza

```text
anomaly = observed / baseline
```

Se calcula solo si:

- el mes es observado y completo;
- hay al menos **7 anos** de referencia;
- `observed >= 3`;
- `baseline >= 3`.

Confianza:

- `high`: 10+ anos de referencia y `baseline >= 10`.
- `low`: resto de los casos validos.
- `insufficient`: no se puede calcular. `anomaly = null`.

La anomalia no es una prediccion ni un nivel de riesgo.

---

## 6. Cobertura: "sin datos" no es "sin fuego"

`coverage_daily` se poblo con un proxy regional (fase 0 del plan de riesgo):
un dia cuenta como cubierto si el sensor registro al menos una deteccion.

- una semana sin filas puede ser "sin fuego" o "sin overpass / nubes";
- la UI muestra `—` para "sin datos" y `0` solo cuando hay una observacion;
- no se debe interpretar la ausencia de filas como ausencia de fuego;
- `calendar_weekly.n_data_years` sigue en `NULL`: el calendario no se
  reconstruyo contra la cobertura.

El proxy no prueba ausencia de nubes. Reemplazarlo por cobertura real de FIRMS
(`coverage_type = 'observed'`) no cambia el esquema.

---

## 7. Politica SP/NRT y mascara industrial

- Se usa la vista canonica: **SP si existe; NRT mientras no exista**.
- No hay solapamiento SP/NRT por sensor/fecha en la base actual.
- La mascara industrial cubre 885 celdas y 240.141 detecciones `type=2` en SP.
- El NRT industrial no cuenta como `fire_day`.
- Las alertas NRT excluyen focos industriales y baja confianza.

---

## 8. Exportaciones

- Dos CSV: calendario semanal y detecciones/hotspots.
- Tope: **50.000 filas**.
- Rango de exportacion de detecciones: hasta **10.000 dias** (el paginado y
  GeoJSON quedan en 3.660).
- Headers: `X-Row-Limit`, `X-Returned-Rows`, `X-Truncated`.
- La UI hace un `HEAD` antes de descargar y avisa si la descarga se trunco.
- Los CSV usan UTF-8 con BOM.

---

## 9. Limites del producto

No se afirma:

- cantidad de incendios unicos (requiere clustering de eventos);
- superficie quemada;
- calibracion fisica de eficiencia de deteccion o de FRP;
- comparabilidad absoluta MODIS/VIIRS entre eras;
- cobertura real por nubosidad u overpass;
- riesgo, prediccion o alerta oficial.

Si se necesita "numero de incendios", el siguiente paso es implementar
`fire_events` con clustering <5 km y mismo dia/dias consecutivos. Hasta
entonces, la unidad correcta es hotspot o celda con actividad.

---

## 10. Estado de la UI

- Calendario mensual de celdas 0,05°.
- Mapa Leaflet con hotspots del mes y desglose por sensor en el popup.
- Comparacion contra la mediana multianual con confianza.
- Detalle diario.
- Alertas NRT y validacion de eventos.
- Cada alerta muestra la probabilidad historica (`%`), sus anos de referencia
  (`n`) y el intervalo en el tooltip; con `insufficient` muestra `—`.
- El popup del mapa muestra la probabilidad historica del rango consultado.
- El panel de metodologia declara formula, cobertura proxy y limites.
- El panel de metodologia muestra Brier, BSS y AUC del holdout 2025 y declara
  la etiqueta `risk-v1-low`.
- Descarga CSV de calendario y hotspots con aviso de truncamiento.
- La UI usa `ALL`; la API permite filtrar por sensor. El selector de sensor es
  trabajo pendiente (F-01).
- La rampa de anomalia actual usa verde/rojo; la revision de accesibilidad es
  trabajo pendiente (F-05).

---

## 11. Probabilidad por celda-semana

La capa se materializa con `backend/db/rebuild_risk.sql` (fases 0 y 1) y se
expone en `/api/alerts` y `/api/calendar/heatmap` (fase 2).

| Concepto | Definicion |
|---|---|
| `k` | Anos de referencia en que la celda tuvo al menos un `fire_day` en la semana ISO. |
| `n` | Anos de referencia con cobertura para esa semana (`coverage_daily`). |
| `p` | Media posterior Beta-Binomial `(k + alpha) / (n + alpha + beta)`. |
| `alpha`, `beta` | Prior empirico por semana ISO: `mu_w * 8` y `(1 - mu_w) * 8`. |
| Intervalo | Wilson al 90 % sobre `k/n` (z = 1,6449). |
| `reliability` | `high` con `n >= 10`; `low` con `5 <= n < 10`; `insufficient` con `n < 5`. |

Decisiones:

- `p` es una **frecuencia historica**, no una prediccion ni riesgo oficial.
- `n` sale de una cobertura **proxy** regional: cota inferior. Una celda
  nublada puede tener `fire_days = 0` sin estar libre de fuego.
- Eras separadas: MODIS 2003-2011 y SNPP 2013-2025. No se mezclan.
- La semana 53 tiene solo 2 anos de referencia en ambas eras: queda
  `insufficient` y la UI muestra `—`.
- `activityPercentile` solo se calcula con `days = 7`; la unidad historica es
  semanal y no se escala.
- Con `insufficient` no se muestra `p`, aunque exista, porque no hay respaldo.

Niveles de alerta (regla del MVP, umbrales provisionales hasta la fase 4):

```text
warning : p >= 0,50
       o percentil >= 0,90 con (p >= 0,10 o >= 10 detecciones)
watch   : p >= 0,25
       o percentil >= 0,75 con (p >= 0,10 o >= 3 detecciones)
info    : resto
sin percentil (days != 7): >= 10 detecciones warning, >= 3 watch
```

El percentil solo escala cuando la celda-semana tiene base historica o el
evento tiene volumen, para no sepultar un evento intenso y sin precedente.

Calibracion (fase 4, holdout 2025, entrenamiento sin fuga):

| Entrenamiento | Brier | BSS | AUC |
|---|---|---|---|
| 2013-2024 | 0,0115 | **0,0286** | 0,740 |
| 2019-2024 | 0,0115 | **0,0236** | 0,713 |

`BSS < 0,05`: la tabla se publica como `risk-v1-low` y la UI lo declara. El
desvio del decil superior (0,025) esta dentro del limite de 0,10. No se promete
calibracion.

---

## 12. Registro de decisiones

| Fecha | Decision | Motivo |
|---|---|---|
| 2026-11-14 | Calendario mensual con `fireCells` | Evitar sumar sensores y detecciones crudas |
| 2026-11-14 | Baseline por AOI, mediana leave-one-out | Robusto ante valores extremos y cambios de rango |
| 2026-11-14 | Excluir 2012 y 2026 del baseline | Transicion de sensor y NRT provisional |
| 2026-11-15 | Agregar `hotspots` y `hotspotsBySensor` | Métrica literal del challenge sin sumar sensores |
| 2026-11-15 | Anomalia mensual en backend | Una sola metodologia y confianza declarada |
| 2026-11-15 | Headers de truncamiento CSV | Evitar descargas incompletas silenciosas |
| 2026-11-15 | Capa de riesgo materializada (`risk_cell_week`, `risk_prior_week`) | Calculo auditables y backend de solo lectura |
| 2026-11-15 | `p` con denominador de cobertura, no de deteccion | Evita el sesgo "dado que hubo deteccion" |
| 2026-11-15 | Nivel hibrido (`p` + percentil + volumen) | No sepultar eventos intensos sin precedente |
| 2026-11-15 | Backtest 2025 y publicacion `risk-v1-low` | BSS 0,029 < 0,05: declarar calibracion baja |
| 2026-11-15 | Export de detecciones hasta 10.000 dias | El rango por defecto 2012-2026 debe funcionar |
