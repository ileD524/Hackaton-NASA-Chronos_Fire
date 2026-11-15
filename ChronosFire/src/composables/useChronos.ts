import { computed, ref } from 'vue'
import type { CalendarData, Filters, Meta, MonthCell } from '@/data/types'
import { SERIES } from '@/data/types'
const filters = ref<Filters>({ from: '2012-01-01', to: '2025-12-31', metric: 'modis', bbox: null })
const calendar = ref<CalendarData | null>(null)
const metadata = ref<Meta | null>(null)
const loading = ref(false)
const error = ref('')
const selectedMonth = ref<{ year: number; month: number } | null>(null)
const calendarOpen = ref(false)
let controller: AbortController | undefined
export function params(f: Filters): URLSearchParams {
  const p = new URLSearchParams({ from: f.from, to: f.to, metric: f.metric })
  if (f.bbox) p.set('bbox', f.bbox.join(','))
  return p
}
export async function fetchJSON<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(url, { signal }); const result = await response.json()
  if (!response.ok) throw new Error(result.error || 'No se pudo cargar la consulta.')
  return result as T
}
async function load(next: Filters = filters.value) {
  controller?.abort(); const current = new AbortController(); controller = current
  loading.value = true; error.value = ''
  try {
    const result = await fetchJSON<CalendarData>(`/api/calendar?${params(next)}`, current.signal)
    if (current.signal.aborted) return
    filters.value = { ...next }; calendar.value = result
    if (!selectedMonth.value || !result.months.some(m => m.year === selectedMonth.value?.year && m.month === selectedMonth.value?.month && m.value !== null)) {
      const peak = result.months.filter(m => m.value !== null).sort((a,b) => (b.value ?? 0) - (a.value ?? 0))[0]
      selectedMonth.value = peak ? { year: peak.year, month: peak.month } : null
    }
  } catch (e) {
    if (!(e instanceof DOMException && e.name === 'AbortError')) error.value = e instanceof Error ? e.message : 'Error al consultar los datos.'
  } finally { if (controller === current) loading.value = false }
}
const months = computed(() => calendar.value?.months ?? [])
const selected = computed(() => months.value.find(m => m.year === selectedMonth.value?.year && m.month === selectedMonth.value?.month))
const valueField = computed(() => ({ modis: 'active_cells_modis', viirs: 'active_cells_viirs', union: 'active_cells_union', calibrated: 'modis_equivalent_cells' } as const)[filters.value.metric])
const selectedDaily = computed(() => calendar.value?.daily.filter(d => d.date_utc.startsWith(`${selectedMonth.value?.year}-${String((selectedMonth.value?.month ?? 0) + 1).padStart(2,'0')}`)) ?? [])
export function useChronos() {
  return {
    filters, calendar, metadata, loading, error, selectedMonth, calendarOpen, months, selected, selectedDaily, valueField,
    seriesLabel: computed(() => SERIES[filters.value.metric]),
    areaLabel: computed(() => filters.value.bbox ? 'Área personalizada · recorte rectangular' : 'Extensión completa de DSv2 · Argentina y entorno'), load,
    async initialize() { try { metadata.value = await fetchJSON<Meta>('/api/meta'); await load() } catch (e) { error.value = e instanceof Error ? e.message : 'Servidor local no disponible.' } },
    exportURL: computed(() => `/api/export?${params(filters.value)}`),
  }
}
export function median(values: number[]): number | null {
  if (!values.length) return null
  const sorted=[...values].sort((a,b)=>a-b); const mid=Math.floor(sorted.length/2)
  return sorted.length%2 ? sorted[mid]! : (sorted[mid-1]!+sorted[mid]!)/2
}
export function monthMedian(months: MonthCell[],month: number,exceptYear?: number) {
  return median(months.filter(m=>m.month===month&&!m.partial&&m.value!==null&&m.year!==exceptYear).map(m=>m.value!))
}
