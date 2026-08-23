import { describe, expect, it, vi } from 'vitest'

import {
  fetchEventDetail,
  fetchEventList,
  fetchPlaceDetail,
  fetchPlaceList,
  toPlaceSearchParams,
  toSearchParams,
} from '../exploreApi'
import {
  eventDetailResponseSchema,
  eventListResponseSchema,
  placeDetailResponseSchema,
  placeListResponseSchema,
} from '../exploreResponseSchemas'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/shared/api/httpClient', () => ({
  httpClient: { get },
}))

describe('exploreApi', () => {
  it('serializes repeated filters as repeated query parameters', () => {
    const params = toSearchParams({
      eventKinds: ['POPUP', 'CONCERT'],
      sectorIds: [1],
      activityIds: [1, 30],
      region1: ['Seoul', 'Busan'],
      region2Other: true,
      freeOnly: true,
      page: 1,
      size: 20,
    })

    expect(params.getAll('eventKinds')).toEqual(['POPUP', 'CONCERT'])
    expect(params.getAll('sectorIds')).toEqual(['1'])
    expect(params.getAll('activityIds')).toEqual(['1', '30'])
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

  it('keeps the UI-only datePreset out of the query string', () => {
    const params = toSearchParams({
      datePreset: 'THIS_WEEKEND',
      startDate: '2026-08-22',
      endDate: '2026-08-23',
    })

    expect(params.get('datePreset')).toBeNull()
    expect(params.get('startDate')).toBe('2026-08-22')
    expect(params.get('endDate')).toBe('2026-08-23')
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

    await expect(fetchEventList({ sort: 'NEWEST' })).resolves.toEqual(data)
    expect(get).toHaveBeenCalledWith('/api/v1/explore/events', {
      params: expect.any(URLSearchParams),
      responseSchema: eventListResponseSchema,
    })
  })

  it('serializes Place filters and returns the unwrapped Place list', async () => {
    const params = toPlaceSearchParams({
      region1: ['Seoul'],
      region2: ['Seongsu', 'Hongdae'],
      region2Other: true,
      sectorIds: [2],
      activityIds: [9, 10],
      placeKinds: ['RESTAURANT', 'CAFE'],
      hasParking: true,
      takeoutAvailable: false,
      savedOnly: true,
      sort: 'POPULAR',
      page: 1,
      size: 20,
    })

    expect(params.getAll('region2')).toEqual(['Seongsu', 'Hongdae'])
    expect(params.get('region2Other')).toBe('true')
    expect(params.getAll('activityIds')).toEqual(['9', '10'])
    expect(params.getAll('placeKinds')).toEqual(['RESTAURANT', 'CAFE'])
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

    await expect(fetchPlaceList({ sort: 'NEWEST' })).resolves.toEqual({ ...data, content: [] })
    expect(get).toHaveBeenCalledWith('/api/v1/explore/places', {
      params: expect.any(URLSearchParams),
      responseSchema: placeListResponseSchema,
    })
  })

  it('fetches one event detail with the requested language', async () => {
    const data = { eventId: 42, title: 'Sample event' }
    get.mockResolvedValueOnce({ data })

    await expect(fetchEventDetail(42, 'en')).resolves.toEqual(data)
    expect(get).toHaveBeenCalledWith('/api/v1/explore/events/42', {
      params: { language: 'en' },
      responseSchema: eventDetailResponseSchema,
    })
  })

  it('asks the server to count the view only when the detail screen opens it', async () => {
    get.mockResolvedValueOnce({ data: { eventId: 42, title: 'Sample event' } })

    await fetchEventDetail(42, 'en', { countView: true })

    expect(get).toHaveBeenCalledWith('/api/v1/explore/events/42', {
      params: { language: 'en', countView: true },
      responseSchema: eventDetailResponseSchema,
    })
  })

  it('does not ask the server to count the view for a Place value read', async () => {
    get.mockResolvedValueOnce({ data: { placeId: 880001, itemId: 1, name: 'Sample' } })

    /* 약속 생성 폼처럼 위치만 읽어 가는 호출은 조회수를 올리면 안 된다. */
    await fetchPlaceDetail(880001, 'en')

    expect(get).toHaveBeenCalledWith('/api/v1/explore/places/880001', {
      params: { language: 'en' },
      responseSchema: placeDetailResponseSchema,
    })
  })

  it('fetches and normalizes one Place detail with the requested language', async () => {
    get.mockResolvedValueOnce({
      data: {
        placeId: 880001,
        itemId: 880001,
        name: 'Seongsu Onsil',
        placeKind: null,
        imageUrls: ['place.jpg', null],
        isActive: null,
        activities: null,
      },
    })

    await expect(fetchPlaceDetail(880001, 'en')).resolves.toMatchObject({
      placeId: 880001,
      itemId: 880001,
      placeKind: 'ETC',
      imageUrls: ['place.jpg'],
      isActive: false,
      activities: [],
    })
    expect(get).toHaveBeenCalledWith('/api/v1/explore/places/880001', {
      params: { language: 'en' },
      responseSchema: placeDetailResponseSchema,
    })
  })
})
