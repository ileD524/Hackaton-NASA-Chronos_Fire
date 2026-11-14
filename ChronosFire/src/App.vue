<script setup lang="ts">
import { ref } from 'vue'
import { format } from '@/composables/useCalendar'
import AppHeader from '@/components/AppHeader.vue'
import HeroSection from '@/components/HeroSection.vue'
import AreaOfInterest from '@/components/AreaOfInterest.vue'
import KpiStrip from '@/components/KpiStrip.vue'
import FireCalendar from '@/components/FireCalendar.vue'
import CalendarLegend from '@/components/CalendarLegend.vue'
import MonthComparison from '@/components/MonthComparison.vue'
import MethodologyPanel from '@/components/MethodologyPanel.vue'
import AppFooter from '@/components/AppFooter.vue'

const region = ref('-25,30 · -57,45')
const fromLabel = ref('01/01/2017')
const toLabel = ref('14/11/2026')
const rangeLabel = ref(`${fromLabel.value} → ${toLabel.value}`)

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
</script>

<template>
  <AppHeader />

  <main class="container main" id="contenido">
    <HeroSection :region="region" :range-label="rangeLabel" />

    <div class="layout">
      <AreaOfInterest class="sidebar" :range-label="rangeLabel" @range-change="onRangeChange" />

      <div class="content">
        <KpiStrip />

        <FireCalendar />

        <CalendarLegend />

        <div class="side-by-side">
          <MonthComparison />
          <MethodologyPanel />
        </div>
      </div>
    </div>
  </main>

  <AppFooter />
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