<script setup lang="ts">
import { IconLoader2 } from '@tabler/icons-vue'

defineProps<{ label: string }>()
</script>

<template>
  <div
    data-testid="settlement-inline-loading"
    class="flex min-h-40 flex-col items-center justify-center gap-3 text-center text-body-sm text-ink-2"
    role="status"
    aria-live="polite"
  >
    <IconLoader2
      :size="24"
      class="animate-spin text-info"
      aria-hidden="true"
    />
    <span>{{ label }}</span>
  </div>
</template>
