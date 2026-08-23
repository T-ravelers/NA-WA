import { inject, type InjectionKey, type Ref } from 'vue'

/**
 * 프로필의 약속 탭이 그리는 항목.
 *
 * 정렬은 서버가 예정(임박한 순) → 지난(최근 순)으로 이미 세워서 준다. 화면은 종류로
 * 거르기만 하고 순서를 다시 만들지 않는다.
 */
export interface MyAppointmentItem {
  appointmentId: number
  appointmentName: string
  itemType: 'EVENT' | 'PLACE'
  meetingPlace: string | null
  /** 서버가 `@JsonFormat(shape = STRING)`으로 고정한 값이라 문자열로 온다. */
  activityStartAt: string
}

export interface MyAppointmentsQuery {
  data: Ref<MyAppointmentItem[] | undefined>
  isPending: Ref<boolean>
  isError: Ref<boolean>
  refetch: () => void
}

export interface MemberAppointmentIntegration {
  useMyAppointments: (enabled: Ref<boolean>) => MyAppointmentsQuery
}

export const memberAppointmentIntegrationKey: InjectionKey<MemberAppointmentIntegration> = Symbol(
  'memberAppointmentIntegration',
)

export function useMyAppointments(enabled: Ref<boolean>): MyAppointmentsQuery {
  const integration = inject(memberAppointmentIntegrationKey)
  if (!integration) throw new Error('Member appointment integration is not configured.')
  return integration.useMyAppointments(enabled)
}
