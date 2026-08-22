<script setup lang="ts">
import { IconChevronLeft } from '@tabler/icons-vue'

interface Props {
  current: number
  title: string
  backLabel?: string
  /**
   * 눈금 수.
   *
   * 시안은 입력 3단계와 전송 완료까지 네 칸으로 그린다. 완료 화면은 되돌아갈 수 없어
   * 이 헤더를 쓰지 않지만, 눈금은 같은 네 칸을 이어받아 마지막 칸을 채운다.
   */
  total?: number
}

const { current, title, backLabel = 'Back', total = 4 } = defineProps<Props>()
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
