import { fetchJourney, fetchJourneyTimeline } from '../api/journeyApi'

export const journeyKeys = {
  all: ['journeys'] as const,
  details: () => [...journeyKeys.all, 'detail'] as const,
  detail: (tripId: number) => [...journeyKeys.details(), tripId] as const,
  timelines: () => [...journeyKeys.all, 'timeline'] as const,
  timeline: (tripId: number) => [...journeyKeys.timelines(), tripId] as const,
}

export function journeyDetailQueryOptions(tripId: number) {
  return {
    queryKey: journeyKeys.detail(tripId),
    queryFn: () => fetchJourney(tripId),
  }
}

export function journeyTimelineQueryOptions(tripId: number) {
  return {
    queryKey: journeyKeys.timeline(tripId),
    queryFn: () => fetchJourneyTimeline(tripId),
  }
}
