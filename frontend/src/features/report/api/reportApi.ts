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
}

interface ReportAnalyticsWire extends Omit<ReportAnalytics, 'categoryBreakdown' | 'dailyTrend'> {
  categoryBreakdown?: ReportCategoryBreakdown[] | null
  dailyTrend?: ReportDailyTrend[] | null
}

interface ReportDetailWire extends Omit<ReportDetail, 'reportContent' | 'analytics'> {
  reportContent: ReportSnapshotWire
  analytics?: ReportAnalyticsWire | null
}

interface ReportExpenseCandidatesWire extends Omit<ReportExpenseCandidates, 'candidates'> {
  candidates?: ReportExpenseCandidate[] | null
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

const DECIMAL_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d+)?$/

function requireDecimalString(value: string, field: string): string {
  if (typeof value !== 'string' || !DECIMAL_PATTERN.test(value)) {
    throw new TypeError(`${field} must be a nonnegative decimal string.`)
  }

  return value
}

function normalizeAnalytics(
  analytics: ReportAnalyticsWire | null | undefined,
): ReportAnalytics | null {
  if (analytics === null || analytics === undefined) {
    return null
  }

  return {
    totalSpent: requireDecimalString(analytics.totalSpent, 'analytics.totalSpent'),
    dailyAverage: requireDecimalString(analytics.dailyAverage, 'analytics.dailyAverage'),
    categoryBreakdown: (analytics.categoryBreakdown ?? []).map((row) => ({
      ...row,
      amount: requireDecimalString(row.amount, 'analytics.categoryBreakdown.amount'),
      percentage: requireDecimalString(row.percentage, 'analytics.categoryBreakdown.percentage'),
    })),
    dailyTrend: (analytics.dailyTrend ?? []).map((row) => ({
      ...row,
      amount: requireDecimalString(row.amount, 'analytics.dailyTrend.amount'),
    })),
  }
}

function normalizeReportDetail(report: ReportDetailWire): ReportDetail {
  return {
    ...report,
    reportContent: {
      ...report.reportContent,
      days: (report.reportContent.days ?? []).map((day) => ({
        ...day,
        items: day.items ?? [],
      })),
    },
    analytics: normalizeAnalytics(report.analytics),
  }
}

function normalizeExpenseCandidates(
  response: ReportExpenseCandidatesWire,
): ReportExpenseCandidates {
  return {
    tripId: response.tripId,
    candidates: (response.candidates ?? []).map((candidate) => ({
      ...candidate,
      amount: requireDecimalString(candidate.amount, 'candidate.amount'),
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
  const response = await httpClient.get<ReportExpenseCandidatesWire>(
    `/api/v1/journeys/${tripId}/report-expense-candidates`,
  )

  return normalizeExpenseCandidates(response.data)
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
