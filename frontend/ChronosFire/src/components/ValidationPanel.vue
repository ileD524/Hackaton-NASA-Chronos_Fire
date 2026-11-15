<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAlerts } from '@/composables/useAlerts'
import { format } from '@/composables/useCalendar'
const { validation, validationLoading, validationError, loadValidation } = useAlerts()
const EVENTS_PER_PAGE = 5
const eventsPage = ref(0)
const eventsPageCount = computed(() =>
  Math.max(1, Math.ceil(validation.value.length / EVENTS_PER_PAGE)))
const pagedValidation = computed(() => {
  const start = eventsPage.value * EVENTS_PER_PAGE
  return validation.value.slice(start, start + EVENTS_PER_PAGE)
})
const validationRange = computed(() => {
  const total = validation.value.length
  if (!total) return ''
  const start = eventsPage.value * EVENTS_PER_PAGE + 1
  const end = Math.min(total, start + EVENTS_PER_PAGE - 1)
  return `${start}–${end} de ${total}`
})
watch(() => validation.value.length, (length) => {
  const pages = Math.max(1, Math.ceil(length / EVENTS_PER_PAGE))
  if (eventsPage.value >= pages) eventsPage.value = pages - 1
})
onMounted(loadValidation)
</script>
<template>
<section class="panel" id="validacion" aria-labelledby="validation-title">
  <header class="panel-head"><div><span class="section-kicker">VALIDACIÓN CON EVENTOS</span><h2 class="panel-title" id="validation-title">Eventos conocidos</h2></div></header>
  <p class="small-note">Baseline de la era del sensor, mediana leave-one-out. Un veredicto `pass` exige anomalía &gt; 1 y confianza suficiente.</p>
  <p v-if="validationLoading" class="small-note" role="status">Calculando validación…</p>
  <p v-else-if="validationError" class="small-note error" role="alert">{{ validationError }}</p>
  <div v-else class="table-wrap">
    <table>
      <thead><tr><th>Evento</th><th>Año / sem.</th><th>Obs.</th><th>Baseline</th><th>Anomalía</th><th>Veredicto</th></tr></thead>
      <tbody>
        <tr v-for="event in pagedValidation" :key="`${event.aoiId}-${event.isoyear}-${event.week}`">
          <th scope="row">{{ event.name }}</th>
          <td>{{ event.isoyear }} / {{ event.week }}</td>
          <td>{{ format(event.observed) }}</td>
          <td>{{ event.baseline === null ? '—' : format(event.baseline) }}</td>
          <td>{{ event.anomaly === null ? '—' : `${format(event.anomaly)}×` }}</td>
          <td><span class="verdict" :class="`verdict-${event.verdict}`">{{ event.verdict }}</span></td>
        </tr>
      </tbody>
    </table>
  </div>
  <nav v-if="eventsPageCount > 1" class="pager" aria-label="Paginación de eventos">
    <button class="link-btn" type="button" :disabled="eventsPage === 0" aria-label="Página anterior de eventos" @click="eventsPage--">Anterior</button>
    <span class="small-note">Página {{ eventsPage + 1 }} de {{ eventsPageCount }} · {{ validationRange }}</span>
    <button class="link-btn" type="button" :disabled="eventsPage >= eventsPageCount - 1" aria-label="Siguiente página de eventos" @click="eventsPage++">Siguiente</button>
  </nav>
</section>
</template>
<style scoped>
.panel-head{display:flex;justify-content:space-between;align-items:center;gap:16px}.table-wrap{overflow-x:auto;margin-top:14px}table{border-collapse:collapse;width:100%;font-size:12px}th,td{padding:8px 6px;text-align:right;border-bottom:1px solid var(--blue-100);white-space:nowrap}th:first-child,td:first-child{text-align:left}thead th{font-size:11px;letter-spacing:.06em;text-transform:uppercase;color:var(--blue-100)}tbody th{font-weight:700}.verdict{font-size:10px;font-weight:800;text-transform:uppercase;padding:3px 7px;border-radius:4px}.verdict-pass{background:#EAFE07;color:#07173F}.verdict-fail{background:#E03000;color:#fff}.verdict-insufficient{background:#0B2A6B;color:#fff}.error{color:#ffb4a8}.pager{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:14px;flex-wrap:wrap}.pager .link-btn:disabled{opacity:.45;cursor:not-allowed}
</style>
