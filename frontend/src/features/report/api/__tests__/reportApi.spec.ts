import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  buildReportCreateRequest,
  createReport,
  fetchReport,
  fetchReportExpenseCandidates,
  fetchReportJourneys,
  fetchReports,
} from '../reportApi'

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))

vi.mock('@/shared/api/httpClient', () => ({
  httpClient: { get, post },
}))

const detailWire = {
  reportId: 100,
  tripId: 7,
  title: 'Jeju Island',
  startDate: '2026-07-18',
  endDate: '2026-07-27',
  generationStatus: 'COMPLETED',
  locale: 'en',
  generatedAt: '2026-07-28T09:00:00',
  createdAt: '2026-07-28T09:00:00',
  reportContent: {
    journey: {
      tripId: 7,
      title: 'Jeju Island',
      startDate: '2026-07-18',
      endDate: '2026-07-27',
    },
    days: [{ visitDate: '2026-07-18', items: null }],
  },
  analytics: {
    totalSpent: '1284500.0000',
    dailyAverage: '128450.0000',
    categoryBreakdown: null,
    dailyTrend: [{ date: '2026-07-18', amount: '0.0000' }],
  },
}

describe('reportApi', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
  })

  it('normalizes absent list collections to empty arrays', async () => {
    get.mockResolvedValueOnce({ data: null }).mockResolvedValueOnce({ data: null })

    await expect(fetchReportJourneys()).resolves.toEqual([])
    await expect(fetchReports()).resolves.toEqual([])
    expect(get).toHaveBeenNthCalledWith(1, '/api/v1/journeys')
    expect(get).toHaveBeenNthCalledWith(2, '/api/v1/reports')
  })

  it('preserves decimal strings and normalizes absent candidate collections', async () => {
    get.mockResolvedValueOnce({ data: { tripId: 7, candidates: null } }).mockResolvedValueOnce({
      data: {
        tripId: 7,
        candidates: [
          {
            transferId: 30,
            amount: '18000.0000',
            occurredDate: '2026-07-18',
            category: 'FOOD',
            displayMemo: null,
            selected: true,
          },
        ],
      },
    })

    await expect(fetchReportExpenseCandidates(7)).resolves.toEqual({ tripId: 7, candidates: [] })
    await expect(fetchReportExpenseCandidates(7)).resolves.toMatchObject({
      candidates: [{ amount: '18000.0000', selected: true }],
    })
  })

  it('rejects a numeric or malformed mock amount instead of losing precision', async () => {
    get.mockResolvedValue({
      data: {
        tripId: 7,
        candidates: [
          {
            transferId: 30,
            amount: 18000,
            occurredDate: '2026-07-18',
            category: 'FOOD',
            displayMemo: null,
            selected: false,
          },
        ],
      },
    })

    await expect(fetchReportExpenseCandidates(7)).rejects.toThrow(
      'candidate.amount must be a nonnegative decimal string.',
    )
  })

  it('deduplicates and sorts selected transfers in the create request', async () => {
    post.mockResolvedValue({ data: detailWire })

    await expect(createReport({ tripId: 7, transferIds: [30, 10, 30] })).resolves.toMatchObject({
      reportId: 100,
      analytics: {
        totalSpent: '1284500.0000',
        categoryBreakdown: [],
        dailyTrend: [{ amount: '0.0000' }],
      },
      reportContent: { days: [{ items: [] }] },
    })
    expect(post).toHaveBeenCalledWith('/api/v1/journeys/7/reports', {
      locale: 'en',
      transferIds: [10, 30],
    })
    expect(buildReportCreateRequest({ tripId: 7, transferIds: [] })).toEqual({
      locale: 'en',
      transferIds: [],
    })
  })

  it('normalizes an absent analytics field as a legacy report', async () => {
    const legacy = {
      ...detailWire,
      analytics: undefined,
      reportContent: { ...detailWire.reportContent, days: null },
    }
    get.mockResolvedValue({ data: legacy })

    await expect(fetchReport(100)).resolves.toMatchObject({
      analytics: null,
      reportContent: { days: [] },
    })
    expect(get).toHaveBeenCalledWith('/api/v1/reports/100')
  })
})
