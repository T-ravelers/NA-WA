import { describe, expect, it, vi } from 'vitest'

import { fetchJourney, fetchJourneyTimeline } from '../../api/journeyApi'
import {
  journeyDetailQueryOptions,
  journeyKeys,
  journeyTimelineQueryOptions,
} from '../journeyQueries'

vi.mock('../../api/journeyApi', () => ({
  fetchJourney: vi.fn(),
  fetchJourneyTimeline: vi.fn(),
}))

describe('journeyQueries', () => {
  it('keeps detail and timeline caches separate per journey', () => {
    expect(journeyKeys.detail(4)).toEqual(['journeys', 'detail', 4])
    expect(journeyKeys.timeline(4)).toEqual(['journeys', 'timeline', 4])
    expect(journeyKeys.detail(4)).not.toEqual(journeyKeys.detail(5))
  })

  it('binds the selected trip id to each query function', async () => {
    vi.mocked(fetchJourney).mockResolvedValue({ tripId: 12 } as never)
    vi.mocked(fetchJourneyTimeline).mockResolvedValue({ tripId: 12, timeline: [] })

    await journeyDetailQueryOptions(12).queryFn()
    await journeyTimelineQueryOptions(12).queryFn()

    expect(fetchJourney).toHaveBeenCalledWith(12)
    expect(fetchJourneyTimeline).toHaveBeenCalledWith(12)
  })
})
