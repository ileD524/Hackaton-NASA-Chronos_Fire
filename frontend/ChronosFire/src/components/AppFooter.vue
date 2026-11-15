<script setup lang="ts">
import BrandMark from '@/components/BrandMark.vue'

const year = new Date().getFullYear()
</script>

<template>
  <footer class="app-footer">
    <div class="container footer-inner">
      <BrandMark class="footer-brand" :size="32" tagline />

      <div class="footer-meta">
        <span class="footer-credit">Datos: NASA Earthdata FIRMS · MODIS y VIIRS · grilla 0,05°</span>
        <span class="footer-nasa">Proyecto participante de NASA Space Apps {{ year }}</span>
      </div>

      <img
        class="footer-logo"
        src="/logos/space-apps-lockup.svg"
        alt="NASA Space Apps Challenge"
        width="150"
        height="59"
      />
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  margin-top: auto;
  padding: var(--space-2) 0 var(--space-5);
  border-top: 1px solid var(--blue-400);
}

.container {
  max-width: 1360px;
  margin: 0 auto;
  padding: 0 32px;
}

.footer-inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-4) var(--space-5);
  justify-content: space-between;
  padding-top: var(--space-4);
  font-size: 14px;
}

.footer-brand {
  flex: none;
}

.footer-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1 1 260px;
  min-width: 0;
}

.footer-credit,
.footer-nasa {
  opacity: 0.85;
}

.footer-logo {
  display: block;
  height: 44px;
  width: auto;
  opacity: 0.95;
}

@media (max-width: 900px) {
  .footer-inner {
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .container {
    padding: 0 20px;
  }
  .footer-inner {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  .footer-meta {
    flex: none;
    align-items: center;
  }
}
</style>
