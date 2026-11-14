<script setup lang="ts">
import { computed } from 'vue'
import { useChronos } from '@/composables/useChronos'
import { format, MONTH_LABELS } from '@/composables/useCalendar'
const { months, seriesLabel, filters }=useChronos()
const total=computed(()=>months.value.reduce((s,m)=>s+(m.value??0),0))
const peak=computed(()=>months.value.filter(m=>m.value!==null&&!m.partial).sort((a,b)=>(b.value??0)-(a.value??0))[0])
const days=computed(()=>months.value.reduce((s,m)=>s+m.available_days,0))
</script>
<template>
  <ul class="kpi-list">
    <li class="kpi"><span class="kpi-caption">{{ filters.metric==='calibrated'?'ACTIVIDAD ESTIMADA':'ACTIVIDAD REGISTRADA' }}</span><strong class="kpi-value">{{ format(total) }}</strong><span class="kpi-label">celda-días · {{ seriesLabel }}</span></li>
    <li class="kpi"><span class="kpi-caption">MES CON MAYOR VALOR</span><strong class="kpi-value smaller">{{ peak ? `${MONTH_LABELS[peak.month]} ${peak.year}` : '—' }}</strong><span class="kpi-label">{{ peak ? `${format(peak.value!)} celda-días · mes completo` : 'Sin meses completos en la selección' }}</span></li>
    <li class="kpi"><span class="kpi-caption">DÍAS EN LA SERIE</span><strong class="kpi-value">{{ format(days) }}</strong><span class="kpi-label">con valor disponible · no acredita cobertura sin nubes</span></li>
  </ul>
</template>
<style scoped>
.kpi-list{list-style:none;margin:0;padding:0;display:grid;grid-template-columns:1.2fr 1fr 1fr;gap:16px}.kpi{background:var(--blue-900);border:1px solid var(--blue-100);border-radius:12px;padding:20px;display:flex;flex-direction:column;gap:9px}.kpi-caption{font-size:11px;letter-spacing:.08em}.kpi-value{font:900 clamp(25px,2.6vw,38px)/1.1 var(--font-display)}.smaller{font-size:28px}.kpi-label{font-size:13px;line-height:1.4}@media(max-width:600px){.kpi-list{grid-template-columns:1fr}.kpi{padding:16px}.kpi-value{font-size:30px}}
</style>
