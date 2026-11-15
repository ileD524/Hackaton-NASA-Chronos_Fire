# Validacion §6.8 - Informe de avance

**Fecha:** 2026-11-14  
**Base:** `chronos_fire` (PostgreSQL 17, 127.0.0.1:5432)  
**Scripts:** `backend/db/validacion/`  
**Salidas:** `reports/validacion_6_8_20261114/`  
**Estado:** V0 ✅ · V1 ✅ · V2 🟡 · V3 ✅ · V3b ✅ · V4 ✅ · V5 🟡

---

## 1. Resumen ejecutivo

La validacion interna y la sensibilidad MODIS/VIIRS ya estan implementadas y
ejecutadas. Los resultados permiten afirmar que:

1. El calendario es internamente consistente: `grid_daily` y `grid_weekly`
   coinciden exactamente en 1.828.709 dias-celda con fuego.
2. A escala nacional, el ranking semanal de MODIS y VIIRS es muy robusto
   (Spearman 0,969; top-5 0,908).
3. En los anios de evento, las semanas criticas coinciden en 5 de 6 ventanas
   (top-5 >= 0,8); las excepciones son La Pampa 2016 y el top-10 de Cordoba 2013.
4. La anomalia del Delta 2020 se reproduce: **66 / 10 / 12 / 6,60** con
   VIIRS SNPP-only. Con todos los sensores da **78 / 10 / 7,80**: el titular
   depende del conjunto de sensores y debe declararse.
5. La coincidencia espacial es estable y coherente con las diferencias de
   sensibilidad: con SNPP, MODIS coincide en 52-69% de sus detecciones y
   VIIRS en 37-49%; con NOAA-20/21 los rangos son similares. VIIRS ve mas
   focos y de menor FRP.

Lo que **no** se puede afirmar todavia:

- que una semana sin filas sea "sin fuego" (`coverage_daily` esta vacia);
- que el ranking local de todos los anios sea estable en todas las AOIs;
- que "todos los sensores" sea una serie neutral: esta dominada por VIIRS.

---

## 2. Decisiones aplicadas

| Decision | Aplicacion |
|---|---|
| Sensor del observado y del baseline | El mismo en cada AOI (era-especifica) |
| Delta 2020 | VIIRS_SNPP observado y baseline 2013-2025 |
| Eventos 2013 | VIIRS_SNPP observado y baseline 2013-2025 (n_ref=12) |
| "VIIRS" | SNPP-only para baseline; todos los VIIRS como detalle |
| Criterio de coincidencia | Top-5 de semanas criticas como principal; Spearman como secundario |
| Ventana espacial | Mismo dia y < 1 km; sensibilidad <= 3 h |
| Confianza | nominal+high como titular; high-only queda pendiente |

La tabla 6.7.1 usaba `n_ref=9` para 2013, compatible con un baseline MODIS
2003-2011. Eso mezclaba sensores: el observado 2013 era SNPP y el baseline
MODIS. En este informe los eventos 2013 se recalculan con SNPP consistente.

---

## 3. Contrato de validacion (V0)

Creado en `00_contrato.sql`:

- `validation_aoi`: bbox, `observed_sensor`, `baseline_sensor`, `ref_from`,
  `ref_to`, estado y notas.
- `validation_aoi_window`: ventanas `isoyear / week_from / week_to`, con
  `expected_peak` como semana de referencia.

AOIs cargadas:

| AOI | Estado | Bbox | Evento |
|---|---|---|---|
| `delta_2020` | confirmed | -34..-32.5, -61..-59.5 | Sep-Oct 2020 |
| `corrientes_2022` | provisional | -29.5..-27.5, -59..-56.5 | Ene-Feb 2022 |
| `salta_2013` | provisional | -25..-22, -65..-63 | Sep 2013 |
| `cordoba_2013` | provisional | -33..-30, -65..-63 | Sep 2013 |
| `la_pampa_2016_2017` | provisional | -39..-35, -68..-63 | Dic 2016-Ene 2017 |

Solo `delta_2020` esta confirmada. Las otras cuatro usan un bbox preliminar y
sus resultados no deben citarse como definitivos hasta revisarlas.

---

## 4. V1 - Validacion interna

| Chequeo | Resultado | Estado |
|---|---|---|
| Fuentes | 8 pares sensor/fuente; 7.082.474 detecciones | ✅ |
| Duplicados exactos | PK `(sensor, source, lat, lon, acquired_at)` | ✅ |
| Solapamiento SP/NRT | 0 pares sensor/fecha con ambas fuentes | ✅ |
| Completitud | 3.719.116 filas = 70.172 celdas × 53 semanas | ✅ |
| Consistencia | 1.828.709 = 1.828.709; 0 inconsistencias | ✅ |
| `n_data_years` | 100% NULL (3.719.116 filas) | ❌ |
| `coverage_daily` | 0 filas | ❌ |
| Mascara industrial | NRT 4.035 (2,80%); SP 0 | ✅ |

### Cobertura observada

- MODIS: 2000 16,7%; 2001 90,7%; 2002 93,4%; 2003-2025 98,6-100%;
  2026 98,4% de los dias transcurridos.
- VIIRS SNPP: 2012 94,5%; 2013-2025 94,5-100%; 2026 91,2%.
- NOAA-20: 2018 75,3% (inicio en abril); 2019-2025 98,6-100%; 2026 99,7%.
- NOAA-21: 2023 83,8% (inicio en marzo); 2024-2025 99,5-100%; 2026 99,4%.

Esta cobertura es **dias con al menos una deteccion**, no cobertura real de
observacion. Un dia sin detecciones puede ser "sin fuego" o "sin overpass /
nubes".

### Actividad semanal

- `fire_day_rate_given_detection`: 0,6008 (semana 53) a 0,9322 (semana 31).
- Maximo absoluto: semana 35, 79.172 celdas con fuego y 107.338 dias-celda.
- Semana 53: solo 4 anios con actividad (2004, 2009, 2015, 2020) y 1.675
  celdas-semana. No usarla para anomalias sin `n_ref >= 7`.

### Semantica de `n_fire_years`

| Metrica | Valor |
|---|---:|
| Celdas-semana con `n_fire_years >= 7` | 18.516 (0,50%) |
| Celdas con al menos una semana `>= 7` | 5.486 (7,82% de 70.172) |
| Celdas-semana con `n_activity_years >= 7` | 25.186 |

El dato "18.516" de §9 son celdas-semana, no celdas. Hay que nombrarlo distinto.

### NRT por anio

- 2026: 61.144 celdas-semana, 29.414 con NRT (`share_nrt = 0,481`).
- El resto de los anios tiene `share_nrt = 0`: SP y NRT son disjuntos.

---

## 5. V2 - Eventos conocidos

| AOI | Anio | Semana pico | Observado | Baseline | n_ref | Anomalia | Confianza |
|---|---:|---:|---:|---:|---:|---:|---|
| Delta del Parana | 2020 | 38 | 92 | 14 | 12 | 6,57 | high |
| Delta del Parana, semana 40 | 2020 | 40 | 66 | 10 | 12 | **6,60** | high |
| Corrientes / Ibera | 2022 | 2 | 453 | 21 | 11 | 21,57 | high |
| Salta / Jujuy | 2013 | 37 | 248 | 66,5 | 12 | 3,73 | high |
| Cordoba | 2013 | 37 | 138 | 18 | 12 | 7,67 | high |
| La Pampa | 2016 | 52 | 260 | 32,5 | 12 | 8,00 | high |
| La Pampa | 2017 | 1 | 326 | 13 | 12 | 25,08 | high |

Observaciones:

- **Delta 2020:** el pico real de la AOI es la semana 38, no la 40. La semana
  40 es la referencia de §6.7.1 y reproduce 66/10/12/6,60. Ambas son validas;
  hay que decir cual se cita.
- **Corrientes 2022:** semanas 2-7 con anomalias 10,57-21,57 y confianza high.
- **La Pampa:** la semana 53 de 2016 queda `insufficient` (solo 2 anios de
  referencia); se reporta aparte.
- **Salta, Cordoba y La Pampa** dan valores distintos a la tabla 6.7.1 porque
  las AOIs son provisionales y porque 2013 ahora usa baseline SNPP, no MODIS.
- Delta con todos los sensores en la semana 40: 78/10/12 = **7,80**.
  La tabla 6.7.1 usa SNPP-only; el plan debe declararlo.

**Resultado:** 6/6 ventanas de evento cumplen `observed >= 3`,
`anomalia > 1` y `n_ref >= 7`.

---

## 6. V3 - Sensibilidad MODIS vs VIIRS

Periodo 2013-2025, semanas 1-52, espina completa con cero-relleno.

| Ambito | Spearman | Top-5 | Top-10 | Ratio SNPP/MODIS |
|---|---:|---:|---:|---:|
| Nacional | 0,969 | 0,908 | 0,892 | 2,264 |
| Corrientes / Ibera | 0,956 | 0,908 | 0,892 | 2,182 |
| Salta / Jujuy | 0,936 | 0,846 | 0,869 | 1,862 |
| La Pampa | 0,889 | 0,862 | 0,823 | 1,786 |
| Cordoba | 0,770 | 0,708 | 0,738 | 2,362 |
| Delta del Parana | 0,769 | 0,600 | 0,669 | 2,881 |

La lectura nacional es robusta. La lectura local de **todos los anios** no
siempre lo es: Delta y Cordoba bajan de 0,8 en top-5. Esto ocurre sobre todo
en anios de baja actividad, donde el ranking es ruidoso.

### Periodos criticos en el anio del evento

| AOI | Anio | Spearman | Top-5 | Top-10 |
|---|---:|---:|---:|---:|
| Delta del Parana | 2020 | 0,965 | 1,000 | 0,800 |
| Corrientes / Ibera | 2022 | 0,913 | 1,000 | 1,000 |
| Salta / Jujuy | 2013 | 0,958 | 0,800 | 0,900 |
| Cordoba | 2013 | 0,606 | 1,000 | 0,700 |
| La Pampa | 2016 | 0,856 | 0,600 | 0,900 |
| La Pampa | 2017 | 0,944 | 1,000 | 0,900 |

Conclusion: **las semanas criticas del evento son robustas** en 5 de 6
ventanas para top-5 (La Pampa 2016 es la excepcion) y en 5 de 6 para top-10
(Cordoba 2013 es la excepcion). El ranking global de todos los anios es una
metrica mas exigente y no debe usarse como unico criterio.

### Calendario combinado ("todos")

| Ambito | Spearman MODIS-todos | Ratio todos/MODIS | Spearman SNPP-todos | Ratio todos/SNPP |
|---|---:|---:|---:|---:|
| Nacional | 0,956 | 3,283 | 0,953 | 1,450 |
| Delta | 0,780 | 4,029 | 0,929 | 1,399 |
| Corrientes | 0,964 | 3,131 | 0,976 | 1,435 |
| La Pampa | 0,908 | 2,568 | 0,934 | 1,438 |
| Salta | 0,950 | 2,556 | 0,971 | 1,373 |
| Cordoba | 0,793 | 3,432 | 0,915 | 1,453 |

El calendario "todos" **no es neutral**: esta mucho mas cerca de SNPP que de
MODIS (ratio 1,45 vs 3,28). La razon es que hay tres sensores VIIRS y uno
MODIS. Para la UI y la API hay que etiquetarlo como "todos los sensores" y no
presentarlo como un promedio.

### High only (V3b)

| Ambito | Spearman | Top-5 | Ratio SNPP/MODIS |
|---|---:|---:|---:|
| Nacional | 0,949 | 0,877 | 0,854 |
| Corrientes / Ibera | 0,828 | 0,769 | 0,903 |
| Salta / Jujuy | 0,877 | 0,800 | 0,714 |
| La Pampa | 0,812 | 0,815 | 0,779 |
| Cordoba | 0,586 | 0,646 | 0,861 |
| Delta del Parana | 0,658 | 0,446 | 1,257 |

En el anio del evento, el top-5 high-only es: Delta 0,600; Cordoba 0,800;
Corrientes 1,000; La Pampa 2016 0,800; La Pampa 2017 1,000; Salta 0,800.

**Hallazgo:** con `high` only el ratio SNPP/MODIS cae de 2,26 a **0,85**. Es
decir, MODIS queda con mas celdas "high" que SNPP. La causa es que la
confianza alta no es homologa: MODIS usa un umbral 0-100 (>= 80) y VIIRS una
clase discreta (`h`). **No usar `high` only para el calendario**; la serie
titular debe ser `nominal + high`.

---

## 7. V4 - Coincidencia espacial

Metodo: solo SP, mismo dia, 3×3 bins de 0,02°, distancia equirectangular
< 1 km, detecciones distintas.

### Serie completa VIIRS SNPP

| Anio | % MODIS con match | % VIIRS con match | FRP med MODIS | FRP med VIIRS |
|---:|---:|---:|---:|---:|
| 2013 | 64,1% | 47,4% | 29,9 | 7,12 |
| 2014 | 60,0% | 42,7% | 34,0 | 7,93 |
| 2015 | 58,0% | 41,5% | 31,0 | 7,58 |
| 2016 | 60,8% | 47,5% | 36,1 | 8,02 |
| 2017 | 61,6% | 49,3% | 37,9 | 8,39 |
| 2018 | 60,2% | 44,0% | 32,5 | 7,76 |
| 2019 | 57,8% | 42,7% | 33,2 | 8,02 |
| 2020 | 68,8% | 49,0% | 32,9 | 8,27 |
| 2021 | 61,0% | 42,4% | 31,9 | 8,23 |
| 2022 | 68,0% | 48,2% | 34,2 | 7,92 |
| 2023 | 63,2% | 45,8% | 32,9 | 8,24 |
| 2024 | 53,5% | 41,8% | 27,7 | 7,51 |
| 2025 | 51,7% | 36,8% | 27,3 | 7,14 |

### Otros VIIRS

| Sensor | Periodo | % MODIS | % VIIRS | FRP med MODIS | FRP med VIIRS |
|---|---|---|---|---|---|
| NOAA-20 | 2018-2025 | 44,8-70,4% | 36,5-49,0% | 26,1-33,9 | 7,39-8,49 |
| NOAA-21 | 2023-2025 | 50,1-55,3% | 36,0-44,2% | 27,4-29,7 | 8,81-9,40 |

Detalle completo: `reports/validacion_6_8_20261114/05_resumen_coincidencia.csv`.

Lectura:

- MODIS coincide con VIIRS en 52-69% de sus detecciones con SNPP, 45-70% con
  NOAA-20 y 50-55% con NOAA-21. VIIRS coincide con MODIS en 37-49% (SNPP),
  36-49% (NOAA-20) y 36-44% (NOAA-21).
- VIIRS tiene mas detecciones y menor FRP por deteccion.
- La densidad de FRP de VIIRS es casi el doble: su pixel es mas chico
  (375 m vs 1 km), no necesariamente el fuego es mas intenso.
- Las detecciones coincidentes tienen mayor FRP que las no coincidentes:
  MODIS 24,3 vs 18,6 MW en 2020; VIIRS 7,83 vs 4,71 MW.
- Restringir a <= 3 h reduce la coincidencia de MODIS de 68,8% a 57,3%.
  El titular de "mismo dia" es mas permisivo; hay que declararlo.
- Mediana de distancia de los pares < 1 km: 0,65 km.

---

## 8. Hallazgos principales

1. **La base es consistente.** Los agregados diarios y semanales cierran
   exactamente y no hay duplicados ni solapamiento SP/NRT.
2. **La anomalia debe ser sensor-consistente.** Delta 2020 es 6,60 con SNPP y
   7,80 con todos. La tabla 6.7.1 usa SNPP-only; la formula de §6.7 no lo dice.
3. **El ranking nacional es robusto; el local no siempre.** Delta y Cordoba
   bajan de 0,8 en top-5 cuando se incluyen todos los anios, pero sus semanas
   criticas si coinciden.
4. **"Todos" esta dominado por VIIRS.** No es un promedio neutral.
5. **VIIRS detecta mas y con menor FRP.** La coincidencia espacial confirma la
   diferencia de sensibilidad; no es un error de armonizacion.
6. **`high` only no es homologo.** El ratio SNPP/MODIS cae de 2,26 a 0,85
   porque MODIS usa un umbral 0-100 y VIIRS una clase discreta. El calendario
   debe usar `nominal + high`.
7. **La cobertura sigue siendo el bloqueo principal.** `coverage_daily` vacia
   y `n_data_years` NULL impiden distinguir "sin datos" de "sin fuego".
8. **Las AOIs de 4 eventos son provisionales.** Sin bbox confirmado, sus
   valores no son auditables.

---

## 9. Limitaciones

- No hay QA/nubosidad por pixel ni area quemada.
- La grilla de 0,05° diluye fuegos pequenos y puede colapsar incendios cercanos.
- El FRP no esta calibrado por bioma ni por sensor.
- La coincidencia usa mismo dia; no distingue overpass. La ventana <= 3 h es
  una sensibilidad, no el titular.
- La distancia es equirectangular; el error a 1 km es despreciable, pero no es
  Haversine.
- 2026 es NRT parcial y queda fuera de V3/V4.
- `high` only no es homologo: MODIS usa umbral 0-100 y VIIRS una clase
  discreta. El calendario usa `nominal + high`.
- No se corrio la sensibilidad 0,05° vs 0,1° de §6.4.

---

## 10. Pendientes

### P0

1. Confirmar o corregir las AOIs `corrientes_2022`, `salta_2013`,
   `cordoba_2013` y `la_pampa_2016_2017`.
2. Decidir y documentar el sensor del observado en la anomalia de la API:
   SNPP-only para 2013-2025; MODIS-only para 2003-2011.
3. Actualizar §6.7.1 para que la tabla declare "observado SNPP-only".

### P1

4. Poblar `coverage_daily` o documentar formalmente que `n_data_years` queda
   NULL y que la UI no debe interpretar ausencia de filas como ausencia de fuego.
5. Correr la sensibilidad 0,05° vs 0,1° de §6.4.
6. Actualizar §8 y §9 del plan con estos resultados (V5).

---

## 11. Reproduccion

```powershell
$env:PGPASSWORD = 'postgres'
$psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
$db = 'chronos_fire'

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\00_contrato.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\01_interna.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\02_eventos.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\03_sensibilidad.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -f backend\db\validacion\03b_sensibilidad_high.sql

& $psql -h 127.0.0.1 -p 5432 -U postgres -d $db -v ON_ERROR_STOP=1 `
  -v anio=2020 -v viirs_sensor=VIIRS_SNPP -v max_hours=24 `
  -f backend\db\validacion\04_coincidencia.sql
```

Barrido completo y consolidado:

```powershell
$env:PGPASSWORD = 'postgres'
.\backend\db\validacion\run_validacion.ps1 -SkipBase
```

---

## 12. Veredicto

La armonizacion es **suficiente para un calendario relativo y para detectar
periodos criticos**, con estas condiciones:

- la anomalia se calcula con sensores homogeneos;
- el calendario "todos" se etiqueta como dominado por VIIRS;
- las AOIs de eventos se confirman;
- la UI no interpreta semanas vacias como "sin fuego".

No es suficiente para comparar **niveles absolutos** entre eras ni para
afirmar que MODIS y VIIRS miden lo mismo. Esa conclusion es consistente con
el alcance operativo del plan y con la frase de demo propuesta en §13.
