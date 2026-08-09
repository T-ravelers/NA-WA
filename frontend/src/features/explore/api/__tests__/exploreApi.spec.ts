import { describe, expect, it, vi } from 'vitest'

import {
  fetchEventDetail,
  fetchEventList,
  fetchPlaceList,
  toPlaceSearchParams,
  toSearchParams,
} from '../exploreApi'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/shared/api/httpClient', () => ({
  httpClient: { get },
}))

describe('exploreApi', () => {
  it('serializes repeated filters as repeated query parameters', () => {
    const params = toSearchParams({
      eventKinds: ['POPUP', 'CONCERT'],
      sectorIds: [1],
      activityIds: [101, 412],
      region1: ['Seoul', 'Busan'],
      region2Other: true,
      freeOnly: true,
      page: 1,
      size: 20,
    })

    expect(params.getAll('eventKinds')).toEqual(['POPUP', 'CONCERT'])
    expect(params.getAll('sectorIds')).toEqual(['1'])
    expect(params.getAll('activityIds')).toEqual(['101', '412'])
    expect(params.getAll('region1')).toEqual(['Seoul', 'Busan'])
    expect(params.get('region2Other')).toBe('true')
    expect(params.get('freeOnly')).toBe('true')
    expect(params.get('page')).toBe('1')
    expect(params.get('size')).toBe('20')
  })

  it('does not send empty keyword or unspecified filters', () => {
    const params = toSearchParams({ keyword: '', eventKinds: undefined })

    expect(params.toString()).toBe('')
  })

  it('returns the unwrapped event list from the shared client', async () => {
    const data = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    }
    get.mockResolvedValueOnce({ data })

    await expect(fetchEventList({ sort: 'LATEST' })).resolves.toEqual(data)
    expect(get).toHaveBeenCalledWith('/api/v1/explore/events', {
      params: expect.any(URLSearchParams),
    })
  })

  it('serializes Place filters and returns the unwrapped Place list', async () => {
    const params = toPlaceSearchParams({
      region1: ['Seoul'],
      region2: ['Seongsu', 'Hongdae'],
      sectorIds: [2],
      activityIds: [101, 102],
      hasParking: true,
      takeoutAvailable: false,
      savedOnly: true,
      sort: 'POPULAR',
      page: 1,
      size: 20,
    })

    expect(params.getAll('region2')).toEqual(['Seongsu', 'Hongdae'])
    expect(params.getAll('activityIds')).toEqual(['101', '102'])
    expect(params.get('hasParking')).toBe('true')
    expect(params.get('takeoutAvailable')).toBe('false')
    expect(params.get('savedOnly')).toBe('true')
    expect(params.get('sort')).toBe('POPULAR')

    const data = {
      content: null,
      page: 1,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    }
    get.mockResolvedValueOnce({ data })

    await expect(fetchPlaceList({ sort: 'LATEST' })).resolves.toEqual({ ...data, content: [] })
    expect(get).toHaveBeenCalledWith('/api/v1/explore/places', {
      params: expect.any(URLSearchParams),
    })
  })

  it('fetches one event detail with the requested language', async () => {
    const data = { eventId: 42, title: 'Sample event' }
    get.mockResolvedValueOnce({ data })

    await expect(fetchEventDetail(42, 'en')).resolves.toEqual(data)
    expect(get).toHaveBeenCalledWith('/api/v1/explore/events/42', {
      params: { language: 'en' },
    })
  })
})
