import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchPlaceList } from '../api/exploreApi'
import type { PlaceSearchFilters } from '../model/placeExplore'
import { exploreKeys } from '../model/exploreKeys'

interface Options {
  enabled?: MaybeRefOrGetter<boolean>
}

export function usePlaceListQuery(
  filters: MaybeRefOrGetter<PlaceSearchFilters>,
  options: Options = {},
) {
  return useQuery({
    queryKey: computed(() => exploreKeys.placeList(toValue(filters))),
    queryFn: () => fetchPlaceList(toValue(filters)),
    enabled: computed(() => toValue(options.enabled ?? true)),
    staleTime: 30_000,
  })
}
