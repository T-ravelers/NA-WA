import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchMyOngoingAppointments } from '../api/appointmentApi'
import { appointmentKeys } from '../model/appointmentKeys'

export function useMyOngoingAppointmentsQuery(enabled: MaybeRefOrGetter<boolean>) {
  return useQuery({
    queryKey: appointmentKeys.mine(),
    queryFn: fetchMyOngoingAppointments,
    enabled: computed(() => toValue(enabled)),
    staleTime: 30_000,
  })
}
