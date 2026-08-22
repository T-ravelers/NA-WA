<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { formatMoney } from './format'
import type { ReportComparisonBarRow, ReportComparisonBarsProps } from './types'

/**
 * 총 지출 막대 — 나와 고른 동료 한 명.
 *
 * 시안 R4 `VS. GROUP MEMBERS`의 `TOTAL SPEND` 블록이다. 동료 칩은 라디오 그룹으로 그린다 —
 * 선택이 콘텐츠를 바꾸는 것이 아니라 같은 막대의 비교 대상을 바꾸는 것이기 때문이다.
 *
 * 막대 길이는 둘 중 큰 값을 100%로 둔다. 둘 다 0이면 막대를 비우고 금액만 보인다.
 * 표시 문자열은 전부 props로 받는다 — `useI18n`을 쓰면 props-only 계약이 깨진다.
 */
const {
  me,
  peers,
  totalLabel,
  chipsLabel,
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

/**
 * 칩 이니셜. `slice(0, 1)`은 UTF-16 코드 유닛 하나를 잘라 이모지로 시작하는 표시명을
 * 서로게이트 페어 절반으로 만든다. `AppointmentMemberList`와 같은 규칙을 쓴다.
 */
function initials(label: string): string {
  return [...label.trim()][0]?.toUpperCase() ?? '?'
}

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

const rows = computed<BarRow[]>(() => {
  const peer = selectedPeer.value
  const max = Math.max(me.amount, peer?.amount ?? 0, 0)
  const width = (amount: number): number => (max > 0 ? (Math.max(amount, 0) / max) * 100 : 0)
  const result: BarRow[] = [
    {
      key: `me-${String(me.id)}`,
      label: me.label,
      amountText: formatMoney(me.amount, locale),
      widthPercent: width(me.amount),
      fillClass: 'bg-ink',
    },
  ]

  if (peer !== null) {
    result.push({
      key: `peer-${String(peer.id)}`,
      label: peer.label,
      amountText: formatMoney(peer.amount, locale),
      widthPercent: width(peer.amount),
      fillClass: 'bg-ink-3',
    })
  }

  return result
})
</script>

<template>
  <div class="flex flex-col gap-4">
    <div
      v-if="peers.length > 0"
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
          class="flex size-5 items-center justify-center rounded-pill bg-surface-3 text-micro font-semibold text-ink"
        >
          {{ initials(peer.label) }}
        </span>
        <span class="truncate">{{ peer.label }}</span>
      </button>
    </div>

    <div class="flex flex-col gap-2">
      <p class="text-micro uppercase text-ink-3">{{ totalLabel }}</p>
      <dl class="flex flex-col gap-2">
        <div
          v-for="row in rows"
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
