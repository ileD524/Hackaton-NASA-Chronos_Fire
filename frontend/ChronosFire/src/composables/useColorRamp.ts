import { ref } from 'vue'

export type RampId = 'sequential' | 'anomaly'

const rampId = ref<RampId>('sequential')

/**
 * Escala secuencial de volumen: cinco quintiles de la selección.
 * Sigue la paleta de marca: azul profundo → azul eléctrico → celeste →
 * lima neón → rojo. La leyenda muestra los cortes numéricos de cada quintil.
 */
export const SEQUENTIAL_STOPS = ['#0A2A66', '#0960E1', '#2E96F5', '#EAFE07', '#E03000']

/**
 * Escala divergente de anomalía con cortes fijos en ±15% y ±50%.
 * `bg` es el fondo de la celda; `ink` es el mismo color de la banda aclarado
 * para usarse como texto sobre el panel azul profundo.
 */
export interface AnomalyBand {
  id: 'low-extreme' | 'low' | 'neutral' | 'high' | 'high-extreme'
  bg: string
  ink: string
  mark: string
  legend: string
  extreme: boolean
  upTo: number
}

export const ANOMALY_BANDS: AnomalyBand[] = [
  { id: 'low-extreme', bg: '#1F7A57', ink: '#6EE7A8', mark: '▼', legend: '≤ −50%', extreme: true, upTo: -0.5 },
  { id: 'low', bg: '#8FD9B6', ink: '#A7E8C8', mark: '', legend: '−50 a −15%', extreme: false, upTo: -0.15 },
  { id: 'neutral', bg: '#0B2A6B', ink: '#FFFFFF', mark: '', legend: '±15%', extreme: false, upTo: 0.15 },
  { id: 'high', bg: '#F08A00', ink: '#FFB24D', mark: '', legend: '+15 a +50%', extreme: false, upTo: 0.5 },
  { id: 'high-extreme', bg: '#E03000', ink: '#FF6A3D', mark: '▲', legend: '≥ +50%', extreme: true, upTo: Infinity },
]

/** Banda de anomalía para una diferencia relativa (0,5 = +50%). */
export function anomalyBand(delta: number | null): AnomalyBand {
  if (delta === null) return ANOMALY_BANDS[2]!
  return ANOMALY_BANDS.find((band) => delta <= band.upTo) ?? ANOMALY_BANDS[4]!
}

export function luminance(hex: string) {
  const rgb = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255).map(n => n <= .04045 ? n / 12.92 : ((n + .055) / 1.055) ** 2.4)
  return .2126 * rgb[0]! + .7152 * rgb[1]! + .0722 * rgb[2]!
}

export function contrast(a: string, b: string) { const x = luminance(a), y = luminance(b); return (Math.max(x, y) + .05) / (Math.min(x, y) + .05) }

/** Texto claro u oscuro según cuál tenga más contraste sobre el fondo dado. */
export function textOn(bg: string) { return contrast(bg, '#07173F') > contrast(bg, '#FFFFFF') ? '#07173F' : '#FFFFFF' }

export function useColorRamp() {
  function colorAt(bucket: number) { return SEQUENTIAL_STOPS[Math.min(4, Math.max(0, Math.round(bucket)))]! }
  return { rampId, colorAt, textOn, label: (id: RampId) => id === 'sequential' ? 'Volumen observado' : 'Diferencia frente a la mediana' }
}
