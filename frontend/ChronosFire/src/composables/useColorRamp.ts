import { ref } from 'vue'

export type RampId = 'sequential' | 'anomaly'

const rampId = ref<RampId>('sequential')

/**
 * Escala secuencial de volumen: cinco tramos de Viridis.
 * Viridis es perceptualmente uniforme y apta para daltonismo; se reserva la
 * paleta de marca para la interfaz. La leyenda muestra los cortes numéricos.
 */
export const SEQUENTIAL_STOPS = ['#440154', '#414487', '#2A788E', '#22A884', '#FDE725']
export const SEQUENTIAL_NAME = 'Viridis'

/**
 * Escala divergente de anomalía con cortes fijos en ±15% y ±50%.
 * Usa PuOr (púrpura ↔ azul profundo ↔ naranja): los dos extremos no dependen
 * solo del color porque además llevan ▼/▲ y etiquetas numéricas.
 * `bg` es el fondo de la celda; `ink` es una variante clara de la banda para
 * usarse como texto sobre el panel azul profundo (8,5:1 a 17:1 de contraste).
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
  { id: 'low-extreme', bg: '#542788', ink: '#D8CCF0', mark: '▼', legend: '≤ −50%', extreme: true, upTo: -0.5 },
  { id: 'low', bg: '#B2ABD2', ink: '#C9B8E8', mark: '', legend: '−50 a −15%', extreme: false, upTo: -0.15 },
  { id: 'neutral', bg: '#0B2A6B', ink: '#FFFFFF', mark: '', legend: '±15%', extreme: false, upTo: 0.15 },
  { id: 'high', bg: '#FDB863', ink: '#FFD1A1', mark: '', legend: '+15 a +50%', extreme: false, upTo: 0.5 },
  { id: 'high-extreme', bg: '#B35806', ink: '#FF9E64', mark: '▲', legend: '≥ +50%', extreme: true, upTo: Infinity },
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

/** Color del quintil `bucket` (0–4) en la escala secuencial. */
export function colorAt(bucket: number) { return SEQUENTIAL_STOPS[Math.min(4, Math.max(0, Math.round(bucket)))]! }

export function useColorRamp() {
  return { rampId, colorAt, textOn, label: (id: RampId) => id === 'sequential' ? 'Volumen observado' : 'Diferencia frente a la mediana' }
}
