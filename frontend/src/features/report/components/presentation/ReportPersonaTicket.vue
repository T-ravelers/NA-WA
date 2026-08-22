<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { vFitText } from '@/shared/lib/fitText'
import AppTicket from '@/shared/ui/AppTicket.vue'

import type { ReportPersonaTicketProps } from './types'

/**
 * 소비 성향 칭호 티켓.
 *
 * 시안 R4의 `Your spending type` 블록이다. 1위 카테고리의 코어색 위에 해시태그 칭호를
 * 크게 찍고, 절취선 아래 stub에 비중 스탬프를 둔다. 조형은 `AppTicket`이 소유한다.
 *
 * 세로형 티켓은 body 높이를 px로 받는다. 설명 문장이 로케일마다 길이가 달라 고정값으로는
 * 잘리므로, body 내용의 실제 높이를 재서 넘긴다. 측정 전(또는 jsdom)에는 시안 실측값을 쓴다.
 *
 * 표시 문자열은 전부 props로 받는다 — `useI18n`을 쓰면 props-only 계약이 깨진다.
 */
const {
  label,
  title,
  description,
  stampValue,
  stampLabel,
  tone = 'paper',
} = defineProps<ReportPersonaTicketProps>()

/** 시안의 body 높이. 라벨 한 줄 + 제목 한 줄 + 설명 두 줄. */
const DEFAULT_BODY_HEIGHT = 148

const bodyContent = ref<HTMLElement | null>(null)
const measuredBodyHeight = ref<number | null>(null)
let resizeObserver: ResizeObserver | null = null

const bodySize = computed(() => {
  const height = measuredBodyHeight.value

  if (height === null || height <= 0) {
    return DEFAULT_BODY_HEIGHT
  }

  return Math.ceil(height)
})

onMounted(() => {
  if (bodyContent.value === null || typeof ResizeObserver === 'undefined') {
    return
  }

  resizeObserver = new ResizeObserver(([entry]) => {
    const height = entry?.borderBoxSize?.[0]?.blockSize ?? entry?.contentRect.height

    if (height !== undefined && Number.isFinite(height)) {
      measuredBodyHeight.value = height
    }
  })
  resizeObserver.observe(bodyContent.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})
</script>

<template>
  <section class="flex flex-col gap-3">
    <AppTicket
      orientation="vertical"
      :body-size="bodySize"
      :notch-size="12"
      :tone="tone"
    >
      <template #body>
        <div
          ref="bodyContent"
          class="flex flex-col gap-2 p-5"
        >
          <p class="text-micro uppercase opacity-75">{{ label }}</p>
          <!-- 해시태그는 한 단어라 줄바꿈이 없다. 좁은 폭에서는 글자를 줄인다(#356). -->
          <h2
            v-fit-text
            class="truncate font-display text-section-header font-bold leading-tight"
          >
            {{ title }}
          </h2>
          <p class="text-body-sm">{{ description }}</p>
        </div>
      </template>

      <template #stub>
        <div class="flex min-h-20 items-center justify-end px-5 py-4">
          <!-- 스탬프. 값은 본문 설명 문장에도 들어 있어 장식으로 둔다. -->
          <span
            aria-hidden="true"
            class="flex size-16 -rotate-6 flex-col items-center justify-center rounded-pill border-2 border-current"
          >
            <span class="font-display text-title-sm font-bold tabular-nums">{{ stampValue }}</span>
            <span class="text-micro uppercase">{{ stampLabel }}</span>
          </span>
        </div>
      </template>
    </AppTicket>
  </section>
</template>
