<script setup lang="ts">
import { useI18n } from 'vue-i18n'

interface Props {
  /** 스크린 리더에 읽힐 설명. 지정하지 않으면 공통 문구를 쓴다. */
  label?: string
  /** 표시할 스켈레톤 줄 수. */
  lines?: number
}

const { label = undefined, lines = 3 } = defineProps<Props>()

const { t } = useI18n()
</script>

<template>
  <div
    class="flex w-full flex-col gap-3"
    role="status"
    aria-live="polite"
  >
    <span class="sr-only">{{ label ?? t('state.loading') }}</span>
    <div
      v-for="line in lines"
      :key="line"
      class="h-16 animate-pulse rounded-card bg-surface-2"
    />
  </div>
</template>
