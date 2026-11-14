<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  rangeLabel: string
}>()

const emit = defineEmits<{
  (e: 'range-change', range: { from: string; to: string }): void
}>()

const lat = ref('-25,30')
const lon = ref('-57,45')
const from = ref('2017-01-01')
const to = ref('2026-11-14')

/** Puntos ilustrativos de hotspots sobre el área; el primer grupo es de alta intensidad. */
const hotspots = [
  { x: 110, y: 85, r: 7, fire: true },
  { x: 150, y: 105, r: 6, fire: true },
  { x: 175, y: 78, r: 8, fire: true },
  { x: 190, y: 120, r: 6, fire: true },
  { x: 125, y: 130, r: 7, fire: true },
  { x: 70, y: 150, r: 4, fire: false },
  { x: 230, y: 90, r: 4, fire: false },
  { x: 205, y: 150, r: 4, fire: false },
]

function applyRange() {
  emit('range-change', { from: from.value, to: to.value })
}
</script>

<template>
  <aside class="panel aoi" aria-label="Área de interés">
    <h2 class="panel-title">Área de interés</h2>

    <svg
      viewBox="0 0 300 190"
      role="img"
      aria-label="Mapa ilustrativo con el polígono del área seleccionada sobre la región de interés"
      class="aoi-map"
    >
      <g class="grid" stroke="#2E96F5" stroke-width="1" opacity="0.5">
        <path d="M0 47H300M0 95H300M0 142H300M75 0V190M150 0V190M225 0V190" fill="none" />
      </g>
      <path
        class="aoi-fill"
        d="M40 150 C70 110 60 70 110 55 C160 40 200 70 240 60 C260 100 230 150 180 165 C120 178 70 170 40 150Z"
      />
      <polygon
        class="aoi-stroke"
        points="70,60 220,48 245,120 150,160 80,130"
      />
      <g v-for="p in hotspots" :key="`${p.x}-${p.y}`" class="aoi-dot">
        <circle :cx="p.x" :cy="p.y" :r="p.fire ? p.r : p.r" class="aoi-circle-fire" v-if="p.fire" />
        <circle :cx="p.x" :cy="p.y" :r="p.r" class="aoi-circle" v-else />
      </g>
    </svg>

    <div class="aoi-actions">
      <button type="button" class="link-btn">Dibujar polígono</button>
      <button type="button" class="link-btn">Limpiar</button>
    </div>

    <div class="field">
      <label for="lat">Latitud · Longitud</label>
      <div class="coord-row">
        <input id="lat" v-model="lat" inputmode="decimal" type="text" />
        <input id="lon" v-model="lon" inputmode="decimal" type="text" aria-label="Longitud" />
      </div>
    </div>

    <div class="field-row">
      <div class="field">
        <label for="from">Desde</label>
        <input id="from" v-model="from" type="date" />
      </div>
      <div class="field">
        <label for="to">Hasta</label>
        <input id="to" v-model="to" type="date" />
      </div>
    </div>

    <button type="button" class="btn btn-primary" @click="applyRange">
      Actualizar calendario
    </button>

    <div class="source-box">
      <strong class="source-title">Serie armonizada</strong>
      <span class="source-desc">MODIS (Terra/Aqua) + VIIRS · NASA FIRMS</span>
    </div>

    <div class="export-row">
      <button type="button" class="btn">
        <svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M12 3v12M7 10l5 5 5-5M5 20h14" />
        </svg>
        Descargar CSV
      </button>
      <button type="button" class="link-btn">Exportar gráficos</button>
    </div>
  </aside>
</template>

<style scoped>
.panel {
  background: var(--surface-card);
  border: 1px solid var(--blue-400);
  border-radius: var(--radius-md);
  padding: var(--space-5);
}

.panel-title {
  font-size: 20px;
  font-weight: 800;
  margin-bottom: 4px;
}

.aoi-map {
  width: 100%;
  display: block;
  margin-top: 14px;
  border-radius: var(--radius-sm);
  background: var(--surface-input);
  min-height: 140px;
}

.aoi-fill {
  fill: var(--blue-400);
  opacity: 0.55;
}

.aoi-stroke {
  fill: var(--warm-lime);
  fill-opacity: 0.12;
  stroke: var(--warm-lime);
  stroke-width: 3;
  stroke-dasharray: 8 5;
}

.aoi-circle {
  fill: var(--warm-lime);
}

.aoi-circle-fire {
  fill: var(--warm-orange);
  stroke: #fff;
  stroke-width: 1.5;
}

.aoi-actions {
  display: flex;
  gap: var(--space-4);
  margin-top: var(--space-2);
  flex-wrap: wrap;
}

.link-btn {
  color: var(--warm-lime);
  font-family: var(--font-body);
  font-size: 16px;
  font-weight: 700;
  text-decoration: underline;
  background: none;
  border: 0;
  cursor: pointer;
  padding: 8px 0;
  min-height: 44px;
}

.field {
  margin-top: var(--space-4);
  display: flex;
  flex-direction: column;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
}

label {
  display: block;
  font-weight: 600;
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  margin-bottom: 6px;
}

input {
  width: 100%;
  box-sizing: border-box;
  min-height: 44px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--blue-100);
  background: var(--surface-input);
  color: var(--white);
  font: 400 15px var(--font-body);
  padding: 0 12px;
}

.coord-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2);
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 0;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font: 700 16px var(--font-body);
  min-height: 48px;
  padding: 10px 18px;
  transition: transform 0.12s ease, background 0.15s ease;
}

.btn:hover {
  transform: translateY(-1px);
}

.btn:active {
  transform: translateY(0);
}

.btn-primary {
  background: var(--warm-lime);
  color: var(--blue-900);
  width: 100%;
  margin-top: var(--space-4);
}

.ico {
  width: 20px;
  height: 20px;
}

.source-box {
  margin-top: var(--space-5);
  background: var(--surface-input);
  border: 1px solid var(--blue-100);
  border-radius: var(--radius-sm);
  padding: var(--space-3) var(--space-4);
  font-size: 14px;
  line-height: 1.4;
}

.source-title {
  color: var(--warm-lime);
  display: block;
}

.source-desc {
  opacity: 0.9;
}

.export-row {
  margin-top: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  align-items: flex-start;
}

.export-row .btn {
  background: transparent;
  border: 1px solid var(--blue-100);
  color: var(--white);
  width: 100%;
}
</style>