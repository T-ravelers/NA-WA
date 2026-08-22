<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { formatCalendarDate } from '@/shared/lib/datetime'
import AppBadge from '@/shared/ui/AppBadge.vue'

import { useSettlementPoints } from '../composables/useSettlementPoints'
import type { SettlementSummary } from '../model/settlement'
import { settlementCompletedDate } from '../model/settlementHistoryFilter'
import { hasViewerPaid, primaryAmount, type SettlementSide } from '../model/settlementList'

/**
 * 목록 카드.
 *
 * 시안은 왼쪽에 무엇에 대한 정산인지를, 오른쪽에 금액과 상태를 모아 둔다. 목록을
 * 훑을 때 눈이 오른쪽 한 줄만 따라가면 되도록 두 열을 나눈다.
 *
 * 시안의 아바타 원과 `Alex · Group division` 부제는 넣지 않았다. 목록 응답
 * (`SettlementSummary`)에 상대 이름도 이니셜도 없어서, 넣으려면 서버가 필드를 더
 * 내려줘야 한다.
 */
interface Props {
  settlement: SettlementSummary
  side: SettlementSide
  /** 완료 구획에서 쓰는 한 줄 표시. */
  compact?: boolean
}

const { settlement, side, compact = false } = defineProps<Props>()
const emit = defineEmits<{ open: [] }>()

const { t, locale } = useI18n()
const points = useSettlementPoints()

const amount = computed(() => points(primaryAmount(settlement, side)))
/**
 * 끝난 날짜.
 *
 * 기간으로 좁혀 보는 화면에서 카드에 날짜가 없으면 고른 기간이 맞는지 확인할 방법이 없다.
 * 아주 예전 정산은 서버에 끝난 시각이 없어 만든 날짜로 대신 나온다.
 */
const completedOn = computed(() =>
  formatCalendarDate(settlementCompletedDate(settlement), locale.value),
)
/** 낸 사람에게는 진행 중이어도 `Paid`가 더 정확한 상태다. */
const showsPaidMark = computed(
  () => side === 'received' && settlement.status === 'REQUESTED' && hasViewerPaid(settlement),
)
</script>

<template>
  <button
    type="button"
    :data-settlement-id="settlement.id"
    class="w-full rounded-card bg-surface-1 p-4 text-left"
    @click="emit('open')"
  >
    <template v-if="compact">
      <span class="flex items-center justify-between gap-3">
        <span class="min-w-0">
          <span class="block truncate text-body">{{ settlement.title }}</span>
          <span
            v-if="completedOn"
            class="mt-1 block text-caption text-ink-3"
            >{{ completedOn }}</span
          >
        </span>
        <span class="flex shrink-0 items-center gap-2">
          <strong class="text-body">{{ amount }}</strong>
          <AppBadge tone="completed">{{ t('settlement.status.COMPLETED') }}</AppBadge>
        </span>
      </span>
    </template>

    <template v-else>
      <span class="flex items-center justify-between gap-3">
        <span class="min-w-0">
          <strong class="block truncate text-title">{{ settlement.title }}</strong>
          <span class="mt-1 block truncate text-caption text-ink-3">
            {{
              side === 'received' ? t('settlement.list.youPay') : t('settlement.list.youCollect')
            }}
            · {{ t(`settlement.type.${settlement.type}`) }} · {{ t('settlement.total') }}
            {{ points(settlement.totalAmount) }}
          </span>
        </span>
        <span class="flex shrink-0 flex-col items-end gap-1.5">
          <strong class="text-title">{{ amount }}</strong>
          <AppBadge
            :tone="showsPaidMark ? 'completed' : 'info'"
            :data-testid="showsPaidMark ? 'settlement-paid-mark' : undefined"
          >
            {{
              showsPaidMark
                ? t('settlement.list.paid')
                : t(`settlement.status.${settlement.status}`)
            }}
          </AppBadge>
        </span>
      </span>
    </template>
  </button>
</template>
