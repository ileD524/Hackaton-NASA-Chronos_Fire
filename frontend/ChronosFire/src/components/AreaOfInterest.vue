<script setup lang="ts">
import { ref, watch } from 'vue'
import { useChronos } from '@/composables/useChronos'
import type { Bbox } from '@/data/types'
const { filters, loading, load, exportURL, hotspotsExportURL, metadata, csvNotice, csvError, csvChecking, checkCsv, metricMode } = useChronos()
const checkCalendarCsv = () => checkCsv(exportURL.value)
const checkHotspotsCsv = () => checkCsv(hotspotsExportURL.value)
const from = ref(filters.value.from), to = ref(filters.value.to)
const area = ref('delta'), west = ref(-61), south = ref(-34), east = ref(-59.5), north = ref(-32.5)
const validation = ref('')
watch(filters, (f) => {
  from.value = f.from
  to.value = f.to
  if (f.bbox) {
    const [w, s, e, n] = f.bbox
    area.value = w === -61 && s === -34 && e === -59.5 && n === -32.5 ? 'delta' : 'custom'
    west.value = w; south.value = s; east.value = e; north.value = n
  } else {
    area.value = 'all'
  }
}, { deep: true })
function preset(id: string) {
  area.value = id
  if (id === 'delta') [west.value, south.value, east.value, north.value] = [-61, -34, -59.5, -32.5]
  if (id === 'cordoba') [west.value, south.value, east.value, north.value] = [-65.5, -35, -62, -29]
  if (id === 'corrientes') [west.value, south.value, east.value, north.value] = [-60, -30.8, -55.5, -27]
}
function apply() {
  validation.value = ''
  if (!from.value || !to.value || from.value > to.value) { validation.value = 'Revisá el orden de las fechas.'; return }
  const bbox: Bbox | null = area.value === 'all' ? null : [Number(west.value), Number(south.value), Number(east.value), Number(north.value)]
  if (bbox && (!bbox.every(Number.isFinite) || bbox[0] >= bbox[2] || bbox[1] >= bbox[3])) {
    validation.value = 'Revisá los límites del rectángulo.'
    return
  }
  load({ from: from.value, to: to.value, metric: 'all', bbox })
}
function reset() {
  area.value = 'all'
  from.value = '2003-01-01'
  to.value = '2026-11-14'
  apply()
}
</script>
<template>
  <aside class="panel aoi" aria-label="Filtros del calendario">
    <div class="section-kicker">EXPLORAR EL ARCHIVO</div><h2 class="panel-title">Tu área de interés</h2>
    <form @submit.prevent="apply" class="filter-form">
      <div class="field"><label for="area">Área</label><select id="area" :value="area" @change="preset(($event.target as HTMLSelectElement).value)"><option value="delta">Delta del Paraná</option><option value="all">Toda la extensión</option><option value="cordoba">Entorno de Córdoba</option><option value="corrientes">Entorno de Corrientes</option><option value="custom">Rectángulo personalizado</option></select></div>
      <div v-if="area !== 'all'" class="bbox-fields">
        <div class="field"><label for="west">Oeste · longitud</label><input id="west" v-model.number="west" type="number" step="any" min="-180" max="180" required></div>
        <div class="field"><label for="east">Este · longitud</label><input id="east" v-model.number="east" type="number" step="any" min="-180" max="180" required></div>
        <div class="field"><label for="south">Sur · latitud</label><input id="south" v-model.number="south" type="number" step="any" min="-90" max="90" required></div>
        <div class="field"><label for="north">Norte · latitud</label><input id="north" v-model.number="north" type="number" step="any" min="-90" max="90" required></div>
        <p class="small-note bbox-note">Recorte por coordenadas; no representa los límites oficiales de una provincia.</p>
      </div>
      <div class="date-fields"><div class="field"><label for="from">Desde</label><input id="from" v-model="from" type="date" min="2003-01-01" max="2026-12-31" required></div><div class="field"><label for="to">Hasta</label><input id="to" v-model="to" type="date" min="2003-01-01" max="2026-12-31" required></div></div>
      <p v-if="validation" class="notice" role="alert">{{ validation }}</p>
      <button class="btn btn-primary full-width" type="submit" :disabled="loading">{{ loading ? 'Consultando…' : 'Actualizar calendario' }} <span aria-hidden="true">↗</span></button>
      <button class="link-btn" type="button" @click="reset">Ver todo el archivo 2003–2026</button>
    </form>
    <div class="source-box" id="datos"><span class="section-kicker">DATOS REALES · NASA FIRMS</span><p>MODIS Terra/Aqua<br>VIIRS Suomi-NPP / NOAA-20 / NOAA-21</p><p class="small-note">Vegetación presunta · confianza nominal/alta · días UTC · celdas 0,05°.</p><p v-if="metadata" class="small-note">{{ new Intl.NumberFormat('es-AR').format(metadata.selected_rows) }} detecciones en el archivo.</p></div>
    <a
      class="btn export-btn"
      :href="exportURL"
      download="chronosfire-calendario-semanal.csv"
      :aria-busy="csvChecking"
      @click="checkCalendarCsv"
    >↓ Exportar calendario CSV</a>
    <a
      class="btn export-btn"
      :href="hotspotsExportURL"
      download="chronosfire-detecciones.csv"
      :aria-busy="csvChecking"
      aria-label="Descargar hotspots CSV: hasta 50000 filas de vegetación nominal o alta, sin focos industriales"
      @click="checkHotspotsCsv"
    >↓ Descargar hotspots CSV</a>
    <p class="small-note export-note">Hasta 50.000 filas. Solo vegetación nominal/alta, sin focos industriales. Usa el área, el rango y el sensor activos.</p>
    <p v-if="csvNotice" class="small-note export-note" role="status">{{ csvNotice }}</p>
    <p v-if="csvError" class="small-note export-error" role="alert">{{ csvError }}</p>
    <p class="small-note">Cero registros no confirma ausencia de fuego. {{ metricMode==='cells' ? 'La comparación histórica se calcula sobre celdas con actividad.' : 'Los hotspots son conteos de detecciones; la comparación histórica se calcula sobre celdas con actividad.' }}</p>
  </aside>
</template>
<style scoped>
.aoi{padding:22px}.filter-form{display:flex;flex-direction:column;gap:18px;margin-top:24px}.bbox-fields{display:grid;grid-template-columns:1fr 1fr;gap:12px}.bbox-note{grid-column:1/-1}.date-fields{display:flex;flex-direction:column;gap:14px}.source-box{border-top:1px solid var(--blue-100);margin-top:22px;padding-top:22px}.source-box p{margin:10px 0}.export-btn{width:100%;margin-top:16px;font-size:14px;text-align:center}.export-btn+.export-btn{margin-top:8px}.export-note{margin-top:10px}.export-error{color:#ffb4a8}.full-width{width:100%;justify-content:space-between}@media(min-width:720px) and (max-width:1100px){.filter-form{display:grid;grid-template-columns:1fr 1fr}.date-fields{flex-direction:row}.source-box{display:none}}
</style>
