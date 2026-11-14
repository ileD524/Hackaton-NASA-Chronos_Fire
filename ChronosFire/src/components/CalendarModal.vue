<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import FireCalendar from '@/components/FireCalendar.vue'

const props = defineProps<{
  open: boolean
  selectedMonth: { year: number; month: number } | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'update:selectedMonth', value: { year: number; month: number } | null): void
}>()

const dialogRef = ref<HTMLElement | null>(null)
const closeBtn = ref<HTMLButtonElement | null>(null)
let lastFocused: HTMLElement | null = null

const modelMonth = computed({
  get: () => props.selectedMonth,
  set: (value) => {
    emit('update:selectedMonth', value)
    // Al elegir un mes se cierra para mostrar el resultado en el mapa
    if (value) emit('update:open', false)
  },
})

function close() {
  emit('update:open', false)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    close()
    return
  }
  if (e.key !== 'Tab' || !dialogRef.value) return
  const focusables = Array.from(
    dialogRef.value.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
    ),
  ).filter((el) => el.offsetParent !== null)
  if (focusables.length === 0) return
  const first = focusables[0]!
  const last = focusables[focusables.length - 1]!
  const active = document.activeElement
  if (e.shiftKey && (active === first || active === dialogRef.value)) {
    e.preventDefault()
    last.focus()
  } else if (!e.shiftKey && active === last) {
    e.preventDefault()
    first.focus()
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      lastFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
      document.body.style.overflow = 'hidden'
      document.addEventListener('keydown', onKeydown)
      requestAnimationFrame(() => closeBtn.value?.focus())
    } else {
      document.body.style.overflow = ''
      document.removeEventListener('keydown', onKeydown)
      lastFocused?.focus()
    }
  },
)
onBeforeUnmount(() => { document.removeEventListener('keydown', onKeydown); document.body.style.overflow = '' })
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="open" class="backdrop" @click.self="close">
        <div
          ref="dialogRef"
          class="dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="cal-modal-title"
        >
          <header class="dialog-head">
            <div class="dialog-head-text">
              <h2 id="cal-modal-title" class="dialog-title">
                Calendario de actividad de incendios
              </h2>
              <p class="dialog-hint">
                Elegí un mes para explorar sus detecciones locales y su detalle diario.
              </p>
            </div>
            <button
              ref="closeBtn"
              type="button"
              class="dialog-close"
              aria-label="Cerrar el calendario"
              @click="close"
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
                stroke-linecap="round"
                aria-hidden="true"
              >
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </header>

          <FireCalendar id="calendario-modal" v-model:selected-month="modelMonth" />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(4, 12, 34, 0.72);
  backdrop-filter: blur(6px);
}

.dialog {
  width: min(1080px, 100%);
  max-height: min(86vh, 900px);
  overflow: auto;
  padding: var(--space-5);
  background: var(--blue-900);
  border: 1px solid var(--blue-100);
  border-radius: var(--radius-lg);
  box-shadow: 0 32px 80px -24px rgba(0, 0, 0, 0.8);
}

.dialog-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.dialog-title {
  font-size: 24px;
  font-weight: 800;
}

.dialog-hint {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--blue-100);
  max-width: 62ch;
}

.dialog-close {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--blue-100);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--white);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.dialog-close:hover {
  background: var(--surface-input);
  border-color: var(--warm-lime);
}

.dialog-close svg {
  width: 20px;
  height: 20px;
}

.modal-enter-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.modal-leave-active {
  transition: opacity 0.12s ease, transform 0.12s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
  transform: scale(0.97) translateY(8px);
}
</style>
