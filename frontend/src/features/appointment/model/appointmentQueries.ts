import { computed, type Ref } from 'vue'

import {
  fetchAppointment,
  fetchAppointmentMembers,
  fetchMyAppointmentParticipation,
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

export function appointmentParticipationQueryOptions(appointmentId: Readonly<Ref<number | null>>) {
  return {
    queryKey: computed(() => appointmentKeys.participation(appointmentId.value)),
    queryFn: () => fetchMyAppointmentParticipation(requireAppointmentId(appointmentId.value)),
  }
}
