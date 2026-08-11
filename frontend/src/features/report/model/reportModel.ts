import type { ReportJourneySummary, ReportSummary } from '../api/reportApi'

export interface ReportJourneyOption extends ReportJourneySummary {
  report: ReportSummary | null
}

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

export function buildReportJourneyOptions(
  journeys: ReportJourneySummary[],
  reports: ReportSummary[],
  today: string = getKoreaToday(),
): ReportJourneyOption[] {
  const reportsByTripId = new Map(reports.map((report) => [report.tripId, report]))

  return journeys
    .filter((journey) => journey.endDate < today)
    .map((journey) => ({ ...journey, report: reportsByTripId.get(journey.tripId) ?? null }))
    .sort(
      (first, second) =>
        second.endDate.localeCompare(first.endDate) || second.tripId - first.tripId,
    )
}

export function parsePositiveRouteId(value: unknown): number | null {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) {
    return null
  }

  const parsed = Number(value)

  return Number.isSafeInteger(parsed) ? parsed : null
}

export function formatReportDate(value: string): string {
  return value.replace(/-/g, '.')
}

export function formatKrwAmount(value: string): string {
  const [integer = '0', fraction = ''] = value.split('.')
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  const meaningfulFraction = fraction.replace(/0+$/, '')

  return `₩${grouped}${meaningfulFraction === '' ? '' : `.${meaningfulFraction}`}`
}

export function formatPercentage(value: string): string {
  const [integer = '0', fraction = ''] = value.split('.')
  const meaningfulFraction = fraction.replace(/0+$/, '')

  return `${integer}${meaningfulFraction === '' ? '' : `.${meaningfulFraction}`}%`
}

export function isZeroAmount(value: string): boolean {
  return /^0+(?:\.0+)?$/.test(value)
}
