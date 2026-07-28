<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <main class="flex min-h-dvh items-center justify-center bg-[#f3f1ec] px-6 text-stone-900">
    <section class="text-center">
      <p class="text-xs font-semibold tracking-[0.32em] text-amber-700">T-RAVELERS</p>
      <h1 class="mt-3 text-4xl font-bold tracking-tight">NA-WA</h1>
      <p class="mt-3 text-base text-stone-600">{{ t('app.tagline') }}</p>
    </section>
  </main>
</template>
