import { ref } from 'vue'
import type { AlertResponse, Bbox, ValidationEvent } from '@/data/types'
import { fetchJSON } from './useChronos'
import { t } from './useI18n'

const alerts = ref<AlertResponse | null>(null)
const validation = ref<ValidationEvent[]>([])
const alertsLoading = ref(false)
const validationLoading = ref(false)
const alertsError = ref('')
const alertsUpdatedAt = ref<Date | null>(null)
const validationError = ref('')

export function useAlerts() {
  async function loadAlerts(bbox: Bbox | null) {
    alertsLoading.value = true
    alertsError.value = ''
    try {
      const params = new URLSearchParams({ days: '7', limit: '50' })
      if (bbox) params.set('bbox', bbox.join(','))
      alerts.value = await fetchJSON<AlertResponse>(`/api/alerts?${params}`)
      alertsUpdatedAt.value = new Date()
    } catch (e) {
      alertsError.value = e instanceof Error ? e.message : t('error.alerts')
    } finally {
      alertsLoading.value = false
    }
  }

  async function loadValidation() {
    validationLoading.value = true
    validationError.value = ''
    try {
      validation.value = await fetchJSON<ValidationEvent[]>('/api/validation')
    } catch (e) {
      validationError.value = e instanceof Error ? e.message : t('error.validation')
    } finally {
      validationLoading.value = false
    }
  }

  return {
    alerts,
    validation,
    alertsLoading,
    validationLoading,
    alertsError,
    alertsUpdatedAt,
    validationError,
    loadAlerts,
    loadValidation,
  }
}
