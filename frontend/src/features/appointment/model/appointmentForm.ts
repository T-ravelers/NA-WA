import type {
  AppointmentCreateRequest,
  AppointmentItemType,
  AppointmentLanguage,
} from '../api/appointmentApi'

export const MIN_APPOINTMENT_DEPOSIT = 5_000
export const MAX_APPOINTMENT_DEPOSIT = 50_000
/** 생성 폼이 처음 보여주는 보증금. 방장이 비워둔 채 넘어가지 않게 범위 안의 값으로 시작한다. */
export const DEFAULT_APPOINTMENT_DEPOSIT = 10_000
export const MAX_MEETING_PLACE_LENGTH = 200
export const MIN_APPOINTMENT_MEMBERS = 2
export const MAX_APPOINTMENT_MEMBERS = 10

export interface AppointmentFormDraft {
  itemId: number | undefined
  itemType: AppointmentItemType | undefined
  /** 약속을 확정할 여정. 여정·날짜 선택 시트를 통과해야만 폼이 렌더링되므로 항상 있다. */
  tripId: number | undefined
  /** 선택한 여정 안에서의 방문 날짜. 활동 시작·종료는 이 날짜 위에서만 조립된다. */
  visitDate: string
  appointmentName: string
  maxMembers: number
  languageCode: AppointmentLanguage
  depositAmount: number | null
  /**
   * `ITEM`이면 이 Event·Place가 열리는 자리에서 그대로 만난다. 그때 `meetingPlace`는
   * 항목 위치로 채워지고 사용자는 직접 적지 않는다. `CUSTOM`이면 아래 입력칸에 적은
   * 값이 그대로 `meetingPlace`가 된다.
   */
  meetingPlaceMode: MeetingPlaceMode
  meetingPlace: string
  /** `visitDate` 하루 안에서의 시각만(`HH:mm`). 날짜 입력은 없다. */
  activityStartTime: string
  activityEndTime: string
}

export type MeetingPlaceMode = 'ITEM' | 'CUSTOM'

export interface AppointmentFormErrors {
  itemContext?: string
  appointmentName?: string
  maxMembers?: string
  languageCode?: string
  depositAmount?: string
  meetingPlace?: string
  activityStartTime?: string
  activityEndTime?: string
}

const APPOINTMENT_LANGUAGES: readonly AppointmentLanguage[] = ['en', 'ja', 'zh-TW', 'vi']

function isAppointmentLanguage(value: string): value is AppointmentLanguage {
  return APPOINTMENT_LANGUAGES.includes(value as AppointmentLanguage)
}

function toTimeRequest(value: string): string {
  return value.length === 5 ? `${value}:00` : value
}

function todayDateString(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

function nowTimeString(): string {
  const now = new Date()
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

export function validateAppointmentBasics(draft: AppointmentFormDraft): AppointmentFormErrors {
  const errors: AppointmentFormErrors = {}

  if (
    draft.itemId === undefined ||
    !Number.isSafeInteger(draft.itemId) ||
    draft.itemId <= 0 ||
    draft.itemType === undefined
  ) {
    errors.itemContext = 'appointment.create.validation.itemContext'
  }

  const appointmentName = draft.appointmentName.trim()
  if (appointmentName === '') {
    errors.appointmentName = 'appointment.create.validation.nameRequired'
  } else if (appointmentName.length > 100) {
    errors.appointmentName = 'appointment.create.validation.nameTooLong'
  }

  if (
    !Number.isSafeInteger(draft.maxMembers) ||
    draft.maxMembers < MIN_APPOINTMENT_MEMBERS ||
    draft.maxMembers > MAX_APPOINTMENT_MEMBERS
  ) {
    errors.maxMembers = 'appointment.create.validation.membersInvalid'
  }

  if (!isAppointmentLanguage(draft.languageCode)) {
    errors.languageCode = 'appointment.create.validation.languageRequired'
  }

  // 항목 위치로 만나기를 골랐다면 그 위치를 아직 못 읽은 것이다(조회 중이거나 실패).
  // 어느 쪽이든 빈 장소로 약속을 만들 수는 없다.
  const meetingPlace = draft.meetingPlace.trim()
  if (meetingPlace === '') {
    errors.meetingPlace =
      draft.meetingPlaceMode === 'ITEM'
        ? 'appointment.create.validation.itemPlaceUnavailable'
        : 'appointment.create.validation.meetingPlaceRequired'
  } else if (meetingPlace.length > MAX_MEETING_PLACE_LENGTH) {
    // 서버도 200자에서 거절한다. 여기서 막지 않으면 필드 안내 없는 통짜 오류만 돌아온다.
    errors.meetingPlace = 'appointment.create.validation.meetingPlaceTooLong'
  }

  return errors
}

/** 보증금과 시각은 2단계에서 함께 받는다. */
export function validateAppointmentSettings(draft: AppointmentFormDraft): AppointmentFormErrors {
  const errors: AppointmentFormErrors = {
    ...validateAppointmentSchedule(draft),
  }

  if (
    draft.depositAmount === null ||
    !Number.isSafeInteger(draft.depositAmount) ||
    draft.depositAmount < MIN_APPOINTMENT_DEPOSIT ||
    draft.depositAmount > MAX_APPOINTMENT_DEPOSIT
  ) {
    errors.depositAmount = 'appointment.create.validation.depositInvalid'
  }

  return errors
}

export function validateAppointmentSchedule(draft: AppointmentFormDraft): AppointmentFormErrors {
  const errors: AppointmentFormErrors = {}

  if (draft.activityStartTime === '') {
    errors.activityStartTime = 'appointment.create.validation.startRequired'
  } else if (draft.visitDate === todayDateString() && draft.activityStartTime <= nowTimeString()) {
    errors.activityStartTime = 'appointment.create.validation.startInPast'
  }

  if (draft.activityEndTime === '') {
    errors.activityEndTime = 'appointment.create.validation.endRequired'
  } else if (draft.activityStartTime !== '' && draft.activityEndTime <= draft.activityStartTime) {
    errors.activityEndTime = 'appointment.create.validation.endAfterStart'
  }

  return errors
}

export function validateAppointmentForm(draft: AppointmentFormDraft): AppointmentFormErrors {
  return {
    ...validateAppointmentBasics(draft),
    ...validateAppointmentSettings(draft),
  }
}

export function toAppointmentCreateRequest(draft: AppointmentFormDraft): AppointmentCreateRequest {
  if (
    draft.itemId === undefined ||
    draft.itemType === undefined ||
    draft.tripId === undefined ||
    draft.depositAmount === null
  ) {
    throw new Error('Appointment context, journey and deposit are required before submission')
  }

  return {
    itemId: draft.itemId,
    itemType: draft.itemType,
    tripId: draft.tripId,
    visitDate: draft.visitDate,
    languageCode: draft.languageCode,
    appointmentName: draft.appointmentName.trim(),
    maxMembers: draft.maxMembers,
    depositAmount: String(draft.depositAmount),
    meetingPlace: draft.meetingPlace.trim(),
    activityStartTime: toTimeRequest(draft.activityStartTime),
    activityEndTime: toTimeRequest(draft.activityEndTime),
  }
}
