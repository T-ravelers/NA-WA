<script setup lang="ts">
import { IconCheck, IconLoader2, IconSend } from '@tabler/icons-vue'

import AppButton from '@/shared/ui/AppButton.vue'

/**
 * 처리 중과 완료를 그리는 전체 화면.
 *
 * 요청 생성과 결제가 같은 화면을 쓰기 때문에 두 흐름의 끝이 같은 모양으로 보인다.
 *
 * 시안은 완료 표식을 선 아이콘이 아니라 **면으로 채운 원** 안에 둔다. 어두운 캔버스
 * 한가운데에서 선 아이콘만으로는 끝났다는 신호가 서지 않는다.
 */
interface Props {
  state: 'processing' | 'done'
  title: string
  description: string
  actionLabel?: string
  /**
   * 완료 표식의 그림.
   *
   * 결제는 체크, 요청 전송은 종이비행기다. 같은 화면을 쓰는 두 흐름이 끝에서 무엇을
   * 했는지 구분되게 한다.
   */
  icon?: 'check' | 'send'
  /**
   * 요청 생성 흐름의 눈금.
   *
   * 되돌아갈 수는 없지만 시안은 마지막 칸이 채워진 네 칸을 그대로 남긴다. 결제 흐름은
   * 단계가 없으므로 넘기지 않는다.
   */
  steps?: number
}

const {
  state,
  title,
  description,
  actionLabel = undefined,
  icon = 'check',
  steps = undefined,
} = defineProps<Props>()
const emit = defineEmits<{ action: [] }>()
</script>

<template>
  <!-- 하단 고정 내비게이션이 CTA를 덮지 않도록 목록 화면과 같은 여백을 둔다. -->
  <section
    class="flex min-h-dvh flex-col px-screen pt-14 pb-32"
    :data-testid="`settlement-status-${state}`"
  >
    <div
      v-if="steps !== undefined && state === 'done'"
      class="flex gap-1.5"
      aria-hidden="true"
    >
      <span
        v-for="index in steps"
        :key="index"
        class="h-1.5 flex-1 rounded-pill bg-gauge"
      />
    </div>

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
      <!--
        면으로 채운 원 위에 흰 그림을 얹는다. `success`(#097e56) 위의 `ink`(#fbfaf8)는
        4.93:1이고, 원 자체도 캔버스(#171717) 대비 3.59:1이라 도형으로 읽힌다.
        반투명(`bg-success/25`)으로 깔면 원과 그림이 2.75:1까지 떨어져 둘 다 묻힌다.
      -->
      <span
        v-else
        class="flex size-20 items-center justify-center rounded-pill bg-success text-ink"
      >
        <IconSend
          v-if="icon === 'send'"
          :size="32"
          :stroke-width="1.8"
          aria-hidden="true"
        />
        <IconCheck
          v-else
          :size="32"
          :stroke-width="2.2"
          aria-hidden="true"
        />
      </span>
      <h1 class="mt-6 text-section-header">{{ title }}</h1>
      <p class="mt-2 max-w-72 text-body-sm text-ink-2">{{ description }}</p>
      <!-- 거래 번호·받는 사람·금액처럼 끝난 일을 적어 두는 자리. 없으면 비운다. -->
      <div class="w-full"><slot name="summary" /></div>
    </div>
    <AppButton
      v-if="actionLabel !== undefined"
      data-action="status-action"
      block
      @click="emit('action')"
      >{{ actionLabel }}</AppButton
    >
  </section>
</template>
