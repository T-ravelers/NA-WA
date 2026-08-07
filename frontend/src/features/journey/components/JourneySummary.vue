<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import type { Journey } from '../api/journeyApi'

interface Props {
  journey: Journey
}

defineProps<Props>()

const { locale, t } = useI18n()

function formatDate(value: string): string {
  const [year, month, day] = value.split('-').map(Number)
  return new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeZone: 'UTC' }).format(
    new Date(Date.UTC(year ?? 0, (month ?? 1) - 1, day ?? 1)),
  )
}

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
  <header class="flex flex-col gap-2">
    <h1 class="font-display text-screen-title font-bold text-ink-display">{{ journey.title }}</h1>
    <p class="text-body-sm text-ink-3">
      <time :datetime="journey.startDate">{{ formatDate(journey.startDate) }}</time>
      –
      <time :datetime="journey.endDate">{{ formatDate(journey.endDate) }}</time>
    </p>
  </header>

  <dl class="grid gap-4 rounded-card bg-surface-1 p-4">
    <div class="flex flex-col gap-1">
      <dt class="text-caption text-ink-3">{{ t('journey.detail.budget') }}</dt>
      <dd class="text-data-lg text-ink">{{ formatBudget(journey.budgetAmount) }}</dd>
    </div>
    <div class="flex flex-col gap-1">
      <dt class="text-caption text-ink-3">{{ t('journey.detail.companions') }}</dt>
      <dd class="text-body text-ink">
        {{ formatCompanionPreference(journey.companionPreference) }}
      </dd>
    </div>
  </dl>
</template>
