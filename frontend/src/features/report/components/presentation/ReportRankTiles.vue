<script setup lang="ts">
import type { ReportRankTile, ReportRankTilesProps } from './types'

/**
 * 카테고리별 순위 타일.
 *
 * 시안 R4의 `#Food 1ST` 2열 타일이다. 색은 Explore 소비영역과 같은 어휘를 쓰는 네 카테고리만
 * 코어색이고 나머지는 면 색이다 — 어떤 색을 줄지는 화면이 정한다(여기서는 소비 카테고리
 * 문자열을 알지 않는다). 순위 글자는 화면이 로케일에 맞춰 만든 것을 그대로 찍는다.
 *
 * Tailwind가 소스에 적힌 클래스만 수집하므로 조합하지 않고 표로 둔다.
 */
const { tiles } = defineProps<ReportRankTilesProps>()

const TILE_CLASS: Record<ReportRankTile['tone'], string> = {
  food: 'bg-food text-on-category',
  shopping: 'bg-shopping text-on-category',
  show: 'bg-show text-on-category',
  beauty: 'bg-beauty text-on-category',
  surface: 'bg-surface-2 text-ink',
}
</script>

<template>
  <ul class="grid grid-cols-2 gap-3">
    <li
      v-for="tile in tiles"
      :key="tile.key"
      class="flex min-h-22 flex-col justify-between rounded-card p-4"
      :class="TILE_CLASS[tile.tone]"
    >
      <span class="text-caption font-semibold"># {{ tile.label }}</span>
      <span class="self-end font-display text-title font-bold uppercase tabular-nums">
        {{ tile.rankText }}
      </span>
    </li>
  </ul>
</template>
