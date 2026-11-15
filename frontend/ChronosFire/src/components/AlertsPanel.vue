<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useChronos } from '@/composables/useChronos'
import { useAlerts } from '@/composables/useAlerts'
import { format } from '@/composables/useCalendar'
const { filters } = useChronos()
const { alerts, alertsLoading, alertsError, alertsUpdatedAt, loadAlerts } = useAlerts()
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
watch(() => filters.value.bbox, (bbox) => loadAlerts(bbox), { deep: true })
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
</script>
<template>
<section class="panel" id="alertas" aria-labelledby="alerts-title">
  <header class="panel-head"><div><span class="section-kicker">ALERTA TEMPRANA · NRT</span><h2 class="panel-title" id="alerts-title">Alertas recientes</h2></div><div class="alert-actions"><span v-if="alerts" class="badge">{{ alerts.count }}</span><button class="link-btn" type="button" :disabled="alertsLoading" @click="refreshAlerts">{{ alertsLoading ? 'Actualizando…' : 'Actualizar' }}</button></div></header>
  <p v-if="alerts" class="small-note">Últimos {{ alerts.days }} días · datos provisionales · última detección {{ time(alerts.latestAvailable) }}<span v-if="updatedLabel"> · {{ updatedLabel }}</span></p>
  <p v-if="alertsLoading" class="small-note" role="status">Consultando alertas…</p>
  <p v-else-if="alertsError" class="small-note error" role="alert">{{ alertsError }}</p>
  <p v-else-if="alerts && !alerts.alerts.length" class="small-note">Sin alertas NRT en el área y período.</p>
  <ol v-else-if="alerts" class="alert-list">
    <li v-for="(alert, index) in alerts.alerts" :key="`${alert.cellLat}-${alert.cellLon}-${index}`">
      <span class="level" :class="`level-${alert.level}`">{{ alert.level }}</span>
      <div class="alert-main"><strong>{{ format(alert.nDetections) }} {{ alert.nDetections === 1 ? 'detección' : 'detecciones' }} · {{ alert.sensors.join(' · ') }}</strong><small>{{ alert.cellLat }}, {{ alert.cellLon }} · {{ time(alert.lastDetection) }}</small></div>
      <span class="alert-frp">{{ alert.frpMaxMw === null ? '—' : `${format(alert.frpMaxMw)} MW` }}</span>
    </li>
  </ol>
  <p v-if="aoiAnomaly !== null" class="small-note">Anomalía del AOI: {{ format(aoiAnomaly) }}×.</p>
</section>
</template>
<style scoped>
.panel-head{display:flex;justify-content:space-between;align-items:center;gap:16px}.alert-actions{display:flex;gap:10px;align-items:center}.badge{border:1px solid var(--warm-lime);color:var(--warm-lime);border-radius:20px;padding:3px 10px;font-size:12px;font-weight:700}.alert-list{list-style:none;margin:18px 0 0;padding:0;display:flex;flex-direction:column;gap:10px}.alert-list li{display:grid;grid-template-columns:70px minmax(0,1fr) auto;align-items:center;gap:12px;padding:10px 0;border-bottom:1px solid var(--blue-100);font-size:13px}.alert-main{display:flex;flex-direction:column;gap:3px;min-width:0}.alert-main small{color:var(--blue-100)}.alert-frp{font-weight:700;white-space:nowrap}.level{font-size:10px;font-weight:800;text-transform:uppercase;letter-spacing:.08em;padding:4px 6px;border-radius:4px;text-align:center}.level-info{background:#0B2A6B;color:#fff}.level-watch{background:#F08A00;color:#07173F}.level-warning{background:#E03000;color:#fff}.error{color:#ffb4a8}@media(max-width:600px){.alert-list li{grid-template-columns:64px minmax(0,1fr)}.alert-frp{display:none}}
</style>
