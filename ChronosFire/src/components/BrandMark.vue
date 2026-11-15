<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'horizontal' | 'stacked' | 'symbol'
    size?: number
    tagline?: boolean
  }>(),
  { variant: 'horizontal', size: 44, tagline: false },
)
</script>

<template>
  <span
    class="brand-mark"
    :class="`brand-mark--${variant}`"
    :style="{ '--mark': `${size}px` }"
  >
    <svg
      class="brand-symbol"
      viewBox="0 0 100 100"
      :width="size"
      :height="size"
      aria-hidden="true"
      focusable="false"
    >
      <path
        d="M22 26L50 80L78 26"
        fill="none"
        stroke="currentColor"
        stroke-width="10"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <ellipse
        cx="50"
        cy="52"
        rx="46"
        ry="17"
        transform="rotate(-28 50 52)"
        fill="none"
        stroke="var(--brand-orbit, #2E96F5)"
        stroke-width="4"
      />
      <circle class="brand-hotspot" cx="84" cy="30" r="8" fill="var(--brand-hotspot, #E43700)" />
    </svg>

    <span v-if="variant !== 'symbol'" class="brand-text">
      <span class="brand-word">VII<span class="brand-dot">.</span>MO</span>
      <span v-if="tagline" class="brand-tagline">Calendario de incendios</span>
    </span>
  </span>
</template>

<style scoped>
.brand-mark {
  display: inline-flex;
  align-items: center;
  gap: calc(var(--mark) * 0.21);
  color: var(--white);
}

.brand-mark--stacked {
  flex-direction: column;
  gap: calc(var(--mark) * 0.15);
  text-align: center;
}

.brand-symbol {
  display: block;
  flex: none;
  overflow: visible;
}

.brand-hotspot {
  transition: filter 0.2s ease;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: calc(var(--mark) * 0.09);
  min-width: 0;
}

.brand-word {
  font-family: var(--font-display);
  font-weight: 900;
  font-size: calc(var(--mark) * 0.667);
  line-height: 1;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--white);
}

.brand-mark--stacked .brand-word {
  font-size: calc(var(--mark) * 0.433);
}

.brand-dot {
  color: var(--brand-hotspot, #E43700);
}

.brand-tagline {
  font-family: var(--font-body);
  font-weight: 600;
  font-size: max(10px, calc(var(--mark) * 0.135));
  line-height: 1.1;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--white);
  opacity: 0.92;
}

.brand-mark--stacked .brand-tagline {
  font-size: max(10px, calc(var(--mark) * 0.11));
}
</style>
