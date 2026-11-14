<script setup lang="ts">
import { computed } from 'vue'
import { format, useCalendar } from '@/composables/useCalendar'
import {
  totalHotspots,
  peakMonth,
  peakYear,
  novemberMedian,
  novemberCurrent,
  yearEnd,
} from '@/data/sampleCalendar'

const { annualTotal } = useCalendar()

const kpis = computed(() => {
  const anomaly = Math.round((novemberCurrent / novemberMedian - 1) * 100)
  return [
    {
      value: format(totalHotspots),
      label: 'hotspots detectados 2017–2026',
      accent: true,
      border: false,
    },
    {
      value: `${monthShort(peakMonth.month)} ${peakMonth.year}`,
      sub: format(peakMonth.value),
      label: 'mes con más actividad',
      border: false,
    },
    {
      value: String(peakYear),
      sub: format(annualTotal(peakYear)),
      label: 'año con más quema',
      border: false,
    },
    {
      value: `+${anomaly}%`,
      sub: `Nov ${yearEnd} sobre la mediana histórica`,
      label: 'actividad actual vs. historia',
      border: true,
    },
  ]
})

function monthShort(month: number): string {
  return ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'][month] ?? ''
}
</script>

<template>
  <ul class="kpi-list">
    <li
      v-for="k in kpis"
      :key="k.label"
      class="kpi"
      :class="{ 'kpi--alert': k.border }"
    >
      <span class="kpi-value" :class="{ 'kpi-value--accent': k.accent }">{{ k.value }}</span>
      <span class="kpi-sub" v-if="k.sub">{{ k.sub }}</span>
      <span class="kpi-label">{{ k.label }}</span>
    </li>
  </ul>
</template>

<style scoped>
.kpi-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
}

.kpi {
  background: var(--surface-card);
  border: 1px solid var(--blue-400);
  border-radius: var(--radius-md);
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: 2px;
  box-shadow: var(--shadow-card);
}

.kpi--alert {
  border-color: var(--warm-orange);
}

.kpi-value {
  font-family: var(--font-display);
  font-weight: 900;
  font-size: clamp(26px, 3vw, 40px);
  line-height: 1.05;
  letter-spacing: 0.02em;
}

.kpi-value--accent {
  color: var(--warm-lime);
}

.kpi-sub {
  font-size: 15px;
  font-weight: 700;
  color: var(--warm-orange);
  margin-top: 2px;
}

.kpi-label {
  font-size: 13px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  opacity: 0.85;
  margin-top: 4px;
}

@media (max-width: 980px) {
  .kpi-list {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 560px) {
  .kpi-list {
    grid-template-columns: 1fr 1fr;
    gap: var(--space-3);
  }
  .kpi {
    padding: var(--space-4);
  }
}
</style>