<script setup lang="ts">
import { IconChevronDown } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { formatCalendarDate } from '@/shared/lib/datetime'
import StateError from '@/shared/ui/StateError.vue'

import SettlementDateFilterSheet from '../components/SettlementDateFilterSheet.vue'
import SettlementEmptyState from '../components/SettlementEmptyState.vue'
import SettlementInlineLoading from '../components/SettlementInlineLoading.vue'
import SettlementListCard from '../components/SettlementListCard.vue'
import SettlementPageHeader from '../components/SettlementPageHeader.vue'
import { resolveSettlementError } from '../model/settlementErrors'
import {
  filterByCompletedDate,
  resolveDateRange,
  type SettlementDateRange,
} from '../model/settlementHistoryFilter'
import { resolveSide, splitIntoSections, type SettlementSide } from '../model/settlementList'
import { useSettlements } from '../model/settlementQueries'

/**
 * 완료된 정산 전체 내역.
 *
 * 목록 API가 페이지네이션 없이 전량을 내려주므로 새로 요청하지 않고 같은 쿼리 캐시를
 * 걸러 쓴다. 목록 화면에서 이미 받아 둔 응답을 그대로 재사용하며, 기간 필터도 그
 * 응답을 화면에서 거른다.
 */
const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const settlementQuery = useSettlements()

const filterOpen = ref(false)

const side = computed<SettlementSide>(() => resolveSide(route.query.side))
/**
 * 고른 기간은 주소에 남긴다.
 *
 * 화면 안에만 두면 새로고침하거나 상세를 보고 돌아왔을 때 조용히 전체 목록으로 돌아가,
 * 사용자는 자기가 무엇을 보고 있는지 알 수 없게 된다.
 */
const range = computed(() => resolveDateRange(route.query.from, route.query.to))
const completed = computed(
  () => splitIntoSections(settlementQuery.data.value?.[side.value] ?? []).completed,
)
const visible = computed(() => filterByCompletedDate(completed.value, range.value))
const errorKey = computed(() => resolveSettlementError(settlementQuery.error.value).messageKey)

const rangeLabel = computed(() => {
  if (range.value === null) return t('settlement.history.anyDate')

  const from = formatCalendarDate(range.value.from, locale.value) || range.value.from
  if (range.value.to === range.value.from) return from

  return t('settlement.history.periodRange', {
    from,
    to: formatCalendarDate(range.value.to, locale.value) || range.value.to,
  })
})

function applyRange(next: SettlementDateRange | null): void {
  filterOpen.value = false
  void router.replace({
    query: {
      side: side.value,
      ...(next === null ? {} : { from: next.from, to: next.to }),
    },
  })
}

function open(settlementId: string): void {
  void router.push({
    name: 'settlement-detail',
    params: { settlementId },
    query: { side: side.value },
  })
}
</script>

<template>
  <section class="flex min-h-dvh flex-col px-screen pt-8 pb-32">
    <SettlementPageHeader
      :data-testid="`settlement-history-${side}`"
      :title="
        t(side === 'sent' ? 'settlement.history.titleCollect' : 'settlement.history.titlePay')
      "
      :back-label="t('settlement.backToList')"
      @back="router.push({ name: 'settlements', query: { side } })"
    />
    <SettlementInlineLoading
      v-if="settlementQuery.isPending.value"
      class="mt-8"
      :label="t('settlement.list.loading')"
    />
    <StateError
      v-else-if="settlementQuery.isError.value"
      class="my-auto"
      :title="t(errorKey)"
      :description="t('settlement.list.retryHint')"
      @retry="settlementQuery.refetch()"
    />
    <SettlementEmptyState
      v-else-if="completed.length === 0"
      class="flex-1"
      :title="t('settlement.history.emptyTitle')"
      :description="t('settlement.history.emptyDescription')"
    />
    <template v-else>
      <!-- 거를 완료 건이 있을 때만 보여준다. 아무것도 없는 화면의 필터는 누를 이유가 없다. -->
      <button
        type="button"
        data-testid="period-filter"
        class="mt-8 flex w-full items-center justify-between gap-3 rounded-card bg-surface-1 px-4 py-3 text-left"
        @click="filterOpen = true"
      >
        <span class="text-caption text-ink-3">{{ t('settlement.history.period') }}</span>
        <span class="flex min-w-0 items-center gap-1 text-body-sm text-ink">
          <span class="truncate">{{ rangeLabel }}</span>
          <IconChevronDown
            :size="16"
            aria-hidden="true"
          />
        </span>
      </button>

      <SettlementEmptyState
        v-if="visible.length === 0"
        class="flex-1"
        :title="t('settlement.history.noneInPeriodTitle')"
        :description="t('settlement.history.noneInPeriodDescription')"
      />
      <ul
        v-else
        class="mt-4 space-y-2"
      >
        <li
          v-for="settlement in visible"
          :key="settlement.id"
        >
          <SettlementListCard
            compact
            :settlement="settlement"
            :side="side"
            @open="open(settlement.id)"
          />
        </li>
      </ul>
    </template>

    <SettlementDateFilterSheet
      v-if="filterOpen"
      :range="range"
      @apply="applyRange"
      @close="filterOpen = false"
    />
  </section>
</template>
