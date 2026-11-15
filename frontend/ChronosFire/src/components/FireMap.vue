<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as L from 'leaflet'
import { useChronos, fetchJSON, params } from '@/composables/useChronos'
import { useCalendar, format } from '@/composables/useCalendar'
import { colorAt, SEQUENTIAL_STOPS } from '@/composables/useColorRamp'
import { SENSOR_OPTIONS } from '@/data/types'
import type { MapData, MapPoint } from '@/data/types'
const emit = defineEmits<{ (e: 'open-calendar'): void }>()
const { selectedMonth, filters, load, metadata } = useChronos()
const { monthName } = useCalendar()
const mapEl = ref<HTMLDivElement | null>(null)
const data = ref<MapData | null>(null)
const busy = ref(false)
const error = ref('')
const expanded = ref(false)
const expandButton = ref<HTMLButtonElement | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)
let map: L.Map | undefined
let points: L.LayerGroup | undefined
let bboxLayer: L.Rectangle | undefined
let resize: ResizeObserver | undefined
let controller: AbortController | undefined
const title = computed(() => selectedMonth.value ? `${monthName(selectedMonth.value.month)} ${selectedMonth.value.year}` : 'Elegí un mes')
const sensorLabel = computed(() =>
  SENSOR_OPTIONS.find((option) => option.id === filters.value.metric)?.label ?? filters.value.metric)
const probabilityLine = (p: MapPoint) => {
  if (p.fireProbability === null || p.probabilityReliability === 'insufficient') {
    return 'Probabilidad histórica: sin respaldo'
  }
  const interval = p.probabilityLow !== null && p.probabilityHigh !== null
    ? ` · IC 90 % ${format(p.probabilityLow * 100)}–${format(p.probabilityHigh * 100)} %`
    : ''
  return `Probabilidad histórica: ${format(p.fireProbability * 100)} % (n=${p.probabilityRefYears ?? '—'}${interval})`
}
const timeWindow = computed(() => {
  const m = selectedMonth.value
  if (!m) return null
  const from = `${m.year}-${String(m.month + 1).padStart(2, '0')}-01`
  const end = new Date(Date.UTC(m.year, m.month + 1, 0)).toISOString().slice(0, 10)
  return { from: from < filters.value.from ? filters.value.from : from, to: end > filters.value.to ? filters.value.to : end }
})
function fitArea() {
  if (!map) return
  const b = filters.value.bbox
  if (b) map.fitBounds([[b[1], b[0]], [b[3], b[2]]], { padding: [24, 24] })
  else map.fitBounds([[-43, -72], [-22, -53]], { padding: [12, 12] })
}
function updateBox() {
  if (!map) return
  if (bboxLayer) map.removeLayer(bboxLayer)
  const b = filters.value.bbox
  if (b) bboxLayer = L.rectangle([[b[1], b[0]], [b[3], b[2]]], { color: '#EAFE07', weight: 2, fill: false, dashArray: '6 4', interactive: false }).addTo(map)
}
async function refresh() {
  if (!map) return
  if (!timeWindow.value) { controller?.abort(); points?.clearLayers(); data.value = null; busy.value = false; return }
  controller?.abort()
  const current = new AbortController()
  controller = current
  busy.value = true
  error.value = ''
  points?.clearLayers()
  data.value = null
  try {
    const query = params({ ...filters.value, ...timeWindow.value })
    const rows = await fetchJSON<MapPoint[]>(`/api/calendar/heatmap?${query}&limit=5000`, current.signal)
    if (current.signal.aborted) return
    const total = rows.reduce((sum, row) => sum + row.hotspots, 0)
    // Quintiles de hotspots de las celdas mostradas: el color doble-codifica
    // la intensidad, mientras el radio sigue indicando días con actividad.
    const values = rows.map((row) => row.hotspots).filter((value) => value > 0).sort((a, b) => a - b)
    const cuts = [0.2, 0.4, 0.6, 0.8].map((p) =>
      values.length ? values[Math.min(values.length - 1, Math.floor(p * (values.length - 1)))]! : 0,
    )
    const bucketOf = (value: number) => {
      let bucket = 0
      while (bucket < 4 && value > cuts[bucket]!) bucket++
      return bucket
    }
    data.value = {
      points: rows,
      total_groups: rows.length,
      shown_groups: rows.length,
      total_hotspots: total,
      shown_hotspots: total,
      aggregation_km: metadata.value?.grid_resolution_deg ?? 0,
    }
    for (const p of rows) {
      const breakdown = Object.entries(p.hotspotsBySensor ?? {})
        .map(([sensor, value]) => `${sensor} ${format(value)}`)
        .join(' · ')
      L.circleMarker([p.cellLat, p.cellLon], {
        radius: Math.min(9, 2 + Math.log1p(p.fireDays) * .9),
        fillColor: colorAt(bucketOf(p.hotspots)),
        color: '#FFFFFF',
        weight: .8,
        opacity: .85,
        fillOpacity: .88,
      })
        .bindPopup(`<strong>${format(p.hotspots)} hotspots</strong><br>${format(p.fireDays)} días con actividad<br><small>${probabilityLine(p)}</small><br><small>${breakdown || 'Sin hotspots'} · Celda ${p.gridId} · 0,05°</small>`)
        .addTo(points!)
    }
    updateBox()
  } catch (e) {
    if (!(e instanceof DOMException && e.name === 'AbortError')) error.value = e instanceof Error ? e.message : 'Error al cargar el mapa.'
  } finally {
    if (controller === current) busy.value = false
  }
}
function useView() {
  if (!map) return
  const b = map.getBounds()
  load({ ...filters.value, bbox: [Math.max(-180, b.getWest()), Math.max(-90, b.getSouth()), Math.min(180, b.getEast()), Math.min(90, b.getNorth())] })
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && expanded.value) expanded.value = false
}
watch(expanded, async (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
  await nextTick()
  map?.invalidateSize()
  if (open) closeButton.value?.focus()
  else expandButton.value?.focus()
})
onMounted(async () => {
  if (!mapEl.value) return
  window.addEventListener('keydown', onKeydown)
  map = L.map(mapEl.value, { preferCanvas: true, minZoom: 3, maxZoom: 13, zoomControl: false, attributionControl: true })
  L.control.zoom({ position: 'bottomright' }).addTo(map)
  map.attributionControl.addAttribution('Cartografía: Natural Earth · Datos: NASA FIRMS')
  points = L.layerGroup().addTo(map)
  fitArea()
  resize = new ResizeObserver(() => map?.invalidateSize())
  resize.observe(mapEl.value)
  refresh()
  try {
    const geo = await fetch('/maps/south-america.geojson').then((r) => r.json())
    if (map) L.geoJSON(geo, { style: { fillColor: '#0042A6', fillOpacity: .45, color: '#2E96F5', weight: .8 }, onEachFeature: (f, l) => l.bindTooltip(f.properties.NAME_ES ?? f.properties.NAME) }).addTo(map).bringToBack()
  } catch { error.value = 'No se pudo cargar la cartografía local.' }
})
watch([timeWindow, () => filters.value.metric, () => filters.value.bbox], () => refresh())
watch(() => filters.value.bbox, () => { updateBox(); fitArea() })
onBeforeUnmount(() => {
  controller?.abort()
  resize?.disconnect()
  map?.remove()
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>
<template>
<div v-if="expanded" class="map-backdrop" aria-hidden="true" @click="expanded = false"></div>
<section class="panel map-panel" :class="{ 'is-expanded': expanded }" id="mapa" aria-labelledby="mapa-title" :role="expanded ? 'dialog' : undefined" :aria-modal="expanded ? 'true' : undefined">
  <header class="map-head"><div><span class="section-kicker">ACTIVIDAD DEL MES</span><h2 id="mapa-title" class="panel-title">{{title}}</h2></div><div class="map-actions"><button v-if="!expanded" ref="expandButton" class="link-btn" type="button" @click="expanded = true">Ampliar mapa</button><button v-if="!expanded" class="chip" @click="emit('open-calendar')">Elegir otro mes ↗</button><button v-else ref="closeButton" class="chip" type="button" @click="expanded = false">Cerrar</button></div></header>
  <div class="map-toolbar"><span v-if="data" :title="'Las detecciones de distintos sensores no se suman'">{{format(data.shown_groups)}} celdas con actividad · {{format(data.total_hotspots)}} hotspots (todas las fuentes)</span><span v-else>{{busy?'Cargando celdas…':'Sin mes seleccionado'}}</span><div><span class="sensor-tag">Serie: {{ sensorLabel }}</span><button class="link-btn" @click="fitArea">Centrar área</button><button class="link-btn" @click="useView">Usar vista como filtro</button></div></div>
  <div class="map-canvas"><div ref="mapEl" class="map" role="region" aria-label="Mapa interactivo de celdas con actividad"></div><div v-if="busy" class="map-notice" role="status">Consultando el mes…</div><div v-if="error" class="map-notice" role="alert">{{error}}</div><div v-else-if="data&&!data.points.length" class="map-notice">No hay celdas con actividad en este mes y área.</div></div>
  <div class="map-legend"><span class="legend-item legend-ramp"><span class="ramp-caption">Intensidad de hotspots · quintiles de lo mostrado</span><span class="ramp" role="img" aria-label="Escala de intensidad de hotspots de menor a mayor"><i v-for="bg in SEQUENTIAL_STOPS" :key="bg" :style="{ background: bg }"></i></span><span class="ramp-minmax" aria-hidden="true">bajo → alto</span></span><span class="legend-item size-key"><i class="dot size-small" aria-hidden="true"></i><i class="dot size-big" aria-hidden="true"></i>Tamaño: días con actividad</span></div>
  <footer class="map-foot"><details class="map-howto"><summary>Cómo leer este mapa</summary><p>Grilla de 0,05° (~5 km). Doble codificación: el color indica la intensidad de hotspots (quintiles de las celdas mostradas) y el tamaño, los días con actividad. El mapa muestra hotspots; el calendario puede alternar entre celdas con actividad y hotspots. Los hotspots de distintos sensores no se suman como promedio.</p><p v-if="data">Se muestran hasta 5.000 celdas ordenadas por días con actividad; los totales corresponden a lo mostrado.{{ data.points.length >= 5000 ? ' Vista parcial: acotá el área o el mes para ver el resto.' : '' }}</p></details></footer>
</section>
</template>
<style scoped>
.map-panel{padding:0;overflow:hidden}.map-head{display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap;padding:22px 24px 12px}.map-actions{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.map-backdrop{position:fixed;inset:0;background:rgba(3,10,26,.62);backdrop-filter:blur(8px);z-index:1000}
.map-panel.is-expanded{position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);z-index:1001;width:min(1180px,calc(100vw - 32px),max(560px,calc((100dvh - 340px) * 4 / 3 + 56px)));max-width:none;max-height:calc(100dvh - 32px);overflow:auto;display:flex;flex-direction:column;border-radius:var(--radius-lg);box-shadow:0 40px 90px -30px rgba(0,0,0,.9)}
.map-panel.is-expanded .map-head{padding:26px 28px 10px}
.map-panel.is-expanded .section-kicker{color:var(--warm-lime)}
.map-panel.is-expanded .panel-title{font-size:32px}
.map-panel.is-expanded .map-actions .chip{border-color:var(--warm-lime);border-radius:8px;padding:9px 20px;font-size:14px;font-weight:700}
.map-panel.is-expanded .map-toolbar{padding:0 28px 16px;font-size:14px}
.map-panel.is-expanded .map-toolbar>div{margin-left:auto}
.map-panel.is-expanded .map-toolbar .link-btn{white-space:nowrap}
.map-panel.is-expanded .map-canvas{display:flex;justify-content:center;padding:0 28px}
.map-panel.is-expanded:has(.map-howto[open]) .map{width:min(100%,max(240px,calc((100dvh - 480px) * 4 / 3)))}
.map-panel.is-expanded .map{width:100%;height:auto;aspect-ratio:4 / 3;border:1px solid rgba(46,150,245,.4);border-radius:10px;overflow:hidden}
.map-panel.is-expanded .map-legend{padding:14px 28px 0}
.map-panel.is-expanded .map-foot{padding:6px 28px 20px}
.map-howto{font-size:13px}.map-howto summary{display:flex;align-items:center;gap:8px;min-height:32px;cursor:pointer;color:var(--warm-lime);font-weight:700;list-style:none}.map-howto summary::-webkit-details-marker{display:none}.map-howto summary::before{content:'▸';font-size:11px;line-height:1}.map-howto[open] summary::before{content:'▾'}.map-foot .map-howto p{margin:8px 0 0;line-height:1.6}.map-foot .map-howto p:first-of-type{margin-top:2px}.map-toolbar{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:0 24px 14px;font-size:13px;flex-wrap:wrap}.map-toolbar>div{display:flex;gap:16px;align-items:center}.sensor-tag{font-size:12px;opacity:.9;white-space:nowrap}.map-canvas{position:relative}.map{height:400px;background:#07173F;z-index:1}.map-notice{position:absolute;left:16px;bottom:16px;z-index:450;padding:10px 14px;background:#07173F;border:1px solid #2E96F5;border-radius:6px;font-size:13px;max-width:80%}.map-legend{display:flex;gap:12px;flex-wrap:wrap;align-items:center;padding:14px 24px 0;font-size:12px}.map-legend span{display:flex;gap:7px;align-items:center}.legend-ramp{flex-wrap:wrap;row-gap:6px}.ramp-caption{font-weight:700}.ramp{display:inline-flex;width:90px;height:12px;border:1px solid #2E96F5;border-radius:3px;overflow:hidden;gap:0!important}.ramp i{flex:1;height:100%}.ramp-minmax{opacity:.85}.size-key{gap:5px!important}.dot{width:9px;height:9px;display:inline-block;border-radius:50%;background:#FDE725;border:1px solid #FFFFFF}.size-small{width:6px;height:6px}.size-big{width:12px;height:12px}.map-foot{padding:8px 24px 16px;font-size:12px}.map-foot p{margin:6px 0}:deep(.leaflet-control-attribution){background:#07173F;color:white}:deep(.leaflet-control-attribution a){color:white}:deep(.leaflet-control-zoom){border:1px solid var(--blue-100);border-radius:8px;overflow:hidden;box-shadow:none}:deep(.leaflet-control-zoom a){width:34px;height:34px;line-height:32px;background:#07173F;color:white;border:0;border-bottom:1px solid rgba(46,150,245,.55);font-size:18px}:deep(.leaflet-control-zoom a:last-child){border-bottom:0}:deep(.leaflet-control-zoom a:hover){background:#0042A6;color:white}:deep(.leaflet-popup-content-wrapper),:deep(.leaflet-popup-tip){background:white;color:#07173F}:deep(.leaflet-tooltip){background:white;color:#07173F}@media(max-width:720px){.map{height:330px}.map-panel.is-expanded{width:calc(100vw - 16px);max-height:calc(100dvh - 16px);border-radius:12px}.map-panel.is-expanded .panel-title{font-size:26px}.map-panel.is-expanded .map{height:min(48dvh,520px);min-height:240px;aspect-ratio:auto;border-radius:8px}.map-panel.is-expanded:has(.map-howto[open]) .map{width:100%;height:min(42dvh,400px)}.map-panel.is-expanded .map-toolbar{flex-direction:column;align-items:flex-start;gap:10px}.map-panel.is-expanded .map-toolbar>div{margin-left:0;flex-wrap:wrap;gap:8px 16px}.map-panel.is-expanded .legend-ramp .ramp-caption{flex:1 0 100%}.map-panel.is-expanded .map-head,.map-panel.is-expanded .map-toolbar,.map-panel.is-expanded .map-canvas,.map-panel.is-expanded .map-legend,.map-panel.is-expanded .map-foot{padding-left:16px;padding-right:16px}.map-head,.map-toolbar{padding-left:16px;padding-right:16px}}
</style>
