<script setup lang="ts">
const navItems = [
  { label: 'Mapa', target: '#mapa' },
  { label: 'Calendario', target: '#calendario' },
  { label: 'Metodología', target: '#metodologia' },
  { label: 'Datos', target: '#datos' },
]
</script>

<template>
  <header class="app-header">
    <div class="container header-inner">
      <a class="brand" href="#calendario" aria-label="ChronosFire · volver al inicio">
        <img
          class="brand-logo"
          src="/logos/space-apps-lockup.svg"
          alt="NASA Space Apps Challenge · logo oficial"
          width="220"
          height="86"
        />
        <span class="brand-name">ChronosFire</span>
      </a>

      <nav class="nav" aria-label="Navegación principal">
        <a
          v-for="item in navItems"
          :key="item.target"
          class="nav-link"
          :href="item.target"
        >
          {{ item.label }}
        </a>
      </nav>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: #07173F;
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(9, 96, 225, 0.4);
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
  gap: 12px;
  text-decoration: none;
}

.brand-logo {
  height: 44px;
  width: auto;
  display: block;
}

.brand-name {
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 15px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--white);
  border-left: 1px solid rgba(46, 150, 245, 0.5);
  padding-left: 14px;
  white-space: nowrap;
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

@media (max-width: 720px) {
  .container {
    padding: 12px 20px;
  }
  .brand-name {
    display: none;
  }
  .brand-logo {
    height: 34px;
  }
  .nav {
    margin-left: 0;
    width: 100%;
  }
}
</style>