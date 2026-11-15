import { computed } from 'vue'
import { useChronos } from './useChronos'
export const MONTH_LABELS = ['Ene','Feb','Mar','Abr','May','Jun','Jul','Ago','Sep','Oct','Nov','Dic']
const NAMES = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre']
export const format = (n: number) => new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 }).format(n)
export function useCalendar() {
  const { filters, months, metricMode, metricValue, metricLabel } = useChronos()
  const years = computed(() => Array.from({ length: Number(filters.value.to.slice(0,4)) - Number(filters.value.from.slice(0,4)) + 1 }, (_,i) => Number(filters.value.from.slice(0,4)) + i))
  const values = computed(() => months.value
    .map((m) => metricValue(m))
    .filter((v): v is number => v !== null))
  const maxVal = computed(() => Math.max(1, ...values.value))
  const minVal = computed(() => 0)
  /** Cortes de quintil (20/40/60/80) sobre los meses con valor de la metrica activa. */
  const thresholds = computed(() => {
    const sorted = [...values.value].sort((a,b) => a - b)
    if (!sorted.length) return [0,0,0,0]
    const at = (p: number) => sorted[Math.min(sorted.length - 1, Math.floor(p * (sorted.length - 1)))]!
    return [at(.2), at(.4), at(.6), at(.8)]
  })
  function bucketOf(value: number | null) {
    if (value === null) return 0
    const cuts = thresholds.value
    let bucket = 0
    while (bucket < 4 && value > cuts[bucket]!) bucket++
    return bucket
  }
  function cellMeta(year: number, month: number) {
    const cell = months.value.find(m => m.year === year && m.month === month)
    const value = metricValue(cell)
    const anomaly = metricMode.value === 'cells' ? cell?.anomaly ?? null : null
    const vsMedian = value !== null && anomaly !== null ? anomaly - 1 : null
    const deltaText = vsMedian === null ? '' : `, ${vsMedian > 0 ? '+' : ''}${Math.round(vsMedian * 100)}% frente a la mediana de otros años completos`
    return { cell, value, bucket: bucketOf(value), norm: value === null ? 0 : Math.log1p(value)/Math.log1p(maxVal.value), vsMedian, aboveMedian: vsMedian !== null && vsMedian > 0,
      label: `${NAMES[month]} ${year}: ${value === null ? 'sin datos para esta serie' : `${format(value)} ${metricLabel.value}${cell?.partial ? ', período parcial' : ''}${deltaText}`}` }
  }
  return { years, maxVal, minVal, thresholds, cellMeta, monthName: (m: number) => NAMES[m] ?? '', monthShort: (m: number) => MONTH_LABELS[m] ?? '', annualTotal: (y: number) => months.value.filter(m => m.year === y).reduce((s,m) => s + (metricValue(m) ?? 0), 0) }
}
