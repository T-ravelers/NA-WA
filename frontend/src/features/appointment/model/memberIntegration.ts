import { inject, type InjectionKey, type Ref } from 'vue'

export interface AppointmentMemberProfile {
  memberId: number
}

export interface AppointmentMemberProfileQuery {
  data: Ref<AppointmentMemberProfile | undefined>
  isPending: Ref<boolean>
  isError: Ref<boolean>
  refetch: () => Promise<unknown>
}

export interface AppointmentMemberIntegration {
  useMemberProfile: () => AppointmentMemberProfileQuery
}

export const appointmentMemberIntegrationKey: InjectionKey<AppointmentMemberIntegration> = Symbol(
  'appointmentMemberIntegration',
)

export function useAppointmentMemberProfile(): AppointmentMemberProfileQuery {
  const integration = inject(appointmentMemberIntegrationKey)
  if (!integration) throw new Error('Appointment member integration is not configured.')
  return integration.useMemberProfile()
}
