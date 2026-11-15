<script setup lang="ts">
import { computed } from 'vue'
import { useChronos, monthMedian } from '@/composables/useChronos'
import { useCalendar, format } from '@/composables/useCalendar'
import { anomalyBand } from '@/composables/useColorRamp'
const {selected,months,seriesLabel}=useChronos();const {monthName}=useCalendar()
const median=computed(()=>selected.value?monthMedian(months.value,selected.value.month,selected.value.year):null)
const delta=computed(()=>selected.value?.value!==null&&selected.value&&!selected.value.partial&&median.value!==null&&median.value>0?(selected.value.value!/median.value-1)*100:null)
const band=computed(()=>anomalyBand(delta.value===null?null:delta.value/100))
</script>
<template><section class="panel" aria-labelledby="comparison-title"><span class="section-kicker">PONER EL MES EN CONTEXTO</span><h2 class="panel-title" id="comparison-title">Frente a su historia</h2><template v-if="selected"><p>{{monthName(selected.month)}} {{selected.year}} · {{seriesLabel}}</p><div class="comparison-number" :style="{color:band.ink}"><span v-if="band.mark" class="comparison-mark" aria-hidden="true">{{band.mark}}</span>{{delta===null?'—':`${delta>0?'+':''}${format(delta)}%`}}</div><p class="small-note">{{delta===null?'No hay una comparación válida para este período parcial o sin referencia.':'Diferencia respecto de la mediana del mismo mes en otros años completos de la selección.'}}</p><dl class="comparison-rows"><div><dt>Mes seleccionado</dt><dd>{{selected.value===null?'—':format(selected.value)}} celdas con actividad</dd></div><div><dt>Mediana de referencia</dt><dd>{{median===null?'—':format(median)}} celdas con actividad</dd></div></dl></template><p v-else>Seleccioná un mes del calendario.</p><p class="small-note">Comparación descriptiva de esta serie. No es una predicción ni un nivel de riesgo.</p></section></template>
<style scoped>.comparison-number{font:900 48px/1.2 var(--font-display);margin:20px 0;color:white}.comparison-mark{font-size:.62em;margin-right:8px;vertical-align:.12em}.comparison-rows{margin:20px 0}.comparison-rows>div{display:flex;justify-content:space-between;gap:12px;padding:12px 0;border-bottom:1px solid var(--blue-100);font-size:13px}.comparison-rows dd{margin:0;font-weight:700;text-align:right}</style>
