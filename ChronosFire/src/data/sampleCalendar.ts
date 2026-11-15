/**
 * Calendario armonizado de hotspots · serie de ejemplo.
 *
 * Los valores replican la magnitud del mockup de referencia (MODIS + VIIRS
 * armonizados sobre un área de interés). En producción este dato provendría de
 * la consulta agregada sobre `calendar_weekly` / `grid_daily` (ver bd ejemplo.txt),
 * y no de un archivo estático.
 */

export interface MonthCell {
  /** Hotspots detectados en el mes (post armonización). */
  value: number
}

const MONTHS_PER_YEAR = 12

/**
 * Transforma un vector plano (bloques de 12 = un año) en una matriz año → mes.
 * Los valores >= 0 se mantienen; el resto (0) se interpreta como mes sin datos.
 */
function buildMatrix(values: number[]): Record<number, MonthCell[]> {
  const yearStart = 2017
  const matrix: Record<number, MonthCell[]> = {}
  const monthCount = Math.ceil(values.length / MONTHS_PER_YEAR)

  for (let y = 0; y < monthCount; y++) {
    const year = yearStart + y
    const cells: MonthCell[] = []
    for (let m = 0; m < MONTHS_PER_YEAR; m++) {
      const idx = y * MONTHS_PER_YEAR + m
      cells.push({ value: values[idx] ?? 0 })
    }
    matrix[year] = cells
  }
  return matrix
}

/**
 * Hotspots por mes · serie 2017–2026 (vector plano por filas; 12 valores por año).
 * Los últimos meses de 2026 aún no transcurrieron y se marcan como 0 (sin dato).
 */
const raw: number[] = [
  // 2017
  266, 228, 285, 494, 522, 1064, 1596, 3610, 7657, 5016, 2394, 684,
  // 2018
  146, 186, 239, 266, 410, 720, 1180, 2540, 4890, 3260, 1980, 520,
  // 2019
  208, 176, 322, 380, 640, 940, 1430, 3220, 6190, 4140, 2620, 760,
  // 2020
  320, 260, 410, 560, 980, 1620, 3120, 8840, 16492, 9320, 4080, 1120,
  // 2021
  240, 214, 360, 430, 720, 1210, 2240, 5480, 9850, 6230, 3410, 890,
  // 2022
  190, 210, 280, 470, 880, 1340, 1980, 4120, 7120, 4720, 2960, 700,
  // 2023
  270, 232, 390, 640, 1140, 1980, 3660, 7420, 12140, 7280, 3820, 1020,
  // 2024
  360, 310, 470, 720, 1210, 2180, 3940, 7260, 10490, 8120, 4890, 1160,
  // 2025
  220, 206, 300, 520, 860, 1360, 2080, 4860, 8260, 5520, 3520, 940,
  // 2026
  262, 244, 352, 560, 910, 1560, 2790, 6010, 9640, 7140, 3488, 0,
]

export const sampleCalendar = buildMatrix(raw)

export const yearStart = 2017
export const yearEnd = 2026

/** Mes y año con el pico absoluto de la serie (Sep 2020 · 16.492). */
export const peakMonth = { year: 2020, month: 8, value: 16492 }

/** Año con mayor volumen acumulado de la serie (2024). */
export const peakYear = 2024

/** Total de hotspots de la serie 2017–2026. */
export const totalHotspots = 337_058

/** Mediana histórica de noviembre (2017–2025) y valor actual (Nov 2026). */
export const novemberMedian = 2325
export const novemberCurrent = 3488