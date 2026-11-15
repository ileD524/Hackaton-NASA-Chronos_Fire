<script setup lang="ts">
import { ANOMALY_BANDS, SEQUENTIAL_NAME, SEQUENTIAL_STOPS, textOn, useColorRamp } from '@/composables/useColorRamp'
import { useCalendar, format } from '@/composables/useCalendar'
import { useChronos } from '@/composables/useChronos'
import { useI18n } from '@/composables/useI18n'
const { rampId } = useColorRamp()
const { thresholds } = useCalendar()
const { metricMode, metricLabel } = useChronos()
const { t, anomalyLegend } = useI18n()
const stepLabel = (i: number) => {
  const cut = thresholds.value
  if (i === 0) return `≤ ${format(cut[0]!)}`
  if (i === 4) return `≥ ${format(cut[3]!)}`
  return `${format(cut[i - 1]!)}–${format(cut[i]!)}`
}
</script>
<template>
  <div class="legend">
    <span>{{ rampId === 'sequential' ? t(metricMode === 'cells' ? 'legend.sequentialTitle.cells' : 'legend.sequentialTitle.hotspots') : t('legend.anomalyTitle') }}</span>
    <div class="legend-steps">
      <template v-if="rampId === 'sequential'">
        <span v-for="(bg, i) in SEQUENTIAL_STOPS" :key="bg" :style="{ background: bg, color: textOn(bg) }">{{ stepLabel(i) }}</span>
      </template>
      <template v-else>
        <span v-for="band in ANOMALY_BANDS" :key="band.id" :style="{ background: band.bg, color: textOn(band.bg) }"><span v-if="band.mark" class="legend-mark" aria-hidden="true">{{ band.mark }}</span>{{ anomalyLegend(band.id) }}</span>
      </template>
    </div>
    <small v-if="rampId === 'anomaly' && metricMode === 'cells'">{{ t('legend.anomalyNote') }}</small>
    <small v-else>{{ t('legend.sequentialNote', { name: SEQUENTIAL_NAME, metric: metricLabel }) }}</small>
  </div>
</template>
<style scoped>
.legend{display:flex;gap:12px;flex-wrap:wrap;align-items:center;font-size:12px}.legend-steps{display:flex;gap:3px;flex-wrap:wrap}.legend-steps span{padding:5px 10px;border:1px solid var(--blue-100);border-radius:3px;font-weight:700;white-space:nowrap}.legend-mark{margin-right:4px}.legend small{width:100%}
</style>
