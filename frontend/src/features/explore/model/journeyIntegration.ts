import type { MaybeRefOrGetter, Ref } from 'vue'
import { inject, type InjectionKey } from 'vue'

export interface ExploreJourneySummary {
  tripId: number
  title: string
  startDate: string
  endDate: string
}

export interface ExploreJourneyListQuery {
  data: Ref<ExploreJourneySummary[] | undefined>
  isPending: Ref<boolean>
  isError: Ref<boolean>
}

export interface ExploreJourneyIntegration {
  useJourneyListQuery: (enabled: MaybeRefOrGetter<boolean>) => ExploreJourneyListQuery
  addJourneyItem: (
    journeyId: number,
    request: { itemId: number; visitDate: string },
  ) => Promise<unknown>
  parseJourneyRouteQuery: (value: unknown) => number | null
  readActiveJourneyId: () => number | null
  storeActiveJourneyId: (journeyId: number) => void
}

export const exploreJourneyIntegrationKey: InjectionKey<ExploreJourneyIntegration> = Symbol(
  'exploreJourneyIntegration',
)

export function useExploreJourneyIntegration(): ExploreJourneyIntegration {
  const integration = inject(exploreJourneyIntegrationKey)
  if (!integration) throw new Error('Explore journey integration is not configured.')
  return integration
}
