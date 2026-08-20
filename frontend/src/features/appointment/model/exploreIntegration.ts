import { inject, type InjectionKey, type Ref } from 'vue'

/**
 * 약속을 만들 항목(Event·Place)의 위치.
 *
 * 만남 장소를 "이 항목 자리에서 그대로"로 고르면 이 값이 `meetingPlace`가 된다.
 * 약속 생성 폼은 `itemId`·`itemType`만 알고 항목이 어디서 열리는지는 모르는데,
 * appointment feature가 explore feature를 직접 import할 수 없어 journey·member와
 * 같은 provide/inject 연동으로 받는다.
 */
export interface AppointmentItemLocation {
  /** 화면에 보여 주고 그대로 `meetingPlace`로 저장하는 장소명. */
  placeName: string | null
  /** 장소명 아래에 보조로 보여 주는 도로명 주소. 저장하지 않는다. */
  addressRoad: string | null
}

export interface AppointmentItemLocationQuery {
  data: Ref<AppointmentItemLocation | undefined>
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
  useItemLocation: (
    itemId: Ref<number | null>,
    itemType: Ref<'EVENT' | 'PLACE' | null>,
  ) => AppointmentItemLocationQuery
}

export const appointmentExploreIntegrationKey: InjectionKey<AppointmentExploreIntegration> = Symbol(
  'appointmentExploreIntegration',
)

export function useAppointmentItemLocation(
  itemId: Ref<number | null>,
  itemType: Ref<'EVENT' | 'PLACE' | null>,
): AppointmentItemLocationQuery {
  const integration = inject(appointmentExploreIntegrationKey)
  if (!integration) throw new Error('Appointment explore integration is not configured.')
  return integration.useItemLocation(itemId, itemType)
}
