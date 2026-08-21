<script setup lang="ts">
import { computed } from 'vue'

import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'

import { formatMoney } from './format'
import type { ReportDailyTrendPoint, ReportDailyTrendProps } from './types'

/**
 * 일자별 지출 추이 꺾은선.
 *
 * 시안 R4의 `Spending trend` 블록이다. 모든 날짜의 점을 표시하고, 같은 값을 스크린
 * 리더용 목록으로도 함께 낸다. 축 라벨은 SVG `<text>`가 아니라 HTML로 그린다. viewBox가
 * 확대·축소되면 SVG 안의 글자 크기는 타이포 토큰의 px 값과 달라지기 때문이다.
 */
const {
  points,
  heading = undefined,
  description = undefined,
  locale = 'en',
} = defineProps<ReportDailyTrendProps>()

/* 시안 실측 기준의 그리기 영역. 좌우 8은 점 반지름이 잘리지 않도록 둔 여백이다. */
const VIEW_WIDTH = 318
const VIEW_HEIGHT = 112
const PLOT_LEFT = 8
const PLOT_RIGHT = 310
const PLOT_TOP = 16
const PLOT_BOTTOM = 93

interface PlottedPoint {
  key: string
  x: number
  y: number
  label: string
  amountText: string
}

/**
 * 최댓값이 0이면(전 일자 0원) 나눗셈이 무너지므로 전부 바닥선에 붙인다.
 * 점이 하나뿐이면 보간할 구간이 없어 가운데에 놓는다.
 */
const plotted = computed<PlottedPoint[]>(() => {
  const max = Math.max(0, ...points.map((point) => point.amount))
  const lastIndex = points.length - 1

  return points.map((point, index) => {
    const ratio = max > 0 ? Math.max(0, point.amount) / max : 0
    const progress = lastIndex > 0 ? index / lastIndex : 0.5

    return {
      key: point.date,
      x: PLOT_LEFT + (PLOT_RIGHT - PLOT_LEFT) * progress,
      y: PLOT_BOTTOM - (PLOT_BOTTOM - PLOT_TOP) * ratio,
      label: point.label,
      amountText: formatMoney(point.amount, locale),
    }
  })
})

const polylinePoints = computed(() =>
  plotted.value.map((point) => `${String(point.x)},${String(point.y)}`).join(' '),
)

/** 축 라벨은 처음·가운데·마지막만 남긴다. 전부 그리면 모바일 폭에서 겹친다. */
const axisLabels = computed<ReportDailyTrendPoint[]>(() => {
  const all = points
  const first = all[0]
  const last = all[all.length - 1]

  if (first === undefined || last === undefined) {
    return []
  }

  const middle = all.length >= 3 ? all[Math.floor((all.length - 1) / 2)] : undefined
  const picked = middle === undefined ? [first, last] : [first, middle, last]

  return [...new Set(picked)]
})

const isEmpty = computed(() => points.length === 0)
</script>

<template>
  <section class="flex flex-col gap-3">
    <h2
      v-if="heading !== undefined"
      class="font-display text-section-header uppercase text-ink"
    >
      {{ heading }}
    </h2>

    <AppCard padding="base">
      <StateEmpty
        v-if="isEmpty"
        :title="emptyTitle"
        :description="emptyDescription"
      />

      <div v-else>
        <svg
          aria-hidden="true"
          :viewBox="`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`"
          class="block w-full"
        >
          <line
            :x1="0"
            :y1="PLOT_BOTTOM"
            :x2="VIEW_WIDTH"
            :y2="PLOT_BOTTOM"
            stroke="currentColor"
            stroke-width="1"
            class="text-hairline"
          />
          <line
            :x1="0"
            :y1="(PLOT_TOP + PLOT_BOTTOM) / 2"
            :x2="VIEW_WIDTH"
            :y2="(PLOT_TOP + PLOT_BOTTOM) / 2"
            stroke="currentColor"
            stroke-width="1"
            stroke-dasharray="3 4"
            class="text-hairline"
          />
          <line
            :x1="0"
            :y1="PLOT_TOP"
            :x2="VIEW_WIDTH"
            :y2="PLOT_TOP"
            stroke="currentColor"
            stroke-width="1"
            stroke-dasharray="3 4"
            class="text-hairline"
          />

          <polyline
            :points="polylinePoints"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linejoin="round"
            stroke-linecap="round"
            class="text-ink transition-all duration-500 motion-reduce:transition-none"
          />

          <circle
            v-for="point in plotted"
            :key="point.key"
            :cx="point.x"
            :cy="point.y"
            r="3"
            fill="currentColor"
            class="text-ink transition-all duration-500 motion-reduce:transition-none"
          />
        </svg>

        <div
          aria-hidden="true"
          class="flex justify-between gap-2 pt-2 text-micro text-ink-3"
        >
          <span
            v-for="point in axisLabels"
            :key="point.date"
            >{{ point.label }}</span
          >
        </div>

        <p
          v-if="description !== undefined"
          class="sr-only"
        >
          {{ description }}
        </p>

        <!-- 화면에는 선만 남으므로 같은 값을 텍스트로도 낸다. -->
        <ul class="sr-only">
          <li
            v-for="point in plotted"
            :key="point.key"
          >
            {{ point.label }}: {{ point.amountText }}
          </li>
        </ul>
      </div>
    </AppCard>
  </section>
</template>
