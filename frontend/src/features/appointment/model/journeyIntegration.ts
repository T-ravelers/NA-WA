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
  /**
   * 그 날짜의 그 자리를 **다른 약속이** 이미 차지했는지. 담아만 둔 자리는 약속
   * 항목으로 승격되므로 참이 아니다 — "여정에 있는지"로 물으면 담아 둔 장소로는
   * 약속을 만들 수 없게 된다.
   */
  checkAppointmentSlotTaken: (tripId: number, itemId: number, visitDate: string) => Promise<boolean>
}

export const appointmentJourneyIntegrationKey: InjectionKey<AppointmentJourneyIntegration> = Symbol(
  'appointmentJourneyIntegration',
)

export function useAppointmentJourneyIntegration(): AppointmentJourneyIntegration {
  const integration = inject(appointmentJourneyIntegrationKey)
  if (!integration) throw new Error('Appointment journey integration is not configured.')
  return integration
}
