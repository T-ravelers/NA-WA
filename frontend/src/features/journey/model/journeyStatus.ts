import { onMounted, onUnmounted, ref, type Ref } from 'vue'

import { formatCalendarDate } from '@/shared/lib/datetime'

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

/**
 * 날짜만 담긴 API 값(`YYYY-MM-DD`)을 로케일 표기로 옮긴다.
 *
 * 시안 J1·J2는 여정 기간을 짧은 월·일로 쓴다. 목록 카드와 상세 헤더가 같은 표기를
 * 쓰도록 포맷을 여기 한 곳에 둔다. 예전에는 목록만 `2026.08.09`로 점을 찍어 두 화면의
 * 표기가 서로 달랐다.
 *
 * 공용 달력 전용 파서와 포맷터가 `YYYY-MM-DD`의 연·월·일을 로컬 달력 값으로 보존한다.
 * 서버 시각 파서를 거치지 않으므로 기기 타임존 변환으로 날짜가 밀리지 않는다.
 */
export function formatJourneyDate(value: string, locale: string): string {
  return formatCalendarDate(value, locale, { dateStyle: 'medium' })
}
