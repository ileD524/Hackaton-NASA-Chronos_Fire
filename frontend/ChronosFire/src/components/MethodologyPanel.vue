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
<div class="risk-method">
  <h3>Probabilidad histórica por celda-semana</h3>
  <p>Para cada celda de 0,05° y semana ISO se estima con qué frecuencia hubo actividad en los años de referencia: <code>p = (k + α) / (n + α + β)</code>, donde <code>k</code> son los años con fuego, <code>n</code> los años con cobertura y α/β un prior semanal débil (κ = 8). El intervalo es Wilson al 90 % sobre <code>k/n</code>.</p>
  <p><code>n</code> usa una cobertura <em>proxy</em>: un día cuenta como cubierto si el sensor registró al menos una detección en la región. No prueba ausencia de nubes, así que la probabilidad es una <strong>cota inferior</strong>. Con 13 años el intervalo es ancho a propósito y se muestra.</p>
  <p>El <strong>percentil de actividad</strong> compara las detecciones de los últimos 7 días con los años de referencia de esa celda-semana. Los niveles priorizan el triage: <strong>warning</strong> con p ≥ 50 % o percentil ≥ 90 % con base histórica (p ≥ 10 %) o al menos 10 detecciones; <strong>watch</strong> con p ≥ 25 % o percentil ≥ 75 % con base o al menos 3 detecciones; <strong>info</strong> en el resto. Son umbrales provisionales.</p>
  <p>No es una predicción, no incorpora meteorología ni combustible, y no es una alerta oficial.</p>
</div>
<div class="audience">
  <h3>Para quién</h3>
  <ul>
    <li><strong>Respuesta a emergencias:</strong> detectar semanas críticas y comparar el mes actual con su historia.</li>
    <li><strong>Científicos:</strong> explorar patrones estacionales y anomalías multianuales.</li>
    <li><strong>Gestión del territorio:</strong> priorizar el monitoreo en áreas con actividad recurrente.</li>
  </ul>
  <p class="use-case"><strong>Caso de validación semanal:</strong> Delta del Paraná, semana 40 de 2020: 6,6× la mediana (66 celdas con actividad frente a 10 de referencia).</p>
  <p class="small-note">Herramienta descriptiva: los niveles info/watch/warning priorizan el triage; no son predicción ni alerta oficial.</p>
</div>
<details><summary>Límites de la comparación</summary><p>La confianza se homologa a baja, nominal y alta. El baseline usa la mediana multianual por AOI y la era de cada sensor.</p><p>No hay calibración física de eficiencia de detección ni de FRP. La cobertura se aproxima con los días con detecciones (`coverage_daily`); no descuenta nubosidad ni overpass. La ausencia de registros no confirma ausencia de fuego.</p><p><template v-if="provisionalLabel">{{ provisionalLabel }} es NRT provisional y queda fuera del baseline. </template>Una celda de 0,05° no representa superficie quemada.</p></details>
<a class="link-btn" href="https://www.earthdata.nasa.gov/data/tools/firms" target="_blank" rel="noopener">Conocer NASA FIRMS ↗</a>
</section>
</template>
<style scoped>.method-steps{padding-left:20px;font-size:14px;line-height:1.55}.method-steps li{margin:12px 0}.method-steps strong{display:block}.panel>p{font-size:14px}.audience{border-top:1px solid var(--blue-100);margin-top:18px;padding-top:16px}.audience h3{font:800 16px var(--font-body);margin:0 0 10px}.audience ul{margin:0;padding-left:20px;font-size:14px;line-height:1.55}.audience li{margin:8px 0}.use-case{font-size:13px;line-height:1.55;margin:14px 0 8px}details{border-top:1px solid var(--blue-100);padding-top:16px;font-size:13px}summary{cursor:pointer;font-weight:700;min-height:40px}details p{line-height:1.6}.risk-method{border-top:1px solid var(--blue-100);margin-top:18px;padding-top:16px}.risk-method h3{font:800 16px var(--font-body);margin:0 0 10px}.risk-method p{font-size:13px;line-height:1.6;margin:8px 0}.risk-method code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px}</style>
