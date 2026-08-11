import { httpClient } from '@/shared/api/httpClient'

export interface ReportJourneySummary {
  tripId: number
  title: string
  startDate: string
  endDate: string
}

export interface ReportSummary extends ReportJourneySummary {
  reportId: number
  generationStatus: string
  locale: string
  generatedAt: string
  createdAt: string
}

export interface ReportSnapshotItem {
  tripItemId: number
  itemId: number
  itemType: string
  title: string
  status: string
}

export interface ReportSnapshotDay {
  visitDate: string
  items: ReportSnapshotItem[]
}

export interface ReportSnapshot {
  journey: ReportJourneySummary
  days: ReportSnapshotDay[]
}

export interface ReportExpenseCandidate {
  transferId: number
  amount: string
  occurredDate: string
  category: string
  displayMemo: string | null
  selected: boolean
}

export interface ReportExpenseCandidates {
  tripId: number
  candidates: ReportExpenseCandidate[]
}

export interface ReportCategoryBreakdown {
  category: string
  amount: string
  percentage: string
}

export interface ReportDailyTrend {
  date: string
  amount: string
}

export interface ReportAnalytics {
  totalSpent: string
  dailyAverage: string
  categoryBreakdown: ReportCategoryBreakdown[]
  dailyTrend: ReportDailyTrend[]
}

export interface ReportDetail extends ReportSummary {
  reportContent: ReportSnapshot
  analytics: ReportAnalytics | null
}

interface ReportSnapshotDayWire extends Omit<ReportSnapshotDay, 'items'> {
  items?: ReportSnapshotItem[] | null
}

interface ReportSnapshotWire extends Omit<ReportSnapshot, 'days'> {
  days?: ReportSnapshotDayWire[] | null
  analytics?: ReportAnalyticsWire | null
}

interface ReportCategoryBreakdownWire {
  category: string
  amount: number
  percentage: number
}

interface ReportDailyTrendWire {
  date: string
  amount: number
}

interface ReportAnalyticsWire {
  totalSpent: number
  dailyAverage: number
  categoryBreakdown?: ReportCategoryBreakdownWire[] | null
  dailyTrend?: ReportDailyTrendWire[] | null
}

interface ReportDetailWire extends Omit<ReportDetail, 'reportContent' | 'analytics'> {
  reportContent: ReportSnapshotWire
}

interface ReportExpenseCandidateWire {
  transferId: number
  amount: number
  occurredOn: string
  category: string
  memo: string | null
  selected: boolean
}

export interface ReportCreateInput {
  tripId: number
  transferIds: number[]
  locale?: string
}

export interface ReportCreateRequest {
  locale: string
  transferIds: number[]
}

function normalizeDecimalNumber(value: number, field: string): string {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) {
    throw new TypeError(`${field} must be a nonnegative finite JSON number.`)
  }

  return String(value)
}

function normalizeAnalytics(
  analytics: ReportAnalyticsWire | null | undefined,
): ReportAnalytics | null {
  if (analytics === null || analytics === undefined) {
    return null
  }

  return {
    totalSpent: normalizeDecimalNumber(analytics.totalSpent, 'analytics.totalSpent'),
    dailyAverage: normalizeDecimalNumber(analytics.dailyAverage, 'analytics.dailyAverage'),
    categoryBreakdown: (analytics.categoryBreakdown ?? []).map((row) => ({
      ...row,
      amount: normalizeDecimalNumber(row.amount, 'analytics.categoryBreakdown.amount'),
      percentage: normalizeDecimalNumber(row.percentage, 'analytics.categoryBreakdown.percentage'),
    })),
    dailyTrend: (analytics.dailyTrend ?? []).map((row) => ({
      ...row,
      amount: normalizeDecimalNumber(row.amount, 'analytics.dailyTrend.amount'),
    })),
  }
}

function normalizeReportDetail(report: ReportDetailWire): ReportDetail {
  const { analytics, ...snapshot } = report.reportContent

  return {
    ...report,
    reportContent: {
      ...snapshot,
      days: (snapshot.days ?? []).map((day) => ({
        ...day,
        items: day.items ?? [],
      })),
    },
    analytics: normalizeAnalytics(analytics),
  }
}

function normalizeExpenseCandidates(
  tripId: number,
  candidates: ReportExpenseCandidateWire[] | null,
): ReportExpenseCandidates {
  return {
    tripId,
    candidates: (candidates ?? []).map((candidate) => ({
      transferId: candidate.transferId,
      amount: normalizeDecimalNumber(candidate.amount, 'candidate.amount'),
      occurredDate: candidate.occurredOn,
      category: candidate.category,
      displayMemo: candidate.memo,
      selected: candidate.selected,
    })),
  }
}

export function buildReportCreateRequest(input: ReportCreateInput): ReportCreateRequest {
  return {
    locale: input.locale ?? 'en',
    transferIds: [...new Set(input.transferIds)].sort((first, second) => first - second),
  }
}

export async function fetchReportJourneys(): Promise<ReportJourneySummary[]> {
  const response = await httpClient.get<ReportJourneySummary[] | null>('/api/v1/journeys')

  return response.data ?? []
}

export async function fetchReports(): Promise<ReportSummary[]> {
  const response = await httpClient.get<ReportSummary[] | null>('/api/v1/reports')

  return response.data ?? []
}

export async function fetchReportExpenseCandidates(
  tripId: number,
): Promise<ReportExpenseCandidates> {
  const response = await httpClient.get<ReportExpenseCandidateWire[] | null>(
    `/api/v1/journeys/${tripId}/report-expense-candidates`,
  )

  return normalizeExpenseCandidates(tripId, response.data)
}

export async function createReport(input: ReportCreateInput): Promise<ReportDetail> {
  const response = await httpClient.post<ReportDetailWire>(
    `/api/v1/journeys/${input.tripId}/reports`,
    buildReportCreateRequest(input),
  )

  return normalizeReportDetail(response.data)
}

export async function fetchReport(reportId: number): Promise<ReportDetail> {
  const response = await httpClient.get<ReportDetailWire>(`/api/v1/reports/${reportId}`)

  return normalizeReportDetail(response.data)
}
