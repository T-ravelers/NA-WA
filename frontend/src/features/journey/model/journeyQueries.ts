import { computed, type Ref } from 'vue'

import { fetchJourney, fetchJourneyTimeline } from '../api/journeyApi'

export const journeyKeys = {
  all: ['journeys'] as const,
  details: () => [...journeyKeys.all, 'detail'] as const,
  detail: (tripId: number | null) => [...journeyKeys.details(), tripId] as const,
  timelines: () => [...journeyKeys.all, 'timeline'] as const,
  timeline: (tripId: number | null, language: string) =>
    [...journeyKeys.timelines(), tripId, language] as const,
}

function requireTripId(tripId: number | null): number {
  if (tripId === null) {
    throw new Error('A valid trip id is required before fetching a journey.')
  }

  return tripId
}

export function journeyDetailQueryOptions(tripId: Readonly<Ref<number | null>>) {
  return {
    queryKey: computed(() => journeyKeys.detail(tripId.value)),
    queryFn: () => fetchJourney(requireTripId(tripId.value)),
  }
}

export function journeyTimelineQueryOptions(
  tripId: Readonly<Ref<number | null>>,
  language: Readonly<Ref<string>>,
) {
  return {
    queryKey: computed(() => journeyKeys.timeline(tripId.value, language.value)),
    queryFn: () => fetchJourneyTimeline(requireTripId(tripId.value), language.value),
  }
}
