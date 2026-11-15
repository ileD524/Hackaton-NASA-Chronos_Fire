<script setup lang="ts">
import { onMounted } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import HeroSection from '@/components/HeroSection.vue'
import AreaOfInterest from '@/components/AreaOfInterest.vue'
import KpiStrip from '@/components/KpiStrip.vue'
import FireMap from '@/components/FireMap.vue'
import FireCalendar from '@/components/FireCalendar.vue'
import CalendarLegend from '@/components/CalendarLegend.vue'
import MonthComparison from '@/components/MonthComparison.vue'
import MethodologyPanel from '@/components/MethodologyPanel.vue'
import AppFooter from '@/components/AppFooter.vue'
import CalendarModal from '@/components/CalendarModal.vue'
import DailyDetail from '@/components/DailyDetail.vue'
import AlertsPanel from '@/components/AlertsPanel.vue'
import ValidationPanel from '@/components/ValidationPanel.vue'
import { useChronos } from '@/composables/useChronos'
const { initialize, filters, areaLabel, loading, error, selectedMonth, calendarOpen, calendar, load } = useChronos()
onMounted(initialize)
</script>
<template>
  <a class="skip-link" href="#contenido">Saltar al contenido</a>
  <AppHeader />
  <main class="container main" id="contenido">
    <HeroSection :region="areaLabel" :range-label="`${filters.from} → ${filters.to} · UTC`" @open-calendar="calendarOpen = true" />
    <div v-if="error" class="notice error-notice" role="alert">{{ error }} <button class="btn" @click="load()">Reintentar</button></div>
    <p v-if="loading" class="loading-note" role="status">Consultando el archivo local…</p>
    <div class="layout" :aria-busy="loading">
      <AreaOfInterest class="sidebar" />
      <div class="content" v-if="calendar">
        <KpiStrip />
        <FireMap @open-calendar="calendarOpen = true" />
        <FireCalendar v-model:selected-month="selectedMonth" />
        <CalendarLegend />
        <DailyDetail />
        <div class="side-by-side"><AlertsPanel /><ValidationPanel /></div>
        <div class="side-by-side"><MonthComparison /><MethodologyPanel /></div>
      </div>
      <div v-else class="panel initial-state"><h2>Tu archivo de actividad</h2><p>{{ error ? 'El servidor no pudo cargar los datos.' : 'Preparando el calendario de detecciones MODIS y VIIRS.' }}</p></div>
    </div>
  </main>
  <AppFooter />
  <CalendarModal v-model:open="calendarOpen" v-model:selected-month="selectedMonth" />
</template>
<style scoped>
.container { max-width: 1500px; margin: 0 auto; padding: 24px 32px 48px; width: 100% }
.main,.content { display: flex; flex-direction: column; gap: 24px; min-width: 0 }
.layout { display: grid; grid-template-columns: 280px minmax(0,1fr); gap: 24px; align-items: start }
.sidebar { position: sticky; top: 108px }
.side-by-side { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; align-items: start }
.initial-state { min-height: 400px }
.loading-note { margin: 0; color: var(--warm-lime) }
@media(max-width:1100px) { .layout { grid-template-columns: 1fr } .sidebar {position:static} }
@media(max-width:720px) { .container {padding:20px 16px 32px}.side-by-side{grid-template-columns:1fr} }
</style>
