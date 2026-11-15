export type Metric = 'all' | 'MODIS' | 'VIIRS_SNPP' | 'VIIRS_NOAA20' | 'VIIRS_NOAA21'
export type Bbox = [number, number, number, number]
export interface Filters { from: string; to: string; metric: Metric; bbox: Bbox | null }

/**
 * Vista de UI del endpoint /api/calendar/monthly.
 * `value` es la cantidad de celdas con actividad (fire_cells) del mes.
 */
export interface MonthCell {
  year: number
  month: number
  value: number | null
  available_days: number
  selected_days: number
  calendar_days: number
  partial: boolean
  modis_detections: number
  viirs_detections: number
  paired_days: number
  paired_modis: number
  paired_viirs: number
  paired_calibrated: number
  fire_cells: number
  fire_days: number
  n_detections: number
  data_status: string
  baseline_fire_cells: number | null
  anomaly: number | null
  reference_years: number
  confidence: string
}

/**
 * Fila diaria agregada en el cliente desde /api/calendar/daily.
 * El backend devuelve celdas; el frontend suma por fecha para el gráfico.
 */
export interface DailyRow {
  date_utc: string
  active_cells_modis: number
  active_cells_viirs: number | null
  active_cells_union: number
  active_cells_selected: number
  modis_equivalent_cells: number | null
  modis_pi90_low: number | null
  modis_pi90_high: number | null
  eligible_modis?: boolean
  viirs_input_usable?: boolean
  fire_day: boolean
  n_detections: number
  sensors: string[]
  frp_max_mw: number | null
  frp_density_max_wm2: number | null
  data_status: string
}

export interface CalendarData extends Filters {
  months: MonthCell[]
  daily: DailyRow[]
  coverage_known: boolean
}

export interface MapPoint {
  gridId: string
  cellLat: number
  cellLon: number
  fireCells: number
  fireDays: number
  nDetections: number
}

export interface MapData {
  points: MapPoint[]
  total_groups: number
  shown_groups: number
  total_detections: number
  shown_detections: number
  aggregation_km: number
}

export interface Meta {
  first_date: string
  last_date: string
  viirs_first: string
  rows: number
  selected_rows: number
  calibration: unknown | null
  bounds: Bbox
}

export interface Alert {
  cellLat: number
  cellLon: number
  lastDetection: string | null
  nDetections: number
  frpMaxMw: number | null
  frpDensityMaxWm2: number | null
  sensors: string[]
  confidenceMax: string
  aoiAnomaly: number | null
  level: 'info' | 'watch' | 'warning'
  industrialMasked: boolean
}

export interface AlertResponse {
  generatedAt: string
  asOf: string
  latestAvailable: string | null
  days: number
  aoi: Bbox
  count: number
  provisional: boolean
  alerts: Alert[]
}

export interface ValidationEvent {
  aoiId: string
  name: string
  isoyear: number
  week: number
  observed: number
  baseline: number | null
  nRefYears: number
  anomaly: number | null
  confidence: string
  verdict: 'pass' | 'fail' | 'insufficient'
}

export const SERIES: Record<Metric, string> = {
  all: 'Todos los sensores · 0,05°',
  MODIS: 'MODIS observado',
  VIIRS_SNPP: 'VIIRS SNPP',
  VIIRS_NOAA20: 'VIIRS NOAA-20',
  VIIRS_NOAA21: 'VIIRS NOAA-21',
}
