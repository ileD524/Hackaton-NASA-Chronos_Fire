<script setup lang="ts">
defineProps<{
  region?: string
  rangeLabel?: string
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
</style>