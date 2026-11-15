<script setup lang="ts">
import { onMounted } from 'vue'
import { useAlerts } from '@/composables/useAlerts'
import { format } from '@/composables/useCalendar'
const { validation, validationLoading, validationError, loadValidation } = useAlerts()
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
        <tr v-for="event in validation" :key="`${event.aoiId}-${event.isoyear}-${event.week}`">
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
</section>
</template>
<style scoped>
.panel-head{display:flex;justify-content:space-between;align-items:center;gap:16px}.table-wrap{overflow-x:auto;margin-top:14px}table{border-collapse:collapse;width:100%;font-size:12px}th,td{padding:8px 6px;text-align:right;border-bottom:1px solid var(--blue-100);white-space:nowrap}th:first-child,td:first-child{text-align:left}thead th{font-size:11px;letter-spacing:.06em;text-transform:uppercase;color:var(--blue-100)}tbody th{font-weight:700}.verdict{font-size:10px;font-weight:800;text-transform:uppercase;padding:3px 7px;border-radius:4px}.verdict-pass{background:#EAFE07;color:#07173F}.verdict-fail{background:#E03000;color:#fff}.verdict-insufficient{background:#0B2A6B;color:#fff}.error{color:#ffb4a8}
</style>
