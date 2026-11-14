<script setup lang="ts">
import { computed, ref } from 'vue'
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
import { monthWindow, type MapWindow } from '@/data/firms'

const region = ref('-25,30 · -57,45')
const fromLabel = ref('01/01/2017')
const toLabel = ref('14/11/2026')
const rangeLabel = ref(`${fromLabel.value} → ${toLabel.value}`)

/** Mes seleccionado: lo comparten el calendario en página, el modal y el mapa. */
const selectedMonth = ref<{ year: number; month: number } | null>(null)
const calendarOpen = ref(false)

/** `null` = modo en vivo; si hay mes, el mapa dibuja ese mes vía WMS-Time. */
const mapWindow = computed<MapWindow | null>(() =>
  selectedMonth.value ? monthWindow(selectedMonth.value.year, selectedMonth.value.month) : null,
)

function onRangeChange(range: { from: string; to: string }) {
  fromLabel.value = formatDate(range.from)
  toLabel.value = formatDate(range.to)
  rangeLabel.value = `${fromLabel.value} → ${toLabel.value}`
}

function formatDate(iso: string): string {
  if (!iso) return iso
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}

function openCalendar() {
  calendarOpen.value = true
}
</script>

<template>
  <AppHeader />

  <main class="container main" id="contenido">
    <HeroSection :region="region" :range-label="rangeLabel" @open-calendar="openCalendar" />

    <div class="layout">
      <AreaOfInterest class="sidebar" :range-label="rangeLabel" @range-change="onRangeChange" />

      <div class="content">
        <FireMap
          :time-window="mapWindow"
          @open-calendar="openCalendar"
          @back-to-live="selectedMonth = null"
        />

        <KpiStrip />

        <FireCalendar v-model:selected-month="selectedMonth" />

        <CalendarLegend />

        <div class="side-by-side">
          <MonthComparison />
          <MethodologyPanel />
        </div>
      </div>
    </div>
  </main>

  <AppFooter />

  <CalendarModal v-model:open="calendarOpen" v-model:selected-month="selectedMonth" />
</template>

<style scoped>
.container {
  max-width: 1360px;
  margin: 0 auto;
  padding: 8px 32px 40px;
  flex: 1;
  width: 100%;
}

.main {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.layout {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: var(--space-5);
  align-items: start;
}

.sidebar {
  position: sticky;
  top: 88px;
}

.content {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.side-by-side {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-5);
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: 1fr;
  }
  .sidebar {
    position: static;
  }
}

@media (max-width: 720px) {
  .container {
    padding: 8px 20px 32px;
  }
  .side-by-side {
    grid-template-columns: 1fr;
  }
}
</style>
