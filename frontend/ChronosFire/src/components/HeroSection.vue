<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from '@/composables/useI18n'

defineProps<{
  region?: string
  rangeLabel?: string
}>()

const emit = defineEmits<{
  (e: 'open-calendar'): void
}>()

const { t } = useI18n()
const reduceMotion = ref(false)
let motionQuery: MediaQueryList | undefined

function syncMotion() {
  reduceMotion.value = motionQuery?.matches ?? false
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  syncMotion()
  motionQuery.addEventListener('change', syncMotion)
})

onUnmounted(() => motionQuery?.removeEventListener('change', syncMotion))
</script>

<template>
  <section class="hero" aria-labelledby="hero-title">
    <div class="hero-copy">
      <div class="hero-eyebrow">{{ t('hero.eyebrow') }}</div>
      <h1 id="hero-title" class="hero-title">
        {{ t('hero.title.1') }}<br />{{ t('hero.title.2') }}
      </h1>
      <!--p class="hero-sub">
        <strong class="accent">Más de dos décadas de hotspots,</strong>
        una sola serie. ¿Cómo está este mes frente a la historia?
      </p-->
      <p class="hero-sub">
        {{ t('hero.sub.pre') }}<strong>{{ t('hero.sub.strong') }}</strong>{{ t('hero.sub.post') }}
      </p>
      <p v-if="region" class="hero-context">
        {{ t('hero.context.label') }}: <strong>{{ region }}</strong> · {{ rangeLabel }}
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
          {{ t('hero.cta') }}
        </button>
        <a class="hero-link" href="#mapa">{{ t('hero.map') }}</a>
      </div>
    </div>

    <div class="hero-mark" aria-hidden="true">
      <svg class="hero-orbit" viewBox="0 0 100 100">
        <defs>
          <path id="hero-orbit-track" d="M90.62 30.4A46 17 -28 0 1 9.38 73.6A46 17 -28 0 1 90.62 30.4" />
          <!-- Recorte con la forma de la V: sólo se activa mientras el hotspot va por la mitad lejana. -->
          <mask id="hero-v-mask" maskUnits="userSpaceOnUse" x="-12" y="-12" width="124" height="124">
            <rect x="-12" y="-12" width="124" height="124" fill="#FFFFFF" />
            <path
              d="M22 26L50 80L78 26"
              fill="none"
              stroke="#000000"
              stroke-width="10"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <animate
                attributeName="stroke-opacity"
                values="0;0;1;1;0"
                keyTimes="0;0.475;0.525;0.975;1"
                dur="20s"
                repeatCount="indefinite"
              />
            </path>
          </mask>
        </defs>

        <!-- Mitad lejana de la órbita: se dibuja antes de la V y queda tapada por ella. -->
        <path class="hero-orbit-ring" d="M90.62 30.4A46 17 -28 0 0 9.38 73.6" />

        <path
          d="M22 26L50 80L78 26"
          fill="none"
          stroke="#FFFFFF"
          stroke-width="10"
          stroke-linecap="round"
          stroke-linejoin="round"
        />

        <!-- Mitad cercana: se dibuja después de la V y la cruza por delante. -->
        <path class="hero-orbit-ring" d="M90.62 30.4A46 17 -28 0 1 9.38 73.6" />

        <!-- Un solo hotspot: siempre encima, recortado por la V cuando pasa por atrás.
             La máscara va en el <g> (sin transformar) para que comparta el espacio de la V;
             si se aplica al círculo, animateMotion desplaza también el recorte. -->
        <circle v-if="reduceMotion" cx="84" cy="30" r="5.5" fill="#E43700" />
        <g v-else mask="url(#hero-v-mask)">
          <circle class="hero-orbit-dot" r="5.5" fill="#E43700">
            <animateMotion dur="20s" repeatCount="indefinite">
              <mpath href="#hero-orbit-track" />
            </animateMotion>
            <animate
              attributeName="r"
              values="5.5;5.1;5.5;5.9;5.5"
              keyTimes="0;0.25;0.5;0.75;1"
              dur="20s"
              repeatCount="indefinite"
            />
          </circle>
        </g>
      </svg>
    </div>
  </section>
</template>

<style scoped>
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: center;
  gap: var(--space-6);
  max-width: 1120px;
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
  color: var(--white);
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

.hero-mark {
  display: none;
}

@media (min-width: 1000px) {
  .hero {
    grid-template-columns: minmax(0, 1fr) clamp(200px, 21vw, 292px);
  }

  .hero-mark {
    display: block;
    justify-self: end;
    width: clamp(200px, 21vw, 292px);
  }
}

.hero-orbit {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}

.hero-orbit-ring {
  fill: none;
  stroke: #2E96F5;
  stroke-width: 1.8;
  opacity: 0.85;
}

.hero-orbit-dot {
  filter: drop-shadow(0 0 6px rgba(228, 55, 0, 0.85));
}
</style>
