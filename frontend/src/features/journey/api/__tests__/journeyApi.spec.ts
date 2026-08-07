import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  addJourneyItem,
  buildJourneyCreateRequest,
  createJourney,
  fetchJourney,
  fetchJourneyTimeline,
  fetchJourneys,
  type Journey,
  type JourneyCreateInput,
} from '../journeyApi'

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))

vi.mock('@/shared/api/httpClient', () => ({
  httpClient: {
    get,
    post,
  },
}))

const journey: Journey = {
  tripId: 7,
  title: 'Seoul Foodie Week',
  startDate: '2026-08-10',
  endDate: '2026-08-12',
  budgetAmount: 500000,
  companionPreference: '2-4',
  regions: [
    { regionCode: 'BUSAN', regionName: 'Busan', displayOrder: 1 },
    { regionCode: 'SEOUL', regionName: 'Seoul', displayOrder: 0 },
  ],
}

const input: JourneyCreateInput = {
  title: '  Seoul Foodie Week  ',
  startDate: '2026-08-10',
  endDate: '2026-08-12',
  budgetAmount: 500000,
  companionPreference: '2-4',
  regions: [
    { regionCode: ' SEOUL ', regionName: ' Seoul ', displayOrder: 8 },
    { regionCode: 'BUSAN', regionName: 'Busan', displayOrder: 3 },
  ],
}

describe('journeyApi', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
  })

  it('normalizes the create request at the API boundary', () => {
    expect(buildJourneyCreateRequest(input)).toEqual({
      ...input,
      title: 'Seoul Foodie Week',
      regions: [
        { regionCode: 'SEOUL', regionName: 'Seoul', displayOrder: 0 },
        { regionCode: 'BUSAN', regionName: 'Busan', displayOrder: 1 },
      ],
    })
  })

  it('posts the normalized request and orders response regions', async () => {
    post.mockResolvedValue({ data: journey })

    const result = await createJourney(input)

    expect(post).toHaveBeenCalledWith('/api/v1/journeys', {
      ...input,
      title: 'Seoul Foodie Week',
      regions: [
        { regionCode: 'SEOUL', regionName: 'Seoul', displayOrder: 0 },
        { regionCode: 'BUSAN', regionName: 'Busan', displayOrder: 1 },
      ],
    })
    expect(result.regions.map((region) => region.regionCode)).toEqual(['SEOUL', 'BUSAN'])
  })

  it('fetches detail through the shared client', async () => {
    get.mockResolvedValue({ data: journey })

    await expect(fetchJourney(7)).resolves.toMatchObject({ tripId: 7 })
    expect(get).toHaveBeenCalledWith('/api/v1/journeys/7')
  })

  it('normalizes absent response regions to an empty list', async () => {
    get.mockResolvedValue({ data: { ...journey, regions: null } })

    await expect(fetchJourney(7)).resolves.toMatchObject({ regions: [] })
  })

  it('normalizes an absent timeline collection to an empty list', async () => {
    const response = { tripId: 7, timeline: null }
    get.mockResolvedValue({ data: response })

    await expect(fetchJourneyTimeline(7)).resolves.toEqual({ tripId: 7, timeline: [] })
    expect(get).toHaveBeenCalledWith('/api/v1/journeys/7/timeline')
  })

  it('normalizes absent day items to an empty list', async () => {
    get.mockResolvedValue({
      data: { tripId: 7, timeline: [{ visitDate: '2026-08-10', items: null }] },
    })

    await expect(fetchJourneyTimeline(7)).resolves.toEqual({
      tripId: 7,
      timeline: [{ visitDate: '2026-08-10', items: [] }],
    })
  })

  it('fetches journeys available to the authenticated member', async () => {
    const data = [
      {
        tripId: 12,
        title: 'Seoul Foodie Week',
        startDate: '2026-03-28',
        endDate: '2026-04-01',
      },
    ]
    get.mockResolvedValueOnce({ data })

    await expect(fetchJourneys()).resolves.toEqual(data)
    expect(get).toHaveBeenCalledWith('/api/v1/journeys')
  })

  it('adds an explore item to the selected journey on the selected date', async () => {
    const data = {
      tripItemId: 7,
      journeyId: 12,
      itemId: 990001,
      itemType: 'EVENT',
      visitDate: '2026-08-08',
      tripItemStatus: 'ADDED',
      createdAt: '2026-08-07T18:00:00',
    } as const
    post.mockResolvedValueOnce({ data })

    await expect(addJourneyItem(12, { itemId: 990001, visitDate: '2026-08-08' })).resolves.toEqual(
      data,
    )

    expect(post).toHaveBeenCalledWith('/api/v1/journeys/12/items', {
      itemId: 990001,
      visitDate: '2026-08-08',
    })
  })

  it('propagates server errors for duplicate or invalid journey items', async () => {
    const error = new Error('JOURNEY-004')
    post.mockRejectedValueOnce(error)

    await expect(addJourneyItem(12, { itemId: 990001, visitDate: '2026-08-31' })).rejects.toBe(
      error,
    )
  })
})
