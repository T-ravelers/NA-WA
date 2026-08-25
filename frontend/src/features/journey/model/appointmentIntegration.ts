import { inject, type InjectionKey, type Ref } from 'vue'

export interface JourneyAppointmentMember {
  appointmentMemberId: number
  displayName: string
  membershipStatus: 'PENDING' | 'ACTIVE' | 'LEFT'
}

export interface JourneyAppointmentMembersQuery {
  data: Ref<JourneyAppointmentMember[] | undefined>
  isPending: Ref<boolean>
  isError: Ref<boolean>
}

export interface JourneyAppointmentIntegration {
  useAppointmentMembersQuery: (
    appointmentId: Readonly<Ref<number | null>>,
  ) => JourneyAppointmentMembersQuery
}

export const journeyAppointmentIntegrationKey: InjectionKey<JourneyAppointmentIntegration> = Symbol(
  'journeyAppointmentIntegration',
)

export function useJourneyAppointmentIntegration(): JourneyAppointmentIntegration {
  const integration = inject(journeyAppointmentIntegrationKey)
  if (!integration) throw new Error('Journey appointment integration is not configured.')
  return integration
}
