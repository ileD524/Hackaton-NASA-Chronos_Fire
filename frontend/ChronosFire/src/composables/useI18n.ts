import { computed, ref, watch } from 'vue'
import { MESSAGES, MONTHS, type MessageKey } from '@/i18n/messages'
import type { Metric } from '@/data/types'

export type Locale = 'es' | 'en'

const STORAGE_KEY = 'chronosfire.locale'

export interface LocaleOption {
  id: Locale
  label: string
  short: string
}

/** Los nombres se muestran en su propio idioma para que siempre sean legibles. */
export const LOCALE_OPTIONS: LocaleOption[] = [
  { id: 'es', label: 'Español', short: 'ES' },
  { id: 'en', label: 'English', short: 'EN' },
]

function detectLocale(): Locale {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === 'es' || stored === 'en') return stored
  } catch {
    // localStorage puede no estar disponible (modo privado, SSR).
  }
  const language = typeof navigator !== 'undefined' ? navigator.language?.toLowerCase() ?? '' : ''
  return language.startsWith('en') ? 'en' : 'es'
}

export const locale = ref<Locale>(detectLocale())

/** Locale BCP 47 para números y fechas. */
export const dateLocale = computed(() => (locale.value === 'en' ? 'en-US' : 'es-AR'))

/** Traduce una clave e interpola `{param}`. */
export function t(key: MessageKey, params?: Record<string, string | number>): string {
  let text: string = MESSAGES[locale.value][key] ?? MESSAGES.es[key]
  if (params) {
    for (const [name, value] of Object.entries(params)) {
      text = text.split(`{${name}}`).join(String(value))
    }
  }
  return text
}

export function formatNumber(value: number, options: Intl.NumberFormatOptions = {}): string {
  return new Intl.NumberFormat(dateLocale.value, options).format(value)
}

export function formatDateTime(
  value: string | number | Date,
  options: Intl.DateTimeFormatOptions = {},
): string {
  const date = value instanceof Date ? value : new Date(value)
  return new Intl.DateTimeFormat(dateLocale.value, options).format(date)
}

export function monthName(index: number): string {
  return MONTHS[locale.value].long[index] ?? ''
}

export function monthShort(index: number): string {
  return MONTHS[locale.value].short[index] ?? ''
}

export function seriesLabel(metric: Metric): string {
  return t(`series.${metric}` as MessageKey)
}

export function sensorLabel(metric: Metric): string {
  return t(`sensor.${metric}` as MessageKey)
}

export function anomalyLegend(
  id: 'low-extreme' | 'low' | 'neutral' | 'high' | 'high-extreme',
): string {
  return t(`band.${id}` as MessageKey)
}

export function setLocale(next: Locale): void {
  locale.value = next
}

function applyDocumentLanguage(): void {
  if (typeof document === 'undefined') return
  document.documentElement.lang = locale.value
  document.title = t('app.title')
  document
    .querySelector('meta[name="description"]')
    ?.setAttribute('content', t('app.description'))
}

watch(
  locale,
  (value) => {
    try {
      localStorage.setItem(STORAGE_KEY, value)
    } catch {
      // Persistencia best-effort: la aplicación sigue funcionando sin storage.
    }
    applyDocumentLanguage()
  },
  { immediate: true },
)

export function useI18n() {
  return {
    locale,
    dateLocale,
    setLocale,
    t,
    formatNumber,
    formatDateTime,
    monthName,
    monthShort,
    seriesLabel,
    sensorLabel,
    anomalyLegend,
    LOCALE_OPTIONS,
  }
}
