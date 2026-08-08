import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchJourneys } from '../api/journeyApi'
import { journeyKeys } from '../model/journeyKeys'

export function useJourneyListQuery(enabled: MaybeRefOrGetter<boolean>) {
  return useQuery({
    queryKey: journeyKeys.list(),
    queryFn: fetchJourneys,
    enabled: computed(() => toValue(enabled)),
    staleTime: 30_000,
  })
}
