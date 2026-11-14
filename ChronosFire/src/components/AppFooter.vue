<script setup lang="ts">
const year = new Date().getFullYear()
</script>

<template>
  <footer class="app-footer">
    <div class="container footer-inner">
      <span class="footer-credit">
        Datos: NASA Earthdata FIRMS · MODIS y VIIRS
      </span>
      <img
        class="footer-logo"
        src="/logos/space-apps-motif.svg"
        alt="Logo pequeño de NASA Space Apps Challenge"
        width="44"
        height="44"
      />
      <span class="footer-nasa">NASA Space Apps Challenge {{ year }} · ChronosFire</span>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  margin-top: auto;
  padding: var(--space-5) 0;
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
  gap: var(--space-4);
  justify-content: space-between;
  font-size: 14px;
}

.footer-logo {
  display: block;
  opacity: 0.95;
}

.footer-nasa {
  opacity: 0.85;
}

@media (max-width: 720px) {
  .container {
    padding: 0 20px;
  }
  .footer-inner {
    justify-content: center;
    text-align: center;
  }
}
</style>