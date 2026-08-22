<script setup lang="ts">
import { computed } from 'vue'

import { seriesInkClass } from './seriesPalette'
import type { ReportRadarChartProps } from './types'

/**
 * 카테고리 균형 레이더 — 내 비중(실선)과 코호트 평균 비중(점선).
 *
 * 시안 R4 `CATEGORY BALANCE` 블록이다. 축은 받은 순서대로 12시부터 시계 방향으로 놓는다.
 * 값은 0–100 비중이고, 둘 중 최댓값을 바깥 링으로 잡아 작은 값도 읽히게 한다.
 *
 * **SVG 안에 `<text>`를 두지 않는다.** 축 라벨은 HTML로 겹쳐 그려 타이포 토큰을 그대로 쓴다.
 * 그래서 라벨 위치도 SVG 좌표가 아니라 상자 기준 퍼센트로 계산한다. 차트는 장식이고 값은
 * 스크린 리더용 목록이 전달한다.
 */
const {
  axes,
  mineLabel,
  cohortLabel,
  description = undefined,
} = defineProps<ReportRadarChartProps>()

const VIEW = 200
const CENTER = VIEW / 2
const RADIUS = 68
/** 라벨이 놓이는 반지름. 상자 밖으로 나가지 않도록 링보다 조금 바깥이다. */
const LABEL_RADIUS = 88
const RINGS = [0.25, 0.5, 0.75, 1]

interface Point {
  x: number
  y: number
}

function pointAt(index: number, ratio: number, radius = RADIUS): Point {
  const angle = -Math.PI / 2 + (Math.PI * 2 * index) / Math.max(axes.length, 1)

  return {
    x: CENTER + Math.cos(angle) * radius * ratio,
    y: CENTER + Math.sin(angle) * radius * ratio,
  }
}

const scaleMax = computed(() =>
  Math.max(1, ...axes.flatMap((axis) => [Math.max(axis.mine, 0), Math.max(axis.cohort, 0)])),
)

function polygon(pick: (axis: ReportRadarChartProps['axes'][number]) => number): string {
  return axes
    .map((axis, index) => {
      const point = pointAt(index, Math.max(pick(axis), 0) / scaleMax.value)

      return `${point.x.toFixed(1)},${point.y.toFixed(1)}`
    })
    .join(' ')
}

const minePolygon = computed(() => polygon((axis) => axis.mine))
const cohortPolygon = computed(() => polygon((axis) => axis.cohort))

const ringPolygons = computed(() =>
  RINGS.map((ratio) =>
    axes
      .map((_, index) => {
        const point = pointAt(index, ratio)

        return `${point.x.toFixed(1)},${point.y.toFixed(1)}`
      })
      .join(' '),
  ),
)

const spokes = computed(() =>
  axes.map((axis, index) => ({ key: axis.key, end: pointAt(index, 1) })),
)

const minePoints = computed(() =>
  axes.map((axis, index) => ({
    key: axis.key,
    ...pointAt(index, Math.max(axis.mine, 0) / scaleMax.value),
  })),
)

/** 상자 기준 퍼센트. 상자가 정사각형이라 viewBox 비율과 같다. */
const labels = computed(() =>
  axes.map((axis, index) => {
    const point = pointAt(index, 1, LABEL_RADIUS)

    return {
      key: axis.key,
      label: axis.label,
      inkClass: seriesInkClass(axis.key),
      left: `${((point.x / VIEW) * 100).toFixed(2)}%`,
      top: `${((point.y / VIEW) * 100).toFixed(2)}%`,
    }
  }),
)

const canDraw = computed(() => axes.length >= 3)
</script>

<template>
  <div class="flex flex-col gap-3">
    <div
      v-if="canDraw"
      class="relative mx-auto w-full max-w-60"
    >
      <svg
        aria-hidden="true"
        :viewBox="`0 0 ${VIEW} ${VIEW}`"
        class="block w-full"
      >
        <polygon
          v-for="(ring, index) in ringPolygons"
          :key="index"
          :points="ring"
          fill="none"
          stroke="currentColor"
          stroke-width="1"
          class="text-hairline"
        />
        <line
          v-for="spoke in spokes"
          :key="spoke.key"
          :x1="CENTER"
          :y1="CENTER"
          :x2="spoke.end.x"
          :y2="spoke.end.y"
          stroke="currentColor"
          stroke-width="1"
          class="text-hairline"
        />
        <polygon
          :points="cohortPolygon"
          fill="currentColor"
          fill-opacity="0.12"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-dasharray="4 3"
          stroke-linejoin="round"
          class="text-ink-3"
        />
        <polygon
          :points="minePolygon"
          fill="currentColor"
          fill-opacity="0.1"
          stroke="currentColor"
          stroke-width="2"
          stroke-linejoin="round"
          class="text-ink transition-all duration-500 motion-reduce:transition-none"
        />
        <circle
          v-for="point in minePoints"
          :key="point.key"
          :cx="point.x"
          :cy="point.y"
          r="3"
          fill="currentColor"
          class="text-ink"
        />
      </svg>

      <span
        v-for="item in labels"
        :key="item.key"
        aria-hidden="true"
        class="absolute -translate-x-1/2 -translate-y-1/2 text-caption font-semibold whitespace-nowrap"
        :class="item.inkClass"
        :style="{ left: item.left, top: item.top }"
      >
        {{ item.label }}
      </span>
    </div>

    <div
      aria-hidden="true"
      class="flex justify-center gap-4 text-micro text-ink-3"
    >
      <span class="flex items-center gap-1.5">
        <span class="inline-block h-0.5 w-4 rounded-pill bg-ink" />
        {{ mineLabel }}
      </span>
      <span class="flex items-center gap-1.5">
        <span class="inline-block h-0.5 w-4 rounded-pill border-t-2 border-dashed border-ink-3" />
        {{ cohortLabel }}
      </span>
    </div>

    <!-- 값은 색이나 모양이 아니라 여기서 읽힌다. -->
    <ul class="sr-only">
      <li
        v-for="axis in axes"
        :key="axis.key"
      >
        {{ axis.label }}: {{ mineLabel }} {{ Math.round(axis.mine) }}%, {{ cohortLabel }}
        {{ Math.round(axis.cohort) }}%
      </li>
    </ul>
    <p
      v-if="description !== undefined"
      class="sr-only"
    >
      {{ description }}
    </p>
  </div>
</template>
