import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter, type Ref } from 'vue'

import { toServerCalendarDate } from '@/shared/lib/datetime'

import {
  fetchMyOngoingAppointments,
  type MyAppointmentScope,
  type MyOngoingAppointment,
} from '../api/appointmentApi'
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

export function filterAppointmentsForServerDate(
  appointments: MyOngoingAppointment[] | undefined,
  date: Date,
): MyOngoingAppointment[] | undefined {
  const calendarDate = toServerCalendarDate(date)
  return appointments?.filter(
    (appointment) => toServerCalendarDate(appointment.activityStartAt) === calendarDate,
  )
}

/**
 * 서울 날짜 기준으로 오늘 활동하는 내 약속 목록.
 *
 * 결제에 연결할 약속은 상태가 아니라 결제한 날짜가 기준이다. 서버의 `ALL` 범위를 받아
 * 취소를 제외한 내 약속 가운데 활동 시작일이 오늘인 항목만 남긴다.
 */
export function useMyTodayAppointmentsQuery(enabled: MaybeRefOrGetter<boolean>) {
  const query = useMyOngoingAppointmentsQuery(enabled, 'ALL')
  const data: Ref<MyOngoingAppointment[] | undefined> = computed(() =>
    filterAppointmentsForServerDate(query.data.value, new Date()),
  )

  return { ...query, data }
}
