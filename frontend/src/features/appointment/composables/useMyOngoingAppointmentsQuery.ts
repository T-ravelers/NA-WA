import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { fetchMyOngoingAppointments, type MyAppointmentScope } from '../api/appointmentApi'
import { appointmentKeys } from '../model/appointmentKeys'

/**
 * 내 약속 목록.
 *
 * 기본 범위는 진행 중(`ONGOING`)이라 지갑 QR 결제의 기존 호출은 그대로다.
 * 프로필의 약속 탭은 `ALL`을 넘겨 지난 약속까지 받는다. 두 범위는 캐시가 서로 다르지만
 * `appointmentKeys.mine()` 하나로 함께 무효화된다.
 */
export function useMyOngoingAppointmentsQuery(
  enabled: MaybeRefOrGetter<boolean>,
  scope: MyAppointmentScope = 'ONGOING',
) {
  return useQuery({
    queryKey: appointmentKeys.myScope(scope),
    queryFn: () => fetchMyOngoingAppointments(scope),
    enabled: computed(() => toValue(enabled)),
    staleTime: 30_000,
  })
}
