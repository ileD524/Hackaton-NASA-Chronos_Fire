<script setup lang="ts">
import { useColorRamp } from '@/composables/useColorRamp'
import { useCalendar, format } from '@/composables/useCalendar'
const {rampId,color,textOn}=useColorRamp();const {maxVal}=useCalendar()
</script>
<template><div class="legend"><span>{{rampId==='sequential'?'Actividad · escala logarítmica':'Desviación respecto de la mediana de otros años completos'}}</span><div class="legend-steps"><span v-for="i in 5" :key="i" :style="{background:color((i-1)/4),color:textOn(color((i-1)/4))}">{{rampId==='sequential'?format(Math.expm1(Math.log1p(maxVal)*(i-1)/4)):`${(i-3)*50}%`}}</span></div><small v-if="rampId==='anomaly'">Extremos saturados en ±100%. Los meses parciales o sin referencia no se comparan y reciben el color central.</small></div></template>
<style scoped>.legend{display:flex;gap:12px;flex-wrap:wrap;align-items:center;font-size:12px}.legend-steps{display:flex;gap:3px}.legend-steps span{padding:5px 10px;border:1px solid var(--blue-100);border-radius:3px;font-weight:700}.legend small{width:100%}</style>
