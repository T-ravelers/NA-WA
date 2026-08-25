<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import AppTicket from '@/shared/ui/AppTicket.vue'
import GaugeBar from '@/shared/ui/GaugeBar.vue'

import type { JourneyDetail } from '../api/journeyApi'
import { formatJourneyDate } from '../model/journeyStatus'

interface Props {
  journey: JourneyDetail
}

const { journey } = defineProps<Props>()

const { locale, t } = useI18n()

// 지갑 통화(KRW)와 1:1이라 통화 스타일 대신 자릿수 구분만 로케일 대응으로 하고
// 단위는 P로 직접 붙인다(#333).
function formatMoney(value: number): string {
  return `${new Intl.NumberFormat(locale.value, { maximumFractionDigits: 0 }).format(value)} P`
}

function formatBudget(value: number | null): string {
  if (value === null) {
    return t('journey.detail.noBudget')
  }

  return formatMoney(value)
}

function formatCompanionPreference(value: string | null): string {
  const messageKeys: Record<string, string> = {
    '1': 'journey.create.companionOne',
    '2-4': 'journey.create.companionSmall',
    '5+': 'journey.create.companionLarge',
  }

  if (value === null) {
    return t('journey.detail.noCompanions')
  }

  const messageKey = messageKeys[value]
  return messageKey === undefined ? value : t(messageKey)
}

const spentAmount = computed(() => Math.max(journey.spentAmount, 0))
const remainingAmount = computed(() =>
  journey.budgetAmount === null ? null : Math.max(journey.budgetAmount - spentAmount.value, 0),
)
const overAmount = computed(() =>
  journey.budgetAmount === null ? 0 : Math.max(spentAmount.value - journey.budgetAmount, 0),
)
const hasBudgetLimit = computed(() => journey.budgetAmount !== null && journey.budgetAmount > 0)
const budgetRatio = computed(() =>
  hasBudgetLimit.value && journey.budgetAmount !== null
    ? spentAmount.value / journey.budgetAmount
    : 0,
)
const budgetPercentage = computed(() => Math.round(budgetRatio.value * 100))
const balanceLabel = computed(() => {
  if (journey.budgetAmount === null) return t('journey.detail.budget')
  return overAmount.value > 0 ? t('journey.detail.overBudget') : t('journey.detail.left')
})
const balanceText = computed(() => {
  if (journey.budgetAmount === null) return t('journey.detail.noBudget')
  return formatMoney(overAmount.value > 0 ? overAmount.value : (remainingAmount.value ?? 0))
})
const budgetStatus = computed(() => {
  if (journey.budgetAmount === null) return t('journey.detail.noBudgetLimit')
  if (journey.budgetAmount === 0) return t('journey.detail.zeroBudget')
  return t('journey.detail.budgetUsed', { percentage: budgetPercentage.value })
})
</script>

<template>
  <header class="flex items-start gap-3">
    <div class="min-w-0 flex-1">
      <h1 class="font-display text-screen-title uppercase text-ink-display">{{ journey.title }}</h1>
      <p class="mt-1 text-body-sm font-medium tabular-nums text-ink-2">
        <time :datetime="journey.startDate">{{
          formatJourneyDate(journey.startDate, locale)
        }}</time>
        –
        <time :datetime="journey.endDate">{{ formatJourneyDate(journey.endDate, locale) }}</time>
      </p>
    </div>
    <slot />
  </header>

  <AppTicket
    :body-size="136"
    tone="paper"
  >
    <template #body>
      <div class="flex size-full flex-col p-4">
        <dl class="flex items-end justify-between gap-4">
          <div class="min-w-0 flex-1">
            <dt class="text-micro uppercase text-on-paper/65">
              {{ t('journey.detail.spent') }}
            </dt>
            <dd class="mt-1 break-all font-display text-data-lg tabular-nums text-on-paper">
              {{ formatMoney(spentAmount) }}
            </dd>
          </div>
          <div class="min-w-0 flex-1 text-right">
            <dt class="text-micro uppercase text-on-paper/65">
              {{ balanceLabel }}
            </dt>
            <dd
              class="mt-1 break-words text-title-sm tabular-nums"
              :class="overAmount > 0 ? 'text-danger' : 'text-success'"
            >
              {{ balanceText }}
            </dd>
          </div>
        </dl>

        <GaugeBar
          class="mt-3"
          :value="budgetRatio"
          :label="budgetStatus"
        />

        <div class="mt-2 flex items-start justify-between gap-3 text-micro text-on-paper/65">
          <span>{{ budgetStatus }}</span>
          <span
            v-if="journey.budgetAmount !== null"
            class="shrink-0 tabular-nums"
          >
            {{ t('journey.detail.budgetTotal', { amount: formatBudget(journey.budgetAmount) }) }}
          </span>
        </div>
      </div>
    </template>

    <template #stub>
      <dl class="flex min-h-14 items-center justify-between gap-4 px-4 py-3">
        <dt class="text-micro uppercase text-on-paper/65">
          {{ t('journey.detail.companions') }}
        </dt>
        <dd class="text-title-sm text-on-paper">
          {{ formatCompanionPreference(journey.companionPreference) }}
        </dd>
      </dl>
    </template>
  </AppTicket>
</template>
