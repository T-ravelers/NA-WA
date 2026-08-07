<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import type { JourneyTimelineDay, JourneyTimelineItem } from '../api/journeyApi'

interface Props {
  days: JourneyTimelineDay[]
}

defineProps<Props>()

const { locale, t } = useI18n()

const dateFormatter = computed(
  () =>
    new Intl.DateTimeFormat(locale.value, {
      weekday: 'long',
      month: 'short',
      day: 'numeric',
      timeZone: 'UTC',
    }),
)
const timeFormatter = computed(
  () =>
    new Intl.DateTimeFormat(locale.value, {
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'UTC',
    }),
)

function toUtcDate(value: string): Date {
  const [datePart, timePart] = value.split('T')
  const [year, month, day] = (datePart ?? '').split('-').map(Number)
  const [hour, minute, second] = (timePart ?? '').split(':').map(Number)

  return new Date(
    Date.UTC(year ?? 0, (month ?? 1) - 1, day ?? 1, hour ?? 0, minute ?? 0, second ?? 0),
  )
}

function formatDate(value: string): string {
  return dateFormatter.value.format(toUtcDate(`${value}T00:00:00`))
}

function formatTime(item: JourneyTimelineItem): string {
  return item.appointment === undefined
    ? t('journey.detail.noTime')
    : timeFormatter.value.format(toUtcDate(item.appointment.activityStartAt))
}

function formatLocation(item: JourneyTimelineItem): string | null {
  const location = item.exploreItem.location
  return (
    [location.region2, location.region3].filter(Boolean).join(' · ') ||
    location.region1 ||
    location.addressRoad ||
    null
  )
}

function typeLabel(item: JourneyTimelineItem): string {
  return item.exploreItem.itemType === 'EVENT'
    ? t('journey.detail.event')
    : t('journey.detail.place')
}
</script>

<template>
  <ol class="flex flex-col gap-8">
    <li
      v-for="day in days"
      :key="day.visitDate"
      class="flex flex-col gap-3"
    >
      <h3 class="text-title text-ink">
        <time :datetime="day.visitDate">{{ formatDate(day.visitDate) }}</time>
      </h3>

      <ol class="flex flex-col gap-3">
        <li
          v-for="item in day.items"
          :key="item.tripItemId"
          class="rounded-card bg-surface-1 p-4"
        >
          <article class="flex flex-col gap-2">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <p class="text-caption text-ink-3">{{ formatTime(item) }}</p>
              <p class="text-caption text-ink-3">
                {{
                  item.status === 'CONFIRMED'
                    ? t('journey.detail.confirmed')
                    : t('journey.detail.saved')
                }}
              </p>
            </div>
            <h4 class="text-title-sm text-ink">{{ item.exploreItem.title }}</h4>
            <p class="text-body-sm text-ink-2">
              {{ typeLabel(item) }}
              <template v-if="formatLocation(item) !== null">
                · {{ formatLocation(item) }}</template
              >
            </p>
            <p
              v-if="item.note !== null && item.note !== ''"
              class="text-body-sm text-ink-3"
            >
              {{ t('journey.detail.note', { note: item.note }) }}
            </p>
          </article>
        </li>
      </ol>
    </li>
  </ol>
</template>
