import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchAppointments, type AppointmentListFilters } from '../api/appointmentApi'
import { appointmentKeys } from '../model/appointmentKeys'
import { APPOINTMENT_LIVE_REFETCH_INTERVAL_MS } from '../model/appointmentLiveRefresh'

export function useAppointmentListQuery(filters: MaybeRefOrGetter<AppointmentListFilters>) {
  return useQuery({
    queryKey: computed(() => appointmentKeys.list(toValue(filters))),
    queryFn: () => fetchAppointments(toValue(filters)),
    staleTime: 30_000,
    // 새로 등록된 약속과 카드의 상태·정원이 화면을 열어 둔 채로도 따라오게 한다.
    // refetchInterval은 staleTime과 무관하게 주기마다 다시 조회한다.
    refetchInterval: APPOINTMENT_LIVE_REFETCH_INTERVAL_MS,
  })
}
