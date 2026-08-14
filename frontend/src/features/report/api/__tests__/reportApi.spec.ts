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
    analytics: {
      totalSpent: 1284500,
      dailyAverage: 128450,
      categoryBreakdown: null,
      dailyTrend: [{ date: '2026-07-18', amount: 0 }],
    },
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

  it('keeps journey item counts on the report-owned journey summary', async () => {
    get.mockResolvedValueOnce({
      data: [
        {
          tripId: 7,
          title: 'Jeju Island',
          startDate: '2026-07-18',
          endDate: '2026-07-27',
          eventCount: 5,
          placeCount: 9,
        },
      ],
    })

    await expect(fetchReportJourneys()).resolves.toEqual([
      {
        tripId: 7,
        title: 'Jeju Island',
        startDate: '2026-07-18',
        endDate: '2026-07-27',
        eventCount: 5,
        placeCount: 9,
      },
    ])
  })

  it('maps the candidate array and numeric amount to the frontend model', async () => {
    get.mockResolvedValueOnce({ data: null }).mockResolvedValueOnce({
      data: [
        {
          transferId: 30,
          amount: 18000,
          occurredOn: '2026-07-18',
          category: 'FOOD',
          memo: null,
          selected: true,
        },
      ],
    })

    await expect(fetchReportExpenseCandidates(7)).resolves.toEqual({ tripId: 7, candidates: [] })
    await expect(fetchReportExpenseCandidates(7)).resolves.toEqual({
      tripId: 7,
      candidates: [
        {
          transferId: 30,
          amount: '18000',
          occurredDate: '2026-07-18',
          category: 'FOOD',
          displayMemo: null,
          selected: true,
        },
      ],
    })
  })

  it('rejects a string amount that violates the numeric backend contract', async () => {
    get.mockResolvedValue({
      data: [
        {
          transferId: 30,
          amount: '18000.0000',
          occurredOn: '2026-07-18',
          category: 'FOOD',
          memo: null,
          selected: false,
        },
      ],
    })

    await expect(fetchReportExpenseCandidates(7)).rejects.toThrow(
      'candidate.amount must be a nonnegative finite JSON number.',
    )
  })

  it('deduplicates and sorts selected transfers in the create request', async () => {
    post.mockResolvedValue({ data: detailWire })

    await expect(createReport({ tripId: 7, transferIds: [30, 10, 30] })).resolves.toMatchObject({
      reportId: 100,
      analytics: {
        totalSpent: '1284500',
        categoryBreakdown: [],
        dailyTrend: [{ amount: '0' }],
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
      reportContent: { ...detailWire.reportContent, days: null, analytics: undefined },
    }
    get.mockResolvedValue({ data: legacy })

    await expect(fetchReport(100)).resolves.toMatchObject({
      analytics: null,
      reportContent: { days: [] },
    })
    expect(get).toHaveBeenCalledWith('/api/v1/reports/100')
  })
})
