import { onMounted, onUnmounted, ref, type Ref } from 'vue'

import type { JourneySummary } from '../api/journeyApi'

export type JourneyListTab = 'ongoing' | 'past'
export type JourneyListStatus = JourneyListTab

const KOREA_TIME_ZONE = 'Asia/Seoul'
const KOREA_UTC_OFFSET_MS = 9 * 60 * 60 * 1000

export function getKoreaToday(now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    day: '2-digit',
    month: '2-digit',
    timeZone: KOREA_TIME_ZONE,
    year: 'numeric',
  }).formatToParts(now)
  const values = Object.fromEntries(
    parts
      .filter(({ type }) => type === 'year' || type === 'month' || type === 'day')
      .map(({ type, value }) => [type, value]),
  )

  return `${values.year}-${values.month}-${values.day}`
}

export function getMillisecondsUntilKoreaMidnight(now: Date = new Date()): number {
  const koreaNow = new Date(now.getTime() + KOREA_UTC_OFFSET_MS)
  const nextMidnightUtc = Date.UTC(
    koreaNow.getUTCFullYear(),
    koreaNow.getUTCMonth(),
    koreaNow.getUTCDate() + 1,
  )

  return Math.max(1, nextMidnightUtc - KOREA_UTC_OFFSET_MS - now.getTime())
}

export function useKoreaToday(): Readonly<Ref<string>> {
  const today = ref(getKoreaToday())
  let timeoutId: ReturnType<typeof setTimeout> | undefined

  const refresh = (): void => {
    today.value = getKoreaToday()
  }

  const handleVisibilityChange = (): void => {
    if (document.visibilityState === 'visible') {
      refresh()
    }
  }

  const scheduleRefresh = (): void => {
    timeoutId = setTimeout(() => {
      refresh()
      scheduleRefresh()
    }, getMillisecondsUntilKoreaMidnight())
  }

  onMounted(() => {
    refresh()
    scheduleRefresh()
    document.addEventListener('visibilitychange', handleVisibilityChange)
  })

  onUnmounted(() => {
    if (timeoutId !== undefined) {
      clearTimeout(timeoutId)
    }
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  })

  return today
}

export function getJourneyStatus(
  endDate: string,
  today: string = getKoreaToday(),
): JourneyListStatus {
  return endDate < today ? 'past' : 'ongoing'
}

export function filterJourneysByStatus(
  journeys: JourneySummary[],
  status: JourneyListStatus,
  today: string = getKoreaToday(),
): JourneySummary[] {
  return journeys.filter((journey) => getJourneyStatus(journey.endDate, today) === status)
}

export function formatJourneyDate(value: string): string {
  return value.replace(/-/g, '.')
}
