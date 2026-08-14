<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import { IconPlus } from '@tabler/icons-vue'

import {
  formatCalendarDate,
  formatServerDateTime,
  parseCalendarDate,
  serializeCalendarDate,
} from '@/shared/lib/datetime'
import AppBadge from '@/shared/ui/AppBadge.vue'

import type { JourneyTimelineDay, JourneyTimelineItem } from '../api/journeyApi'

interface Props {
  days: JourneyTimelineDay[]
  tripId: number
  startDate: string
  endDate: string
}

const props = defineProps<Props>()

const { locale, t } = useI18n()

function formatDate(value: string): string {
  return formatCalendarDate(value, locale.value, {
    weekday: 'long',
    month: 'short',
    day: 'numeric',
  })
}

function formatTime(item: JourneyTimelineItem): string {
  if (item.appointment === undefined) return t('journey.detail.noTime')

  return (
    formatServerDateTime(item.appointment.activityStartAt, locale.value, {
      hour: '2-digit',
      minute: '2-digit',
    }) || t('journey.detail.noTime')
  )
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

/** 여정 기간의 날짜를 로컬 달력 기준으로 하루씩 늘어놓는다. */
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

/**
 * Discover는 이미 `startDate`/`endDate`와 `tab`을 읽고(`ExploreView`), explore 상세는
 * `journeyId`를 읽는다(`EventDetailView`·`PlaceDetailView`의 `parseJourneyRouteQuery`).
 * 셋 다 기존 키다. 새 쿼리 키를 만들지 않는다. Events가 기본 탭이므로 `tab`을 붙이지 않는다.
 *
 * Places 목록은 아직 날짜로 걸러지지 않는다. place에는 시간 값이 없어 검색 API에도 날짜
 * 파라미터가 없다. 그쪽 날짜는 필터가 아니라 "어느 여정의 어느 날에 담는가"라는 맥락이다.
 * `journeyId`와 함께 그 맥락을 store로 옮겨 담아 담기 시트에 프리필하는 일은 #192가 맡는다.
 *
 * 여정 기간 밖 날짜는 항목 추가 API가 `JOURNEY-007`로 거절한다
 * (`JourneyService.addJourneyItem`). 그런 날짜로는 링크를 만들지 않는다.
 */
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

const displayDays = computed(() => {
  const itemsByDate = new Map(props.days.map((day) => [day.visitDate, day.items]))
  /* 시안 J2는 날짜 앞에 `Day 1` 순번을 둔다. 여정 시작일로부터 몇 번째 날인지다. */
  const dayNumbers = new Map(
    journeyDates(props.startDate, props.endDate).map((visitDate, index) => [visitDate, index + 1]),
  )
  /* 타임라인 응답은 항목이 있는 날짜만 내려주므로 빈 날짜는 여기서 채운다. 반대로 기간
     밖으로 밀려난 항목도 화면에서 감추지 않는다. `YYYY-MM-DD`는 사전순이 곧 날짜순이다. */
  const visitDates = [...new Set([...dayNumbers.keys(), ...itemsByDate.keys()])].sort()

  return visitDates.map((visitDate) => {
    const dayNumber = dayNumbers.get(visitDate)
    const dateLabel = formatDate(visitDate)

    return {
      visitDate,
      dateLabel,
      /* 기간 밖 날짜에는 붙일 순번이 없다. 날짜만 보여준다. */
      dayLabel: dayNumber === undefined ? null : t('journey.detail.dayLabel', { index: dayNumber }),
      /* 기간 밖 날짜로는 담을 수 없으므로 링크 자체를 만들지 않는다. */
      addLinks:
        dayNumber === undefined
          ? null
          : {
              eventTo: exploreLink(visitDate, 'events'),
              placeTo: exploreLink(visitDate, 'places'),
              /* 날짜마다 같은 라벨이 반복되므로 접근 가능한 이름에 날짜를 넣어 구분한다. */
              eventLabel: t('journey.detail.addEventForDate', { date: dateLabel }),
              placeLabel: t('journey.detail.addPlaceForDate', { date: dateLabel }),
            },
      items: (itemsByDate.get(visitDate) ?? []).map((item) => ({
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
    }
  })
})
</script>

<template>
  <ol class="flex flex-col gap-6">
    <li
      v-for="day in displayDays"
      :key="day.visitDate"
      class="flex flex-col gap-3"
    >
      <h3 class="flex items-baseline gap-2">
        <span
          v-if="day.dayLabel !== null"
          class="font-display text-title uppercase text-ink-display"
          >{{ day.dayLabel }}</span
        >
        <time
          :datetime="day.visitDate"
          class="text-body-sm font-medium tabular-nums text-ink-3"
          >{{ day.dateLabel }}</time
        >
      </h3>

      <ol
        v-if="day.items.length > 0"
        class="flex flex-col"
      >
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

      <!-- 하루에 여러 개를 담는 것이 정상 흐름이라 항목이 있는 날에도 그대로 둔다. -->
      <div
        v-if="day.addLinks !== null"
        class="flex gap-2"
      >
        <RouterLink
          :to="day.addLinks.eventTo"
          :aria-label="day.addLinks.eventLabel"
          class="flex min-h-13 flex-1 items-center justify-center gap-2 rounded-sm border-2 border-dashed border-hairline-2 text-body-sm font-semibold text-ink-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
        >
          <IconPlus
            :size="18"
            :stroke-width="1.75"
            aria-hidden="true"
          />
          {{ t('journey.detail.addEvent') }}
        </RouterLink>
        <RouterLink
          :to="day.addLinks.placeTo"
          :aria-label="day.addLinks.placeLabel"
          class="flex min-h-13 flex-1 items-center justify-center gap-2 rounded-sm border-2 border-dashed border-hairline-2 text-body-sm font-semibold text-ink-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
        >
          <IconPlus
            :size="18"
            :stroke-width="1.75"
            aria-hidden="true"
          />
          {{ t('journey.detail.addPlace') }}
        </RouterLink>
      </div>
    </li>
  </ol>
</template>
