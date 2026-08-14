import { useQuery } from '@tanstack/vue-query'

import { fetchMyOngoingAppointments } from '../api/appointmentApi'
import { appointmentKeys } from '../model/appointmentKeys'

export function useMyOngoingAppointmentsQuery() {
  return useQuery({
    queryKey: appointmentKeys.mine(),
    queryFn: fetchMyOngoingAppointments,
    staleTime: 30_000,
  })
}
