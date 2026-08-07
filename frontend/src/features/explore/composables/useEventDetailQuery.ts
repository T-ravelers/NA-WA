import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchEventDetail } from '../api/exploreApi'
import { exploreKeys } from '../model/exploreKeys'

export function useEventDetailQuery(
  eventId: MaybeRefOrGetter<number | string | undefined>,
  language: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() => exploreKeys.eventDetail(String(toValue(eventId)), toValue(language))),
    queryFn: () => fetchEventDetail(toValue(eventId) as number | string, toValue(language)),
    enabled: computed(() => {
      const value = toValue(eventId)
      return value !== undefined && String(value).trim() !== ''
    }),
    staleTime: 30_000,
  })
}
