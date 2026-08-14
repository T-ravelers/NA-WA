<script setup lang="ts">
import { IconChevronLeft } from '@tabler/icons-vue'

interface Props {
  title: string
  backLabel?: string
}
const { title, backLabel = 'Back' } = defineProps<Props>()
const emit = defineEmits<{ back: [] }>()
</script>

<template>
  <header class="flex items-center gap-5">
    <button
      type="button"
      :aria-label="backLabel"
      class="flex size-11 shrink-0 items-center justify-start text-ink"
      @click="emit('back')"
    >
      <IconChevronLeft
        :size="24"
        :stroke-width="1.8"
        aria-hidden="true"
      />
    </button>
    <h1 class="font-display text-screen-title font-bold uppercase text-ink-display">{{ title }}</h1>
  </header>
</template>
