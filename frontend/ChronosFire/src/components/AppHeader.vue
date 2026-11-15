<script setup lang="ts">
import { computed } from 'vue'
import BrandMark from '@/components/BrandMark.vue'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { useI18n } from '@/composables/useI18n'

const { t } = useI18n()

const navItems = computed(() => [
  { label: t('nav.map'), target: '#mapa' },
  { label: t('nav.calendar'), target: '#calendario' },
  { label: t('nav.alerts'), target: '#alertas' },
  { label: t('nav.validation'), target: '#validacion' },
  { label: t('nav.methodology'), target: '#metodologia' },
  { label: t('nav.data'), target: '#datos' },
])
</script>

<template>
  <header class="app-header">
    <div class="container header-inner">
      <a class="brand" href="#calendario" :aria-label="t('nav.brand')">
        <BrandMark :size="40" />
      </a>

      <nav class="nav" :aria-label="t('nav.main')">
        <a
          v-for="item in navItems"
          :key="item.target"
          class="nav-link"
          :href="item.target"
        >
          {{ item.label }}
        </a>
      </nav>

      <LanguageSwitcher class="lang" />
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: rgba(7, 23, 63, 0.92);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(9, 96, 225, 0.4);
}

.app-header::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 2px;
  background: linear-gradient(
    90deg,
    var(--blue-100) 0%,
    var(--warm-lime) 58%,
    var(--warm-orange) 100%
  );
}

.container {
  max-width: 1360px;
  margin: 0 auto;
  padding: 12px 32px;
  box-sizing: border-box;
}

.header-inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px 24px;
}

.brand {
  display: inline-flex;
  align-items: center;
  text-decoration: none;
  border-radius: var(--radius-sm);
}

.brand :deep(.brand-symbol) {
  transition: transform 0.2s ease;
}

.brand:hover :deep(.brand-symbol) {
  transform: scale(1.03);
}

.brand:hover :deep(.brand-hotspot) {
  filter: drop-shadow(0 0 6px var(--warm-orange));
}

.nav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 22px;
  margin-left: auto;
}

.nav-link {
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-size: 14px;
  color: var(--white);
  text-decoration: none;
  padding: 6px 2px;
  border-bottom: 2px solid transparent;
  transition: color 0.15s ease, border-color 0.15s ease;
}

.nav-link:hover {
  color: var(--warm-lime);
}

.nav-link[aria-current='page'] {
  border-bottom-color: var(--warm-lime);
  color: var(--warm-lime);
}

.lang {
  flex: none;
}

@media (max-width: 720px) {
  .container {
    padding: 10px 20px;
  }
  .header-inner {
    justify-content: space-between;
  }
  .nav {
    order: 3;
    margin-left: 0;
    width: 100%;
    gap: 4px 18px;
  }
  .lang {
    order: 2;
    margin-left: auto;
  }
  .nav-link {
    font-size: 13px;
  }
}

@media (max-width: 430px) {
  .nav {
    gap: 2px 14px;
  }
  .nav-link {
    font-size: 12px;
    letter-spacing: 0.05em;
  }
}
</style>
