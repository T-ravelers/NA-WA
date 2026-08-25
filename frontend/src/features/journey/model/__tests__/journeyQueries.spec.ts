import { ref } from 'vue'
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
    expect(journeyKeys.timeline(4, 'en')).toEqual(['journeys', 'timeline', 4, 'en'])
    expect(journeyKeys.timeline(4, 'ja')).not.toEqual(journeyKeys.timeline(4, 'en'))
    expect(journeyKeys.detail(4)).not.toEqual(journeyKeys.detail(5))
  })

  it('binds the selected trip id to each query function', async () => {
    const tripId = ref<number | null>(12)
    const language = ref('en')
    vi.mocked(fetchJourney).mockResolvedValue({ tripId: 12 } as never)
    vi.mocked(fetchJourneyTimeline).mockResolvedValue({ tripId: 12, timeline: [] })

    await journeyDetailQueryOptions(tripId).queryFn()
    await journeyTimelineQueryOptions(tripId, language).queryFn()

    expect(fetchJourney).toHaveBeenCalledWith(12)
    expect(fetchJourneyTimeline).toHaveBeenCalledWith(12, 'en')
  })

  it('updates query keys and query functions when the route trip id changes', async () => {
    const tripId = ref<number | null>(7)
    const language = ref('en')
    const detailOptions = journeyDetailQueryOptions(tripId)
    const timelineOptions = journeyTimelineQueryOptions(tripId, language)

    expect(detailOptions.queryKey.value).toEqual(['journeys', 'detail', 7])
    expect(timelineOptions.queryKey.value).toEqual(['journeys', 'timeline', 7, 'en'])

    tripId.value = 8
    language.value = 'ja'
    await detailOptions.queryFn()
    await timelineOptions.queryFn()

    expect(detailOptions.queryKey.value).toEqual(['journeys', 'detail', 8])
    expect(timelineOptions.queryKey.value).toEqual(['journeys', 'timeline', 8, 'ja'])
    expect(fetchJourney).toHaveBeenCalledWith(8)
    expect(fetchJourneyTimeline).toHaveBeenCalledWith(8, 'ja')
  })
})
