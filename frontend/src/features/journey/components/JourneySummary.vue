<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import AppCard from '@/shared/ui/AppCard.vue'

import type { Journey } from '../api/journeyApi'
import { formatJourneyDate } from '../model/journeyStatus'

interface Props {
  journey: Journey
}

defineProps<Props>()

const { locale, t } = useI18n()

function formatBudget(value: number | null): string {
  if (value === null) {
    return t('journey.detail.noBudget')
  }

  return new Intl.NumberFormat(locale.value, {
    style: 'currency',
    currency: 'KRW',
    maximumFractionDigits: 0,
  }).format(value)
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
</script>

<template>
  <header class="flex flex-col gap-1">
    <h1 class="font-display text-screen-title uppercase text-ink-display">{{ journey.title }}</h1>
    <p class="text-body-sm font-medium tabular-nums text-ink-2">
      <time :datetime="journey.startDate">{{ formatJourneyDate(journey.startDate, locale) }}</time>
      –
      <time :datetime="journey.endDate">{{ formatJourneyDate(journey.endDate, locale) }}</time>
    </p>
  </header>

  <!--
    시안 J2의 예산 블록은 `Total / Left`와 소진 게이지, 소비영역 범례를 함께 보여준다.
    상세 API(`Journey`)에는 지출 합계가 없어 게이지와 `Left`를 채울 값이 없으므로,
    같은 카드 조형에 현재 값인 예산과 동행 인원만 좌우로 놓는다.
  -->
  <AppCard padding="lg">
    <dl class="flex items-end justify-between gap-4">
      <div class="min-w-0">
        <dt class="text-caption uppercase tracking-wide text-ink-3">
          {{ t('journey.detail.budget') }}
        </dt>
        <dd class="mt-1 text-data-lg tabular-nums text-ink">
          {{ formatBudget(journey.budgetAmount) }}
        </dd>
      </div>
      <div class="min-w-0 text-right">
        <dt class="text-caption uppercase tracking-wide text-ink-3">
          {{ t('journey.detail.companions') }}
        </dt>
        <dd class="mt-1 text-title text-ink">
          {{ formatCompanionPreference(journey.companionPreference) }}
        </dd>
      </div>
    </dl>
  </AppCard>
</template>
