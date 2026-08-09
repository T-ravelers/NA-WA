import type { EventSearchFilters } from './eventExplore'
import type { PlaceSearchFilters } from './placeExplore'

export const exploreKeys = {
  all: ['explore'] as const,
  events: () => [...exploreKeys.all, 'events'] as const,
  eventList: (filters: EventSearchFilters) => [...exploreKeys.events(), 'list', filters] as const,
  eventDetail: (eventId: number | string, language: string) =>
    [...exploreKeys.events(), 'detail', eventId, language] as const,
  places: () => [...exploreKeys.all, 'places'] as const,
  placeList: (filters: PlaceSearchFilters) => [...exploreKeys.places(), 'list', filters] as const,
} as const
