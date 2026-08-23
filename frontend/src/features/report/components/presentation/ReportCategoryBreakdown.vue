<script setup lang="ts">
import { computed } from 'vue'

import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'

import { formatMoney, formatPercent } from './format'
import ReportSeriesMarker from './ReportSeriesMarker.vue'
import { seriesInkClass } from './seriesPalette'
import type { ReportCategoryBreakdownProps } from './types'

/**
 * 카테고리 구성 도넛과 범례.
 *
 * 시안 R4의 `By category` 블록이다. 도넛은 장식이고 정보는 범례가 텍스트로 전달한다.
 * 그래서 SVG는 접근성 트리에서 감추고, 범례를 실제 목록으로 그린다.
 *
 * **SVG 안에 `<text>`를 두지 않는다.** viewBox가 42 단위로 축소돼 있어 SVG 안에서
 * 글자 크기를 지정하면 타이포 토큰의 px 값이 그대로 쓰이지 않는다. 가운데 숫자는 HTML로
 * 겹쳐 그려 토큰을 그대로 쓴다.
 */
const {
  items,
  heading = undefined,
  centerValue = undefined,
  centerLabel = undefined,
  description = undefined,
  locale = 'en',
} = defineProps<ReportCategoryBreakdownProps>()

/** 원주가 정확히 100이 되는 반지름. 퍼센트를 그대로 dasharray에 넣기 위한 값이다. */
const DONUT_RADIUS = 15.9155

interface DonutSegment {
  key: string
  dashArray: string
  dashOffset: number
  inkClass: string
}

const rows = computed(() =>
  items.map((item, index) => ({
    key: `${item.category}-${String(index)}`,
    category: item.category,
    label: item.label,
    percentText: formatPercent(item.percentage, locale),
    amountText: formatMoney(item.amount, locale),
  })),
)

/**
 * 12시 방향에서 시계 방향으로 이어 붙인다. `dashOffset` 25는 원의 시작점을 3시에서
 * 12시로 돌리는 값이고, 그 뒤로는 앞 조각의 길이만큼 빼 나간다.
 *
 * 값이 0인 카테고리는 조각을 만들지 않는다. 0%도 dasharray `0 100`이면 선끝 처리 때문에
 * 점으로 남는다. 범례에는 그대로 0원·0%로 남으므로 정보는 사라지지 않는다.
 */
const segments = computed<DonutSegment[]>(() => {
  const result: DonutSegment[] = []
  let offset = 25

  items.forEach((item, index) => {
    const value = Math.min(Math.max(item.percentage, 0), 100)

    if (value > 0) {
      result.push({
        key: `${item.category}-${String(index)}`,
        dashArray: `${String(value)} ${String(100 - value)}`,
        dashOffset: offset,
        inkClass: seriesInkClass(item.category),
      })
    }

    offset -= value
  })

  return result
})

const isEmpty = computed(() => items.length === 0)
</script>

<template>
  <section class="flex flex-col gap-3">
    <h2
      v-if="heading !== undefined"
      class="font-display text-section-header uppercase text-ink"
    >
      {{ heading }}
    </h2>

    <AppCard
      padding="lg"
      class="min-h-49"
    >
      <StateEmpty
        v-if="isEmpty"
        :title="emptyTitle"
        :description="emptyDescription"
      />

      <!-- 좁은 폭(280)에서는 범례가 도넛 옆에 들어가지 않아 아래로 내려간다. -->
      <div
        v-else
        class="flex min-h-full flex-wrap items-center gap-5"
      >
        <div class="relative size-33 shrink-0">
          <svg
            aria-hidden="true"
            viewBox="0 0 42 42"
            class="size-full"
          >
            <circle
              cx="21"
              cy="21"
              :r="DONUT_RADIUS"
              fill="none"
              stroke="currentColor"
              stroke-width="6"
              class="text-surface-3"
            />
            <circle
              v-for="segment in segments"
              :key="segment.key"
              cx="21"
              cy="21"
              :r="DONUT_RADIUS"
              fill="none"
              stroke="currentColor"
              stroke-width="6"
              :stroke-dasharray="segment.dashArray"
              :stroke-dashoffset="segment.dashOffset"
              class="transition-all duration-500 motion-reduce:transition-none"
              :class="segment.inkClass"
            />
          </svg>

          <div
            v-if="centerValue !== undefined"
            aria-hidden="true"
            class="absolute inset-0 flex flex-col items-center justify-center"
          >
            <span class="text-title-sm tabular-nums text-ink">{{ centerValue }}</span>
            <span
              v-if="centerLabel !== undefined"
              class="text-caption text-ink-3"
              >{{ centerLabel }}</span
            >
          </div>
        </div>

        <ul class="flex min-w-36 flex-1 flex-col gap-3">
          <li
            v-for="row in rows"
            :key="row.key"
            class="flex items-center gap-2 text-caption tabular-nums"
          >
            <ReportSeriesMarker :category="row.category" />
            <span class="flex min-w-0 flex-1 flex-col gap-0.5">
              <span class="truncate text-ink-2">{{ row.label }}</span>
              <span class="text-micro text-ink-3">{{ row.percentText }}</span>
            </span>
            <span class="shrink-0 text-right text-ink">{{ row.amountText }}</span>
          </li>
        </ul>
      </div>

      <p
        v-if="description !== undefined && !isEmpty"
        class="sr-only"
      >
        {{ description }}
      </p>
    </AppCard>
  </section>
</template>
