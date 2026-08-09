import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchPlaceDetail } from '../api/exploreApi'
import { exploreKeys } from '../model/exploreKeys'

export function usePlaceDetailQuery(
  placeId: MaybeRefOrGetter<number | string | undefined>,
  language: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() => exploreKeys.placeDetail(String(toValue(placeId)), toValue(language))),
    queryFn: () => fetchPlaceDetail(toValue(placeId) as number | string, toValue(language)),
    enabled: computed(() => {
      const value = toValue(placeId)
      return value !== undefined && String(value).trim() !== ''
    }),
    staleTime: 30_000,
  })
}
