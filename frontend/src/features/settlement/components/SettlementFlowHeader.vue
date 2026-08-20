<script setup lang="ts">
import { IconChevronLeft } from '@tabler/icons-vue'

interface Props {
  current: number
  title: string
  backLabel?: string
  /** 입력 단계 수. 처리 중·완료는 되돌아갈 수 없어 여기에 포함하지 않는다. */
  total?: number
}

const { current, title, backLabel = 'Back', total = 3 } = defineProps<Props>()
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
      class="mt-6 flex gap-1.5"
      aria-label="Request progress"
    >
      <span
        v-for="index in total"
        :key="index"
        class="h-1.5 flex-1 rounded-pill"
        :class="index <= current ? 'bg-gauge' : 'bg-hairline-strong'"
      />
    </div>
  </header>
</template>
