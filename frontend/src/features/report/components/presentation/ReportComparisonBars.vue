<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { getAvatarInitial } from '@/shared/lib/avatarInitial'
import AppImage from '@/shared/ui/AppImage.vue'

import { formatMoney } from './format'
import type { ReportComparisonBarRow, ReportComparisonBarsProps } from './types'

/**
 * 총 지출·일 평균 막대 — 나와 고른 동료 한 명.
 *
 * 시안 R4 `VS. GROUP MEMBERS`는 이니셜 칩과 `TOTAL SPEND`만 그리지만, #436에서 동료를
 * 더 잘 구분하도록 API 프로필 사진과 `DAILY AVG` 비교를 함께 쓰기로 했다. 사진이 없거나
 * 깨지면 모든 아바타 자리와 같은 공용 이니셜 폴백을 쓴다.
 *
 * 동료 칩은 라디오 그룹이다 — 선택이 콘텐츠를 바꾸는 것이 아니라 같은 두 막대의 비교
 * 대상을 바꾸기 때문이다. 각 지표는 둘 중 큰 값을 100%로 두고, 둘 다 0이면 막대를 비운다.
 * 표시 문자열은 전부 props로 받는다 — `useI18n`을 쓰면 props-only 계약이 깨진다.
 */
const {
  me,
  peers,
  totalLabel,
  dailyAverageLabel,
  chipsLabel,
  chips = true,
  locale = 'en',
} = defineProps<ReportComparisonBarsProps>()

const selectedPeerId = ref<number | null>(peers[0]?.id ?? null)

// 동료 목록이 바뀌어 고른 사람이 사라지면 첫 사람으로 돌아간다.
watch(
  () => peers,
  (next) => {
    if (!next.some((peer) => peer.id === selectedPeerId.value)) {
      selectedPeerId.value = next[0]?.id ?? null
    }
  },
)

const selectedPeer = computed<ReportComparisonBarRow | null>(
  () => peers.find((peer) => peer.id === selectedPeerId.value) ?? null,
)

interface BarRow {
  key: string
  label: string
  amountText: string
  widthPercent: number
  fillClass: string
}

interface MetricBlock {
  key: 'totalSpent' | 'dailyAverage'
  label: string
  rows: BarRow[]
}

function metricRows(metric: MetricBlock['key']): BarRow[] {
  const peer = selectedPeer.value
  const max = Math.max(Number(me[metric]), Number(peer?.[metric] ?? 0), 0)
  const width = (amount: number): number => (max > 0 ? (Math.max(amount, 0) / max) * 100 : 0)
  const result: BarRow[] = [
    {
      key: `me-${String(me.id)}`,
      label: me.label,
      amountText: formatMoney(me[metric], locale),
      widthPercent: width(Number(me[metric])),
      fillClass: 'bg-ink',
    },
  ]

  if (peer !== null) {
    result.push({
      key: `peer-${String(peer.id)}`,
      label: peer.label,
      amountText: formatMoney(peer[metric], locale),
      widthPercent: width(Number(peer[metric])),
      fillClass: 'bg-ink-3',
    })
  }

  return result
}

const metricBlocks = computed<MetricBlock[]>(() => [
  { key: 'totalSpent', label: totalLabel, rows: metricRows('totalSpent') },
  { key: 'dailyAverage', label: dailyAverageLabel, rows: metricRows('dailyAverage') },
])
</script>

<template>
  <div class="flex flex-col gap-4">
    <div
      v-if="chips && peers.length > 0"
      role="radiogroup"
      :aria-label="chipsLabel"
      class="flex flex-wrap gap-2"
    >
      <button
        v-for="peer in peers"
        :key="peer.id"
        type="button"
        role="radio"
        :aria-checked="peer.id === selectedPeerId"
        class="flex h-9 items-center gap-2 rounded-pill px-3 text-caption transition-transform active:scale-[0.98]"
        :class="
          peer.id === selectedPeerId
            ? 'bg-paper-fill text-on-paper'
            : 'border border-hairline bg-transparent text-ink-2'
        "
        @click="selectedPeerId = peer.id"
      >
        <span
          aria-hidden="true"
          class="flex size-5 shrink-0 items-center justify-center overflow-hidden rounded-pill bg-surface-3 text-micro font-semibold text-ink"
        >
          <AppImage
            :src="peer.profileImageUrl"
            alt=""
            class="size-full object-cover"
          >
            <span class="flex size-full items-center justify-center">
              {{ getAvatarInitial(peer.label) }}
            </span>
          </AppImage>
        </span>
        <span class="truncate">{{ peer.label }}</span>
      </button>
    </div>

    <div
      v-for="metric in metricBlocks"
      :key="metric.key"
      :data-metric="metric.key"
      class="flex flex-col gap-2"
    >
      <p class="text-micro uppercase text-ink-3">{{ metric.label }}</p>
      <dl class="flex flex-col gap-2">
        <div
          v-for="row in metric.rows"
          :key="row.key"
          class="flex items-center gap-3 text-caption tabular-nums"
        >
          <dt class="w-12 shrink-0 truncate text-ink-2">{{ row.label }}</dt>
          <dd class="flex min-w-0 flex-1 items-center gap-3">
            <span
              aria-hidden="true"
              class="h-2 min-w-0 flex-1 overflow-hidden rounded-pill bg-surface-3"
            >
              <span
                class="block h-full rounded-pill transition-all duration-500 motion-reduce:transition-none"
                :class="row.fillClass"
                :style="{ width: `${String(row.widthPercent)}%` }"
              />
            </span>
            <span class="shrink-0 text-ink">{{ row.amountText }}</span>
          </dd>
        </div>
      </dl>
    </div>
  </div>
</template>
