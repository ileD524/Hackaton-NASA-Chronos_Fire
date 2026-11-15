<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useChronos } from '@/composables/useChronos'
import { useI18n } from '@/composables/useI18n'
import { SENSOR_OPTIONS } from '@/data/types'
import type { Bbox, Metric } from '@/data/types'
const { t, formatNumber, sensorLabel } = useI18n()
const { filters, loading, load, exportURL, hotspotsExportURL, metadata, csvNotice, csvError, csvChecking, checkCsv, metricMode } = useChronos()
const checkCalendarCsv = () => checkCsv(exportURL.value)
const checkHotspotsCsv = () => checkCsv(hotspotsExportURL.value)
const from = ref(filters.value.from), to = ref(filters.value.to)
const sensor = ref<Metric>(filters.value.metric)
const area = ref('delta'), west = ref(-61), south = ref(-34), east = ref(-59.5), north = ref(-32.5)
const validation = ref('')
const sensorOptions = computed(() => SENSOR_OPTIONS.map((option) => ({ id: option.id, label: sensorLabel(option.id) })))
const dateMin = computed(() => metadata.value?.first_date || undefined)
const dateMax = computed(() => metadata.value?.last_date || undefined)
const archiveLabel = computed(() => {
  const first = metadata.value?.first_date?.slice(0, 4)
  const last = metadata.value?.last_date?.slice(0, 4)
  return first && last ? t('aoi.archiveRange', { first, last }) : t('aoi.archive')
})
watch(filters, (f) => {
  from.value = f.from
  to.value = f.to
  sensor.value = f.metric
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
  if (!from.value || !to.value || from.value > to.value) { validation.value = t('aoi.validationDate'); return }
  const bbox: Bbox | null = area.value === 'all' ? null : [Number(west.value), Number(south.value), Number(east.value), Number(north.value)]
  if (bbox && (!bbox.every(Number.isFinite) || bbox[0] >= bbox[2] || bbox[1] >= bbox[3])) {
    validation.value = t('aoi.validationBbox')
    return
  }
  load({ from: from.value, to: to.value, metric: sensor.value, bbox })
}
function changeSensor() {
  // El cambio de sensor usa el area y el rango ya aplicados; no pisa ediciones pendientes.
  if (!loading.value) load({ ...filters.value, metric: sensor.value })
}
function reset() {
  area.value = 'all'
  from.value = metadata.value?.first_date ?? ''
  to.value = metadata.value?.last_date ?? ''
  apply()
}
</script>
<template>
  <aside class="panel aoi" :aria-label="t('aoi.aria')">
    <div class="aoi-scroll" role="region" :aria-label="t('aoi.ariaRegion')">
      <div class="section-kicker">{{ t('aoi.kicker') }}</div><h2 class="panel-title">{{ t('aoi.title') }}</h2>
      <form id="aoi-form" @submit.prevent="apply" class="filter-form">
        <div class="field"><label for="area">{{ t('aoi.area') }}</label><select id="area" :value="area" @change="preset(($event.target as HTMLSelectElement).value)"><option value="delta">{{ t('aoi.option.delta') }}</option><option value="all">{{ t('aoi.option.all') }}</option><option value="cordoba">{{ t('aoi.option.cordoba') }}</option><option value="corrientes">{{ t('aoi.option.corrientes') }}</option><option value="custom">{{ t('aoi.option.custom') }}</option></select></div>
        <div class="field"><label for="sensor">{{ t('aoi.sensor') }}</label><select id="sensor" v-model="sensor" :disabled="loading" @change="changeSensor"><option v-for="option in sensorOptions" :key="option.id" :value="option.id">{{ option.label }}</option></select></div>
        <p v-if="sensor === 'all'" class="small-note sensor-note">{{ t('aoi.sensorNote.all') }}</p>
        <p v-else class="small-note sensor-note">{{ t('aoi.sensorNote.single') }}</p>
        <p class="small-note sensor-note">{{ t('aoi.sensorNote.scope') }}</p>
        <div v-if="area !== 'all'" class="bbox-fields">
          <div class="field"><label for="west">{{ t('aoi.west') }}</label><input id="west" v-model.number="west" type="number" step="any" min="-180" max="180" required></div>
          <div class="field"><label for="east">{{ t('aoi.east') }}</label><input id="east" v-model.number="east" type="number" step="any" min="-180" max="180" required></div>
          <div class="field"><label for="south">{{ t('aoi.south') }}</label><input id="south" v-model.number="south" type="number" step="any" min="-90" max="90" required></div>
          <div class="field"><label for="north">{{ t('aoi.north') }}</label><input id="north" v-model.number="north" type="number" step="any" min="-90" max="90" required></div>
          <p class="small-note bbox-note">{{ t('aoi.bboxNote') }}</p>
        </div>
        <div class="date-fields"><div class="field"><label for="from">{{ t('aoi.from') }}</label><input id="from" v-model="from" type="date" :min="dateMin" :max="dateMax" required></div><div class="field"><label for="to">{{ t('aoi.to') }}</label><input id="to" v-model="to" type="date" :min="dateMin" :max="dateMax" required></div></div>
      </form>
      <div class="source-box" id="datos"><span class="section-kicker">{{ t('aoi.dataKicker') }}</span><p>{{ t('aoi.dataModis') }}<br>{{ t('aoi.dataViirs') }}</p><p class="small-note">{{ t('aoi.dataNote') }}</p><p v-if="metadata" class="small-note">{{ t('aoi.dataDetections', { count: formatNumber(metadata.selected_rows) }) }}</p></div>
      <a
        class="btn export-btn"
        :href="exportURL"
        :download="t('aoi.calendarFile')"
        :aria-busy="csvChecking"
        @click="checkCalendarCsv"
      >{{ t('aoi.exportCalendar') }}</a>
      <a
        class="btn export-btn"
        :href="hotspotsExportURL"
        :download="t('aoi.hotspotsFile')"
        :aria-busy="csvChecking"
        :aria-label="t('aoi.exportHotspotsAria')"
        @click="checkHotspotsCsv"
      >{{ t('aoi.exportHotspots') }}</a>
      <p class="small-note export-note">{{ t('aoi.exportNote') }}</p>
      <p v-if="csvNotice" class="small-note export-note" role="status">{{ csvNotice }}</p>
      <p v-if="csvError" class="small-note export-error" role="alert">{{ csvError }}</p>
      <p class="small-note">{{ metricMode === 'cells' ? t('aoi.zeroNote.cells') : t('aoi.zeroNote.hotspots') }}</p>
    </div>
    <div class="aoi-actions">
      <p v-if="validation" class="notice aoi-error" role="alert">{{ validation }}</p>
      <button class="btn btn-primary full-width" type="submit" form="aoi-form" :disabled="loading">{{ loading ? t('aoi.submitting') : t('aoi.submit') }} <span aria-hidden="true">↗</span></button>
      <button class="link-btn" type="button" @click="reset">{{ archiveLabel }}</button>
    </div>
  </aside>
</template>
<style scoped>
.aoi{padding:0;display:flex;flex-direction:column;min-height:0}.aoi-scroll{flex:1 1 auto;min-height:0;padding:22px;scroll-padding:8px;scrollbar-width:thin;scrollbar-color:#2E96F5 transparent}.aoi-scroll::-webkit-scrollbar{width:8px}.aoi-scroll::-webkit-scrollbar-thumb{background:#2E96F5;border-radius:4px}.aoi-actions{flex:none;display:flex;flex-direction:column;gap:8px;padding:12px 22px 16px;border-top:1px solid var(--blue-100);background:var(--blue-900);box-shadow:0 -12px 18px -14px rgba(0,0,0,.85)}.aoi-error{margin:0;padding:8px 10px;font-size:12px;line-height:1.5}.filter-form{display:flex;flex-direction:column;gap:16px;margin-top:20px}.bbox-fields{display:grid;grid-template-columns:1fr 1fr;gap:12px}.bbox-note{grid-column:1/-1}.sensor-note{grid-column:1/-1;margin-top:-6px}.date-fields{display:flex;flex-direction:column;gap:14px}.source-box{border-top:1px solid var(--blue-100);margin-top:18px;padding-top:18px}.source-box p{margin:8px 0}.export-btn{width:100%;margin-top:12px;font-size:14px;text-align:center}.export-btn+.export-btn{margin-top:6px}.export-note{margin-top:8px}.export-error{color:#ffb4a8}.full-width{width:100%;justify-content:space-between}@media(min-width:1101px){.aoi{max-height:calc(100dvh - 124px)}.aoi-scroll{overflow-y:auto;overscroll-behavior:contain}}@media(min-width:720px) and (max-width:1100px){.filter-form{display:grid;grid-template-columns:1fr 1fr}.date-fields{flex-direction:row}.source-box{display:none}}
</style>
