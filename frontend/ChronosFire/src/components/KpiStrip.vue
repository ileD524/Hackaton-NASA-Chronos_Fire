<script setup lang="ts">
import { computed } from 'vue'
import { useChronos } from '@/composables/useChronos'
import { format, useCalendar } from '@/composables/useCalendar'
import { useI18n } from '@/composables/useI18n'
import { anomalyBand } from '@/composables/useColorRamp'
const { t, monthShort } = useI18n()
const { months, seriesLabel, metricMode, metricValue, metricLabel, metricUnit } = useChronos()
const { cellMeta } = useCalendar()
const total = computed(() => months.value.reduce((sum, m) => sum + (metricValue(m) ?? 0), 0))
const peak = computed(() => months.value
  .filter((m) => metricValue(m) !== null && !m.partial)
  .sort((a, b) => (metricValue(b) ?? 0) - (metricValue(a) ?? 0))[0])
const peakBand = computed(() => metricMode.value === 'cells' && peak.value
  ? anomalyBand(cellMeta(peak.value.year, peak.value.month).vsMedian)
  : null)
const completeMonths = computed(() => months.value.filter((m) => metricValue(m) !== null && !m.partial).length)
const partialIncluded = computed(() => months.value.some((m) => m.partial && metricValue(m) !== null))
const peakUnit = computed(() => metricMode.value === 'cells' ? t('unit.cells') : t('unit.hotspots'))
</script>
<template>
  <ul class="kpi-list">
    <li class="kpi"><span class="kpi-caption">{{ t('kpi.activity') }}</span><strong class="kpi-value">{{ format(total) }}</strong><span class="kpi-label">{{ metricUnit }} · {{ seriesLabel }}<span v-if="partialIncluded">{{ t('kpi.includesPartial') }}</span></span></li>
    <li class="kpi"><span class="kpi-caption">{{ t('kpi.peak') }}</span><strong class="kpi-value smaller" :style="peakBand?{color:peakBand.ink}:undefined">{{ peak ? `${monthShort(peak.month)} ${peak.year}` : '—' }}</strong><span class="kpi-label">{{ peak ? t('kpi.peakLabel', { value: format(metricValue(peak)!), unit: peakUnit }) : t('kpi.peakEmpty') }}</span></li>
    <li class="kpi"><span class="kpi-caption">{{ t('kpi.complete') }}</span><strong class="kpi-value">{{ format(completeMonths) }}</strong><span class="kpi-label">{{ t('kpi.completeLabel', { metric: metricLabel }) }}</span></li>
  </ul>
</template>
<style scoped>
.kpi-list{list-style:none;margin:0;padding:0;display:grid;grid-template-columns:1.2fr 1fr 1fr;gap:16px}.kpi{background:var(--blue-900);border:1px solid var(--blue-100);border-radius:12px;padding:20px;display:flex;flex-direction:column;gap:9px}.kpi-caption{font-size:11px;letter-spacing:.08em}.kpi-value{font:900 clamp(25px,2.6vw,38px)/1.1 var(--font-display)}.smaller{font-size:28px}.kpi-label{font-size:13px;line-height:1.4}@media(max-width:600px){.kpi-list{grid-template-columns:1fr}.kpi{padding:16px}.kpi-value{font-size:30px}}
</style>
