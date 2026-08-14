import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchEventList } from '../api/exploreApi'
import type { EventSearchFilters } from '../model/eventExplore'
import { exploreKeys } from '../model/exploreKeys'

interface Options {
  enabled?: MaybeRefOrGetter<boolean>
}

export function useEventListQuery(
  filters: MaybeRefOrGetter<EventSearchFilters>,
  options: Options = {},
) {
  return useQuery({
    queryKey: computed(() => exploreKeys.eventList(toValue(filters))),
    queryFn: () => fetchEventList(toValue(filters)),
    enabled: computed(() => toValue(options.enabled ?? true)),
    staleTime: 30_000,
  })
}
