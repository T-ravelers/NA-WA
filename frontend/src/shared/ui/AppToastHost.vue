<script setup lang="ts">
import { useToasts } from './toast'

const toasts = useToasts()
</script>

<template>
  <div
    class="pointer-events-none fixed inset-x-0 bottom-28 z-50 mx-auto flex w-full max-w-shell flex-col items-center gap-2 px-screen"
    aria-live="polite"
  >
    <p
      v-for="toast in toasts"
      :key="toast.id"
      class="rounded-pill bg-ink px-4 py-2 text-caption text-canvas shadow-sheet"
    >
      {{ toast.message }}
    </p>
  </div>
</template>
