import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchAppointments, type AppointmentListFilters } from '../api/appointmentApi'
import { appointmentKeys } from '../model/appointmentKeys'

export function useAppointmentListQuery(filters: MaybeRefOrGetter<AppointmentListFilters>) {
  return useQuery({
    queryKey: computed(() => appointmentKeys.list(toValue(filters))),
    queryFn: () => fetchAppointments(toValue(filters)),
    staleTime: 30_000,
  })
}
