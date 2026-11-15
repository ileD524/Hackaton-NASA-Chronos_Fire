<script setup lang="ts">
import { computed } from 'vue'
import { useCalendar, format } from '@/composables/useCalendar'
import { useChronos } from '@/composables/useChronos'
import { anomalyBand, useColorRamp, type RampId } from '@/composables/useColorRamp'
const props=defineProps<{ selectedMonth: {year:number;month:number}|null; id?:string }>()
const emit=defineEmits<{(e:'update:selectedMonth',value:{year:number;month:number}|null):void}>()
const {years,cellMeta,monthShort,monthName,annualTotal}=useCalendar()
const {seriesLabel}=useChronos()
const {rampId,colorAt,textOn}=useColorRamp()
const sectionId=props.id??'calendario'
const selectionInfo=computed(()=>props.selectedMonth?{...props.selectedMonth,...cellMeta(props.selectedMonth.year,props.selectedMonth.month)}:null)
function styleCell(year:number,month:number) {
  const meta=cellMeta(year,month)
  if(meta.value===null)return {}
  const bg=rampId.value==='anomaly'?anomalyBand(meta.vsMedian).bg:colorAt(meta.bucket)
  return {backgroundColor:bg,color:textOn(bg)}
}
function isExtreme(year:number,month:number) {
  if(rampId.value!=='anomaly')return false
  const meta=cellMeta(year,month)
  return meta.value!==null&&anomalyBand(meta.vsMedian).extreme
}
const ramps: {id:RampId;name:string}[]=[{id:'sequential',name:'Actividad mensual'},{id:'anomaly',name:'Diferencia histórica'}]
</script>
<template>
  <section class="panel calendar-panel" :id="sectionId" :aria-labelledby="`${sectionId}-title`">
    <header class="panel-head"><div><span class="section-kicker">LA ACTIVIDAD A TRAVÉS DEL TIEMPO</span><h2 class="panel-title" :id="`${sectionId}-title`">Calendario de actividad</h2></div><span class="series-badge">{{ seriesLabel }}</span></header>
    <p class="small-note">Celdas de 0,05° (~5 km) con actividad por mes. No mide área quemada ni cantidad de incendios únicos.</p>
    <div class="ramp-tabs" role="group" aria-label="Escala del calendario"><button v-for="r in ramps" :key="r.id" class="chip" :class="{'chip--active':rampId===r.id}" :aria-pressed="rampId===r.id" @click="rampId=r.id">{{r.name}}</button></div>
    <div class="cal-scroll" tabindex="0" aria-label="Tabla desplazable del calendario">
      <table class="cal"><caption class="sr-only">Celda-días por mes y año; seleccioná un mes para actualizar el mapa y el detalle diario.</caption><thead><tr><th scope="col">Año / total</th><th v-for="m in 12" :key="m" scope="col"><abbr :title="monthName(m-1)">{{monthShort(m-1)}}</abbr></th></tr></thead>
        <tbody><tr v-for="year in years" :key="year"><th scope="row"><span class="year-num">{{year}}</span><span class="year-total">{{format(annualTotal(year))}}</span></th>
          <td v-for="m in 12" :key="m"><button class="cal-cell" :class="{'is-selected':selectedMonth?.year===year&&selectedMonth?.month===m-1,'is-extreme':isExtreme(year,m-1),'is-missing':cellMeta(year,m-1).value===null,'is-partial':cellMeta(year,m-1).cell?.partial}" :style="styleCell(year,m-1)" :aria-label="cellMeta(year,m-1).label" :aria-pressed="selectedMonth?.year===year&&selectedMonth?.month===m-1" :disabled="cellMeta(year,m-1).value===null" :title="cellMeta(year,m-1).label" @click="emit('update:selectedMonth',{year,month:m-1})">{{cellMeta(year,m-1).value===null?'—':format(cellMeta(year,m-1).value!)}}<sup v-if="cellMeta(year,m-1).cell?.partial&&cellMeta(year,m-1).value!==null">*</sup></button></td>
        </tr></tbody>
      </table>
    </div>
    <div v-if="selectionInfo" class="selection-detail" aria-live="polite"><strong>{{monthName(selectionInfo.month)}} {{selectionInfo.year}}</strong><span>{{selectionInfo.value===null?'Sin datos':`${format(selectionInfo.value)} celdas con actividad`}}</span><span class="small-note">{{selectionInfo.cell?.data_status === 'observed' ? 'Mes con datos' : 'Sin datos'}}<span v-if="selectionInfo.cell?.partial"> · período parcial*</span></span></div>
    <p class="small-note">* Mes recortado por el rango consultado. — Sin datos para esta serie. El cero se conserva como cero celdas.</p>
  </section>
</template>
<style scoped>
.calendar-panel{padding:24px}.panel-head{display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap}.ramp-tabs{display:flex;gap:8px;margin:20px 0}.cal-scroll{overflow-x:auto;padding:5px 3px 9px}.cal{border-collapse:separate;border-spacing:4px;table-layout:fixed;width:100%;min-width:760px}.cal th{font:700 12px var(--font-body);padding:5px 0}.cal th:first-child{width:66px;text-align:left}.cal abbr{text-decoration:none}.year-num{display:block;font-weight:700;font-size:14px}.year-total{display:block;font-size:10px;font-weight:400;margin-top:4px}.cal td{padding:0}.cal-cell{border:1px solid #2E96F5;border-radius:4px;width:100%;height:39px;padding:2px;font:700 11px var(--font-body);cursor:pointer;position:relative;white-space:nowrap}.cal-cell:hover{outline:2px solid white;outline-offset:1px}.cal-cell.is-selected{outline:3px solid var(--warm-lime);outline-offset:0}.cal-cell.is-missing{background:repeating-linear-gradient(135deg,transparent,transparent 4px,#0042a6 4px,#0042a6 5px);color:white;cursor:default;border-style:dashed}.cal-cell.is-extreme{box-shadow:inset 0 0 0 2px rgba(255,255,255,.92)}.cal-cell sup{margin-left:1px}.selection-detail{display:flex;gap:14px;align-items:center;flex-wrap:wrap;border-top:1px solid var(--blue-100);padding-top:16px;margin-top:12px}.selection-detail strong{text-transform:capitalize}.series-badge{border:1px solid var(--blue-100);padding:5px 10px;border-radius:20px;font-size:12px}
@media(max-width:720px){.calendar-panel{padding:16px}.cal{min-width:760px}}
</style>
