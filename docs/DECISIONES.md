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

`coverage_daily` esta vacia y `n_data_years` es `NULL`. Por lo tanto:

- una semana sin filas puede ser "sin fuego" o "sin overpass / nubes";
- la UI muestra `—` para "sin datos" y `0` solo cuando hay una observacion;
- no se debe interpretar la ausencia de filas como ausencia de fuego.

Cerrar esta brecha requiere poblar `coverage_daily` con disponibilidad real de
FIRMS. Es trabajo pendiente y esta documentado como limite.

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
- Descarga CSV de calendario y hotspots con aviso de truncamiento.
- La UI usa `ALL`; la API permite filtrar por sensor. El selector de sensor es
  trabajo pendiente (F-01).
- La rampa de anomalia actual usa verde/rojo; la revision de accesibilidad es
  trabajo pendiente (F-05).

---

## 11. Registro de decisiones

| Fecha | Decision | Motivo |
|---|---|---|
| 2026-11-14 | Calendario mensual con `fireCells` | Evitar sumar sensores y detecciones crudas |
| 2026-11-14 | Baseline por AOI, mediana leave-one-out | Robusto ante valores extremos y cambios de rango |
| 2026-11-14 | Excluir 2012 y 2026 del baseline | Transicion de sensor y NRT provisional |
| 2026-11-15 | Agregar `hotspots` y `hotspotsBySensor` | Métrica literal del challenge sin sumar sensores |
| 2026-11-15 | Anomalia mensual en backend | Una sola metodologia y confianza declarada |
| 2026-11-15 | Headers de truncamiento CSV | Evitar descargas incompletas silenciosas |
| 2026-11-15 | Export de detecciones hasta 10.000 dias | El rango por defecto 2012-2026 debe funcionar |
