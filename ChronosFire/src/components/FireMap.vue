<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as L from 'leaflet'
import {
  GIBS_BASE,
  GIBS_BLACKMARBLE_URL,
  GIBS_LAYER_MODIS,
  GIBS_LAYER_VIIRS,
  GIBS_TILEMATRIXSET,
  FIRMS_WMS_PROJECTION,
  FIRMS_WMS_URL,
  FIRE_DOT_COLOR,
  FIRE_DOT_SIZE,
  INITIAL_CENTER,
  INITIAL_ZOOM,
  gibsDate,
  type MapWindow,
} from '@/data/firms'

type LiveWindow = '24h' | '48h' | '72h' | '7d'
type BaseMapId = 'dark' | 'satellite' | 'gibs'

const props = defineProps<{
  /** Ventana de fechas a dibujar (WMS-Time). `null` muestra la capa live más reciente. */
  timeWindow: MapWindow | null
}>()

const emit = defineEmits<{
  (e: 'open-calendar'): void
  (e: 'back-to-live'): void
}>()

const mapEl = ref<HTMLDivElement | null>(null)
const liveWindow = ref<LiveWindow>('24h')
const baseMapId = ref<BaseMapId>('dark')
const layerError = ref(false)

let map: L.Map | null = null
let baseLayer: L.TileLayer | null = null
let fireLayer: L.TileLayer | null = null
let resizeObserver: ResizeObserver | null = null

const LIVE_LAYERS: Record<LiveWindow, string> = {
  '24h': 'fires_viirs_24',
  '48h': 'fires_viirs_48',
  '72h': 'fires_viirs_72',
  '7d': 'fires_viirs_7',
}

const windowOptions: { id: LiveWindow; label: string }[] = [
  { id: '24h', label: '24 h' },
  { id: '48h', label: '48 h' },
  { id: '72h', label: '72 h' },
  { id: '7d', label: '7 días' },
]

const MONTHS_SHORT = [
  'ene', 'feb', 'mar', 'abr', 'may', 'jun',
  'jul', 'ago', 'sep', 'oct', 'nov', 'dic',
] as const

const windowLabel = computed(() => {
  if (!props.timeWindow) return ''
  const [year, month] = props.timeWindow.from.split('-')
  return `${MONTHS_SHORT[Number(month) - 1]} ${year}`
})

function makeBaseLayer(id: BaseMapId): L.TileLayer {
  if (id === 'gibs') {
    // En vivo: imagen VIIRS NOAA-21 reciente. Histórico: MODIS Terra del mes elegido.
    const layerId = props.timeWindow ? GIBS_LAYER_MODIS : GIBS_LAYER_VIIRS
    const date = gibsDate(props.timeWindow)
    const url = `${GIBS_BASE}/${layerId}/default/${date}/${GIBS_TILEMATRIXSET}/{z}/{y}/{x}.jpg`
    return L.tileLayer(url, {
      attribution: 'Fondo © NASA GIBS (color verdadero)',
      maxZoom: 12,
      // GIBS solo publica teselas hasta el nivel 9; arriba Leaflet escala esas.
      maxNativeZoom: 9,
    })
  }
  if (id === 'satellite') {
    return L.tileLayer(
      'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      {
        attribution: 'Imágenes © Esri, Maxar, Earthstar Geographics',
        maxZoom: 12,
      },
    )
  }
  // "Nocturno": Black Marble (luz nocturna, NASA GIBS). Sin API key.
  return L.tileLayer(GIBS_BLACKMARBLE_URL, {
    attribution: 'Noche © NASA GIBS · Black Marble (VIIRS)',
    maxZoom: 12,
    maxNativeZoom: 8,
  })
}

function buildFireLayer(): L.TileLayer {
  const params: Record<string, string> = {
    SRS: FIRMS_WMS_PROJECTION,
    // Obligatorio: el default de Leaflet para WMS es image/jpeg, y JPEG no tiene
    // canal alfa. El servidor devuelve entonces un fondo opaco que tapa el mapa base.
    format: 'image/png',
    symbols: 'circle',
    colors: FIRE_DOT_COLOR,
    size: String(FIRE_DOT_SIZE),
    transparent: 'true',
  }
  if (props.timeWindow) {
    // WMS-Time: histórico (hasta 31 días por consulta)
    params.layers = 'fires_viirs'
    params.TIME = `${props.timeWindow.from}/${props.timeWindow.to}`
  } else {
    // Live: última ventana de horas/días
    params.layers = LIVE_LAYERS[liveWindow.value]
  }
  const layer = L.tileLayer.wms(FIRMS_WMS_URL, { ...params, pane: 'fire', maxZoom: 12 })
  layer.on('tileerror', () => {
    layerError.value = true
  })
  layer.on('load', () => {
    layerError.value = false
  })
  return layer
}

function addBaseLayer(id: BaseMapId) {
  if (!map) return
  if (baseLayer) map.removeLayer(baseLayer)
  baseLayer = makeBaseLayer(id)
  baseLayer.addTo(map)
}

function rebuildFireLayer() {
  if (!map) return
  if (fireLayer) map.removeLayer(fireLayer)
  fireLayer = buildFireLayer()
  fireLayer.addTo(map)
}

onMounted(() => {
  if (!mapEl.value) return
  map = L.map(mapEl.value, {
    zoomControl: false,
    minZoom: 3,
    maxZoom: 12,
    worldCopyJump: true,
  })
  // Pane propia para la capa de incendios: siempre por encima de cualquier
  // mapa base, sin depender del orden en que se reconstruyan las capas.
  const firePane = map.createPane('fire')
  firePane.style.zIndex = '250'
  L.control.zoom({ position: 'bottomright' }).addTo(map)
  map.setView(INITIAL_CENTER, INITIAL_ZOOM)
  addBaseLayer(baseMapId.value)
  rebuildFireLayer()
  requestAnimationFrame(() => map?.invalidateSize())
  resizeObserver = new ResizeObserver(() => map?.invalidateSize())
  resizeObserver.observe(mapEl.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  map?.remove()
  map = null
  baseLayer = null
  fireLayer = null
})

// El fondo GIBS depende de la ventana de tiempo (imagen de esa época),
// por lo que se reconstruye también cuando cambia el mes seleccionado.
watch(
  [baseMapId, () => props.timeWindow],
  ([id], [prevId]) => {
    if (id !== prevId || id === 'gibs') addBaseLayer(id)
  },
)

watch(
  () => [props.timeWindow, liveWindow.value] as const,
  ([win, live], [prevWin, prevLive]) => {
    if (win === prevWin && live === prevLive) return
    rebuildFireLayer()
  },
)
</script>

<template>
  <section class="panel map-panel" id="mapa" aria-labelledby="mapa-title">
    <header class="map-head">
      <div class="map-head-left">
        <h2 id="mapa-title" class="panel-title">Incendios activos</h2>
        <span
          class="map-status"
          :class="timeWindow ? 'map-status--hist' : 'map-status--live'"
          role="status"
        >
          <span class="status-dot" aria-hidden="true"></span>
          {{ timeWindow ? `Histórico · ${windowLabel}` : 'En vivo · VIIRS NOAA-20/21' }}
        </span>
      </div>

      <div class="map-actions">
        <div v-if="!timeWindow" class="chip-group" role="group" aria-label="Ventana de tiempo en vivo">
          <button
            v-for="option in windowOptions"
            :key="option.id"
            type="button"
            class="chip"
            :class="{ 'chip--active': liveWindow === option.id }"
            :aria-pressed="liveWindow === option.id"
            @click="liveWindow = option.id"
          >
            {{ option.label }}
          </button>
        </div>
        <button v-else type="button" class="chip chip--ghost" @click="emit('back-to-live')">
          Volver a en vivo
        </button>

        <div class="chip-group" role="group" aria-label="Mapa base">
          <button
            type="button"
            class="chip"
            :class="{ 'chip--active': baseMapId === 'dark' }"
            :aria-pressed="baseMapId === 'dark'"
            @click="baseMapId = 'dark'"
          >
            Nocturno
          </button>
          <button
            type="button"
            class="chip"
            :class="{ 'chip--active': baseMapId === 'satellite' }"
            :aria-pressed="baseMapId === 'satellite'"
            @click="baseMapId = 'satellite'"
          >
            Satélite
          </button>
          <button
            type="button"
            class="chip"
            :class="{ 'chip--active': baseMapId === 'gibs' }"
            :aria-pressed="baseMapId === 'gibs'"
            @click="baseMapId = 'gibs'"
          >
            NASA GIBS
          </button>
        </div>

        <button type="button" class="chip chip--cta" @click="emit('open-calendar')">
          <svg
            class="chip-ico"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <rect x="3" y="5" width="18" height="16" rx="2" />
            <path d="M3 9h18M8 3v4M16 3v4" />
          </svg>
          Calendario
        </button>
      </div>
    </header>

    <div class="map-canvas">
      <div
        ref="mapEl"
        class="map"
        aria-label="Mapa interactivo de incendios con datos en vivo de NASA FIRMS"
      ></div>

      <div class="map-legend">
        <span class="legend-dot" aria-hidden="true"></span>
        <span class="legend-text">
          Hotspot VIIRS 375 m<template v-if="timeWindow"> · {{ windowLabel }}</template>
        </span>
      </div>

      <div v-if="layerError" class="map-notice" role="alert">
        La capa de FIRMS no se pudo cargar. Revisá tu conexión e intentá de nuevo.
      </div>
    </div>

    <footer class="map-foot">
      <span>Datos: NASA FIRMS · VIIRS (NOAA-20/21) · actualización cada ~15 min</span>
      <a
        class="map-foot-link"
        href="https://firms.modaps.eosdis.nasa.gov/mapserver/wms-info/"
        target="_blank"
        rel="noopener"
      >
        Servicio WMS
      </a>
    </footer>
  </section>
</template>

<style scoped>
.panel {
  background: var(--surface-card);
  border: 1px solid var(--blue-400);
  border-radius: var(--radius-md);
}

.map-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.map-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
  padding: var(--space-4) var(--space-5) var(--space-3);
}

.map-head-left {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2) var(--space-3);
}

.panel-title {
  font-size: 22px;
  font-weight: 800;
}

.map-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  border: 1px solid var(--blue-100);
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--white);
}

.map-status--live {
  border-color: rgba(234, 254, 7, 0.6);
}

.map-status--live .status-dot {
  background: var(--warm-lime);
  animation: status-pulse 1.6s ease-in-out infinite;
}

.map-status--hist {
  background: rgba(228, 55, 0, 0.18);
  border-color: var(--warm-orange);
}

.map-status--hist .status-dot {
  background: var(--warm-orange);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}

@keyframes status-pulse {
  0%,
  100% {
    opacity: 1;
    box-shadow: 0 0 0 0 rgba(234, 254, 7, 0.5);
  }
  50% {
    opacity: 0.6;
    box-shadow: 0 0 0 5px rgba(234, 254, 7, 0);
  }
}

.map-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.chip-group {
  display: inline-flex;
  gap: var(--space-2);
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid var(--blue-100);
  border-radius: 999px;
  background: rgba(7, 23, 63, 0.35);
  color: var(--white);
  font: 700 13px var(--font-body);
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.12s ease;
}

.chip:hover {
  background: var(--surface-hover);
  transform: translateY(-1px);
}

.chip--active {
  background: var(--surface-input);
  border-color: var(--warm-lime);
}

.chip--ghost {
  border-style: dashed;
}

.chip--cta {
  background: var(--warm-lime);
  border-color: var(--warm-lime);
  color: var(--blue-900);
}

.chip--cta:hover {
  background: #f4ff5e;
}

.chip-ico {
  width: 16px;
  height: 16px;
}

.map-canvas {
  position: relative;
}

.map {
  /* z-index: 0 crea un contexto de apilamiento: la leyenda y el aviso de
     error (hermanos absolutos) quedan por encima de los paneles de Leaflet */
  position: relative;
  z-index: 0;
  width: 100%;
  height: 520px;
  background: var(--blue-900);
}

.map-legend {
  position: absolute;
  top: 12px;
  left: 12px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border: 1px solid var(--blue-700);
  border-radius: 999px;
  background: rgba(7, 23, 63, 0.82);
  backdrop-filter: blur(4px);
  font-size: 12px;
  font-weight: 600;
  color: var(--white);
}

.legend-dot {
  width: 10px;
  height: 10px;
  flex: none;
  border-radius: 50%;
  background: #ff7300;
  box-shadow: 0 0 8px rgba(255, 115, 0, 0.9);
}

.map-notice {
  position: absolute;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  background: var(--warm-red);
  color: var(--white);
  font-size: 13px;
  font-weight: 600;
  box-shadow: var(--shadow-card);
}

.map-foot {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-2) var(--space-3);
  padding: 10px var(--space-5);
  border-top: 1px solid rgba(46, 150, 245, 0.25);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.72);
}

.map-foot-link {
  color: var(--blue-100);
  font-weight: 600;
  text-decoration: underline;
}

/* Controles de Leaflet adaptados al tema oscuro */
:deep(.leaflet-control-attribution) {
  background: rgba(7, 23, 63, 0.8);
  color: rgba(255, 255, 255, 0.7);
  font-size: 11px;
}

:deep(.leaflet-control-attribution a) {
  color: var(--blue-100);
}

:deep(.leaflet-control-zoom a) {
  background: rgba(10, 32, 80, 0.92);
  border-color: var(--blue-700);
  color: var(--white);
}

:deep(.leaflet-control-zoom a:hover) {
  background: var(--surface-input);
}

@media (max-width: 1100px) {
  .map {
    height: 420px;
  }
}

@media (max-width: 720px) {
  .map {
    height: 340px;
  }

  .map-head {
    padding: var(--space-4);
  }
}
</style>
