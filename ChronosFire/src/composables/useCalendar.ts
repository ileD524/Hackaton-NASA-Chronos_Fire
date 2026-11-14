import { computed, type ComputedRef } from 'vue'
import {
  sampleCalendar,
  yearStart,
  yearEnd,
  type MonthCell,
} from '@/data/sampleCalendar'

export const MONTH_LABELS = [
  'Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun',
  'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic',
] as const

const monthNames = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
] as const

/** Rango de valores usado para normalizar cada celda a [0,1]. */
function domain(matrix: Record<number, MonthCell[]>): [number, number] {
  let min = Infinity
  let max = 0
  for (const monthCells of Object.values(matrix)) {
    for (const cell of monthCells) {
      if (cell.value > 0) {
        if (cell.value < min) min = cell.value
        if (cell.value > max) max = cell.value
      }
    }
  }
  return [min === Infinity ? 0 : min, max]
}

/** Mediana histórica por mes (sobre años con dato), para detectar anomalías. */
function monthlyMedians(matrix: Record<number, MonthCell[]>): number[] {
  const medians: number[] = []
  for (let m = 0; m < 12; m++) {
    const series: number[] = []
    for (const monthCells of Object.values(matrix)) {
      const v = monthCells[m]?.value ?? 0
      if (v > 0) series.push(v)
    }
    series.sort((a, b) => a - b)
    const n = series.length
    medians[m] = n === 0 ? 0 : n % 2 ? series[(n - 1) / 2]! : (series[n / 2 - 1]! + series[n / 2]!) / 2
  }
  return medians
}

export function useCalendar() {
  const years: ComputedRef<number[]> = computed(() => {
    const list: number[] = []
    for (let y = yearStart; y <= yearEnd; y++) list.push(y)
    return list
  })

  const medians = monthlyMedians(sampleCalendar)
  const [minVal, maxVal] = domain(sampleCalendar)

  /** Normaliza un valor a [0,1] dentro del dominio global. */
  function norm(value: number): number {
    if (maxVal <= minVal) return 0
    return (value - minVal) / (maxVal - minVal)
  }

  /** Dato de una celda con todos sus derivados (normalizado, anomalía, etiqueta). */
  function cellMeta(year: number, month: number): {
    cell: MonthCell | undefined
    value: number
    norm: number
    vsMedian: number // ratio respecto a la mediana del mes (-1..∞)
    aboveMedian: boolean
    currentMonth: boolean
    label: string
  } {
    const cell = sampleCalendar[year]?.[month]
    const value = cell?.value ?? 0
    const median = medians[month]!
    const vsMedian = median > 0 ? value / median - 1 : value > 0 ? 1 : 0
    const currentMonth = year === yearEnd && month === 10 // Nov 2026
    const short = value >= 1000 ? `${Math.round(value / 100) / 10}k` : String(value)
    return {
      cell,
      value,
      norm: norm(value),
      vsMedian,
      aboveMedian: vsMedian > 0.05,
      currentMonth,
      label: value === 0 ? 'Sin datos' : `${year} ${monthNames[month]} · ${format(value)} hotspots`,
    }
  }

  function monthName(month: number): string {
    return monthNames[month] ?? ''
  }

  function annualTotal(year: number): number {
    return (sampleCalendar[year] ?? []).reduce((acc, c) => acc + c.value, 0)
  }

  /** Último año con dato completo (cuenta mes a mes no nulos). */
  const maxCompleteYear = computed(() => {
    for (let y = yearEnd; y >= yearStart; y--) {
      const cells = sampleCalendar[y] ?? []
      const hasFuture = cells.some((c, m) => c.value === 0 && yearEnd === y && m > 10)
      if (!hasFuture && cells.some((c) => c.value > 0)) return y
    }
    return yearEnd
  })

  return {
    years,
    medians,
    minVal,
    maxVal,
    norm,
    cellMeta,
    monthName,
    annualTotal,
    maxCompleteYear,
    monthShort: (m: number) => MONTH_LABELS[m] ?? '',
  }
}

export function format(value: number): string {
  return new Intl.NumberFormat('es').format(value)
}