<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAlerts } from '@/composables/useAlerts'
import { useI18n } from '@/composables/useI18n'
import { format } from '@/composables/useCalendar'
import type { ValidationEvent } from '@/data/types'

const { validation, validationLoading, validationError, loadValidation } = useAlerts()
const { t } = useI18n()
const EVENTS_PER_PAGE = 3
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
  return t('common.range', { start, end, total })
})
const verdicts = computed(() => [
  {
    code: 'pass',
    label: t('validation.verdict.pass'),
    help: t('validation.help.pass'),
  },
  {
    code: 'fail',
    label: t('validation.verdict.fail'),
    help: t('validation.help.fail'),
  },
  {
    code: 'insufficient',
    label: t('validation.verdict.insufficient'),
    help: t('validation.help.insufficient'),
  },
] as const)
const verdictLabel = (verdict: string) =>
  verdicts.value.find((item) => item.code === verdict)?.label ?? verdict
const barWidth = (event: ValidationEvent, key: 'observed' | 'baseline') => {
  const value = key === 'observed' ? event.observed : event.baseline
  const max = Math.max(event.observed, event.baseline ?? 0)
  if (value === null || max <= 0) return '0%'
  return `${Math.max(2, Math.round((value / max) * 100))}%`
}
watch(() => validation.value.length, (length) => {
  const pages = Math.max(1, Math.ceil(length / EVENTS_PER_PAGE))
  if (eventsPage.value >= pages) eventsPage.value = pages - 1
})
onMounted(loadValidation)
</script>
<template>
<section class="panel validation-panel" id="validacion" aria-labelledby="validation-title">
  <header class="panel-head"><div><span class="section-kicker">{{ t('validation.kicker') }}</span><h2 class="panel-title" id="validation-title">{{ t('validation.title') }}</h2></div></header>
  <p class="intro">{{ t('validation.intro') }}</p>
  <details class="how">
    <summary>{{ t('validation.howSummary') }}</summary>
    <p>{{ t('validation.howText') }}</p>
  </details>
  <ul class="verdict-legend" :aria-label="t('validation.verdictAria')">
    <li v-for="item in verdicts" :key="item.code">
      <span class="verdict-slot"><span class="verdict" :class="`verdict-${item.code}`">{{ item.label }}</span></span>
      <span>{{ item.help }}</span>
    </li>
  </ul>
  <p v-if="validationLoading" class="small-note" role="status">{{ t('validation.loading') }}</p>
  <p v-else-if="validationError" class="small-note error" role="alert">{{ validationError }}</p>
  <ul v-else class="event-list">
    <li v-for="event in pagedValidation" :key="`${event.aoiId}-${event.isoyear}-${event.week}`" class="event-card">
      <div class="event-head">
        <div>
          <h3 class="event-name">{{ event.name }}</h3>
          <p class="event-when">{{ t('validation.week', { week: event.week, year: event.isoyear }) }}</p>
        </div>
        <span class="verdict" :class="`verdict-${event.verdict}`" :title="t('validation.verdictTitle', { verdict: event.verdict })">{{ verdictLabel(event.verdict) }}</span>
      </div>
      <p class="event-anomaly" :class="{ 'is-empty': event.anomaly === null }">
        <span class="anomaly-value">{{ event.anomaly === null ? '—' : `${format(event.anomaly)}×` }}</span>
        <span class="anomaly-label">{{ event.anomaly === null ? t('validation.noAnomaly') : t('validation.timesBaseline') }}</span>
      </p>
      <div class="bars">
        <div class="bar-row">
          <div class="bar-head"><span>{{ t('validation.observed') }}</span><strong>{{ format(event.observed) }}</strong></div>
          <div class="bar-track" aria-hidden="true"><span class="bar-fill bar-observed" :style="{ width: barWidth(event, 'observed') }"></span></div>
        </div>
        <div class="bar-row">
          <div class="bar-head"><span>{{ t('validation.baseline') }}</span><strong>{{ event.baseline === null ? '—' : format(event.baseline) }}</strong></div>
          <div class="bar-track" aria-hidden="true"><span class="bar-fill bar-baseline" :style="{ width: barWidth(event, 'baseline') }"></span></div>
        </div>
      </div>
    </li>
  </ul>
  <nav v-if="eventsPageCount > 1" class="pager" :aria-label="t('validation.pagerAria')">
    <button class="link-btn" type="button" :disabled="eventsPage === 0" :aria-label="t('validation.prevAria')" @click="eventsPage--">{{ t('common.previous') }}</button>
    <span class="small-note">{{ t('common.page', { page: eventsPage + 1, total: eventsPageCount }) }} · {{ validationRange }}</span>
    <button class="link-btn" type="button" :disabled="eventsPage >= eventsPageCount - 1" :aria-label="t('validation.nextAria')" @click="eventsPage++">{{ t('common.next') }}</button>
  </nav>
</section>
</template>
<style scoped>
.panel-head{display:flex;justify-content:space-between;align-items:center;gap:16px}
.intro{margin:14px 0 0;font-size:14px;line-height:1.5;color:#dfe6f7}
.how{margin-top:10px;font-size:13px;line-height:1.5;color:#dfe6f7}
.how summary{display:flex;align-items:center;gap:8px;min-height:32px;color:#6fb0ff;font-weight:700;list-style:none;cursor:pointer}
.how summary::-webkit-details-marker{display:none}
.how summary::before{content:'▸';font-size:11px;line-height:1}
.how[open] summary::before{content:'▾'}
.how p{margin:6px 0 0}
.verdict-legend{display:flex;flex-direction:column;gap:10px;margin:16px 0 0;padding:14px;list-style:none;border:1px solid #1F3F86;border-radius:10px;font-size:13px;line-height:1.45;color:#dfe6f7}
.verdict-legend li{display:flex;align-items:flex-start;gap:12px}
.verdict-slot{flex:none;width:84px;display:flex;justify-content:center}
.verdict{display:inline-block;font-size:12px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;padding:5px 10px;border-radius:6px;white-space:nowrap}
.verdict-pass{background:var(--warm-lime);color:#101820}
.verdict-fail{background:#C92A05;color:#fff}
.verdict-insufficient{background:transparent;color:#fff;border:1px solid #6fb0ff}
.event-list{display:flex;flex-direction:column;gap:12px;margin:16px 0 0;padding:0;list-style:none}
.event-card{display:flex;flex-direction:column;gap:16px;background:#0B2257;border:1px solid #1F3F86;border-radius:12px;padding:18px}
.event-head{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}
.event-head .verdict{flex:none}
.event-name{margin:0;font:800 20px/1.2 var(--font-display);text-transform:none;letter-spacing:0}
.event-when{margin:3px 0 0;font-size:13px;color:#c5d0ea}
.event-anomaly{display:flex;align-items:baseline;gap:10px;margin:0}
.anomaly-value{font:800 40px/1 var(--font-display);color:var(--warm-lime)}
.anomaly-label{font-size:14px;color:#dfe6f7}
.event-anomaly.is-empty .anomaly-value{color:#c5d0ea}
.bars{display:flex;flex-direction:column;gap:10px}
.bar-head{display:flex;justify-content:space-between;align-items:baseline;gap:12px;margin-bottom:5px;font-size:13px}
.bar-head span{font-size:11px;font-weight:600;letter-spacing:.08em;text-transform:uppercase;color:#c5d0ea}
.bar-track{height:8px;background:#16336f;border-radius:4px;overflow:hidden}
.bar-fill{display:block;height:100%;border-radius:4px}
.bar-observed{background:var(--warm-lime)}
.bar-baseline{background:#6fb0ff}
.small-note{margin:12px 0 0}
.error{color:#ffb4a8}
.pager{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:16px;flex-wrap:wrap}
.pager .link-btn:disabled{opacity:.45;cursor:not-allowed}
@media(max-width:600px){.event-card{padding:14px}.anomaly-value{font-size:34px}.verdict-slot{width:80px}}
</style>
