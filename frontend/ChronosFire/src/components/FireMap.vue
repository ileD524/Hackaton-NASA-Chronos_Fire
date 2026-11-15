<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as L from 'leaflet'
import { useChronos, fetchJSON, params } from '@/composables/useChronos'
import { useCalendar, format } from '@/composables/useCalendar'
import type { MapData, MapPoint } from '@/data/types'
const emit = defineEmits<{ (e: 'open-calendar'): void }>()
const { selectedMonth, filters, load, metadata } = useChronos()
const { monthName } = useCalendar()
const mapEl = ref<HTMLDivElement | null>(null)
const data = ref<MapData | null>(null)
const busy = ref(false)
const error = ref('')
let map: L.Map | undefined
let points: L.LayerGroup | undefined
let bboxLayer: L.Rectangle | undefined
let resize: ResizeObserver | undefined
let controller: AbortController | undefined
const title = computed(() => selectedMonth.value ? `${monthName(selectedMonth.value.month)} ${selectedMonth.value.year}` : 'Elegí un mes')
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
        fillColor: '#EAFE07',
        color: '#07173F',
        weight: .5,
        fillOpacity: .8,
      })
        .bindPopup(`<strong>${format(p.hotspots)} hotspots</strong><br>${format(p.fireDays)} días con actividad<br><small>${breakdown || 'Sin hotspots'} · Celda ${p.gridId} · 0,05°</small>`)
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
onMounted(async () => {
  if (!mapEl.value) return
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
onBeforeUnmount(() => { controller?.abort(); resize?.disconnect(); map?.remove() })
</script>
<template>
<section class="panel map-panel" id="mapa" aria-labelledby="mapa-title">
  <header class="map-head"><div><span class="section-kicker">ACTIVIDAD DEL MES</span><h2 id="mapa-title" class="panel-title">{{title}}</h2></div><button class="chip" @click="emit('open-calendar')">Elegir otro mes ↗</button></header>
  <div class="map-toolbar"><span v-if="data">{{format(data.total_hotspots)}} hotspots en {{format(data.shown_groups)}} celdas mostradas</span><span v-else>{{busy?'Cargando celdas…':'Sin mes seleccionado'}}</span><div><button class="link-btn" @click="fitArea">Centrar área</button><button class="link-btn" @click="useView">Usar vista como filtro</button></div></div>
  <div class="map-canvas"><div ref="mapEl" class="map" role="region" aria-label="Mapa interactivo de celdas con actividad"></div><div v-if="busy" class="map-notice" role="status">Consultando el mes…</div><div v-if="error" class="map-notice" role="alert">{{error}}</div><div v-else-if="data&&!data.points.length" class="map-notice">No hay celdas con actividad en este mes y área.</div></div>
  <div class="map-legend"><span><i class="dot activity"></i>Celdas con actividad</span></div>
  <footer class="map-foot"><p>Grilla de 0,05° (~5 km). El mapa muestra hotspots; el calendario puede alternar entre celdas con actividad y hotspots. El tamaño indica días con actividad y los hotspots de distintos sensores no se suman como promedio.</p><p v-if="data">Se muestran hasta 5.000 celdas ordenadas por días con actividad; los totales corresponden a lo mostrado.{{ data.points.length >= 5000 ? ' Vista parcial: acotá el área o el mes para ver el resto.' : '' }}</p></footer>
</section>
</template>
<style scoped>
.map-panel{padding:0;overflow:hidden}.map-head{display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap;padding:22px 24px 12px}.map-toolbar{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:0 24px 14px;font-size:13px;flex-wrap:wrap}.map-toolbar>div{display:flex;gap:16px}.map-canvas{position:relative}.map{height:400px;background:#07173F;z-index:1}.map-notice{position:absolute;left:16px;bottom:16px;z-index:450;padding:10px 14px;background:#07173F;border:1px solid #2E96F5;border-radius:6px;font-size:13px;max-width:80%}.map-legend{display:flex;gap:20px;flex-wrap:wrap;padding:14px 24px 0;font-size:12px}.map-legend span{display:flex;gap:7px;align-items:center}.dot{width:9px;height:9px;display:inline-block;border-radius:50%}.activity{background:#EAFE07}.map-foot{padding:8px 24px 16px;font-size:12px}.map-foot p{margin:6px 0}:deep(.leaflet-control-attribution){background:#07173F;color:white}:deep(.leaflet-control-attribution a){color:white}:deep(.leaflet-control-zoom a){background:#07173F;color:white}:deep(.leaflet-popup-content-wrapper),:deep(.leaflet-popup-tip){background:white;color:#07173F}:deep(.leaflet-tooltip){background:white;color:#07173F}@media(max-width:720px){.map{height:330px}.map-head,.map-toolbar{padding-left:16px;padding-right:16px}}
</style>
