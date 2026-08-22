<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import AppBadge from '@/shared/ui/AppBadge.vue'

import { useSettlementPoints } from '../composables/useSettlementPoints'
import type { SettlementSummary } from '../model/settlement'
import { hasViewerPaid, primaryAmount, type SettlementSide } from '../model/settlementList'

/**
 * 목록 카드.
 *
 * 낼 쪽은 내가 낼 금액을, 받을 쪽은 받을 금액을 가장 크게 보여준다. 완료 구획은
 * 훑어보는 용도라 한 줄로 접는다.
 */
interface Props {
  settlement: SettlementSummary
  side: SettlementSide
  /** 완료 구획에서 쓰는 한 줄 표시. */
  compact?: boolean
}

const { settlement, side, compact = false } = defineProps<Props>()
const emit = defineEmits<{ open: [] }>()

const { t } = useI18n()
const points = useSettlementPoints()

const amount = computed(() => points(primaryAmount(settlement, side)))
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
        <span class="min-w-0 truncate text-body">{{ settlement.title }}</span>
        <span class="flex shrink-0 items-center gap-2">
          <strong class="text-body">{{ amount }}</strong>
          <AppBadge tone="completed">{{ t('settlement.status.COMPLETED') }}</AppBadge>
        </span>
      </span>
    </template>

    <template v-else>
      <span class="flex items-start justify-between gap-3">
        <strong class="min-w-0 truncate text-title">{{ settlement.title }}</strong>
        <AppBadge
          :tone="showsPaidMark ? 'completed' : 'pending'"
          :data-testid="showsPaidMark ? 'settlement-paid-mark' : undefined"
        >
          {{
            showsPaidMark ? t('settlement.list.paid') : t(`settlement.status.${settlement.status}`)
          }}
        </AppBadge>
      </span>
      <span class="mt-4 flex items-end justify-between gap-3">
        <span class="min-w-0">
          <span class="block text-caption text-ink-3">
            {{
              side === 'received' ? t('settlement.list.youPay') : t('settlement.list.youCollect')
            }}
          </span>
          <strong class="mt-1 block truncate text-data-lg">{{ amount }}</strong>
        </span>
        <span class="shrink-0 text-right text-caption text-ink-3">
          <span class="block">{{ t(`settlement.type.${settlement.type}`) }}</span>
          <span class="mt-1 block">
            {{ t('settlement.total') }} {{ points(settlement.totalAmount) }}
          </span>
        </span>
      </span>
    </template>
  </button>
</template>
