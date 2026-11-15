# Recursos de NASA usados en ChronosFire

Documento de referencia: qué servicios de NASA consume la app, para qué sirve cada
uno, cuántas transacciones gasta y dónde está implementado.

Última actualización: 2026-11-14.

---

## Resumen

| Capa de la app | Servicio de NASA | ¿Clave? | Costo en transacciones | Código |
| --- | --- | --- | --- | --- |
| Focos de incendio (principal) | FIRMS **API · endpoint `area`** | Sí (`MAP_KEY`) | 1 por llamada (1–5 días) | `src/data/firms.ts` → `fetchAreaFires()` |
| Focos de incendio (respaldo) | FIRMS **WMS** | Sí (`MAP_KEY`) | 1 **por tesela** (~12–15 por vista) | `src/components/FireMap.vue` → `buildFireLayer()` |
| Mapa base nocturno | **NASA GIBS** · `VIIRS_Black_Marble` | No | 0 | `GIBS_BLACKMARBLE_URL` |
| Mapa base color verdadero | **NASA GIBS** · VIIRS / MODIS True Color | No | 0 | `makeBaseLayer('gibs')` |
| Imágenes satelitales (opcional) | Esri World Imagery | No | 0 (fuera de NASA) | `makeBaseLayer('satellite')` |

La regla de diseño: **el dato (focos) se pide a la API `area`, que cobra por
consulta y no por tesela**; el WMS quedó solo como respaldo automático.

---

## 1. FIRMS API · endpoint `area` (fuente principal)

Base: `https://firms.modaps.eosdis.nasa.gov/api/area/`
Docs: <https://firms.modaps.eosdis.nasa.gov/api/area/>

### Formato

```
/api/area/csv/[MAP_KEY]/[SOURCE]/[AREA_COORDINATES]/[DAY_RANGE]/[DATE]
```

| Parámetro | Valor usado | Notas |
| --- | --- | --- |
| `MAP_KEY` | `VITE_MAP_KEY` | 32 caracteres hex |
| `SOURCE` | ver tabla de épocas | determina sensor y archivo |
| `AREA_COORDINATES` | `west,south,east,north` | bbox del AOI: `-62,-30,-53,-20` |
| `DAY_RANGE` | `1..5` | **máximo 5 días por consulta** |
| `DATE` | `YYYY-MM-DD` | devuelve `DATE .. DATE+DAY_RANGE-1` |

### Productos y épocas (verificado contra la API)

| Fecha consultada | Producto | Resultado |
| --- | --- | --- |
| 2026-11-05 (5 días) | `VIIRS_NOAA21_NRT` | 1990 focos |
| 2025-06-01 | `VIIRS_NOAA21_NRT` | 21 focos |
| 2026-09-01 | `VIIRS_SNPP_SP` | 0 focos (SP todavía no procesado) |
| 2025-06-01 | `VIIRS_SNPP_SP` | 39 focos |
| 2023-01-01 | `VIIRS_SNPP_SP` | 689 focos |
| 2019-06-01 | `VIIRS_NOAA20_SP` | 7 focos |
| 2020-03-01 | `VIIRS_NOAA21_NRT/SP`, `VIIRS_SNPP_NRT` | 0 focos |
| 2016-06-01 | `VIIRS_SNPP_SP` | 58 focos |
| 2010-05-01 | `MODIS_SP` | 35 focos |
| 2010-05-01 | `MODIS_NRT` | 0 focos |

**Conclusión:** `NRT` solo conserva la época reciente; el archivo histórico son los
`_SP`. Elegir mal el producto devuelve **0 focos sin error**, por eso la app
resuelve el producto por fecha en `selectFirmsSource()`:

```
from >= 2024-01-17  → VIIRS_NOAA21_NRT
from >= 2012-01-20  → VIIRS_SNPP_SP
resto               → MODIS_SP
```

### Respuesta

CSV con cabecera (VIIRS):

```
latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,
instrument,confidence,version,bright_ti5,frp,daynight
```

MODIS usa `brightness`/`bright_t31` en lugar de `bright_ti4`/`bright_ti5`; el
parser acepta ambas.

### Implementación

`src/data/firms.ts`:

- `chunkRange()` parte el rango en bloques de 5 días → un mes = 7 llamadas (31 días).
- `areaCache` / `areaInFlight` cachean por `(producto, bbox, desde, hasta)`:
  volver a un mes ya visto **no gasta transacciones** (la caché vive en memoria,
  se pierde al recargar la página).
- `parseAreaCsv()` normaliza los campos a `FirePoint`.
- Deduplicación por `lat,lon,fecha,hora,satélite` (los bloques pueden solaparse).
- `FirmsApiError` expone el mensaje del servidor (ej. `Invalid MAP_KEY`).

CORS: la API responde `Access-Control-Allow-Origin: *`, así que se puede llamar
con `fetch` desde el navegador.

---

## 2. FIRMS WMS (respaldo automático)

Base: `https://firms.modaps.eosdis.nasa.gov/mapserver/wms/fires/[MAP_KEY]`
Docs: <https://firms.modaps.eosdis.nasa.gov/mapserver/wms-info/>

| Parámetro | Valor | Por qué |
| --- | --- | --- |
| `layers` | `fires_viirs_24/48/72/7` (vivo) o `fires_viirs` + `TIME` (histórico) | capas documentadas por FIRMS |
| `SRS` | `EPSG:3857` | **obligatorio**: `crs=EPSG3857` devuelve imagen vacía |
| `format` | `image/png` | **obligatorio**: el default de Leaflet es `image/jpeg` |
| `transparent` | `true` | el PNG necesita canal alfa |
| `TIME` | `YYYY-MM-DD/YYYY-MM-DD` | histórico, máximo 31 días |

### Dos trampas que costaron depuración

1. **`format`**: si no se especifica, Leaflet usa `image/jpeg`. Un JPEG **no tiene
   canal alfa**, así que el servidor devuelve el fondo opaco y la capa tapa
   completamente el mapa base (síntoma: "un fondo gris con los puntos encima").
   Verificado: mismo bbox → `image/jpeg` = `Format24bppRgb, A=255` (opaco);
   `image/png` = `Format32bppArgb, A=0` (transparente).
2. **`SRS`**: con `crs=EPSG3857` (que Leaflet agrega solo) la respuesta es una
   imagen en blanco; hay que forzar `SRS=EPSG:3857`.

### Costo

Cada `GetMap` es una transacción. Una vista son ~9–15 teselas, y cada zoom/pan las
vuelve a pedir: dos consultas históricas pueden agotar el cupo de 5000/10 min.
Por eso solo se usa cuando la API falla (`usingWms` en el componente).

---

## 3. NASA GIBS (mapas base)

Base: `https://gibs.earthdata.nasa.gov/wmts/epsg3857/best`
Docs: <https://nasa-gibs.github.io/gibs-api-docs/>

| Capa | TileMatrixSet | Formato | Fecha |
| --- | --- | --- | --- |
| `VIIRS_Black_Marble` (Nocturno) | `GoogleMapsCompatible_Level8` | `.png` | `2016-01-01` |
| `VIIRS_NOAA21_CorrectedReflectance_TrueColor` | `GoogleMapsCompatible_Level9` | `.jpg` | fecha del período |
| `MODIS_Terra_CorrectedReflectance_TrueColor` | `GoogleMapsCompatible_Level9` | `.jpg` | histórico |

Cada capa tiene su propio `TileMatrixSet` y extensión; si no coinciden con las
`GetCapabilities`, GIBS responde **400** (con `.jpg` donde va `.png`, o con un TMS
distinto, no hay imagen).

No requiere clave ni tiene cuota de transacciones.

**Nota sobre anomalías térmicas:** existen capas `*_Thermal_Anomalies_375m_Day/Night`
(equivalentes a los focos de FIRMS) desde 2024-01-17, pero en EPSG:3857 se publican
como **`.mvt` (vector tiles)**, no como ráster. Usarlas exigiría un renderizador de
vector tiles (`leaflet.vectorgrid` / MapLibre). Quedan como alternativa futura sin
cuota; hoy los focos se obtienen por la API.

---

## 4. FIRMS `active_fire` (archivos CSV estáticos) — evaluado, no usado

Base: `https://firms.modaps.eosdis.nasa.gov/data/active_fire/`

Tamaños verificados (HTTP 200, sin clave):

| Archivo | Peso |
| --- | --- |
| `noaa-20-viirs-c2/csv/J1_VIIRS_C2_Global_24h.csv` | 6.35 MB |
| `noaa-20-viirs-c2/csv/J1_VIIRS_C2_Global_48h.csv` | 9.94 MB |
| `noaa-20-viirs-c2/csv/J1_VIIRS_C2_Global_7d.csv` | 33.7 MB |
| `noaa-21-viirs-c2/csv/J2_VIIRS_C2_Global_24h.csv` | 6.54 MB |
| `modis-c6.1/csv/MODIS_C6_1_Global_24h.csv` | 1.13 MB |

Ventajas: cero transacciones y cero claves, se puede cachear en disco.
Desventaja: son **globales** (6–34 MB) y solo cubren las últimas horas/días, así que
no sirven para histórico y obligan a filtrar por bbox en el cliente.

Queda como respaldo de último recurso si se agota la clave por completo.

---

## 5. MAP_KEY

- Se lee de `VITE_MAP_KEY` (`import.meta.env`). Vite solo expone variables con
  prefijo `VITE_` y solo carga `.env` desde la raíz del proyecto (`ChronosFire/`).
- Si la variable no está, se usa la clave incrustada en `firms.ts` para que la app
  no se rompa en la demo.
- Límite: **5000 transacciones / 10 minutos**. Consultas largas (7 días) pueden
  contar como varias.
- Estado de la clave:
  `https://firms.modaps.eosdis.nasa.gov/mapserver/mapkey_status/?MAP_KEY=<clave>`
  → `{ "transaction_limit": 5000, "current_transactions": N, "transaction_interval": "10 minutes" }`
- **Cuando la clave está por encima del límite, la API responde `400 Invalid MAP_KEY`
  y el WMS sigue funcionando.** Por eso el WMS se conserva como respaldo.

---

## 6. Costo por acción de usuario

| Acción | Transacciones |
| --- | --- |
| Cargar el mapa (24 h) | 1 |
| Chip 48 h / 72 h | 1 |
| Chip 7 días | 2 (bloques de 5 + 2) |
| Abrir un mes del calendario | ≤ 7 (31 días ÷ 5) |
| Volver a un mes ya abierto | 0 (caché en memoria) |
| Cambiar de mapa base | 0 |
| Pan / zoom | 0 (los puntos ya están en el cliente) |
| Respaldo WMS (si la API falla) | ~12–15 por vista, y otra vez en cada zoom/pan |

---

## 7. Decisiones de implementación

- **Vectorial en lugar de raster**: los focos se dibujan con `L.circleMarker` sobre
  un renderer `L.canvas` en el pane `fire` (z-index 250), así quedan siempre por
  encima de cualquier mapa base y permiten popup por foco (brillo, FRP, confianza,
  satélite, hora).
- **Color y tamaño por FRP**: 4 tramos (`<5`, `<20`, `<50`, `≥50` MW).
- **Tope de 8000 marcadores**: un mes del AOI puede traer ~12 000 focos; se dibujan
  los de mayor FRP y la leyenda lo indica ("top N por FRP").
- **`loadSeq`**: descarta respuestas viejas si el usuario cambia de mes mientras
  carga.
- **Respaldo automático**: si `fetchAreaFires()` falla, se dibuja el WMS y la leyenda
  muestra "(WMS)".

---

## 8. Enlaces

- API `area`: <https://firms.modaps.eosdis.nasa.gov/api/area/>
- Estado de la clave: <https://firms.modaps.eosdis.nasa.gov/mapserver/mapkey_status/>
- WMS info: <https://firms.modaps.eosdis.nasa.gov/mapserver/wms-info/>
- Descarga masiva / archivos: <https://firms.modaps.eosdis.nasa.gov/download/>
- Activos (CSV diarios): <https://firms.modaps.eosdis.nasa.gov/active_fire>
- GIBS: <https://nasa-gibs.github.io/gibs-api-docs/>
- Atributos de VIIRS NOAA-21: <https://www.earthdata.nasa.gov/learn/find-data/near-real-time/firms/vj214imgtdlnrt>
