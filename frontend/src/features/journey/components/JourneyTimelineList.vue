<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import AppBadge from '@/shared/ui/AppBadge.vue'

import type { JourneyTimelineDay, JourneyTimelineItem } from '../api/journeyApi'

interface Props {
  days: JourneyTimelineDay[]
}

const props = defineProps<Props>()

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

const displayDays = computed(() =>
  props.days.map((day, dayIndex) => ({
    visitDate: day.visitDate,
    dateLabel: formatDate(day.visitDate),
    /* 시안 J2는 날짜 앞에 `Day 1` 순번을 둔다. 목록 순서에서 그대로 나온다. */
    dayLabel: t('journey.detail.dayLabel', { index: dayIndex + 1 }),
    items: day.items.map((item) => ({
      tripItemId: item.tripItemId,
      timeLabel: formatTime(item),
      confirmed: item.status === 'CONFIRMED',
      statusLabel:
        item.status === 'CONFIRMED' ? t('journey.detail.confirmed') : t('journey.detail.saved'),
      title: item.exploreItem.title,
      typeLabel: typeLabel(item),
      location: formatLocation(item),
      note: item.note,
    })),
  })),
)
</script>

<template>
  <ol class="flex flex-col gap-6">
    <li
      v-for="day in displayDays"
      :key="day.visitDate"
      class="flex flex-col gap-3"
    >
      <h3 class="flex items-baseline gap-2">
        <span class="font-display text-title uppercase text-ink-display">{{ day.dayLabel }}</span>
        <time
          :datetime="day.visitDate"
          class="text-body-sm font-medium tabular-nums text-ink-3"
          >{{ day.dateLabel }}</time
        >
      </h3>

      <ol class="flex flex-col">
        <li
          v-for="item in day.items"
          :key="item.tripItemId"
          class="flex gap-3"
        >
          <p
            class="w-13 shrink-0 pt-0.5 text-right text-body-sm font-semibold tabular-nums text-ink-2"
          >
            {{ item.timeLabel }}
          </p>

          <!-- 시간 축 레일. 장식이므로 접근성 트리에서 감춘다. -->
          <div
            aria-hidden="true"
            class="flex w-4 shrink-0 flex-col items-center"
          >
            <span class="mt-1 size-3 shrink-0 rounded-pill bg-hairline-strong" />
            <span class="my-1 w-0.5 flex-1 bg-hairline" />
          </div>

          <article class="min-w-0 flex-1 pb-4">
            <div class="flex flex-col gap-2 rounded-md bg-surface-1 px-4 py-3.5">
              <div class="flex flex-wrap items-center gap-2">
                <h4 class="min-w-0 text-title-sm text-ink">{{ item.title }}</h4>
                <AppBadge
                  :tone="item.confirmed ? 'ongoing' : 'neutral'"
                  :dot="item.confirmed"
                >
                  {{ item.statusLabel }}
                </AppBadge>
              </div>
              <p class="text-body-sm text-ink-2">
                {{ item.typeLabel }}
                <template v-if="item.location !== null"> · {{ item.location }}</template>
              </p>
              <p
                v-if="item.note !== null && item.note !== ''"
                class="text-body-sm text-ink-3"
              >
                {{ t('journey.detail.note', { note: item.note }) }}
              </p>
            </div>
          </article>
        </li>
      </ol>
    </li>
  </ol>
</template>
