<script setup lang="ts">
import { computed } from 'vue'
import { useChronos } from '@/composables/useChronos'
import { useCalendar, format } from '@/composables/useCalendar'
import type { DailyRow } from '@/data/types'
const { selected, selectedDaily, valueField, seriesLabel, metricMode } = useChronos()
const { monthName } = useCalendar()
const max = computed(() => Math.max(1, ...selectedDaily.value.map((d) => Number(d[valueField.value] ?? 0))))
const bars = computed(() => selectedDaily.value.map((d) => {
  const value = Number(d[valueField.value] ?? 0)
  return { date: d.date_utc, value, height: value / max.value * 100 }
}))
const dailyUnit = computed(() => metricMode.value === 'cells' ? 'celdas con actividad / día' : 'hotspots detectados / día')
const barUnit = computed(() => metricMode.value === 'cells' ? 'celdas' : 'hotspots')
function viirsHotspots(row: DailyRow) {
  return Object.entries(row.hotspots_by_sensor ?? {})
    .filter(([sensor]) => sensor.startsWith('VIIRS'))
    .reduce((sum, [, value]) => sum + value, 0)
}
</script>
<template>
<section class="panel" aria-labelledby="daily-title"><header class="daily-head"><div><span class="section-kicker">ACERCARSE AL DETALLE</span><h2 class="panel-title" id="daily-title">{{selected?`${monthName(selected.month)} ${selected.year}`:'Detalle diario'}}</h2></div><span class="small-note">{{ seriesLabel }} · {{ dailyUnit }}</span></header>
<div class="daily-chart" role="img" :aria-label="`${metricMode === 'cells' ? 'Celdas con actividad' : 'Hotspots detectados'} por día en ${selected?monthName(selected.month):'el mes seleccionado'}`"><div class="day-column" v-for="bar in bars" :key="bar.date"><div class="bar-space"><span class="daily-bar" :style="{height:`${Math.max(bar.height,bar.value===0?1:2)}%`}" :title="`${bar.date}: ${format(bar.value)} ${barUnit}`"></span></div><span class="day-label">{{Number(bar.date.slice(-2))%5===0||bar.date.endsWith('01')?Number(bar.date.slice(-2)):''}}</span></div></div>
<details><summary>Ver valores diarios accesibles</summary><div class="daily-table-wrap"><table class="daily-table"><caption class="sr-only">Serie diaria de {{ metricMode === 'cells' ? 'celdas con actividad' : 'hotspots detectados' }}; los hotspots por sensor no se suman entre sí como promedio.</caption><thead v-if="metricMode==='cells'"><tr><th>Fecha UTC</th><th>Celdas con actividad</th></tr></thead><thead v-else><tr><th>Fecha UTC</th><th>Hotspots MODIS</th><th>Hotspots VIIRS</th><th>Total hotspots</th></tr></thead><tbody><template v-for="d in selectedDaily" :key="d.date_utc"><tr v-if="metricMode==='cells'"><th scope="row">{{d.date_utc}}</th><td>{{format(d.active_cells_selected)}}</td></tr><tr v-else><th scope="row">{{d.date_utc}}</th><td>{{format(d.hotspots_by_sensor.MODIS ?? 0)}}</td><td>{{format(viirsHotspots(d))}}</td><td>{{format(d.hotspots)}}</td></tr></template></tbody></table></div></details>
</section>
</template>
<style scoped>
.daily-head{display:flex;justify-content:space-between;gap:16px;flex-wrap:wrap;align-items:center}.daily-chart{display:flex;gap:4px;height:170px;margin:24px 0}.day-column{flex:1;min-width:0;display:flex;flex-direction:column;gap:6px}.bar-space{height:145px;display:flex;align-items:end;border-bottom:1px solid #2E96F5}.daily-bar{width:100%;display:block;background:#2E96F5;border-radius:3px 3px 0 0}.missing-bar{background:repeating-linear-gradient(135deg,#0042A6,#0042A6 3px,transparent 3px,transparent 6px);opacity:.7}.day-label{height:15px;text-align:center;font-size:10px}summary{cursor:pointer;font-weight:700;padding:12px 0;font-size:14px}.daily-table-wrap{max-height:300px;overflow:auto}.daily-table{border-collapse:collapse;width:100%;font-size:12px}.daily-table td,.daily-table th{padding:8px;text-align:right;border-bottom:1px solid #0042A6}.daily-table th:first-child{text-align:left;white-space:nowrap}@media(max-width:600px){.daily-chart{gap:2px}}
</style>
