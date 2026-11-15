<script setup lang="ts">
import { ANOMALY_BANDS, SEQUENTIAL_STOPS, textOn, useColorRamp } from '@/composables/useColorRamp'
import { useCalendar, format } from '@/composables/useCalendar'
const { rampId } = useColorRamp()
const { thresholds } = useCalendar()
const stepLabel = (i: number) => {
  const cut = thresholds.value
  if (i === 0) return `≤ ${format(cut[0]!)}`
  if (i === 4) return `≥ ${format(cut[3]!)}`
  return `${format(cut[i - 1]!)}–${format(cut[i]!)}`
}
</script>
<template>
  <div class="legend">
    <span>{{ rampId === 'sequential' ? 'Actividad · escala logarítmica' : 'Desviación respecto de la mediana de otros años completos' }}</span>
    <div class="legend-steps">
      <template v-if="rampId === 'sequential'">
        <span v-for="(bg, i) in SEQUENTIAL_STOPS" :key="bg" :style="{ background: bg, color: textOn(bg) }">{{ stepLabel(i) }}</span>
      </template>
      <template v-else>
        <span v-for="band in ANOMALY_BANDS" :key="band.id" :style="{ background: band.bg, color: textOn(band.bg) }"><span v-if="band.mark" class="legend-mark" aria-hidden="true">{{ band.mark }}</span>{{ band.legend }}</span>
      </template>
    </div>
    <small v-if="rampId === 'anomaly'">Cortes fijos en ±15% y ±50%. ▼ marca los meses por debajo de −50%, ▲ los que superan +50%. Los meses parciales o sin referencia no se comparan y quedan en el tramo central.</small>
    <small v-else>Cinco quintiles de los meses con valor en la selección: cada tramo agrupa un 20% de los meses y la leyenda muestra sus cortes exactos. El número de cada celda es siempre el valor real.</small>
  </div>
</template>
<style scoped>
.legend{display:flex;gap:12px;flex-wrap:wrap;align-items:center;font-size:12px}.legend-steps{display:flex;gap:3px;flex-wrap:wrap}.legend-steps span{padding:5px 10px;border:1px solid var(--blue-100);border-radius:3px;font-weight:700;white-space:nowrap}.legend-mark{margin-right:4px}.legend small{width:100%}
</style>
