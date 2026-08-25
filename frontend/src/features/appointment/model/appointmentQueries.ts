import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter, type Ref } from 'vue'

import {
  fetchAppointment,
  fetchAppointmentMembers,
  fetchMyAppointmentParticipation,
  fetchMyAppointmentReviewStatus,
} from '../api/appointmentApi'
import { appointmentKeys } from './appointmentKeys'

function requireAppointmentId(appointmentId: number | null): number {
  if (appointmentId === null) {
    throw new Error('A valid appointment id is required before fetching an appointment.')
  }

  return appointmentId
}

export function appointmentDetailQueryOptions(appointmentId: Readonly<Ref<number | null>>) {
  return {
    queryKey: computed(() => appointmentKeys.detail(appointmentId.value)),
    queryFn: () => fetchAppointment(requireAppointmentId(appointmentId.value)),
  }
}

export function appointmentMembersQueryOptions(appointmentId: Readonly<Ref<number | null>>) {
  return {
    queryKey: computed(() => appointmentKeys.members(appointmentId.value)),
    queryFn: () => fetchAppointmentMembers(requireAppointmentId(appointmentId.value)),
  }
}

/**
 * 다른 feature가 app 계층의 주입을 통해 약속 멤버를 읽을 때 쓰는 공개 composable이다.
 * Query key와 fetch 함수는 appointment가 계속 소유하고, 소비 feature는 응답 모양만 받는다.
 */
export function useAppointmentMembersQuery(appointmentId: MaybeRefOrGetter<number | null>) {
  return useQuery({
    queryKey: computed(() => appointmentKeys.members(toValue(appointmentId))),
    queryFn: () => fetchAppointmentMembers(requireAppointmentId(toValue(appointmentId))),
    enabled: computed(() => toValue(appointmentId) !== null),
    staleTime: 30_000,
  })
}

export function appointmentParticipationQueryOptions(appointmentId: Readonly<Ref<number | null>>) {
  return {
    queryKey: computed(() => appointmentKeys.participation(appointmentId.value)),
    queryFn: () => fetchMyAppointmentParticipation(requireAppointmentId(appointmentId.value)),
  }
}

export function appointmentReviewStatusQueryOptions(appointmentId: Readonly<Ref<number | null>>) {
  return {
    queryKey: computed(() => appointmentKeys.reviewStatus(appointmentId.value)),
    queryFn: () => fetchMyAppointmentReviewStatus(requireAppointmentId(appointmentId.value)),
  }
}
