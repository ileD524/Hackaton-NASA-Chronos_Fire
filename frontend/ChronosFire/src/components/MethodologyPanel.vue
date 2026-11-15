<script setup lang="ts">
import { computed } from 'vue'
import { useChronos } from '@/composables/useChronos'
const { metadata } = useChronos()
const sensorYear = (value: string | null | undefined) => value ? value.slice(0, 4) : ''
const coverageLabel = computed(() => {
  const bySensor = new Map<string, { from: string; to: string }>()
  for (const row of metadata.value?.sensors ?? []) {
    if (!row.from) continue
    const current = bySensor.get(row.sensor)
    bySensor.set(row.sensor, {
      from: !current || row.from < current.from ? row.from : current.from,
      to: row.to && (!current || row.to > current.to) ? row.to : current?.to ?? '',
    })
  }
  return [...bySensor.entries()]
    .map(([sensor, period]) => {
      const label = sensor.replace(/_/g, ' ')
      const from = sensorYear(period.from)
      const to = sensorYear(period.to)
      return to ? `${label} ${from}–${to}` : `${label} ${from}`
    })
    .join(' · ')
})
const provisionalLabel = computed(() => {
  const period = metadata.value?.provisional
  if (!period?.from) return ''
  const from = sensorYear(period.from)
  const to = sensorYear(period.to)
  return to && to !== from ? `${from}–${to}` : from
})
</script>
<template>
<section class="panel" id="metodologia" aria-labelledby="method-title"><span class="section-kicker">DE LA OBSERVACIÓN A LA COMPARACIÓN</span><h2 class="panel-title" id="method-title">Qué estás viendo</h2>
<p v-if="coverageLabel">{{ coverageLabel }}. Vegetación presunta con confianza nominal o alta.</p>
<ol class="method-steps"><li><strong>Una grilla común.</strong> Detecciones asignadas por su centro a celdas de 0,05° (~5 km).</li><li><strong>Un día común.</strong> Agregación diaria en UTC; una celda con actividad en un día cuenta una vez.</li><li><strong>Una serie comparable.</strong> El calendario usa celdas con actividad, no una suma de detecciones entre sensores.</li></ol>
<p><strong>Celda con actividad ≠ hotspot ≠ incendio.</strong> Una celda puede contener varios hotspots; un hotspot es una detección satelital y un incendio único requeriría agrupar eventos. La comparación histórica se calcula sobre celdas con actividad; los hotspots se muestran como conteo de detecciones.</p>
<details><summary>Límites de la comparación</summary><p>La confianza se homologa a baja, nominal y alta. El baseline usa la mediana multianual por AOI y la era de cada sensor.</p><p>No hay calibración física de eficiencia de detección ni de FRP. La ausencia de registros no confirma ausencia de fuego: la cobertura real por nubosidad y overpass no está disponible.</p><p><template v-if="provisionalLabel">{{ provisionalLabel }} es NRT provisional y queda fuera del baseline. </template>Una celda de 0,05° no representa superficie quemada.</p></details>
<a class="link-btn" href="https://www.earthdata.nasa.gov/data/tools/firms" target="_blank" rel="noopener">Conocer NASA FIRMS ↗</a>
</section>
</template>
<style scoped>.method-steps{padding-left:20px;font-size:14px;line-height:1.55}.method-steps li{margin:12px 0}.method-steps strong{display:block}.panel>p{font-size:14px}details{border-top:1px solid var(--blue-100);padding-top:16px;font-size:13px}summary{cursor:pointer;font-weight:700;min-height:40px}details p{line-height:1.6}</style>
