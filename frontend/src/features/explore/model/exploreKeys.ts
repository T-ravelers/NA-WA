import type { EventSearchFilters } from './eventExplore'

export const exploreKeys = {
  all: ['explore'] as const,
  events: () => [...exploreKeys.all, 'events'] as const,
  eventList: (filters: EventSearchFilters) => [...exploreKeys.events(), 'list', filters] as const,
  eventDetail: (eventId: number | string, language: string) =>
    [...exploreKeys.events(), 'detail', eventId, language] as const,
} as const
