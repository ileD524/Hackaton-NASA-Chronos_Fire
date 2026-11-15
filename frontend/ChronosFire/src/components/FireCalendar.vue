<script setup lang="ts">
import { computed, ref } from 'vue'
import { useCalendar, format } from '@/composables/useCalendar'
import { useChronos } from '@/composables/useChronos'
import type { MetricMode } from '@/data/types'
import { ANOMALY_BANDS, SEQUENTIAL_STOPS, anomalyBand, useColorRamp, type RampId } from '@/composables/useColorRamp'
import { buildCalendarSvg, calendarSvgFilename, downloadCalendarSvg } from '@/utils/exportCalendarSvg'
import { calendarPngFilename, downloadCalendarPng } from '@/utils/exportCalendarPng'
const props=defineProps<{ selectedMonth: {year:number;month:number}|null; id?:string }>()
const emit=defineEmits<{(e:'update:selectedMonth',value:{year:number;month:number}|null):void}>()
const {years,cellMeta,monthShort,monthName,annualTotal,thresholds}=useCalendar()
const {seriesLabel,metricMode,metricLabel,filters,areaLabel}=useChronos()
const {rampId,colorAt,textOn}=useColorRamp()
const sectionId=props.id??'calendario'
const exportNotice=ref('')
const exportError=ref('')
const exportingPng=ref(false)
const selectionInfo=computed(()=>props.selectedMonth?{...props.selectedMonth,...cellMeta(props.selectedMonth.year,props.selectedMonth.month)}:null)
const compactNumber=new Intl.NumberFormat('es-AR',{notation:'compact',maximumFractionDigits:1})
const formatCell=(value:number)=>value>=10000?compactNumber.format(value):format(value)
function styleCell(year:number,month:number) {
  const meta=cellMeta(year,month)
  if(meta.value===null)return {}
  const bg=rampId.value==='anomaly'?anomalyBand(meta.vsMedian).bg:colorAt(meta.bucket)
  return {backgroundColor:bg,color:textOn(bg)}
}
function isExtreme(year:number,month:number) {
  if(metricMode.value!=='cells'||rampId.value!=='anomaly')return false
  const meta=cellMeta(year,month)
  return meta.value!==null&&anomalyBand(meta.vsMedian).extreme
}
function setMode(mode:MetricMode) {
  metricMode.value=mode
  if(mode==='hotspots'&&rampId.value==='anomaly')rampId.value='sequential'
}
const ramps=computed<{id:RampId;name:string}[]>(()=>metricMode.value==='cells'
  ?[{id:'sequential',name:'Actividad mensual'},{id:'anomaly',name:'Diferencia histórica'}]
  :[{id:'sequential',name:'Hotspots detectados'}])
const legend=computed(()=>{
  if(rampId.value==='anomaly'&&metricMode.value==='cells')return {
    title:'Desviación respecto de la mediana de otros años completos',
    items:ANOMALY_BANDS.map((band)=>({label:band.legend,fill:band.bg,ink:textOn(band.bg),mark:band.mark||undefined})),
    note:'Cortes fijos en ±15% y ±50%. ▼ marca los meses por debajo de −50%, ▲ los que superan +50%. Los meses parciales o sin referencia no se comparan y quedan en el tramo central.',
  }
  const cuts=thresholds.value
  const stepLabel=(i:number)=>i===0?`≤ ${format(cuts[0]!)}`:i===4?`≥ ${format(cuts[3]!)}`:`${format(cuts[i-1]!)}–${format(cuts[i]!)}`
  return {
    title:`${metricMode.value==='cells'?'Celdas con actividad':'Hotspots detectados'} · quintiles de la selección`,
    items:SEQUENTIAL_STOPS.map((bg,i)=>({label:stepLabel(i),fill:bg,ink:textOn(bg)})),
    note:'Cinco quintiles de los meses con valor en la selección; el número de cada celda es el valor real.',
  }
})
function calendarSvgText() {
  const rows=years.value.map((year)=>({
    year,
    total:format(annualTotal(year)),
    cells:Array.from({length:12},(_,month)=>{
      const meta=cellMeta(year,month)
      const missing=meta.value===null
      const bg=missing?'#07173F':(rampId.value==='anomaly'?anomalyBand(meta.vsMedian).bg:colorAt(meta.bucket))
      return {
        value:missing?null:format(meta.value!),
        fill:bg,
        ink:textOn(bg),
        partial:meta.cell?.partial,
        extreme:isExtreme(year,month),
        label:meta.label,
      }
    }),
  }))
  const notes=metricMode.value==='cells'
    ?['Celdas de 0,05° (~5 km) con actividad por mes. No mide área quemada ni cantidad de incendios únicos.','La comparación histórica se calcula sobre celdas con actividad.']
    :['Hotspots detectados por mes: detecciones de vegetación nominal/alta, sin focos industriales. Varios hotspots pueden caer en una misma celda y no son incendios únicos.','La comparación histórica se calcula sobre celdas con actividad; los hotspots se muestran como conteo de detecciones.']
  notes.push(
    'El cero se conserva como cero; el guion indica que no hay datos para esta serie.',
    '* Mes recortado por el rango consultado.',
    '«Todos los sensores» es la unión de fuentes, no un promedio: la serie está dominada por VIIRS y las detecciones de distintos sensores no se suman.',
  )
  if(rampId.value==='anomaly')notes.push('▼ marca los meses por debajo de −50% y ▲ los que superan +50% respecto de la mediana de otros años completos.')
  const generatedAt=new Intl.DateTimeFormat('es-AR',{dateStyle:'medium',timeStyle:'short'}).format(new Date())
  const svg=buildCalendarSvg({
    title:'Calendario de actividad de fuego',
    subtitle:[areaLabel.value,`${filters.value.from} → ${filters.value.to} · UTC`,seriesLabel.value,`${metricLabel.value} por mes`],
    monthLabels:Array.from({length:12},(_,month)=>monthShort(month)),
    rows,
    legend:legend.value,
    notes,
    footer:`Chronos Fire · NASA FIRMS · MODIS + VIIRS · Generado el ${generatedAt}`,
  })
  return svg
}
function exportSvg() {
  exportError.value=''
  const svg=calendarSvgText()
  const filename=calendarSvgFilename(areaLabel.value,filters.value.from,filters.value.to)
  downloadCalendarSvg(svg,filename)
  exportNotice.value=`SVG descargado: ${filename}`
}
async function exportPng() {
  if(exportingPng.value)return
  exportingPng.value=true
  exportError.value=''
  exportNotice.value='Generando PNG…'
  try {
    const svg=calendarSvgText()
    const filename=calendarPngFilename(areaLabel.value,filters.value.from,filters.value.to)
    await downloadCalendarPng(svg,filename,2)
    exportNotice.value=`PNG descargado: ${filename}`
  } catch(e) {
    exportNotice.value=''
    exportError.value=e instanceof Error?e.message:'No se pudo generar el PNG.'
  } finally {
    exportingPng.value=false
  }
}
</script>
<template>
  <section class="panel calendar-panel" :id="sectionId" :aria-labelledby="`${sectionId}-title`">
    <header class="panel-head"><div><span class="section-kicker">LA ACTIVIDAD A TRAVÉS DEL TIEMPO</span><h2 class="panel-title" :id="`${sectionId}-title`">Calendario de actividad</h2></div><span class="series-badge">{{ seriesLabel }}</span></header>
    <p class="small-note">{{ metricMode==='cells' ? 'Celdas de 0,05° (~5 km) con actividad por mes. No mide área quemada ni cantidad de incendios únicos.' : 'Hotspots detectados por mes: detecciones de vegetación nominal/alta, sin focos industriales. Varios hotspots pueden caer en una misma celda y no son incendios únicos.' }}</p>
    <div class="mode-tabs" role="group" aria-label="Métrica del calendario"><button class="chip" :class="{'chip--active':metricMode==='cells'}" :aria-pressed="metricMode==='cells'" @click="setMode('cells')">Celdas con actividad</button><button class="chip" :class="{'chip--active':metricMode==='hotspots'}" :aria-pressed="metricMode==='hotspots'" @click="setMode('hotspots')">Hotspots detectados</button></div>
    <div class="ramp-tabs" role="group" aria-label="Escala del calendario"><button v-for="r in ramps" :key="r.id" class="chip" :class="{'chip--active':rampId===r.id}" :aria-pressed="rampId===r.id" @click="rampId=r.id">{{r.name}}</button></div>
    <div class="cal-scroll" tabindex="0" aria-label="Tabla desplazable del calendario">
      <table class="cal" :class="{'is-hotspots':metricMode==='hotspots'}"><caption class="sr-only">{{ metricLabel }} por mes y año; seleccioná un mes para actualizar el mapa y el detalle diario.</caption><thead><tr><th scope="col">Año / total</th><th v-for="m in 12" :key="m" scope="col"><abbr :title="monthName(m-1)">{{monthShort(m-1)}}</abbr></th></tr></thead>
        <tbody><tr v-for="year in years" :key="year"><th scope="row"><span class="year-num">{{year}}</span><span class="year-total" :title="`${format(annualTotal(year))} ${metricLabel} en ${year}`">{{formatCell(annualTotal(year))}}</span></th>
          <td v-for="m in 12" :key="m"><button class="cal-cell" :class="{'is-selected':selectedMonth?.year===year&&selectedMonth?.month===m-1,'is-extreme':isExtreme(year,m-1),'is-missing':cellMeta(year,m-1).value===null,'is-partial':cellMeta(year,m-1).cell?.partial}" :style="styleCell(year,m-1)" :aria-label="cellMeta(year,m-1).label" :aria-pressed="selectedMonth?.year===year&&selectedMonth?.month===m-1" :disabled="cellMeta(year,m-1).value===null" :title="cellMeta(year,m-1).label" @click="emit('update:selectedMonth',{year,month:m-1})">{{cellMeta(year,m-1).value===null?'—':formatCell(cellMeta(year,m-1).value!)}}<sup v-if="cellMeta(year,m-1).cell?.partial&&cellMeta(year,m-1).value!==null">*</sup></button></td>
        </tr></tbody>
      </table>
    </div>
    <div v-if="selectionInfo" class="selection-detail" aria-live="polite"><strong>{{monthName(selectionInfo.month)}} {{selectionInfo.year}}</strong><span>{{selectionInfo.value===null?'Sin datos':`${format(selectionInfo.value)} ${metricLabel}`}}</span><span class="small-note">{{selectionInfo.cell?.data_status === 'observed' ? 'Mes con datos' : 'Sin datos'}}<span v-if="selectionInfo.cell?.partial"> · período parcial*</span></span></div>
    <div class="export-row"><button class="btn" type="button" :disabled="exportingPng" :aria-describedby="`${sectionId}-export-note`" @click="exportSvg">Descargar el calendario en SVG</button><button class="btn" type="button" :disabled="exportingPng" :aria-describedby="`${sectionId}-export-note`" @click="exportPng">{{ exportingPng ? 'Generando PNG…' : 'Descargar el calendario en PNG' }}</button><span class="export-status small-note" role="status" aria-live="polite">{{exportNotice}}</span></div>
    <p v-if="exportError" class="small-note export-error" role="alert">{{exportError}}</p>
    <p class="small-note" :id="`${sectionId}-export-note`">El SVG es vectorial y editable; el PNG usa la misma vista a 2×. Ambos incluyen valores, colores, leyenda y nota metodológica.</p>
    <p class="small-note">* Mes recortado por el rango consultado. — Sin datos para esta serie. El cero se conserva como cero {{metricMode==='cells'?'celdas':'hotspots'}}.</p>
    <p v-if="metricMode==='hotspots'" class="small-note">La comparación histórica y la rampa de anomalía se calculan sobre celdas con actividad. Los hotspots se muestran como conteo de detecciones.</p>
    <p v-else class="small-note">Los valores de más de cuatro cifras se abrevian en la grilla; el valor completo está en el tooltip y en la etiqueta accesible.</p>
  </section>
</template>
<style scoped>
.calendar-panel{padding:24px}.panel-head{display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap}.export-row{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-top:16px}.export-status{min-height:1.2em}.export-error{color:#ffb4a8}.mode-tabs,.ramp-tabs{display:flex;gap:8px;margin:20px 0 0;flex-wrap:wrap}.ramp-tabs{margin-top:10px}.cal-scroll{overflow-x:auto;padding:5px 3px 9px}.cal{border-collapse:separate;border-spacing:4px;table-layout:fixed;width:100%;min-width:760px}.cal th{font:700 12px var(--font-body);padding:5px 0}.cal th:first-child{width:66px;text-align:left}.cal abbr{text-decoration:none}.year-num{display:block;font-weight:700;font-size:14px}.year-total{display:block;font-size:10px;font-weight:400;margin-top:4px}.cal td{padding:0}.cal-cell{border:1px solid #2E96F5;border-radius:4px;width:100%;height:39px;padding:2px;font:700 11px var(--font-body);cursor:pointer;position:relative;white-space:nowrap}.cal.is-hotspots .cal-cell{font-size:10px;font-variant-numeric:tabular-nums}.cal-cell:hover{outline:2px solid white;outline-offset:1px}.cal-cell.is-selected{outline:3px solid var(--warm-lime);outline-offset:0}.cal-cell.is-missing{background:repeating-linear-gradient(135deg,transparent,transparent 4px,#0042a6 4px,#0042a6 5px);color:white;cursor:default;border-style:dashed}.cal-cell.is-extreme{box-shadow:inset 0 0 0 2px rgba(255,255,255,.92)}.cal-cell sup{margin-left:1px}.selection-detail{display:flex;gap:14px;align-items:center;flex-wrap:wrap;border-top:1px solid var(--blue-100);padding-top:16px;margin-top:12px}.selection-detail strong{text-transform:capitalize}.series-badge{border:1px solid var(--blue-100);padding:5px 10px;border-radius:20px;font-size:12px}
@media(max-width:720px){.calendar-panel{padding:16px}.cal{min-width:760px}}
</style>
