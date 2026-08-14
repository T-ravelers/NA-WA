<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <div class="flex min-h-48 flex-col items-center justify-center gap-2 px-4 text-center">
    <p class="text-title text-ink">{{ title }}</p>
    <p class="max-w-72 text-body-sm text-ink-3">{{ description }}</p>
  </div>
</template>
