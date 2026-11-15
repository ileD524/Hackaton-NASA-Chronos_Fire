<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useChronos } from '@/composables/useChronos'
import { useAlerts } from '@/composables/useAlerts'
import { format } from '@/composables/useCalendar'
import type { Alert } from '@/data/types'
const { filters } = useChronos()
const { alerts, alertsLoading, alertsError, alertsUpdatedAt, loadAlerts } = useAlerts()
const ALERTS_PER_PAGE = 5
const alertsPage = ref(0)
const now = ref(Date.now())
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
  loadAlerts(bbox)
}, { deep: true })
watch(() => alerts.value?.alerts.length, (length) => {
  const pages = Math.max(1, Math.ceil((length ?? 0) / ALERTS_PER_PAGE))
  if (alertsPage.value >= pages) alertsPage.value = pages - 1
})
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
const referenceLabel = (alert: Alert) => alert.probabilityRefYears === null ? 'sin base' : `n=${alert.probabilityRefYears}`
const probabilityHelp = (alert: Alert) => {
  if (alert.fireProbability === null) {
    return 'Sin respaldo histórico suficiente para esta celda y semana: no se muestra probabilidad.'
  }
  const interval = alert.probabilityLow !== null && alert.probabilityHigh !== null
    ? `IC 90 %: ${percent(alert.probabilityLow)}–${percent(alert.probabilityHigh)}`
    : ''
  const percentile = alert.activityPercentile !== null
    ? ` · percentil de actividad ${percent(alert.activityPercentile)}`
    : ''
  return `Frecuencia histórica estimada de actividad en esta celda para esta semana del año. ${interval}${percentile}. No es predicción ni riesgo oficial.`
}
</script>
<template>
<section class="panel" id="alertas" aria-labelledby="alerts-title">
  <header class="panel-head"><div><span class="section-kicker">ALERTA TEMPRANA · NRT</span><h2 class="panel-title" id="alerts-title">Alertas recientes</h2></div><div class="alert-actions"><span v-if="alerts" class="badge">{{ alerts.count }}</span><button class="link-btn" type="button" :disabled="alertsLoading" @click="refreshAlerts">{{ alertsLoading ? 'Actualizando…' : 'Actualizar' }}</button></div></header>
  <p v-if="alerts" class="small-note">Últimos {{ alerts.days }} días · datos provisionales · última detección {{ time(alerts.latestAvailable) }}<span v-if="updatedLabel"> · {{ updatedLabel }}</span></p>
  <p v-if="alerts" class="small-note probability-note">El % es la frecuencia histórica estimada de la celda en esta semana del año, con sus años de referencia (n). No es predicción ni riesgo oficial.</p>
  <p v-if="alertsLoading" class="small-note" role="status">Consultando alertas…</p>
  <p v-else-if="alertsError" class="small-note error" role="alert">{{ alertsError }}</p>
  <p v-else-if="alerts && !alerts.alerts.length" class="small-note">Sin alertas NRT en el área y período.</p>
  <ol v-else-if="alerts" class="alert-list">
    <li v-for="(alert, index) in pagedAlerts" :key="`${alert.cellLat}-${alert.cellLon}-${alert.lastDetection}-${index}`">
      <span class="level" :class="`level-${alert.level}`">{{ alert.level }}</span>
      <div class="alert-main"><strong>{{ format(alert.nDetections) }} {{ alert.nDetections === 1 ? 'detección' : 'detecciones' }} · {{ alert.sensors.join(' · ') }}</strong><small>{{ alert.cellLat }}, {{ alert.cellLon }} · {{ time(alert.lastDetection) }}</small></div>
      <div class="alert-prob" :title="probabilityHelp(alert)" :aria-label="`Probabilidad histórica ${probabilityLabel(alert)}, ${referenceLabel(alert)}`">
        <strong>{{ probabilityLabel(alert) }}</strong>
        <small>{{ referenceLabel(alert) }}</small>
      </div>
      <span class="alert-frp">{{ alert.frpMaxMw === null ? '—' : `${format(alert.frpMaxMw)} MW` }}</span>
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
.panel-head{display:flex;justify-content:space-between;align-items:center;gap:16px}.alert-actions{display:flex;gap:10px;align-items:center}.badge{border:1px solid var(--warm-lime);color:var(--warm-lime);border-radius:20px;padding:3px 10px;font-size:12px;font-weight:700}.alert-list{list-style:none;margin:18px 0 0;padding:0;display:flex;flex-direction:column;gap:10px}.alert-list li{display:grid;grid-template-columns:70px minmax(0,1fr) auto auto;align-items:center;gap:12px;padding:10px 0;border-bottom:1px solid var(--blue-100);font-size:13px}.alert-main{display:flex;flex-direction:column;gap:3px;min-width:0}.alert-main small{color:var(--blue-100)}.alert-frp{font-weight:700;white-space:nowrap}.alert-prob{display:flex;flex-direction:column;align-items:flex-end;gap:2px;white-space:nowrap}.alert-prob strong{font-size:14px}.alert-prob small{color:var(--blue-100);font-size:11px}.probability-note{margin-top:-6px}.level{font-size:10px;font-weight:800;text-transform:uppercase;letter-spacing:.08em;padding:4px 6px;border-radius:4px;text-align:center}.level-info{background:#0B2A6B;color:#fff}.level-watch{background:#F08A00;color:#07173F}.level-warning{background:#E03000;color:#fff}.error{color:#ffb4a8}.pager{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:14px;flex-wrap:wrap}.pager .link-btn:disabled{opacity:.45;cursor:not-allowed}@media(max-width:600px){.alert-list li{grid-template-columns:64px minmax(0,1fr) auto}.alert-frp{display:none}}
</style>
