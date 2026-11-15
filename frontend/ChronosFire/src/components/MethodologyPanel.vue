<script setup lang="ts">
import { computed } from 'vue'
import { useChronos } from '@/composables/useChronos'
import { format } from '@/composables/useCalendar'
import { useI18n } from '@/composables/useI18n'
const { metadata } = useChronos()
const { t, formatNumber } = useI18n()
const risk = computed(() => metadata.value?.risk ?? null)
const decimal = (value: number | null, digits = 3) => value === null
  ? '—'
  : formatNumber(value, { minimumFractionDigits: digits, maximumFractionDigits: digits })
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
<section class="panel" id="metodologia" aria-labelledby="method-title"><span class="section-kicker">{{ t('method.kicker') }}</span><h2 class="panel-title" id="method-title">{{ t('method.title') }}</h2>
<p v-if="coverageLabel">{{ t('method.coverage', { coverage: coverageLabel }) }}</p>
<ol class="method-steps"><li><strong>{{ t('method.step1.strong') }}</strong> {{ t('method.step1.body') }}</li><li><strong>{{ t('method.step2.strong') }}</strong> {{ t('method.step2.body') }}</li><li><strong>{{ t('method.step3.strong') }}</strong> {{ t('method.step3.body') }}</li></ol>
<p><strong>{{ t('method.definition.strong') }}</strong> {{ t('method.definition.body') }}</p>
<div class="risk-method">
  <h3>{{ t('method.riskTitle') }}</h3>
  <p>{{ t('method.riskFormula') }}</p>
  <p>{{ t('method.riskCoverage') }}</p>
  <p>{{ t('method.riskLevels') }}</p>
  <p>{{ t('method.disclaimer') }}</p>
  <p v-if="risk" class="calibration" :class="`calibration-${risk.calibrationStatus}`">
    <strong>{{ t('method.calibration', { year: risk.holdoutYear ?? '—' }) }}</strong>
    {{ t('method.calibrationLine', { brier: decimal(risk.brier, 4), bss: decimal(risk.bss, 4), auc: decimal(risk.auc, 3), cells: format(risk.cellsWeek ?? 0) }) }}
    <template v-if="risk.calibrationStatus === 'low'">
      {{ t('method.calibrationLow', { threshold: decimal(risk.bssThreshold, 2), version: risk.modelVersion }) }}
    </template>
    <template v-else-if="risk.stabilityBss !== null">
      {{ t('method.calibrationStability', { bss: decimal(risk.stabilityBss, 4) }) }}
    </template>
  </p>
</div>
<div class="audience">
  <h3>{{ t('method.audienceTitle') }}</h3>
  <ul>
    <li><strong>{{ t('method.audience.emergency.strong') }}</strong> {{ t('method.audience.emergency.body') }}</li>
    <li><strong>{{ t('method.audience.science.strong') }}</strong> {{ t('method.audience.science.body') }}</li>
    <li><strong>{{ t('method.audience.land.strong') }}</strong> {{ t('method.audience.land.body') }}</li>
  </ul>
  <p class="use-case">{{ t('method.useCase') }}</p>
  <p class="small-note">{{ t('method.note') }}</p>
</div>
<details><summary>{{ t('method.limits') }}</summary><p>{{ t('method.limits1') }}</p><p>{{ t('method.limits2') }}</p><p><template v-if="provisionalLabel">{{ t('method.provisional', { period: provisionalLabel }) }}</template>{{ t('method.limits3') }}</p></details>
<a class="link-btn" href="https://www.earthdata.nasa.gov/data/tools/firms" target="_blank" rel="noopener">{{ t('method.firms') }}</a>
</section>
</template>
<style scoped>.method-steps{padding-left:20px;font-size:14px;line-height:1.55}.method-steps li{margin:12px 0}.method-steps strong{display:block}.panel>p{font-size:14px}.audience{border-top:1px solid var(--blue-100);margin-top:18px;padding-top:16px}.audience h3{font:800 16px var(--font-body);margin:0 0 10px}.audience ul{margin:0;padding-left:20px;font-size:14px;line-height:1.55}.audience li{margin:8px 0}.use-case{font-size:13px;line-height:1.55;margin:14px 0 8px}details{border-top:1px solid var(--blue-100);padding-top:16px;font-size:13px}summary{cursor:pointer;font-weight:700;min-height:40px}details p{line-height:1.6}.risk-method{border-top:1px solid var(--blue-100);margin-top:18px;padding-top:16px}.risk-method h3{font:800 16px var(--font-body);margin:0 0 10px}.risk-method p{font-size:13px;line-height:1.6;margin:8px 0}.risk-method code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px}.calibration{margin-top:12px;padding:10px 12px;border:1px solid var(--blue-100);border-radius:6px;font-size:12px!important}.calibration-low{border-color:#F08A00}</style>
