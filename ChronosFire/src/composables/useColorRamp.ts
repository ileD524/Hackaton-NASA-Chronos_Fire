import { ref } from 'vue'
export type RampId = 'sequential' | 'anomaly'
const rampId = ref<RampId>('sequential')
export function luminance(hex: string) {
  const rgb = [1,3,5].map(i => parseInt(hex.slice(i,i+2),16)/255).map(n => n <= .04045 ? n/12.92 : ((n+.055)/1.055)**2.4)
  return .2126*rgb[0]! + .7152*rgb[1]! + .0722*rgb[2]!
}
export function contrast(a: string,b: string) { const x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05) }
export function useColorRamp() {
  const shades = ['#07173F','#0042A6','#0960E1','#2E96F5','#FFFFFF']
  function color(t: number) { return shades[Math.min(4, Math.max(0,Math.floor(t*4.999)))]! }
  function textOn(bg: string) { return contrast(bg,'#07173F') > contrast(bg,'#FFFFFF') ? '#07173F' : '#FFFFFF' }
  return { rampId, color, textOn, label: (id: RampId) => id === 'sequential' ? 'Volumen observado' : 'Diferencia frente a la mediana' }
}
