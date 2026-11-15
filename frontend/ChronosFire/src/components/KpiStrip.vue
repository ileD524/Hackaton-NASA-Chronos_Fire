<script setup lang="ts">
import { computed } from 'vue'
import { useChronos } from '@/composables/useChronos'
import { format, MONTH_LABELS, useCalendar } from '@/composables/useCalendar'
import { anomalyBand } from '@/composables/useColorRamp'
const { months, seriesLabel } = useChronos()
const { cellMeta } = useCalendar()
const total = computed(() => months.value.reduce((sum, m) => sum + (m.value ?? 0), 0))
const peak = computed(() => months.value.filter((m) => m.value !== null && !m.partial).sort((a, b) => (b.value ?? 0) - (a.value ?? 0))[0])
const peakBand = computed(() => peak.value ? anomalyBand(cellMeta(peak.value.year, peak.value.month).vsMedian) : null)
const completeMonths = computed(() => months.value.filter((m) => m.value !== null && !m.partial).length)
</script>
<template>
  <ul class="kpi-list">
    <li class="kpi"><span class="kpi-caption">ACTIVIDAD REGISTRADA</span><strong class="kpi-value">{{ format(total) }}</strong><span class="kpi-label">celdas-mes con actividad · {{ seriesLabel }}</span></li>
    <li class="kpi"><span class="kpi-caption">MES CON MAYOR VALOR</span><strong class="kpi-value smaller" :style="peakBand?{color:peakBand.ink}:undefined">{{ peak ? `${MONTH_LABELS[peak.month]} ${peak.year}` : '—' }}</strong><span class="kpi-label">{{ peak ? `${format(peak.value!)} celdas · mes completo` : 'Sin meses completos en la selección' }}</span></li>
    <li class="kpi"><span class="kpi-caption">MESES COMPLETOS</span><strong class="kpi-value">{{ format(completeMonths) }}</strong><span class="kpi-label">en la selección · no acredita cobertura sin nubes</span></li>
  </ul>
</template>
<style scoped>
.kpi-list{list-style:none;margin:0;padding:0;display:grid;grid-template-columns:1.2fr 1fr 1fr;gap:16px}.kpi{background:var(--blue-900);border:1px solid var(--blue-100);border-radius:12px;padding:20px;display:flex;flex-direction:column;gap:9px}.kpi-caption{font-size:11px;letter-spacing:.08em}.kpi-value{font:900 clamp(25px,2.6vw,38px)/1.1 var(--font-display)}.smaller{font-size:28px}.kpi-label{font-size:13px;line-height:1.4}@media(max-width:600px){.kpi-list{grid-template-columns:1fr}.kpi{padding:16px}.kpi-value{font-size:30px}}
</style>
