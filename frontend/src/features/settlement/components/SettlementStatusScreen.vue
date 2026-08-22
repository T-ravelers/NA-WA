<script setup lang="ts">
import { IconCircleCheck, IconLoader2 } from '@tabler/icons-vue'

import AppButton from '@/shared/ui/AppButton.vue'

/**
 * 처리 중과 완료를 그리는 전체 화면.
 *
 * 되돌아갈 수 없는 지점이라 단계 표시를 두지 않는다. 요청 생성과 결제가 같은 화면을
 * 쓰기 때문에 두 흐름의 끝이 같은 모양으로 보인다.
 */
interface Props {
  state: 'processing' | 'done'
  title: string
  description: string
  actionLabel?: string
}

const { state, title, description, actionLabel = undefined } = defineProps<Props>()
const emit = defineEmits<{ action: [] }>()
</script>

<template>
  <!-- 하단 고정 내비게이션이 CTA를 덮지 않도록 목록 화면과 같은 여백을 둔다. -->
  <section
    class="flex min-h-dvh flex-col px-screen pt-8 pb-32"
    :data-testid="`settlement-status-${state}`"
  >
    <div
      class="my-auto flex flex-col items-center text-center"
      role="status"
      aria-live="polite"
    >
      <IconLoader2
        v-if="state === 'processing'"
        :size="40"
        :stroke-width="1.8"
        class="animate-spin text-settlement"
        aria-hidden="true"
      />
      <IconCircleCheck
        v-else
        :size="40"
        :stroke-width="1.8"
        class="text-success"
        aria-hidden="true"
      />
      <h1 class="mt-6 text-section-header">{{ title }}</h1>
      <p class="mt-2 max-w-72 text-body-sm text-ink-2">{{ description }}</p>
    </div>
    <AppButton
      v-if="actionLabel !== undefined"
      data-action="status-action"
      block
      variant="settle"
      @click="emit('action')"
      >{{ actionLabel }}</AppButton
    >
  </section>
</template>
