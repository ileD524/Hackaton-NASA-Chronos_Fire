import { computed, ref, watch } from 'vue'
import type { CalendarData, DailyRow, Filters, Meta, MetricMode, MonthCell } from '@/data/types'
import { SERIES } from '@/data/types'
import { apiUrl } from '@/api'

interface ApiMonthly {
  year: number
  month: number
  fireCells: number
  fireDays: number
  nDetections: number
  detectionsBySensor: Record<string, number>
  hotspots: number
  hotspotsBySensor: Record<string, number>
  partial: boolean
  dataStatus: string
  baselineFireCells: number | null
  anomaly: number | null
  referenceYears: number
  confidence: string
}

interface ApiDaily {
  date: string
  gridId: string
  cellLat: number
  cellLon: number
  fireDay: boolean
  nDetections: number
  sensors: string[]
  hotspots: number
  hotspotsBySensor: Record<string, number>
  frpMaxMw: number | null
  frpDensityMaxWm2: number | null
  source: string | null
  dataStatus: string
}

interface ApiSensor {
  sensor?: string
  source?: string
  rows?: number
  from?: string | null
  to?: string | null
}

interface ApiMeta {
  gridResolutionDeg?: number
  sensors?: ApiSensor[]
  dateRange?: { from?: string; to?: string }
  counts?: { detections?: number; coverageDaily?: number }
  provisional?: { from?: string; to?: string; reason?: string } | null
}

const DELTA_BBOX: Filters['bbox'] = [-61, -34, -59.5, -32.5]
// La serie histórica del calendario comienza en 2003. Los meses sin cobertura
// permanecen visibles como "sin datos" en vez de ocultar años del período.
export const TIMELINE_START = '2003-01-01'
const filters = ref<Filters>({
  from: '',
  to: '',
  metric: 'all',
  bbox: DELTA_BBOX,
})
const metricMode = ref<MetricMode>('cells')
const calendar = ref<CalendarData | null>(null)
const metadata = ref<Meta | null>(null)
const loading = ref(false)
const error = ref('')
const selectedMonth = ref<{ year: number; month: number } | null>(null)
const calendarOpen = ref(false)
const csvNotice = ref('')
const csvError = ref('')
const csvChecking = ref(false)
let controller: AbortController | undefined
let dailyController: AbortController | undefined
let lastDailyKey = ''

const MONTH_DAYS = (year: number, month: number) => new Date(Date.UTC(year, month + 1, 0)).getUTCDate()
const iso = (date: Date) => date.toISOString().slice(0, 10)

export function apiSensor(metric: Filters['metric']): string {
  return metric === 'all' ? 'ALL' : metric
}

export function params(f: Filters): URLSearchParams {
  const p = new URLSearchParams({ from: f.from, to: f.to, sensor: apiSensor(f.metric) })
  if (f.bbox) p.set('bbox', f.bbox.join(','))
  return p
}

/**
 * Filtros del CSV de hotspots. Usa el mismo bbox, rango y sensor que la vista
 * activa y agrega la definicion armonizada: vegetacion efectiva, confianza
 * nominal/alta y, por la logica de la vista, sin focos industriales.
 */
export function hotspotsParams(f: Filters): URLSearchParams {
  const p = params(f)
  p.set('vegetation', 'true')
  p.append('confidence', 'nominal')
  p.append('confidence', 'high')
  p.set('limit', '50000')
  return p
}

export async function fetchJSON<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(apiUrl(url), { signal })
  const result = await response.json()
  if (!response.ok) throw new Error(result?.error || result?.message || 'No se pudo cargar la consulta.')
  return result as T
}

function monthCell(row: ApiMonthly): MonthCell {
  const calendarDays = MONTH_DAYS(row.year, row.month)
  const bySensor = row.detectionsBySensor ?? {}
  const viirsDetections = Object.entries(bySensor)
    .filter(([sensor]) => sensor.startsWith('VIIRS'))
    .reduce((sum, [, value]) => sum + value, 0)
  const observed = row.dataStatus === 'observed'
  return {
    year: row.year,
    month: row.month,
    value: observed ? row.fireCells : null,
    available_days: observed ? calendarDays : 0,
    selected_days: observed ? calendarDays : 0,
    calendar_days: calendarDays,
    partial: row.partial,
    modis_detections: bySensor.MODIS ?? 0,
    viirs_detections: viirsDetections,
    paired_days: 0,
    paired_modis: 0,
    paired_viirs: 0,
    paired_calibrated: 0,
    fire_cells: row.fireCells,
    fire_days: row.fireDays,
    n_detections: row.nDetections,
    hotspots: row.hotspots ?? 0,
    hotspots_by_sensor: row.hotspotsBySensor ?? {},
    data_status: row.dataStatus,
    baseline_fire_cells: row.baselineFireCells,
    anomaly: row.anomaly,
    reference_years: row.referenceYears,
    confidence: row.confidence,
  }
}

function aggregateDaily(rows: ApiDaily[], metric: Filters['metric']): DailyRow[] {
  const byDate = new Map<string, DailyRow & { sensorSet: Set<string> }>()
  for (const row of rows) {
    const key = row.date
    let day = byDate.get(key)
    if (!day) {
      day = {
        date_utc: key,
        active_cells_modis: 0,
        active_cells_viirs: null,
        active_cells_union: 0,
        active_cells_selected: 0,
        modis_equivalent_cells: null,
        modis_pi90_low: null,
        modis_pi90_high: null,
        fire_day: false,
        n_detections: 0,
        sensors: [],
        hotspots: 0,
        hotspots_by_sensor: {},
        frp_max_mw: null,
        frp_density_max_wm2: null,
        data_status: 'observed',
        sensorSet: new Set<string>(),
      }
      byDate.set(key, day)
    }
    if (row.fireDay) {
      day.active_cells_union += 1
      if (row.sensors?.includes('MODIS')) day.active_cells_modis += 1
      if (row.sensors?.some((sensor) => sensor.startsWith('VIIRS'))) {
        day.active_cells_viirs = (day.active_cells_viirs ?? 0) + 1
      }
      if (metric === 'all' || row.sensors?.includes(metric)) day.active_cells_selected += 1
    }
    day.fire_day = day.fire_day || row.fireDay
    day.n_detections += row.nDetections
    day.hotspots += row.hotspots ?? 0
    for (const [sensor, value] of Object.entries(row.hotspotsBySensor ?? {})) {
      day.hotspots_by_sensor[sensor] = (day.hotspots_by_sensor[sensor] ?? 0) + value
    }
    day.frp_max_mw = Math.max(day.frp_max_mw ?? 0, row.frpMaxMw ?? 0) || null
    day.frp_density_max_wm2 = Math.max(day.frp_density_max_wm2 ?? 0, row.frpDensityMaxWm2 ?? 0) || null
    for (const sensor of row.sensors ?? []) day.sensorSet.add(sensor)
  }
  return [...byDate.values()].map((day) => {
    const { sensorSet, ...value } = day
    return { ...value, sensors: [...sensorSet].sort() }
  })
}

/**
 * Valor de la metrica activa. Devuelve null cuando el mes no esta observado
 * para no presentar "0 hotspots" como si hubiera ausencia de fuego.
 */
function metricValue(cell: MonthCell | null | undefined): number | null {
  if (!cell || cell.data_status !== 'observed') return null
  return metricMode.value === 'cells' ? cell.value : cell.hotspots
}

async function loadDaily() {
  if (!selectedMonth.value || !calendar.value) return
  const { year, month } = selectedMonth.value
  const start = new Date(Date.UTC(year, month, 1))
  const end = new Date(Date.UTC(year, month + 1, 0))
  const from = iso(start) < filters.value.from ? filters.value.from : iso(start)
  const to = iso(end) > filters.value.to ? filters.value.to : iso(end)
  const key = `${from}|${to}|${filters.value.metric}|${filters.value.bbox?.join(',') ?? ''}`
  if (key === lastDailyKey) return
  lastDailyKey = key

  dailyController?.abort()
  const current = new AbortController()
  dailyController = current
  try {
    const query = params({ ...filters.value, from, to })
    const rows = await fetchJSON<ApiDaily[]>(`/api/calendar/daily?${query}&limit=50000`, current.signal)
    if (current.signal.aborted || !calendar.value) return
    calendar.value.daily = aggregateDaily(rows, filters.value.metric)
  } catch (e) {
    lastDailyKey = ''
    if (!(e instanceof DOMException && e.name === 'AbortError')) {
      error.value = e instanceof Error ? e.message : 'Error al cargar el detalle diario.'
    }
  }
}

async function load(next: Filters = filters.value) {
  if (!next.from || !next.to) {
    if (metadata.value) {
      error.value = 'El servidor no informó el rango de fechas.'
      return
    }
    await initialize()
    return
  }
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = ''
  // Actualizar el contexto del AOI de inmediato; los datos llegan despues.
  filters.value = { ...next }
  try {
    const rows = await fetchJSON<ApiMonthly[]>(`/api/calendar/monthly?${params(next)}`, current.signal)
    if (current.signal.aborted) return
    const months = rows.map(monthCell)
    calendar.value = { ...next, months, daily: [], coverage_known: metadata.value?.coverage_known ?? false }
    const currentSelection = selectedMonth.value
    const keep = currentSelection && months.some(
      (m) => m.year === currentSelection.year && m.month === currentSelection.month && metricValue(m) !== null,
    )
    if (!keep) {
      const peak = months.filter((m) => metricValue(m) !== null)
        .sort((a, b) => (metricValue(b) ?? 0) - (metricValue(a) ?? 0))[0]
      selectedMonth.value = peak ? { year: peak.year, month: peak.month } : null
    }
    await loadDaily()
  } catch (e) {
    if (!(e instanceof DOMException && e.name === 'AbortError')) {
      error.value = e instanceof Error ? e.message : 'Error al consultar los datos.'
    }
  } finally {
    if (controller === current) loading.value = false
  }
}

function formatRows(value: string | null) {
  const rows = Number(value)
  return Number.isFinite(rows) ? new Intl.NumberFormat('es-AR').format(rows) : '—'
}

async function checkCsv(url: string) {
  if (csvChecking.value) return
  csvNotice.value = ''
  csvError.value = ''
  csvChecking.value = true
  try {
    const response = await fetch(url, { method: 'HEAD' })
    if (!response.ok) throw new Error(`No se pudo verificar el CSV (HTTP ${response.status}).`)
    const returned = formatRows(response.headers.get('X-Returned-Rows'))
    const truncated = response.headers.get('X-Truncated') === 'true'
    csvNotice.value = truncated
      ? `Descarga limitada a ${returned === '—' ? '50.000' : returned} filas. Acotá el área o el rango para obtener el resto.`
      : `Descarga completa${returned === '—' ? '' : `: ${returned} filas`}.`
  } catch (e) {
    csvError.value = e instanceof Error ? e.message : 'No se pudo verificar el CSV.'
  } finally {
    csvChecking.value = false
  }
}

const months = computed(() => calendar.value?.months ?? [])
const selected = computed(() =>
  months.value.find((m) => m.year === selectedMonth.value?.year && m.month === selectedMonth.value?.month),
)
const valueField = computed<'active_cells_selected' | 'hotspots'>(() =>
  metricMode.value === 'cells' ? 'active_cells_selected' : 'hotspots')
const metricLabel = computed(() =>
  metricMode.value === 'cells' ? 'celdas con actividad' : 'hotspots detectados')
const metricUnit = computed(() =>
  metricMode.value === 'cells' ? 'celdas-mes con actividad' : 'hotspots detectados en el rango')
const selectedDaily = computed(() => calendar.value?.daily ?? [])

watch(selectedMonth, () => { void loadDaily() }, { deep: true })

async function initialize() {
  try {
    const meta = await fetchJSON<ApiMeta>('/api/meta')
    const sensors = (meta.sensors ?? []).map((sensor) => ({
      sensor: sensor.sensor ?? '',
      source: sensor.source ?? '',
      rows: sensor.rows ?? 0,
      from: sensor.from ?? null,
      to: sensor.to ?? null,
    }))
    const viirsFirst = sensors
      .filter((sensor) => sensor.sensor.startsWith('VIIRS') && sensor.from)
      .map((sensor) => sensor.from as string)
      .sort()[0] ?? ''
    const from = TIMELINE_START
    const to = meta.dateRange?.to ?? ''
    filters.value = { ...filters.value, from, to }
    metadata.value = {
      first_date: from,
      last_date: to,
      viirs_first: viirsFirst,
      rows: meta.counts?.detections ?? 0,
      selected_rows: meta.counts?.detections ?? 0,
      calibration: null,
      grid_resolution_deg: meta.gridResolutionDeg ?? 0,
      sensors,
      coverage_known: (meta.counts?.coverageDaily ?? 0) > 0,
      provisional: meta.provisional?.from
        ? {
            from: meta.provisional.from,
            to: meta.provisional.to ?? '',
            reason: meta.provisional.reason ?? '',
          }
        : null,
    }
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Servidor no disponible.'
  }
}

export function useChronos() {
  return {
    filters,
    calendar,
    metadata,
    loading,
    error,
    selectedMonth,
    calendarOpen,
    months,
    selected,
    selectedDaily,
    valueField,
    metricMode,
    metricValue,
    metricLabel,
    metricUnit,
    seriesLabel: computed(() => SERIES[filters.value.metric]),
    areaLabel: computed(() => {
      const bbox = filters.value.bbox
      if (!bbox) return 'Extensión completa · Argentina'
      const [w, s, e, n] = bbox
      if (w === -61 && s === -34 && e === -59.5 && n === -32.5) return 'Delta del Paraná'
      return 'Área personalizada · recorte rectangular'
    }),
    load,
    initialize,
    csvNotice,
    csvError,
    csvChecking,
    checkCsv,
    exportURL: computed(() => apiUrl(`/api/calendar/export.csv?${params(filters.value)}`)),
    hotspotsExportURL: computed(() => apiUrl(`/api/detections/export.csv?${hotspotsParams(filters.value)}`)),
  }
}


