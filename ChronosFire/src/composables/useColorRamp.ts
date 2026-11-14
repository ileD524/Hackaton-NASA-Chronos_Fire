import { computed, ref, type Ref } from 'vue'

/** Ramps de color accesibles para el calendario de actividad. */
export type RampId = 'anomaly' | 'sequential'

interface ColorStop {
  pos: number
  color: [number, number, number] // rgb 0..255
}

/**
 * Ramp divergente con la paleta NASA: azul→lime→rojo cálido.
 * Pensado para resaltar anomalías respecto a lo normal.
 */
const ANOMALY_STOPS: ColorStop[] = [
  { pos: 0.0, color: [11, 42, 107] }, // #0B2A6B
  { pos: 0.35, color: [46, 150, 245] }, // #2E96F5
  { pos: 0.6, color: [234, 254, 7] }, // #EAFE07
  { pos: 0.78, color: [228, 55, 0] }, // #E43700
  { pos: 1.0, color: [142, 17, 0] }, // #8E1100
]

/**
 * Ramp secuencial "viridis-like", seguro ante protanopia/deuteranopia.
 * Alternativa bi-canal (luminancia + croma) para el challenge.
 */
const SEQUENTIAL_STOPS: ColorStop[] = [
  { pos: 0.0, color: [7, 23, 63] }, // #07173F
  { pos: 0.35, color: [9, 96, 225] }, // #0960E1
  { pos: 0.62, color: [46, 150, 245] }, // #2E96F5
  { pos: 0.85, color: [234, 254, 7] }, // #EAFE07
  { pos: 1.0, color: [255, 255, 255] }, // blanco (máximo)
]

const RAMPS: Record<RampId, ColorStop[]> = {
  anomaly: ANOMALY_STOPS,
  sequential: SEQUENTIAL_STOPS,
}

function clamp01(n: number): number {
  return n < 0 ? 0 : n > 1 ? 1 : n
}

function interp(t: number, stops: ColorStop[]): [number, number, number] {
  if (t <= stops[0]!.pos) return stops[0]!.color
  for (let i = 1; i < stops.length; i++) {
    const a = stops[i - 1]!
    const b = stops[i]!
    if (t <= b.pos) {
      const f = (t - a.pos) / (b.pos - a.pos)
      return [
        Math.round(a.color[0]! + (b.color[0]! - a.color[0]!) * f),
        Math.round(a.color[1]! + (b.color[1]! - a.color[1]!) * f),
        Math.round(a.color[2]! + (b.color[2]! - a.color[2]!) * f),
      ]
    }
  }
  return stops[stops.length - 1]!.color
}

function toHex(c: [number, number, number]): string {
  return (
    '#' +
    c
      .map((n) => n.toString(16).padStart(2, '0'))
      .join('')
      .toUpperCase()
  )
}

/**
 * Devuelve el ramp activo y una función que mapea un valor [0,1] a color hex.
 * También corrige el color de texto (claro/oscuro) según el fondo para garantizar contraste.
 */
export function useColorRamp() {
  const rampId: Ref<RampId> = ref('anomaly')

  const stops = computed(() => RAMPS[rampId.value]!)

  function color(t: number): string {
    return toHex(interp(clamp01(t), stops.value))
  }

  /** Texto legible (blanco u oscuro) sobre un color de celda dado. */
  function textOn(bg: string): string {
    const hex = bg.replace('#', '')
    const r = parseInt(hex.slice(0, 2), 16)
    const g = parseInt(hex.slice(2, 4), 16)
    const b = parseInt(hex.slice(4, 6), 16)
    // Luma relativa (WCAG)
    const lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return lum > 140 ? '#07173f' : '#ffffff'
  }

  function label(id: RampId): string {
    return id === 'anomaly' ? 'Anomalía (divergente)' : 'Secuencial (daltónicos)'
  }

  return { rampId, color, textOn, label, stops: computed(() => stops.value) }
}