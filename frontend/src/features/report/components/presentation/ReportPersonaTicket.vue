<script setup lang="ts">
import { IconShare } from '@tabler/icons-vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { vFitText } from '@/shared/lib/fitText'
import AppButton from '@/shared/ui/AppButton.vue'
import AppTicket from '@/shared/ui/AppTicket.vue'

import type { ReportPersonaTicketProps } from './types'

/**
 * 소비 성향 칭호 티켓.
 *
 * 시안 R4의 `Your spending type` 블록이다. 섹션 제목 아래 티켓을 두고, 도넛 1위 조각과 같은
 * 색 위에 해시태그 칭호를 크게 찍고, 절취선 아래 stub에 공유 버튼과 비중 스탬프를 둔다.
 * 조형은 `AppTicket`이 소유한다.
 *
 * 세로형 티켓은 body 높이를 px로 받는다. 설명 문장이 로케일마다 길이가 달라 고정값으로는
 * 잘리므로, body 내용의 실제 높이를 재서 넘긴다. 측정 전(또는 jsdom)에는 시안 실측값을 쓴다.
 *
 * 표시 문자열은 전부 props로 받는다 — `useI18n`을 쓰면 props-only 계약이 깨진다.
 */
const {
  heading = undefined,
  label,
  title,
  description,
  stampValue,
  stampLabel,
  shareLabel = undefined,
  tone = 'paper',
} = defineProps<ReportPersonaTicketProps>()

const emit = defineEmits<{ share: [] }>()

/**
 * 스탬프 라벨 하한. 원의 곡선 안에 넣기 위한 값이다.
 *
 * `text-micro`는 11px이라 기본 하한 50%는 5.5px가 되어 읽을 수 없다. 시안의 스탬프 라벨이
 * 8px급이므로 그 부근인 65%(7.15px)를 하한으로 둔다.
 */
const STAMP_LABEL_MIN_RATIO = 0.65

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
    <h2
      v-if="heading !== undefined"
      class="font-display text-section-header uppercase text-ink"
    >
      {{ heading }}
    </h2>

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
          <h3
            v-fit-text
            class="truncate font-display text-section-header font-bold leading-tight"
          >
            {{ title }}
          </h3>
          <p class="text-body-sm">{{ description }}</p>
        </div>
      </template>

      <template #stub>
        <!-- 280px에서는 알약과 스탬프가 한 줄에 안 들어간다. 스탬프가 둘째 줄 오른쪽으로 내려간다. -->
        <div class="flex min-h-20 flex-wrap items-center gap-3 px-5 py-4">
          <!-- 높이(44px)는 `on-ticket` 변형이 갖는다. 여기서 `dense`로 다시 정하지 않는다. -->
          <AppButton
            v-if="shareLabel !== undefined"
            variant="on-ticket"
            @click="emit('share')"
          >
            <span class="inline-flex items-center gap-2">
              <IconShare
                :size="18"
                :stroke-width="2"
                aria-hidden="true"
              />
              {{ shareLabel }}
            </span>
          </AppButton>
          <!-- 스탬프. 값은 본문 설명 문장에도 들어 있어 장식으로 둔다. -->
          <span
            aria-hidden="true"
            class="ml-auto flex size-16 -rotate-6 flex-col items-center justify-center rounded-pill border-2 border-current"
          >
            <span class="font-display text-title-sm font-bold tabular-nums">{{ stampValue }}</span>
            <!--
              라벨 칸을 원의 지름(60px)이 아니라 44px로 잡는다. 라벨은 세로 중앙보다 아래에
              놓여서 그 높이에서 원이 내주는 폭이 44~48px뿐이다. 칸을 지름으로 두면
              `Transport`·`ショッピング`이 곡선을 뚫고 나간다.
            -->
            <span
              v-fit-text.wrap="STAMP_LABEL_MIN_RATIO"
              class="max-w-11 truncate text-micro uppercase"
              >{{ stampLabel }}</span
            >
          </span>
        </div>
      </template>
    </AppTicket>
  </section>
</template>
