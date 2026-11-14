<script setup lang="ts">
import { computed } from 'vue'
import { format } from '@/composables/useCalendar'
import { novemberMedian, novemberCurrent } from '@/data/sampleCalendar'

const pct = computed(() =>
  Math.round((novemberCurrent / novemberMedian - 1) * 100),
)

/* Circunferencia de radio 50: el arco "valor" mide lo que avanza el mes actual
   sobre una escala donde la mediana histórica (2.325) es el punto medio. */
const RADIUS = 50
const CIRC = 2 * Math.PI * RADIUS
const maxScale = novemberCurrent * 1.05
const valueLen = computed(() => CIRC * (novemberCurrent / maxScale))
</script>

<template>
  <section class="panel" aria-labelledby="cmp-title">
    <h2 id="cmp-title" class="panel-title">Noviembre 2026 vs. mediana</h2>

    <div class="gauge" role="img" :aria-label="`Noviembre 2026 supera en un ${pct}% la mediana histórica`">
      <svg viewBox="0 0 120 120" class="gauge-svg">
        <circle class="gauge-track" cx="60" cy="60" r="50" />
        <circle
          class="gauge-value"
          cx="60"
          cy="60"
          r="50"
          :stroke-dasharray="`${valueLen} ${CIRC}`"
          stroke-dashoffset="0"
          transform="rotate(-90 60 60)"
        />
        <line
          class="gauge-median"
          x1="60"
          y1="10"
          x2="60"
          y2="26"
          transform="rotate(-90 60 60) rotate(0 60 10)"
        />
        <text x="60" y="58" text-anchor="middle" class="gauge-num">+{{ pct }}%</text>
        <text x="60" y="74" text-anchor="middle" class="gauge-cap">vs mediana</text>
      </svg>
    </div>

    <dl class="rows">
      <div class="row">
        <dt>Mediana histórica 2017–2025</dt>
        <dd class="row-val row-val--base">{{ format(novemberMedian) }}</dd>
      </div>
      <div class="row">
        <dt>Noviembre 2026 (al 14/11)</dt>
        <dd class="row-val row-val--hot">{{ format(novemberCurrent) }}</dd>
      </div>
    </dl>

    <p class="note">
      <strong class="note-strong">Por encima de lo normal:</strong>
      la actividad supera la mediana en un <strong>{{ pct }}%</strong>.
    </p>
  </section>
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
}

.gauge {
  width: 132px;
  margin: var(--space-4) auto;
}

.gauge-svg {
  width: 100%;
  height: auto;
  display: block;
}

.gauge-track {
  fill: none;
  stroke: var(--surface-input);
  stroke-width: 12;
}

.gauge-value {
  fill: none;
  stroke: var(--warm-orange);
  stroke-width: 12;
  stroke-linecap: round;
}

.gauge-median {
  stroke: var(--warm-lime);
  stroke-width: 3;
}

.gauge-num {
  font-family: var(--font-display);
  font-weight: 900;
  font-size: 20px;
  fill: var(--warm-lime);
}

.gauge-cap {
  font-size: 9px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  fill: var(--white);
  opacity: 0.8;
}

.rows {
  margin: 0;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 1px solid rgba(9, 96, 225, 0.35);
}

.row dt {
  font-size: 14px;
  opacity: 0.9;
}

.row-val {
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 20px;
  white-space: nowrap;
}

.row-val--hot {
  color: var(--warm-orange);
}

.note {
  margin: var(--space-4) 0 0;
  font-size: 15px;
  line-height: 1.45;
}

.note-strong {
  color: var(--warm-lime);
}
</style>