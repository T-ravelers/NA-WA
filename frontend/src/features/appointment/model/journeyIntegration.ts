import type { MaybeRefOrGetter, Ref } from 'vue'
import { inject, type InjectionKey } from 'vue'

export interface AppointmentJourneySummary {
  tripId: number
  title: string
  startDate: string
  endDate: string
}

export interface AppointmentJourneyListQuery {
  data: Ref<AppointmentJourneySummary[] | undefined>
  isPending: Ref<boolean>
  isError: Ref<boolean>
}

export interface AppointmentJourneyIntegration {
  useJourneyListQuery: (enabled: MaybeRefOrGetter<boolean>) => AppointmentJourneyListQuery
  checkJourneyItemExists: (tripId: number, itemId: number, visitDate: string) => Promise<boolean>
}

export const appointmentJourneyIntegrationKey: InjectionKey<AppointmentJourneyIntegration> = Symbol(
  'appointmentJourneyIntegration',
)

export function useAppointmentJourneyIntegration(): AppointmentJourneyIntegration {
  const integration = inject(appointmentJourneyIntegrationKey)
  if (!integration) throw new Error('Appointment journey integration is not configured.')
  return integration
}
