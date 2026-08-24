import { inject, type InjectionKey, type Ref } from 'vue'

/**
 * 약속을 만들 항목(Event·Place)의 위치와 운영 기간.
 *
 * 만남 장소를 "이 항목 자리에서 그대로"로 고르면 장소명이 `meetingPlace`가 되고,
 * 운영 기간은 날짜 선택 달력을 좁히는 데 쓴다. 약속 생성 화면은 `itemId`·`itemType`만
 * 알고 항목이 어디서 언제 열리는지는 모르는데, appointment feature가 explore feature를
 * 직접 import할 수 없어 journey·member와 같은 provide/inject 연동으로 받는다.
 */
export interface AppointmentItemDetail {
  /** 화면에 보여 주고 그대로 `meetingPlace`로 저장하는 장소명. */
  placeName: string | null
  /** 장소명 아래에 보조로 보여 주는 도로명 주소. 저장하지 않는다. */
  addressRoad: string | null
  /**
   * 항목 운영 기간(`YYYY-MM-DD`). EVENT에서만 채워진다.
   *
   * Place는 기간이 없어 둘 다 `null`이고, 상시 이벤트는 `endDate`만 `null`이다 —
   * 상한이 없다고 하한까지 없는 것이 아니다. 서버가 이 기간 밖 날짜를 `JOURNEY-012`로
   * 거절하므로, 달력도 같은 기준으로 좁혀야 제출한 뒤에야 실패하는 일이 없다.
   */
  startDate: string | null
  endDate: string | null
}

export interface AppointmentItemDetailQuery {
  data: Ref<AppointmentItemDetail | undefined>
  /**
   * **실제로 읽고 있는 중일 때만 참이다.**
   *
   * `isPending`은 `enabled: false`로 꺼져 있는 쿼리에서도 참이라 "읽는 중"을
   * 판단하는 데 쓸 수 없다 — 항목 정보가 없어 쿼리가 꺼진 상태를 영영 읽는 중으로
   * 보게 된다. 그래서 이 연동은 `isPending`을 노출하지 않는다.
   */
  isLoading: Ref<boolean>
  isError: Ref<boolean>
}

export interface AppointmentExploreIntegration {
  useItemDetail: (
    itemId: Ref<number | null>,
    itemType: Ref<'EVENT' | 'PLACE' | null>,
  ) => AppointmentItemDetailQuery
}

export const appointmentExploreIntegrationKey: InjectionKey<AppointmentExploreIntegration> = Symbol(
  'appointmentExploreIntegration',
)

export function useAppointmentItemDetail(
  itemId: Ref<number | null>,
  itemType: Ref<'EVENT' | 'PLACE' | null>,
): AppointmentItemDetailQuery {
  const integration = inject(appointmentExploreIntegrationKey)
  if (!integration) throw new Error('Appointment explore integration is not configured.')
  return integration.useItemDetail(itemId, itemType)
}
