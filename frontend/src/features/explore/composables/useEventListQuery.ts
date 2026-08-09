import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchEventList } from '../api/exploreApi'
import type { EventSearchFilters } from '../model/eventExplore'
import { exploreKeys } from '../model/exploreKeys'

export function useEventListQuery(filters: MaybeRefOrGetter<EventSearchFilters>) {
  return useQuery({
    queryKey: computed(() => exploreKeys.eventList(toValue(filters))),
    queryFn: () => fetchEventList(toValue(filters)),
    staleTime: 30_000,
  })
}
