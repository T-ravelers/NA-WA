import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchPlaceList } from '../api/exploreApi'
import type { PlaceSearchFilters } from '../model/placeExplore'
import { exploreKeys } from '../model/exploreKeys'

export function usePlaceListQuery(filters: MaybeRefOrGetter<PlaceSearchFilters>) {
  return useQuery({
    queryKey: computed(() => exploreKeys.placeList(toValue(filters))),
    queryFn: () => fetchPlaceList(toValue(filters)),
    staleTime: 30_000,
  })
}
