export type Metric = 'modis' | 'viirs' | 'union' | 'calibrated'
export type Bbox = [number, number, number, number]
export interface Filters { from: string; to: string; metric: Metric; bbox: Bbox | null }
export interface MonthCell {
  year: number; month: number; value: number | null; available_days: number
  selected_days: number; calendar_days: number; partial: boolean
  modis_detections: number; viirs_detections: number
  paired_days: number; paired_modis: number; paired_viirs: number; paired_calibrated: number
}
export interface DailyRow {
  date_utc: string; active_cells_modis: number; active_cells_viirs: number | null
  active_cells_union: number; modis_equivalent_cells: number | null
  modis_pi90_low: number | null; modis_pi90_high: number | null
  eligible_modis?: boolean; viirs_input_usable?: boolean
}
export interface CalendarData extends Filters { months: MonthCell[]; daily: DailyRow[]; coverage_known: boolean }
export interface MapPoint { lat: number; lon: number; n: number; modis: number; viirs: number; frp_max_mw: number }
export interface MapData { points: MapPoint[]; total_groups: number; shown_groups: number; total_detections: number; shown_detections: number; aggregation_km: number }
export interface Meta { first_date: string; last_date: string; viirs_first: string; rows: number; selected_rows: number; bounds: Bbox }
export const SERIES: Record<Metric, string> = {
  modis: 'MODIS observado', viirs: 'VIIRS observado', union: 'Unión MODIS + VIIRS', calibrated: 'VIIRS calibrado a MODIS',
}
