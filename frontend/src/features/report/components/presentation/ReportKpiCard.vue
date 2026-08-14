<script setup lang="ts">
import { IconArrowsExchange } from '@tabler/icons-vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import AppTicket from '@/shared/ui/AppTicket.vue'

import { formatMoney } from './format'
import type { ReportKpiCardProps } from './types'

/**
 * 총 지출·일 평균 KPI.
 *
 * 시안 R4의 `Analysis` 블록이다. 절취선과 노치가 있는 종이톤 조형이므로 `AppCard`가
 * 아니라 `AppTicket`을 쓴다. 노치와 절취선은 `AppTicket`이 소유하므로 여기서 다시
 * 그리지 않는다.
 *
 * `AppTicket`의 가로형 body가 왼쪽 고정 폭을 차지하므로 금액 영역을 body에 두고,
 * 아이콘을 오른쪽 stub에 둔다. 금액은 고정 영역 안에서 줄바꿈할 수 있어 자릿수가
 * 긴 통화 문자열도 절취선을 넘어가거나 조용히 잘리지 않는다.
 *
 * 표시 문자열은 전부 props로 받는다. `useI18n`을 쓰면 상위(#153)의 i18n 등록 없이는
 * 렌더되지 않아 props-only 계약이 깨진다.
 */
const { data, heading = undefined, locale = 'en' } = defineProps<ReportKpiCardProps>()

/** 금액 영역 폭. 시안의 350px 카드에서 왼쪽 약 253px을 차지한다. */
const DEFAULT_AMOUNT_COLUMN_WIDTH = 253

const ticketContainer = ref<HTMLElement | null>(null)
const measuredContainerWidth = ref<number | null>(null)
let resizeObserver: ResizeObserver | null = null

const amountColumnWidth = computed(() => {
  const width = measuredContainerWidth.value

  if (width === null || width <= 0) {
    return DEFAULT_AMOUNT_COLUMN_WIDTH
  }

  return Math.min(DEFAULT_AMOUNT_COLUMN_WIDTH, Math.floor(width * 0.75))
})

const totalAmountClass = computed(() =>
  amountColumnWidth.value < DEFAULT_AMOUNT_COLUMN_WIDTH ? 'text-title' : 'text-data-lg',
)

onMounted(() => {
  if (ticketContainer.value === null || typeof ResizeObserver === 'undefined') {
    return
  }

  resizeObserver = new ResizeObserver(([entry]) => {
    const width = entry?.contentRect.width

    if (width !== undefined && Number.isFinite(width)) {
      measuredContainerWidth.value = width
    }
  })
  resizeObserver.observe(ticketContainer.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})

const totalText = computed(() => formatMoney(data.totalSpent, data.currency, locale))
const dailyAverageText = computed(() => formatMoney(data.dailyAverage, data.currency, locale))
</script>

<template>
  <section class="flex flex-col gap-3">
    <h2
      v-if="heading !== undefined"
      class="font-display text-section-header uppercase text-ink"
    >
      {{ heading }}
    </h2>

    <div
      ref="ticketContainer"
      class="w-full"
    >
      <AppTicket
        orientation="horizontal"
        :body-size="amountColumnWidth"
        :notch-size="12"
        tone="paper"
        class="min-h-35"
      >
        <template #body>
          <dl class="flex min-w-0 flex-col justify-center gap-3 p-5">
            <div class="flex min-w-0 flex-col gap-1">
              <dt class="text-micro uppercase text-on-paper/65">{{ totalLabel }}</dt>
              <dd
                class="break-all font-display tabular-nums text-on-paper"
                :class="totalAmountClass"
              >
                {{ totalText }}
              </dd>
            </div>
            <div class="flex min-w-0 flex-col gap-1">
              <dt class="text-micro uppercase text-on-paper/65">{{ dailyAverageLabel }}</dt>
              <dd class="break-all text-title tabular-nums text-on-paper">
                {{ dailyAverageText }}
              </dd>
            </div>
          </dl>
        </template>

        <template #stub>
          <!-- 아이콘은 장식이다. 값은 모두 body 쪽 텍스트로 읽힌다. -->
          <div
            aria-hidden="true"
            class="flex size-full items-center justify-center"
          >
            <span class="flex size-11 items-center justify-center rounded-pill border-2">
              <IconArrowsExchange
                :size="20"
                :stroke-width="1.75"
              />
            </span>
          </div>
        </template>
      </AppTicket>
    </div>
  </section>
</template>
