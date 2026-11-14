<script setup lang="ts">
defineProps<{
  region?: string
  rangeLabel?: string
}>()

const emit = defineEmits<{
  (e: 'open-calendar'): void
}>()
</script>

<template>
  <section class="hero" aria-labelledby="hero-title">
    <div class="hero-eyebrow">Alerta temprana · MODIS + VIIRS armonizados</div>
    <h1 id="hero-title" class="hero-title">
      Calendario de<br />actividad de incendios
    </h1>
    <!--p class="hero-sub">
      <strong class="accent">Más de dos décadas de hotspots,</strong>
      una sola serie. ¿Cómo está este mes frente a la historia?
    </p-->
    <p class="hero-sub">
      Recupera datos de <strong class="accent">más de dos décadas de historia</strong>.
    </p>
    <p v-if="region" class="hero-context">
      Área de interés: <strong>{{ region }}</strong> · {{ rangeLabel }}
    </p>

    <div class="hero-actions">
      <button type="button" class="hero-cta" @click="emit('open-calendar')">
        <svg
          class="hero-cta-ico"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M3 9h18M8 3v4M16 3v4" />
        </svg>
        Abrir el calendario
      </button>
      <a class="hero-link" href="#mapa">Ver el mapa en vivo</a>
    </div>
  </section>
</template>

<style scoped>
.hero {
  max-width: 900px;
  padding-top: 8px;
}

.hero-eyebrow {
  font-weight: 600;
  font-size: 14px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--warm-lime);
  margin-bottom: var(--space-3);
}

.hero-title {
  font-weight: 900;
  font-size: clamp(34px, 5vw, 60px);
  line-height: 1.08;
  letter-spacing: 0.02em;
}

.hero-sub {
  font-size: clamp(20px, 2.4vw, 30px);
  line-height: 1.25;
  margin: var(--space-4) 0 0;
  color: var(--white);
  opacity: 0.95;
  max-width: 820px;
}

.accent {
  color: var(--warm-lime);
  font-weight: 700;
}

.hero-context {
  margin-top: var(--space-4);
  font-size: 15px;
  color: var(--blue-100);
}

.hero-context strong {
  color: var(--white);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-4);
  margin-top: var(--space-5);
}

.hero-cta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 10px 22px;
  border: 0;
  border-radius: var(--radius-sm);
  background: var(--warm-lime);
  color: var(--blue-900);
  font: 800 16px var(--font-body);
  letter-spacing: 0.02em;
  cursor: pointer;
  transition: transform 0.12s ease, background 0.15s ease;
}

.hero-cta:hover {
  background: #f4ff5e;
  transform: translateY(-1px);
}

.hero-cta:active {
  transform: translateY(0);
}

.hero-cta-ico {
  width: 20px;
  height: 20px;
}

.hero-link {
  display: inline-flex;
  align-items: center;
  min-height: 48px;
  padding: 0 4px;
  font: 700 16px var(--font-body);
  color: var(--white);
  text-decoration: underline;
  text-underline-offset: 4px;
}

.hero-link:hover {
  color: var(--warm-lime);
}
</style>