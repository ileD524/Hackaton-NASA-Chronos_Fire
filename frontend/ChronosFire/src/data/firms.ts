/**
 * NASA FIRMS · Configuración del servicio WMS
 * https://firms.modaps.eosdis.nasa.gov/mapserver/wms-info/
 *
 * MAP_KEY es una clave pública que NASA distribuye para usar sus servicios
 * web (WMS / WMS-Time) desde mapas web en el cliente.
 *
 * Se lee de la variable de entorno VITE_MAP_KEY: Vite solo expone al navegador
 * variables con prefijo VITE_, y pueden definirse en un .env en la raíz del
 * proyecto (esta carpeta) o en el entorno del sistema (setx / shell).
 * Como respaldo, si la variable no está, se usa la clave incrustada para que
 * la app no se rompa.
 */
const FALLBACK_MAP_KEY = '3bf09359d68a675f83164e52ec92fd7e'
export const FIRMS_MAP_KEY: string = import.meta.env.VITE_MAP_KEY || FALLBACK_MAP_KEY

/** URL base del servicio WMS "fires" (con la clave embebida). */
export const FIRMS_WMS_URL = `https://firms.modaps.eosdis.nasa.gov/mapserver/wms/fires/${FIRMS_MAP_KEY}`

/**
 * El servidor solo dibuja teselas Web Mercator si recibe SRS=EPSG:3857.
 * El parámetro crs=EPSG3857 que Leaflet agrega por defecto es ignorado y
 * devuelve una imagen en blanco, por eso se fuerza SRS explícitamente.
 */
export const FIRMS_WMS_PROJECTION = 'EPSG:3857'

/** Color (R G B) y tamaño en píxeles de los puntos de hotspot en el WMS. */
export const FIRE_DOT_COLOR = '255 115 0'
export const FIRE_DOT_SIZE = 4

/** Ventana de fechas para dibujar en el mapa (WMS-Time: máx. 31 días por consulta). */
export interface MapWindow {
  from: string // YYYY-MM-DD
  to: string // YYYY-MM-DD
}

/**
 * Ventana de un mes completo del calendario (month en base 0, 0 = enero).
 * Un mes tiene como máximo 31 días, así que siempre cabe en la consulta WMS-Time.
 */
export function monthWindow(year: number, month: number): MapWindow {
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate()
  const mm = String(month + 1).padStart(2, '0')
  const dd = String(lastDay).padStart(2, '0')
  return { from: `${year}-${mm}-01`, to: `${year}-${mm}-${dd}` }
}

/** Centro y zoom de la vista inicial: área de interés (Chaco, Argentina). */
export const INITIAL_CENTER: [number, number] = [-25.3, -57.45]
export const INITIAL_ZOOM = 5

/**
 * NASA GIBS (Global Imagery Browse Services) · fondos de imagen satelital
 * sin API key. Es el mismo servicio de "backgrounds" que usa el mapa
 * oficial de FIRMS.
 * https://gibs.earthdata.nasa.gov
 *
 * Nota: cada capa tiene su propio TileMatrixSet y extensión — hay que tomarlos
 * de las GetCapabilities (un .jpg donde toca .png, o un TMS sin el sufijo,
 * devuelve 400).
 */
export const GIBS_BASE = 'https://gibs.earthdata.nasa.gov/wmts/epsg3857/best'
/** El set de teselas de GIBS para EPSG:3857 (sin el sufijo devuelve 400). */
export const GIBS_TILEMATRIXSET = 'GoogleMapsCompatible_Level9'
/** VIIRS NOAA-21 para modo en vivo; MODIS Terra cubre la historia desde 2000. */
export const GIBS_LAYER_VIIRS = 'VIIRS_NOAA21_CorrectedReflectance_TrueColor'
export const GIBS_LAYER_MODIS = 'MODIS_Terra_CorrectedReflectance_TrueColor'
/**
 * Fondo nocturno: composite Black Marble (VIIRS, 2016) — estático, .png,
 * TMS GoogleMapsCompatible_Level8 (hasta nivel 8).
 */
export const GIBS_BLACKMARBLE_URL =
  `${GIBS_BASE}/VIIRS_Black_Marble/default/2016-01-01/GoogleMapsCompatible_Level8/{z}/{y}/{x}.png`

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10)
}

/**
 * Fecha de imagen para el fondo GIBS:
 * - Modo en vivo: hace 2 días (GIBS tiene un desfase de ~1-2 días).
 * - Modo histórico: el día 15 del mes seleccionado, limitado a lo último
 *   disponible si el mes todavía está en curso.
 */
export function gibsDate(timeWindow: MapWindow | null): string {
  const latest = new Date(Date.now() - 2 * 86400000)
  if (!timeWindow) return isoDate(latest)
  const [y, m] = timeWindow.from.split('-').map(Number)
  const monthStart = new Date(Date.UTC(y!, m! - 1, 1))
  const candidate = new Date(Date.UTC(y!, m! - 1, 15))
  const pick = candidate.getTime() <= latest.getTime() ? candidate : latest
  return isoDate(pick.getTime() < monthStart.getTime() ? monthStart : pick)
}

/* ==================================================================== *
 * DATOS DE FOCOS · API "area" de FIRMS (vectorial)
 *
 * A diferencia del WMS (1 transacción por tesela), cada llamada a esta API
 * devuelve todos los focos de un bbox para 1..5 días = 1 transacción.
 * Un mes completo (31 días) consume ~7 transacciones en lugar de miles.
 * https://firms.modaps.eosdis.nasa.gov/api/area/
 * ==================================================================== */

/** Caja geográfica en grados: oeste, sur, este, norte. */
export interface Bbox {
  west: number
  south: number
  east: number
  north: number
}

/**
 * Área de interés por defecto (Chaco, Argentina) usada para las consultas
 * de la API. Mantenerla acotada reduce el peso de la respuesta.
 */
export const AOI_BBOX: Bbox = { west: -62, south: -30, east: -53, north: -20 }

/** Productos VIIRS/MODIS del endpoint area. */
export type FirmsSource =
  | 'VIIRS_NOAA21_NRT'
  | 'VIIRS_NOAA20_NRT'
  | 'VIIRS_SNPP_NRT'
  | 'MODIS_NRT'
  | 'VIIRS_NOAA21_SP'
  | 'VIIRS_NOAA20_SP'
  | 'VIIRS_SNPP_SP'
  | 'MODIS_SP'

/** NOAA-21 es el sensor con más cobertura reciente. */
export const FIRMS_AREA_SOURCE: FirmsSource = 'VIIRS_NOAA21_NRT'

/**
 * Cada producto cubre una época distinta (verificado contra la API):
 * - NOAA-21 NRT: desde 2024-01-17 (el archivo NRT se conserva completo).
 * - VIIRS S-NPP SP: desde 2012-01-20 (procesamiento estándar = archivo).
 * - MODIS SP: desde 2000.
 * Elegir mal el producto devuelve 0 focos sin error, por eso se resuelve
 * a partir de la fecha en lugar de dejarlo fijo.
 */
const NOAA21_FROM = '2024-01-17'
const SNPP_FROM = '2012-01-20'

export function selectFirmsSource(from: string): FirmsSource {
  if (from >= NOAA21_FROM) return FIRMS_AREA_SOURCE
  if (from >= SNPP_FROM) return 'VIIRS_SNPP_SP'
  return 'MODIS_SP'
}

export const FIRMS_AREA_API = 'https://firms.modaps.eosdis.nasa.gov/api/area/csv'

/** Un foco detectado (valores de las columnas CSV de FIRMS). */
export interface FirePoint {
  lat: number
  lon: number
  /** Brillo en kelvin (bright_ti4 en VIIRS, brightness en MODIS). */
  brightness: number
  /** Fire Radiative Power en MW. */
  frp: number
  confidence: string
  /** Código de satélite: N21, N20, SNPP, Aqua, Terra… */
  satellite: string
  date: string // YYYY-MM-DD
  time: string // HHMM UTC
  daynight: string // D | N
}

/** Error de la API (clave inválida, cuota agotada, área demasiado grande…). */
export class FirmsApiError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'FirmsApiError'
  }
}

/** El endpoint area acepta como máximo 5 días por consulta. */
const MAX_DAYS_PER_QUERY = 5

function parseAreaCsv(text: string): FirePoint[] {
  const lines = text.trim().split(/\r?\n/)
  if (lines.length < 2) return []

  const header = lines[0]!.split(',').map((h) => h.trim().toLowerCase())
  const col = (name: string) => header.indexOf(name)

  const iLat = col('latitude')
  const iLon = col('longitude')
  if (iLat < 0 || iLon < 0) return []

  // VIIRS expone bright_ti4; MODIS, brightness.
  const iBright = col('bright_ti4') >= 0 ? col('bright_ti4') : col('brightness')
  const iFrp = col('frp')
  const iConf = col('confidence')
  const iSat = col('satellite')
  const iDate = col('acq_date')
  const iTime = col('acq_time')
  const iDn = col('daynight')

  const num = (cols: string[], index: number): number => {
    if (index < 0) return 0
    const value = Number(cols[index])
    return Number.isFinite(value) ? value : 0
  }
  const str = (cols: string[], index: number): string =>
    index >= 0 ? (cols[index] ?? '').trim() : ''

  const points: FirePoint[] = []
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i]!
    if (!line.trim()) continue
    const cols = line.split(',')
    const lat = num(cols, iLat)
    const lon = num(cols, iLon)
    if (lat === 0 && lon === 0) continue
    points.push({
      lat,
      lon,
      brightness: num(cols, iBright),
      frp: num(cols, iFrp),
      confidence: str(cols, iConf),
      satellite: str(cols, iSat),
      date: str(cols, iDate),
      time: str(cols, iTime).padStart(4, '0'),
      daynight: str(cols, iDn),
    })
  }
  return points
}

function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number)
  const date = new Date(Date.UTC(y!, m! - 1, d!))
  date.setUTCDate(date.getUTCDate() + days)
  return isoDate(date)
}

function daysBetween(from: string, to: string): number {
  const parse = (iso: string) => {
    const [y, m, d] = iso.split('-').map(Number)
    return Date.UTC(y!, m! - 1, d!)
  }
  return Math.round((parse(to) - parse(from)) / 86400000) + 1
}

/** Parte un rango en bloques de 5 días (el máximo por consulta). */
function chunkRange(from: string, to: string): MapWindow[] {
  const chunks: MapWindow[] = []
  let cursor = from
  while (daysBetween(cursor, to) > 0) {
    const remaining = daysBetween(cursor, to)
    const size = Math.min(MAX_DAYS_PER_QUERY, remaining)
    const end = addDays(cursor, size - 1)
    chunks.push({ from: cursor, to: end > to ? to : end })
    cursor = addDays(end, 1)
    if (chunks.length > 64) break
  }
  return chunks
}

/** Ventana de los últimos N días (incluye hoy), en UTC. */
export function lastDays(days: number): MapWindow {
  const to = isoDate(new Date())
  return { from: addDays(to, -(days - 1)), to }
}

const areaCache = new Map<string, FirePoint[]>()
const areaInFlight = new Map<string, Promise<FirePoint[]>>()

export interface AreaFiresRequest {
  from: string
  to: string
  source?: FirmsSource
  bbox?: Bbox
  /** Fuerza una nueva descarga ignorando la caché en memoria. */
  fresh?: boolean
}

/**
 * Descarga los focos del área en el rango pedido.
 * Cachea el resultado en memoria por (fuente, bbox, rango) para que navegar
 * entre meses ya consultados no gaste transacciones.
 */
export async function fetchAreaFires({
  from,
  to,
  source,
  bbox = AOI_BBOX,
  fresh = false,
}: AreaFiresRequest): Promise<FirePoint[]> {
  // El producto se resuelve por fecha salvo que el llamador imponga uno.
  const resolved = source ?? selectFirmsSource(from)
  const bboxKey = `${bbox.west},${bbox.south},${bbox.east},${bbox.north}`
  const cacheKey = `${resolved}|${bboxKey}|${from}|${to}`

  if (!fresh) {
    const cached = areaCache.get(cacheKey)
    if (cached) return cached
    const inFlight = areaInFlight.get(cacheKey)
    if (inFlight) return inFlight
  }

  const job = (async () => {
    const points: FirePoint[] = []
    const seen = new Set<string>()

    for (const chunk of chunkRange(from, to)) {
      const span = Math.min(MAX_DAYS_PER_QUERY, daysBetween(chunk.from, chunk.to))
      const url = `${FIRMS_AREA_API}/${FIRMS_MAP_KEY}/${resolved}/${bboxKey}/${span}/${chunk.from}`
      const response = await fetch(url)

      if (!response.ok) {
        const body = (await response.text().catch(() => '')).trim()
        throw new FirmsApiError(body || `HTTP ${response.status}`)
      }

      const text = await response.text()
      for (const point of parseAreaCsv(text)) {
        const id = `${point.lat},${point.lon},${point.date},${point.time},${point.satellite}`
        if (seen.has(id)) continue
        seen.add(id)
        points.push(point)
      }
    }

    points.sort((a, b) => `${a.date}${a.time}`.localeCompare(`${b.date}${b.time}`))
    areaCache.set(cacheKey, points)
    return points
  })()

  areaInFlight.set(cacheKey, job)
  try {
    return await job
  } finally {
    areaInFlight.delete(cacheKey)
  }
}
