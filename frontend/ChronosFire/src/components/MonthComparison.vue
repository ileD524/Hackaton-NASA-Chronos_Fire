<script setup lang="ts">
import { computed } from 'vue'
import { useChronos } from '@/composables/useChronos'
import { useI18n } from '@/composables/useI18n'
import { useCalendar, format } from '@/composables/useCalendar'
import { anomalyBand } from '@/composables/useColorRamp'
const { selected, seriesLabel, filters, metricMode, metricValue } = useChronos()
const { monthName } = useCalendar()
const { t } = useI18n()
const isCells = computed(() => metricMode.value === 'cells')
const selectedValue = computed(() => metricValue(selected.value))
const median = computed(() => isCells.value ? selected.value?.baseline_fire_cells ?? null : null)
const delta = computed(() => {
  if (!isCells.value) return null
  const cell = selected.value
  return cell && cell.anomaly !== null ? (cell.anomaly - 1) * 100 : null
})
const band = computed(() => anomalyBand(delta.value === null ? null : delta.value / 100))
const confidenceKey = computed(() => selected.value?.confidence === 'high' ? 'comparison.confidence.high' : selected.value?.confidence === 'low' ? 'comparison.confidence.low' : 'comparison.confidence.insufficient')
const confidenceLabel = computed(() => t(confidenceKey.value))
</script>
<template>
  <section class="panel" aria-labelledby="comparison-title">
    <span class="section-kicker">{{ t('comparison.kicker') }}</span>
    <h2 class="panel-title" id="comparison-title">{{ t('comparison.title') }}</h2>
    <template v-if="selected">
      <p>{{ t('comparison.monthLine', { month: monthName(selected.month), year: selected.year, series: seriesLabel }) }}</p>
      <template v-if="isCells">
        <div class="comparison-number" :style="{ color: band.ink }"><span v-if="band.mark" class="comparison-mark" aria-hidden="true">{{ band.mark }}</span>{{ delta === null ? '—' : `${delta > 0 ? '+' : ''}${format(delta)}%` }}</div>
        <p class="small-note">{{ delta === null ? t('comparison.deltaNone') : t('comparison.deltaNote') }}<span v-if="delta !== null">{{ t('comparison.refYears', { n: selected.reference_years, confidence: confidenceLabel }) }}</span></p>
        <dl class="comparison-rows">
          <div><dt>{{ t('comparison.selectedMonth') }}</dt><dd>{{ selectedValue === null ? '—' : format(selectedValue) }} {{ t('comparison.cellsUnit') }}</dd></div>
          <div><dt>{{ t('comparison.median') }}</dt><dd>{{ median === null ? '—' : format(median) }} {{ t('comparison.cellsUnit') }}</dd></div>
        </dl>
      </template>
      <template v-else>
        <div class="comparison-number">{{ selectedValue === null ? '—' : format(selectedValue) }}</div>
        <p class="small-note">{{ t('comparison.hotspotsMonth') }}</p>
        <dl class="comparison-rows">
          <div><dt>{{ t('comparison.selectedMonth') }}</dt><dd>{{ selectedValue === null ? '—' : t('comparison.hotspotsSelected', { value: format(selectedValue) }) }}</dd></div>
          <div><dt>{{ t('comparison.historicalComparison') }}</dt><dd>{{ t('comparison.notAvailable') }}</dd></div>
        </dl>
      </template>
      <p v-if="filters.metric === 'all'" class="small-note">{{ t('comparison.allSensors') }}</p>
    </template>
    <p v-else>{{ t('comparison.selectMonth') }}</p>
    <p class="small-note">{{ isCells ? t('comparison.foot.cells') : t('comparison.foot.hotspots') }}</p>
  </section>
</template>
<style scoped>.comparison-number{font:900 48px/1.2 var(--font-display);margin:20px 0;color:white}.comparison-mark{font-size:.62em;margin-right:8px;vertical-align:.12em}.comparison-rows{margin:20px 0}.comparison-rows>div{display:flex;justify-content:space-between;gap:12px;padding:12px 0;border-bottom:1px solid var(--blue-100);font-size:13px}.comparison-rows dd{margin:0;font-weight:700;text-align:right}</style>
