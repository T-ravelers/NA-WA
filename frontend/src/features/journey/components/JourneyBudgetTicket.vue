<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { spendingCategoryLabelKey, toSpendingCategory } from '@/shared/lib/spendingCategory'
import AppTicket from '@/shared/ui/AppTicket.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'
import GaugeBar from '@/shared/ui/GaugeBar.vue'
import type { Category } from '@/shared/ui/category'

import type { JourneyExpenseCandidate } from '../model/reportIntegration'

interface Props {
  /* 합계는 서버가 준 값이 정본이다(#541). Report LIVE와 같은 정의라 정산으로 회수한
     금액까지 상계돼 있어, 후보 목록을 프론트에서 더한 값과 다를 수 있다. */
  spentAmount: number
  budgetAmount: number | null
  /* 범례가 어떤 소비영역을 썼는지 세는 데만 쓴다. 금액 표시에는 쓰지 않는다. */
  expenses: JourneyExpenseCandidate[]
  itemCount: number
}

const { spentAmount, budgetAmount, expenses, itemCount } = defineProps<Props>()
const { locale, t } = useI18n()

const CATEGORY: Partial<Record<string, Category>> = {
  FOOD: 'food',
  SHOPPING: 'shopping',
  BEAUTY: 'beauty',
  SHOW: 'show',
}

const spent = computed(() => Math.max(spentAmount, 0))
const remaining = computed(() =>
  budgetAmount === null ? null : Math.max(budgetAmount - spent.value, 0),
)
const over = computed(() => (budgetAmount === null ? 0 : Math.max(spent.value - budgetAmount, 0)))
const ratio = computed(() =>
  budgetAmount === null || budgetAmount <= 0 ? 0 : spent.value / budgetAmount,
)
/* 게이지 폭은 GaugeBar가 100%에서 자르지만, 읽히는 비율은 실제 소비율 그대로 둔다(#538). */
const percentage = computed(() => Math.round(ratio.value * 100))

const balanceLabel = computed(() => {
  if (budgetAmount === null) return t('journey.detail.budget')
  return over.value > 0 ? t('journey.detail.overBudget') : t('journey.detail.left')
})
const balanceText = computed(() => {
  if (budgetAmount === null) return t('journey.detail.noBudget')
  return formatMoney(over.value > 0 ? over.value : (remaining.value ?? 0))
})
const budgetStatus = computed(() => {
  if (budgetAmount === null) return t('journey.detail.noBudgetLimit')
  if (budgetAmount === 0) return t('journey.detail.zeroBudget')
  return t('journey.detail.budgetUsed', { percentage: percentage.value })
})

const legend = computed(() => {
  const totals = new Map<string, number>()
  for (const expense of expenses) {
    const category = toSpendingCategory(expense.category)
    const amount = Number(expense.amount)
    if (CATEGORY[category] === undefined || !Number.isFinite(amount) || amount < 0) continue
    totals.set(category, (totals.get(category) ?? 0) + amount)
  }

  return [...totals.entries()]
    .sort((first, second) => second[1] - first[1])
    .slice(0, 3)
    .map(([category]) => ({
      category: CATEGORY[category] as Category,
      label: t(spendingCategoryLabelKey(category)),
    }))
})

// 지갑 통화(KRW)와 1:1이라 통화 스타일 대신 자릿수 구분만 로케일 대응으로 하고
// 단위는 P로 직접 붙인다(#333).
function formatMoney(value: number): string {
  return `${new Intl.NumberFormat(locale.value, { maximumFractionDigits: 0 }).format(value)} P`
}

function formatBudget(value: number | null): string {
  return value === null ? t('journey.detail.noBudget') : formatMoney(value)
}
</script>

<template>
  <AppTicket
    :body-size="134"
    tone="paper"
    class="w-full"
  >
    <template #body>
      <div class="flex h-full flex-col gap-3.5 px-5 pt-5 pb-4.5">
        <div class="flex items-end justify-between gap-4">
          <div class="min-w-0">
            <p class="text-caption font-semibold tracking-wide uppercase text-on-paper-2">
              {{ t('journey.detail.spent') }}
            </p>
            <p class="mt-1 truncate text-data-lg tabular-nums text-on-paper">
              {{ formatMoney(spent) }}
            </p>
          </div>
          <div class="shrink-0 text-right">
            <p class="text-caption font-semibold tracking-wide uppercase text-on-paper-2">
              {{ balanceLabel }}
            </p>
            <p
              class="mt-0.5 text-title font-bold tabular-nums"
              :class="over > 0 ? 'text-danger' : 'text-success'"
            >
              {{ balanceText }}
            </p>
          </div>
        </div>

        <div class="flex flex-col gap-2">
          <GaugeBar
            :value="ratio"
            :label="budgetStatus"
          />
          <div class="flex items-center justify-between gap-3 text-caption text-on-paper">
            <span>{{ budgetStatus }}</span>
            <span class="truncate text-on-paper-2">
              {{ t('journey.detail.budgetTotal', { amount: formatBudget(budgetAmount) }) }}
            </span>
          </div>
        </div>
      </div>
    </template>

    <template #stub>
      <div class="flex h-10 items-center gap-2 px-5 text-caption font-semibold text-on-paper-2">
        <div class="flex min-w-0 items-center gap-2 overflow-hidden">
          <span
            v-for="entry in legend"
            :key="entry.category"
            class="inline-flex shrink-0 items-center gap-1.5"
          >
            <CategoryDot :category="entry.category" />
            {{ entry.label }}
          </span>
        </div>
        <span class="ml-auto shrink-0">
          {{ t('journey.detail.itemCount', { count: itemCount }) }}
        </span>
      </div>
    </template>
  </AppTicket>
</template>
