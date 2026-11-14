<script setup lang="ts">
withDefaults(defineProps<{ onOpen?: (panelName: 'metodologia') => void }>(), {
  onOpen: undefined,
})
</script>

<template>
  <section class="panel" id="metodologia" aria-labelledby="meth-title">
    <h2 id="meth-title" class="panel-title">Cómo se armoniza</h2>

    <div class="using-rows">
      <div class="using-row">
        <span class="using-key">MODIS · 2000 → hoy</span>
        <div class="using-bar"><span class="using-fill using-fill--modis" /></div>
      </div>
      <div class="using-row">
        <span class="using-key">VIIRS · 2012 → hoy</span>
        <div class="using-bar"><span class="using-fill using-fill--viirs" /></div>
      </div>
      <div class="using-row using-row--result">
        <span class="using-key">Serie armonizada</span>
        <div class="using-bar"><span class="using-fill using-fill--harm" /></div>
      </div>
    </div>

    <p class="meth-text">
      Se normalizan resolución, sensibilidad y horarios para que importe el
      patrón, no el sensor.
      <button type="button" class="link-btn" @click="onOpen && onOpen('metodologia')">
        Ver metodología
      </button>
    </p>
  </section>
</template>

<style scoped>
.panel {
  background: var(--surface-card);
  border: 1px solid var(--blue-400);
  border-radius: var(--radius-md);
  padding: var(--space-5);
}

.panel-title {
  font-size: 20px;
  font-weight: 800;
}

.using-rows {
  margin: var(--space-4) 0 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.using-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: 14px;
}

.using-key {
  width: 150px;
  flex-shrink: 0;
  opacity: 0.9;
}

.using-bar {
  flex: 1;
  height: 14px;
  border-radius: 7px;
  background: var(--surface-input);
  overflow: hidden;
}

.using-fill {
  display: block;
  height: 100%;
  border-radius: 7px;
}

.using-fill--modis {
  width: 100%;
  background: var(--blue-100);
}

.using-fill--viirs {
  width: 58%;
  margin-left: 42%;
  background: var(--blue-400);
}

.using-fill--harm {
  width: 100%;
  background: var(--warm-lime);
}

.using-row--result .using-key {
  color: var(--warm-lime);
  font-weight: 700;
}

.meth-text {
  margin: var(--space-4) 0 0;
  font-size: 15px;
  line-height: 1.45;
}

.link-btn {
  color: var(--warm-lime);
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 700;
  text-decoration: underline;
  background: none;
  border: 0;
  cursor: pointer;
  padding: 4px 0 4px 4px;
  min-height: 44px;
}
</style>