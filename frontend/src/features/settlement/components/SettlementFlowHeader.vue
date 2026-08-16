<script setup lang="ts">
import { IconChevronLeft } from '@tabler/icons-vue'

interface Props {
  current: number
  title?: string
  backLabel?: string
}

const { current, title = 'SENT', backLabel = 'Back' } = defineProps<Props>()
const emit = defineEmits<{ back: [] }>()
</script>

<template>
  <header>
    <div class="flex items-center gap-5">
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
      <h1 class="font-display text-screen-title font-bold uppercase text-ink-display">
        {{ title }}
      </h1>
    </div>
    <div
      class="mt-6 grid grid-cols-4 gap-1.5"
      aria-label="Request progress"
    >
      <span
        v-for="index in 4"
        :key="index"
        class="h-1.5 rounded-pill"
        :class="index <= current ? 'bg-gauge' : 'bg-hairline-strong'"
      />
    </div>
  </header>
</template>
