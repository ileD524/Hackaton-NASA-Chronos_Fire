<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useChronos } from '@/composables/useChronos'
import { useAlerts } from '@/composables/useAlerts'
import { format } from '@/composables/useCalendar'
import type { Alert } from '@/data/types'
const { filters } = useChronos()
const { alerts, alertsLoading, alertsError, alertsUpdatedAt, loadAlerts } = useAlerts()
const ALERTS_PER_PAGE = 4
const alertsPage = ref(0)
const now = ref(Date.now())
const helpOpen = ref<string | null>(null)
let refreshTimer: ReturnType<typeof setInterval> | undefined
let clockTimer: ReturnType<typeof setInterval> | undefined
function refreshAlerts() {
  if (!alertsLoading.value) loadAlerts(filters.value.bbox)
}
function tick() {
  now.value = Date.now()
}
onMounted(() => {
  loadAlerts(filters.value.bbox)
  refreshTimer = setInterval(refreshAlerts, 5 * 60 * 1000)
  clockTimer = setInterval(tick, 30 * 1000)
})
onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (clockTimer) clearInterval(clockTimer)
})
watch(() => filters.value.bbox, (bbox) => {
  alertsPage.value = 0
  helpOpen.value = null
  loadAlerts(bbox)
}, { deep: true })
watch(() => alerts.value?.alerts.length, (length) => {
  const pages = Math.max(1, Math.ceil((length ?? 0) / ALERTS_PER_PAGE))
  if (alertsPage.value >= pages) alertsPage.value = pages - 1
})
watch(alertsPage, () => { helpOpen.value = null })
const alertPageCount = computed(() =>
  Math.max(1, Math.ceil((alerts.value?.alerts.length ?? 0) / ALERTS_PER_PAGE)))
const pagedAlerts = computed(() => {
  const start = alertsPage.value * ALERTS_PER_PAGE
  return (alerts.value?.alerts ?? []).slice(start, start + ALERTS_PER_PAGE)
})
const alertRange = computed(() => {
  const total = alerts.value?.alerts.length ?? 0
  if (!total) return ''
  const start = alertsPage.value * ALERTS_PER_PAGE + 1
  const end = Math.min(total, start + ALERTS_PER_PAGE - 1)
  return `${start}–${end} de ${total}`
})
const updatedLabel = computed(() => {
  if (!alertsUpdatedAt.value) return ''
  const seconds = Math.max(0, Math.floor((now.value - alertsUpdatedAt.value.getTime()) / 1000))
  if (seconds < 60) return 'Actualizado hace menos de un minuto'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `Actualizado hace ${minutes} min`
  const hours = Math.floor(minutes / 60)
  return `Actualizado hace ${hours} h`
})
const time = (value: string | null) => value ? new Date(value).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' }) : '—'
const aoiAnomaly = computed(() => alerts.value?.alerts[0]?.aoiAnomaly ?? null)
const percent = (value: number | null) => value === null ? '—' : `${format(value * 100)} %`
const probabilityLabel = (alert: Alert) => percent(alert.fireProbability)
const referenceLabel = (alert: Alert) => {
  const n = alert.probabilityRefYears
  if (n === null) return 'sin años de referencia'
  return n === 1 ? '1 año de referencia' : `${n} años de referencia`
}
const percentileLabel = (alert: Alert) => alert.activityPercentile === null
  ? '—'
  : `${Math.round(alert.activityPercentile * 100)}`
const intervalLabel = (alert: Alert) => alert.probabilityLow === null || alert.probabilityHigh === null
  ? 'sin intervalo'
  : `${percent(alert.probabilityLow)}–${percent(alert.probabilityHigh)}`
const barLabel = (alert: Alert) =>
  `Intervalo de confianza 90 %: ${intervalLabel(alert)}. Frecuencia estimada: ${probabilityLabel(alert)}.`
const scaled = (value: number | null) => value === null ? 0 : Math.min(100, Math.max(0, value * 100))
const bandStyle = (alert: Alert) => {
  const low = scaled(alert.probabilityLow)
  const high = scaled(alert.probabilityHigh)
  return { left: `${low}%`, width: `${Math.max(0.8, high - low)}%` }
}
const dotStyle = (alert: Alert) => {
  const position = scaled(alert.fireProbability)
  return { left: `${Math.min(98, Math.max(2, position))}%` }
}
const tipKey = (index: number) => `${alertsPage.value}-${index}`
const tipId = (index: number) => `risk-tip-${alertsPage.value}-${index}`
const toggleHelp = (key: string) => {
  helpOpen.value = helpOpen.value === key ? null : key
}
</script>
<template>
<section class="panel" id="alertas" aria-labelledby="alerts-title">
  <header class="panel-head"><div><span class="section-kicker">ALERTA TEMPRANA · NRT</span><h2 class="panel-title" id="alerts-title">Alertas recientes</h2></div><div class="alert-actions"><span v-if="alerts" class="badge">{{ alerts.count }}</span><button class="link-btn" type="button" :disabled="alertsLoading" @click="refreshAlerts">{{ alertsLoading ? 'Actualizando…' : 'Actualizar' }}</button></div></header>
  <p v-if="alerts" class="small-note">Últimos {{ alerts.days }} días · datos provisionales · última detección {{ time(alerts.latestAvailable) }}<span v-if="updatedLabel"> · {{ updatedLabel }}</span></p>
  <details v-if="alerts" class="freq-help">
    <summary>¿Qué significa la frecuencia histórica?</summary>
    <p>Es la frecuencia con la que esta celda de 0,05° tuvo actividad en esta misma semana del año durante los años de referencia (por ejemplo, 13 años de SNPP). Se estima con <code>p = (k + α) / (n + α + β)</code>, donde <code>k</code> son los años con fuego y <code>n</code> los años con cobertura.</p>
    <p>No es una predicción ni una alerta oficial: la cobertura es un proxy regional y la probabilidad es una cota inferior. Con 13 años el intervalo es ancho a propósito y se muestra junto al valor.</p>
  </details>
  <p v-if="alertsLoading" class="small-note" role="status">Consultando alertas…</p>
  <p v-else-if="alertsError" class="small-note error" role="alert">{{ alertsError }}</p>
  <p v-else-if="alerts && !alerts.alerts.length" class="small-note">Sin alertas NRT en el área y período.</p>
  <ol v-else-if="alerts" class="alert-cards">
    <li v-for="(alert, index) in pagedAlerts" :key="`${alert.cellLat}-${alert.cellLon}-${alert.lastDetection}-${index}`" class="alert-card">
      <div class="card-top">
        <span class="level" :class="`level-${alert.level}`">{{ alert.level }}</span>
        <span class="card-time">{{ time(alert.lastDetection) }}</span>
      </div>
      <div class="card-metrics">
        <div class="metric">
          <span class="metric-label">Detecciones</span>
          <span class="metric-value">{{ format(alert.nDetections) }}</span>
        </div>
        <div class="metric">
          <span class="metric-label">Potencia</span>
          <span class="metric-value">{{ alert.frpMaxMw === null ? '—' : format(alert.frpMaxMw) }}<span v-if="alert.frpMaxMw !== null" class="unit"> MW</span></span>
        </div>
        <div class="metric metric-freq">
          <span class="metric-label">Frecuencia histórica
            <button class="info" type="button" :aria-expanded="helpOpen === tipKey(index)" :aria-controls="tipId(index)" :aria-label="`Ver intervalo y años de referencia. ${barLabel(alert)}`" @click="toggleHelp(tipKey(index))">i</button>
          </span>
          <span class="metric-value">{{ probabilityLabel(alert) }}</span>
          <div v-if="alert.fireProbability !== null" class="risk-bar" role="img" :aria-label="barLabel(alert)">
            <span class="risk-band" :style="bandStyle(alert)"></span>
            <span class="risk-dot" :style="dotStyle(alert)"></span>
          </div>
          <p class="ref-years">{{ referenceLabel(alert) }}</p>
          <p v-if="alert.activityPercentile !== null" class="percentile">Actividad reciente: percentil {{ percentileLabel(alert) }}</p>
          <div v-if="helpOpen === tipKey(index)" :id="tipId(index)" class="risk-tip" role="group" aria-live="polite">
            <p><strong>IC 90 %:</strong> {{ intervalLabel(alert) }}</p>
            <p><strong>Referencia:</strong> {{ alert.probabilityRefYears === null ? 'sin años con cobertura' : `${alert.probabilityRefYears} años con cobertura` }}.</p>
            <p v-if="alert.activityPercentile !== null"><strong>Actividad reciente:</strong> percentil {{ percentileLabel(alert) }}; el {{ percentileLabel(alert) }} % de los años de referencia tuvo menos detecciones en esta semana.</p>
            <p>Frecuencia histórica estimada: no es predicción ni riesgo oficial.</p>
          </div>
        </div>
      </div>
      <div class="card-foot">
        <ul class="sensors" aria-label="Sensores">
          <li v-for="sensor in alert.sensors" :key="sensor">{{ sensor }}</li>
        </ul>
        <span class="coords"><span aria-hidden="true">◉</span> {{ alert.cellLat }}, {{ alert.cellLon }}</span>
      </div>
    </li>
  </ol>
  <nav v-if="alerts && alertPageCount > 1" class="pager" aria-label="Paginación de alertas">
    <button class="link-btn" type="button" :disabled="alertsPage === 0" aria-label="Página anterior de alertas" @click="alertsPage--">Anterior</button>
    <span class="small-note">Página {{ alertsPage + 1 }} de {{ alertPageCount }} · {{ alertRange }}</span>
    <button class="link-btn" type="button" :disabled="alertsPage >= alertPageCount - 1" aria-label="Siguiente página de alertas" @click="alertsPage++">Siguiente</button>
  </nav>
  <p v-if="aoiAnomaly !== null" class="small-note">Anomalía del AOI: {{ format(aoiAnomaly) }}×.</p>
</section>
</template>
<style scoped>
.panel-head{display:flex;justify-content:space-between;align-items:center;gap:16px}.alert-actions{display:flex;gap:10px;align-items:center}.badge{border:1px solid var(--warm-lime);color:var(--warm-lime);border-radius:20px;padding:3px 10px;font-size:12px;font-weight:700}.freq-help{margin-top:10px;border-top:1px solid rgba(46,150,245,.25);padding-top:10px;font-size:12px}.freq-help summary{cursor:pointer;color:var(--warm-lime);font-weight:700;min-height:32px;display:flex;align-items:center}.freq-help p{line-height:1.6;margin:8px 0 0;font-size:12px}.freq-help code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:11px}.alert-cards{list-style:none;margin:16px 0 0;padding:0;display:grid;gap:14px}.alert-card{border:1px solid rgba(46,150,245,.35);border-radius:12px;padding:14px 16px;background:rgba(255,255,255,.02)}.card-top{display:flex;justify-content:space-between;align-items:center;gap:12px}.card-time{font-size:12px;color:var(--blue-100);text-align:right}.card-metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin-top:14px}.metric{display:flex;flex-direction:column;gap:4px;min-width:0}.metric-label{display:flex;align-items:center;font-size:10px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:var(--blue-100)}.metric-value{font-size:clamp(20px,2.4vw,26px);font-weight:800;line-height:1.1}.unit{font-size:12px;font-weight:600;color:var(--blue-100)}.metric-freq{position:relative}.risk-bar{position:relative;height:8px;margin-top:6px;border-radius:999px;background:rgba(46,150,245,.25)}.risk-band{position:absolute;top:0;bottom:0;border-radius:999px;background:rgba(234,254,7,.35)}.risk-dot{position:absolute;top:50%;width:10px;height:10px;border-radius:50%;background:var(--warm-lime);border:2px solid #07173F;transform:translate(-50%,-50%)}.ref-years,.percentile{margin:0;font-size:11px;color:var(--blue-100)}.percentile{margin-top:2px}.info{width:18px;height:18px;margin-left:6px;padding:0;border:1px solid var(--blue-100);border-radius:50%;background:transparent;color:inherit;font-size:10px;font-weight:800;line-height:1;cursor:pointer}.info:focus-visible{outline:2px solid var(--warm-lime);outline-offset:2px}.risk-tip{position:absolute;z-index:5;top:calc(100% + 8px);right:0;width:min(300px,82vw);padding:10px 12px;background:#07173F;border:1px solid #2E96F5;border-radius:8px;box-shadow:0 12px 30px rgba(0,0,0,.45);font-size:11px;line-height:1.5}.risk-tip p{margin:0 0 6px}.risk-tip p:last-child{margin-bottom:0}.card-foot{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-top:12px;flex-wrap:wrap}.sensors{display:flex;flex-wrap:wrap;gap:6px;list-style:none;margin:0;padding:0}.sensors li{border:1px solid rgba(46,150,245,.45);border-radius:999px;padding:2px 8px;font-size:10px;letter-spacing:.04em}.coords{font-size:11px;color:var(--blue-100);white-space:nowrap}.level{font-size:10px;font-weight:800;text-transform:uppercase;letter-spacing:.08em;padding:4px 6px;border-radius:4px;text-align:center}.level-info{background:#0B2A6B;color:#fff}.level-watch{background:#F08A00;color:#07173F}.level-warning{background:#E03000;color:#fff}.error{color:#ffb4a8}.pager{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:14px;flex-wrap:wrap}.pager .link-btn:disabled{opacity:.45;cursor:not-allowed}@media(max-width:560px){.card-metrics{grid-template-columns:1fr 1fr}.metric-freq{grid-column:1/-1}.metric-value{font-size:22px}.risk-tip{right:auto;left:0;width:100%}.card-foot{flex-direction:column;align-items:flex-start;gap:8px}.coords{white-space:normal}}
</style>
