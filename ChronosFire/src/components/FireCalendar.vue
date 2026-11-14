<script setup lang="ts">
import { ref, computed } from 'vue'
import { useCalendar, format } from '@/composables/useCalendar'
import { useColorRamp, type RampId } from '@/composables/useColorRamp'

const { years, cellMeta, monthShort, monthName, annualTotal } = useCalendar()
const { rampId, color, textOn, label } = useColorRamp()

const selected = ref<{ year: number; month: number } | null>(null)

const ramps: { id: RampId; desc: string }[] = [
  { id: 'anomaly', desc: 'Resalta meses por encima de lo normal' },
  { id: 'sequential', desc: 'Secuencial, seguro para daltónicos' },
]

const selectionInfo = computed(() => {
  if (!selected.value) return null
  const meta = cellMeta(selected.value.year, selected.value.month)
  if (meta.value === 0) return null
  const pct = Math.round(meta.vsMedian * 100)
  return {
    year: selected.value.year,
    month: selected.value.month,
    ...meta,
    pct,
  }
})

function styleCell(year: number, month: number) {
  const meta = cellMeta(year, month)
  const bg =
    meta.value === 0 ? 'transparent' : color(meta.norm)
  const fg = meta.value === 0 ? 'var(--blue-100)' : textOn(bg)
  const isSel = selected.value?.year === year && selected.value?.month === month
  return {
    background: bg,
    color: fg,
    border: meta.value === 0 ? '1px dashed var(--blue-100)' : '1px solid transparent',
    outline: meta.aboveMedian && meta.value > 0 ? '1px solid var(--warm-orange)' : isSel ? '2px solid #fff' : 'none',
    outlineOffset: meta.aboveMedian ? '-1px' : '0',
  }
}

function selectCell(year: number, month: number) {
  selected.value = { year, month }
}
</script>

<template>
  <section class="panel" id="calendario" aria-labelledby="cal-title">
    <header class="panel-head">
      <h2 id="cal-title" class="panel-title">Hotspots por mes y año</h2>
      <span class="head-note">Haz clic en una celda para ver el detalle · los meses con borde naranja superan la mediana</span>
    </header>

    <div class="ramp-tabs" role="group" aria-label="Modo de color del calendario">
      <button
        v-for="r in ramps"
        :key="r.id"
        type="button"
        class="ramp-tab"
        :class="{ 'is-active': rampId === r.id }"
        :aria-pressed="rampId === r.id"
        @click="rampId = r.id"
      >
        <span class="ramp-name">{{ label(r.id) }}</span>
        <span class="ramp-desc">{{ r.desc }}</span>
      </button>
    </div>

    <div class="cal-scroll">
      <table class="cal" role="grid" aria-label="Calendario de actividad de incendios por mes y año">
        <thead>
          <tr>
            <th class="cal-corner" scope="col"><span class="sr-only">Año</span></th>
            <th v-for="m in 12" :key="m" class="cal-mlab" scope="col">
              <abbr :title="`Mes ${m}`">{{ monthShort(m - 1) }}</abbr>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="year in years" :key="year">
            <th class="cal-ylab" scope="row">
              <span class="year-num">{{ year }}</span>
              <span class="year-total" v-if="annualTotal(year) > 0">{{ format(annualTotal(year)) }}</span>
            </th>
            <td
              v-for="m in 12"
              :key="m"
              class="cal-cell"
              :style="styleCell(year, m - 1)"
              :data-empty="cellMeta(year, m - 1).value === 0"
              :aria-label="cellMeta(year, m - 1).label"
              role="gridcell"
              :tabindex="cellMeta(year, m - 1).value > 0 ? 0 : -1"
              @click="selectCell(year, m - 1)"
              @keydown.enter="selectCell(year, m - 1)"
              @keydown.space.prevent="selectCell(year, m - 1)"
            >
              {{ cellMeta(year, m - 1).value === 0 ? '—' : format(cellMeta(year, m - 1).value) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="selectionInfo" class="detail" role="status" aria-live="polite">
      <div class="detail-kicker">{{ selectionInfo.year }} · {{ monthName(selectionInfo.month) }}</div>
      <div class="detail-value">{{ format(selectionInfo.value) }} hotspots</div>
      <div class="detail-note" :class="{ 'detail-note--up': selectionInfo.pct > 0 }">
        {{ selectionInfo.pct > 0 ? '▲' : '▼' }} {{ Math.abs(selectionInfo.pct) }}% respecto a la mediana histórica del mes
      </div>
    </div>
    <p v-else class="detail-empty">Selecciona un mes para compararlo con su historia.</p>
  </section>
</template>

<style scoped>
.panel {
  background: var(--surface-card);
  border: 1px solid var(--blue-400);
  border-radius: var(--radius-md);
  padding: var(--space-5);
}

.panel-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.panel-title {
  font-size: 22px;
  font-weight: 800;
}

.head-note {
  font-size: 13px;
  opacity: 0.8;
}

.ramp-tabs {
  display: inline-flex;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
  flex-wrap: wrap;
}

.ramp-tab {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  font-family: var(--font-body);
  border: 1px solid var(--blue-100);
  background: transparent;
  color: var(--white);
  border-radius: var(--radius-sm);
  padding: 8px 14px;
  cursor: pointer;
  min-height: 20px;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.ramp-tab.is-active {
  background: var(--surface-input);
  border-color: var(--warm-lime);
}

.ramp-name {
  font-weight: 700;
  font-size: 14px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.ramp-desc {
  font-size: 12px;
  opacity: 0.75;
}

.cal-scroll {
  overflow-x: auto;
  padding-bottom: 6px;
}

.cal {
  border-collapse: collapse;
  min-width: 760px;
  width: 100%;
}

.cal-mlab {
  font-weight: 700;
  font-size: 13px;
  text-align: center;
  padding: 4px 2px;
  color: var(--blue-100);
}

.cal-mlab abbr {
  text-decoration: none;
}

.cal-ylab {
  font-weight: 700;
  font-size: 14px;
  text-align: right;
  padding-right: 10px;
  white-space: nowrap;
}

.year-num {
  display: block;
  font-family: var(--font-display);
  font-weight: 800;
}

.year-total {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--warm-lime);
  opacity: 0.9;
}

.cal-corner {
  width: 74px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.cal-cell {
  height: 40px;
  min-width: 48px;
  border-radius: 6px;
  text-align: center;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 13px;
  cursor: pointer;
  transition: transform 0.1s ease, box-shadow 0.1s ease, filter 0.1s ease;
}

.cal-cell[data-empty='true'] {
  cursor: default;
}

.cal-cell:not([data-empty='true']):hover {
  transform: scale(1.06);
  filter: brightness(1.08);
  z-index: 2;
}

.detail {
  margin-top: var(--space-4);
  background: var(--surface-input);
  border: 1px solid var(--blue-100);
  border-radius: var(--radius-sm);
  padding: var(--space-4);
}

.detail-kicker {
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--blue-100);
}

.detail-value {
  font-family: var(--font-display);
  font-weight: 900;
  font-size: 28px;
  margin: 2px 0;
}

.detail-note {
  font-size: 15px;
  font-weight: 700;
  color: var(--warm-orange);
}

.detail-note--up {
  color: var(--warm-orange);
}

.detail-empty {
  margin-top: var(--space-4);
  font-size: 15px;
  opacity: 0.85;
}
</style>