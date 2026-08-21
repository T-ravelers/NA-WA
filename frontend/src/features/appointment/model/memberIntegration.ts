import { inject, type InjectionKey, type Ref } from 'vue'

export interface AppointmentMemberStats {
  completionRate: number | null
  noShowCount: number
  averageRating: number | null
  reviewCount: number
}

export interface AppointmentMemberIntegration {
  useMemberStats: (memberId: Ref<number | null>) => {
    data: Ref<AppointmentMemberStats | undefined>
    isPending: Ref<boolean>
    isError: Ref<boolean>
  }
}

export const appointmentMemberIntegrationKey: InjectionKey<AppointmentMemberIntegration> = Symbol(
  'appointmentMemberIntegration',
)

export function useAppointmentMemberStats(memberId: Ref<number | null>) {
  const integration = inject(appointmentMemberIntegrationKey)
  if (!integration) throw new Error('Appointment member integration is not configured.')
  return integration.useMemberStats(memberId)
}
