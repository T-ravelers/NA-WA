<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import { IconPlus, IconX } from '@tabler/icons-vue'

import {
  formatCalendarDate,
  formatServerDateTime,
  parseCalendarDate,
  serializeCalendarDate,
} from '@/shared/lib/datetime'
import CategoryDot from '@/shared/ui/CategoryDot.vue'

import type { JourneyTimelineDay, JourneyTimelineItem } from '../api/journeyApi'
import { categoryForJourneyItem } from '../model/journeyCategory'
import JourneyTimelineItemCard from './JourneyTimelineItemCard.vue'

interface Props {
  days: JourneyTimelineDay[]
  tripId: number
  startDate: string
  endDate: string
  removingTripItemId?: number | null
}

const props = defineProps<Props>()
const emit = defineEmits<{ remove: [item: JourneyTimelineItem] }>()
const { locale, t } = useI18n()

function formatDate(value: string): string {
  return formatCalendarDate(value, locale.value, { month: 'short', day: 'numeric' })
}

function formatTime(item: JourneyTimelineItem): string {
  if (item.appointment === undefined) return t('journey.detail.noTime')

  return (
    formatServerDateTime(item.appointment.activityStartAt, locale.value, {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }) || t('journey.detail.noTime')
  )
}

function formatLocation(item: JourneyTimelineItem): string | null {
  const location = item.exploreItem.location
  return (
    location.addressRoad ||
    [location.region2, location.region3].filter(Boolean).join(' · ') ||
    location.region1 ||
    null
  )
}

function distanceToNextStop(
  current: JourneyTimelineItem,
  next: JourneyTimelineItem | undefined,
): number | null {
  if (next === undefined) return null

  const from = current.exploreItem.location
  const to = next.exploreItem.location
  if (
    from.latitude === null ||
    from.longitude === null ||
    to.latitude === null ||
    to.longitude === null
  ) {
    return null
  }

  const radians = (degrees: number) => (degrees * Math.PI) / 180
  const latitudeDelta = radians(to.latitude - from.latitude)
  const longitudeDelta = radians(to.longitude - from.longitude)
  const fromLatitude = radians(from.latitude)
  const toLatitude = radians(to.latitude)
  const haversine =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(fromLatitude) * Math.cos(toLatitude) * Math.sin(longitudeDelta / 2) ** 2

  return 6371 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
}

function journeyDates(startDate: string, endDate: string): string[] {
  const start = parseCalendarDate(startDate)
  const end = parseCalendarDate(endDate)
  if (start === null || end === null || start.getTime() > end.getTime()) return []

  const dates: string[] = []
  const current = new Date(start.getTime())
  while (current.getTime() <= end.getTime()) {
    dates.push(serializeCalendarDate(current))
    current.setDate(current.getDate() + 1)
  }

  return dates
}

function exploreLink(visitDate: string, tab: 'events' | 'places'): RouteLocationRaw {
  const journeyId = String(props.tripId)

  return {
    name: 'explore',
    query:
      tab === 'places'
        ? { tab: 'places', journeyId, startDate: visitDate, endDate: visitDate }
        : { journeyId, startDate: visitDate, endDate: visitDate },
  }
}

function detailLink(item: JourneyTimelineItem): RouteLocationRaw {
  return item.exploreItem.itemType === 'EVENT'
    ? { name: 'explore-event-detail', params: { eventId: String(item.itemId) } }
    : { name: 'explore-place-detail', params: { placeId: String(item.itemId) } }
}

const displayDays = computed(() => {
  const itemsByDate = new Map(props.days.map((day) => [day.visitDate, day.items]))
  const dayNumbers = new Map(
    journeyDates(props.startDate, props.endDate).map((visitDate, index) => [visitDate, index + 1]),
  )
  const visitDates = [...new Set([...dayNumbers.keys(), ...itemsByDate.keys()])].sort()

  return visitDates.map((visitDate) => {
    const items = itemsByDate.get(visitDate) ?? []
    const dayNumber = dayNumbers.get(visitDate)
    const dateLabel = formatDate(visitDate)

    return {
      visitDate,
      dateLabel,
      dayLabel: dayNumber === undefined ? null : t('journey.detail.dayLabel', { index: dayNumber }),
      addLinks:
        dayNumber === undefined
          ? null
          : {
              eventTo: exploreLink(visitDate, 'events'),
              placeTo: exploreLink(visitDate, 'places'),
              eventLabel: t('journey.detail.addEventForDate', { date: dateLabel }),
              placeLabel: t('journey.detail.addPlaceForDate', { date: dateLabel }),
            },
      items: items.map((item, index) => ({
        source: item,
        tripItemId: item.tripItemId,
        timeLabel: formatTime(item),
        location: formatLocation(item),
        distanceKm: distanceToNextStop(item, items[index + 1]),
        /* 운영 `placeKind`는 한국어 원문이라 소비영역 이름으로는 한 번도 맞지 않는다.
           Explore와 같은 표시 규칙을 쓰는 #540의 매핑이 그 자리를 맡는다. */
        category: categoryForJourneyItem(item),
        detailTo: detailLink(item),
        detailName:
          item.exploreItem.itemType === 'EVENT'
            ? t('journey.detail.eventDetailFor', { title: item.exploreItem.title })
            : t('journey.detail.placeDetailFor', { title: item.exploreItem.title }),
        /* 시안은 하루의 대표 일정 한 장만 멤버 칩을 펼치고 나머지는 압축한다. */
        large: index === 0 && item.status === 'CONFIRMED',
        removeName: t('journey.remove.actionFor', { title: item.exploreItem.title }),
      })),
    }
  })
})
</script>

<template>
  <ol class="flex flex-col gap-16">
    <li
      v-for="day in displayDays"
      :key="day.visitDate"
    >
      <h3 class="flex h-8 items-start">
        <span
          v-if="day.dayLabel !== null"
          class="w-25 text-title font-bold uppercase text-ink"
        >
          {{ day.dayLabel }}
        </span>
        <time
          :datetime="day.visitDate"
          class="pt-1 text-body-sm text-ink-3"
        >
          {{ day.dateLabel }}
        </time>
      </h3>

      <ol v-if="day.items.length > 0">
        <li
          v-for="item in day.items"
          :key="item.tripItemId"
          class="flex"
        >
          <p class="w-13 shrink-0 pt-0.5 text-right text-body-sm tabular-nums text-ink-2">
            {{ item.timeLabel }}
          </p>

          <div
            aria-hidden="true"
            class="ml-3 flex w-4 shrink-0 flex-col items-center"
          >
            <CategoryDot
              :category="item.category"
              class="mt-1 size-3"
            />
            <span class="my-1 w-0.5 flex-1 bg-surface-1" />
          </div>

          <article class="relative ml-3 min-w-0 flex-1 pb-6">
            <JourneyTimelineItemCard
              :item="item.source"
              :category="item.category"
              :detail-to="item.detailTo"
              :detail-name="item.detailName"
              :location="item.location"
              :distance-km="item.distanceKm"
              :large="item.large"
            />

            <!--
              카드 전체가 상세로 가는 링크라 삭제 버튼을 그 안에 둘 수 없다(링크 중첩).
              카드 위에 얹어 시안의 조형을 지키면서 누를 자리를 44px로 지킨다.
            -->
            <button
              type="button"
              :aria-label="item.removeName"
              :data-testid="`itinerary-remove-${item.tripItemId}`"
              :disabled="props.removingTripItemId === item.tripItemId"
              class="absolute right-0.5 top-0.5 z-20 flex size-11 items-center justify-center rounded-pill text-on-paper-2 disabled:opacity-40"
              @click="emit('remove', item.source)"
            >
              <IconX
                :size="19"
                :stroke-width="1.75"
                aria-hidden="true"
              />
            </button>
          </article>
        </li>
      </ol>

      <div
        v-if="day.addLinks !== null"
        class="flex gap-2"
      >
        <RouterLink
          :to="day.addLinks.eventTo"
          :aria-label="day.addLinks.eventLabel"
          class="flex min-h-13 flex-1 items-center justify-center gap-2 rounded-sm border-2 border-dashed border-hairline-2 text-body-sm font-semibold text-ink-2"
        >
          <IconPlus
            :size="18"
            aria-hidden="true"
          />
          {{ t('journey.detail.addEvent') }}
        </RouterLink>
        <RouterLink
          :to="day.addLinks.placeTo"
          :aria-label="day.addLinks.placeLabel"
          class="flex min-h-13 flex-1 items-center justify-center gap-2 rounded-sm border-2 border-dashed border-hairline-2 text-body-sm font-semibold text-ink-2"
        >
          <IconPlus
            :size="18"
            aria-hidden="true"
          />
          {{ t('journey.detail.addPlace') }}
        </RouterLink>
      </div>
    </li>
  </ol>
</template>
