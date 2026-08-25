<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import type { JourneyDetail } from '../api/journeyApi'
import type { JourneyExpenseCandidate } from '../model/reportIntegration'
import { formatJourneyDate } from '../model/journeyStatus'
import JourneyBudgetTicket from './JourneyBudgetTicket.vue'

interface Props {
  journey: JourneyDetail
  /* 금액은 `journey.spentAmount`(#541)가 정본이다. 이 후보 목록은 티켓 아래 범례에서
     어떤 소비영역을 썼는지 세는 데만 쓴다. */
  expenses: JourneyExpenseCandidate[]
  itemCount: number
}

defineProps<Props>()
const { locale } = useI18n()
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

  <JourneyBudgetTicket
    :spent-amount="journey.spentAmount"
    :budget-amount="journey.budgetAmount"
    :expenses="expenses"
    :item-count="itemCount"
  />
</template>
