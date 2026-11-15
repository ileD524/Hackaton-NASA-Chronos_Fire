<script setup lang="ts">
import { useI18n } from '@/composables/useI18n'

const { locale, t, LOCALE_OPTIONS } = useI18n()
</script>

<template>
  <label class="lang-switcher">
    <svg
      class="lang-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18M12 3a15 15 0 0 1 0 18M12 3a15 15 0 0 0 0 18" />
    </svg>
    <span class="sr-only">{{ t('lang.label') }}</span>
    <select v-model="locale" class="lang-select" :aria-label="t('lang.label')">
      <option v-for="option in LOCALE_OPTIONS" :key="option.id" :value="option.id">
        {{ option.label }}
      </option>
    </select>
  </label>
</template>

<style scoped>
.lang-switcher {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 4px 2px 9px;
  border: 1px solid var(--blue-100);
  border-radius: var(--radius-sm);
  background: rgba(7, 23, 63, 0.55);
}

.lang-icon {
  flex: none;
  width: 16px;
  height: 16px;
  color: var(--warm-lime);
}

.lang-select {
  appearance: none;
  min-height: 32px;
  padding: 2px 22px 2px 4px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--white);
  font: 700 12px var(--font-body);
  letter-spacing: 0.04em;
  cursor: pointer;
  color-scheme: dark;
  background-image: linear-gradient(45deg, transparent 50%, var(--white) 50%),
    linear-gradient(135deg, var(--white) 50%, transparent 50%);
  background-position: calc(100% - 11px) 14px, calc(100% - 7px) 14px;
  background-size: 4px 4px, 4px 4px;
  background-repeat: no-repeat;
}

.lang-select:hover {
  color: var(--warm-lime);
}

.lang-select option {
  background: var(--blue-900);
  color: var(--white);
  font-weight: 600;
}
</style>
