import type { JourneySummary } from '../api/journeyApi'

export type JourneyListTab = 'ongoing' | 'past'
export type JourneyListStatus = JourneyListTab

const KOREA_TIME_ZONE = 'Asia/Seoul'

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
