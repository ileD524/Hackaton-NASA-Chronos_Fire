import { computed } from 'vue'
import { useChronos, monthMedian } from './useChronos'
export const MONTH_LABELS = ['Ene','Feb','Mar','Abr','May','Jun','Jul','Ago','Sep','Oct','Nov','Dic']
const NAMES = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre']
export const format = (n: number) => new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 }).format(n)
export function useCalendar() {
  const { filters, months } = useChronos()
  const years = computed(() => Array.from({ length: Number(filters.value.to.slice(0,4)) - Number(filters.value.from.slice(0,4)) + 1 }, (_,i) => Number(filters.value.from.slice(0,4)) + i))
  const maxVal = computed(() => Math.max(1,...months.value.map(m => m.value ?? 0)))
  const minVal = computed(() => 0)
  function cellMeta(year: number, month: number) {
    const cell = months.value.find(m => m.year === year && m.month === month)
    const value = cell?.value ?? null
    const med = monthMedian(months.value, month, year)
    const vsMedian = value !== null && med !== null && med > 0 && !cell?.partial ? value / med - 1 : null
    return { cell, value, norm: value === null ? 0 : Math.log1p(value)/Math.log1p(maxVal.value), vsMedian, aboveMedian: vsMedian !== null && vsMedian > 0,
      label: `${NAMES[month]} ${year}: ${value === null ? 'sin datos para esta serie' : `${format(value)} celda-días${cell?.partial ? ', período parcial' : ''}`}` }
  }
  return { years, maxVal, minVal, cellMeta, monthName: (m: number) => NAMES[m] ?? '', monthShort: (m: number) => MONTH_LABELS[m] ?? '', annualTotal: (y: number) => months.value.filter(m => m.year === y).reduce((s,m) => s + (m.value ?? 0), 0) }
}
