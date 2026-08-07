import { describe, expect, it, vi } from 'vitest'

import { fetchEventList, toSearchParams } from '../exploreApi'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/shared/api/httpClient', () => ({
  httpClient: { get },
}))

describe('exploreApi', () => {
  it('serializes repeated filters as repeated query parameters', () => {
    const params = toSearchParams({
      eventKinds: ['POPUP', 'CONCERT'],
      region1: ['Seoul', 'Busan'],
      freeOnly: true,
      page: 1,
      size: 20,
    })

    expect(params.getAll('eventKinds')).toEqual(['POPUP', 'CONCERT'])
    expect(params.getAll('region1')).toEqual(['Seoul', 'Busan'])
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
})
