<script setup lang="ts">
import { useCalendar } from '@/composables/useCalendar'
import { useColorRamp } from '@/composables/useColorRamp'
import { format } from '@/composables/useCalendar'

const { minVal, maxVal } = useCalendar()
const { rampId, color } = useColorRamp()

interface Step {
  label: string
  value: number
}

const steps: Step[] = [
  { label: 'Bajo', value: minVal },
  { label: '', value: (maxVal - minVal) * 0.33 + minVal },
  { label: 'Medio', value: (maxVal - minVal) * 0.66 + minVal },
  { label: 'Alto', value: maxVal },
]
</script>

<template>
  <div class="legend" aria-label="Leyenda del calendario">
    <span class="legend-title">Intensidad</span>
    <div class="legend-ramp">
      <span
        v-for="(s, i) in steps"
        :key="i"
        class="legend-swatch"
        :style="{ background: color(i / (steps.length - 1)) }"
        :title="`${s.label} · ${format(s.value)}`"
      />
    </div>
    <div class="legend-labels">
      <span v-for="s in steps" :key="s.label">{{ s.label }}</span>
    </div>
    <p class="legend-note">
      Modo actual: {{ rampId === 'anomaly' ? 'divergente' : 'secuencial' }} ·
      clic para cambiar en el calendario.
    </p>
  </div>
</template>

<style scoped>
.legend {
  margin-top: var(--space-4);
  font-size: 13px;
}

.legend-title {
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-right: var(--space-2);
}

.legend-ramp {
  display: inline-flex;
  gap: 2px;
  vertical-align: middle;
}

.legend-swatch {
  width: 26px;
  height: 16px;
  border-radius: 3px;
  border: 1px solid rgba(255, 255, 255, 0.25);
}

.legend-labels {
  display: flex;
  justify-content: space-between;
  max-width: 320px;
  margin-top: 2px;
}

.legend-note {
  margin: var(--space-3) 0 0;
  opacity: 0.75;
  font-size: 13px;
}
</style>