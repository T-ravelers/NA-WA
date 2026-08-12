import type {
  AppointmentCreateRequest,
  AppointmentItemType,
  AppointmentLanguage,
} from '../api/appointmentApi'

export const MIN_APPOINTMENT_DEPOSIT = 5_000
export const MAX_APPOINTMENT_DEPOSIT = 50_000
export const MIN_APPOINTMENT_MEMBERS = 2
export const MAX_APPOINTMENT_MEMBERS = 10

export interface AppointmentFormDraft {
  itemId: number | undefined
  itemType: AppointmentItemType | undefined
  appointmentName: string
  maxMembers: number
  languageCode: AppointmentLanguage
  depositAmount: number | null
  meetingPlace: string
  meetingAddress: string
  activityStartAt: string
  activityEndAt: string
  joinDeadline: string
}

export interface AppointmentFormErrors {
  itemContext?: string
  appointmentName?: string
  maxMembers?: string
  languageCode?: string
  depositAmount?: string
  meetingPlace?: string
  activityStartAt?: string
  activityEndAt?: string
  joinDeadline?: string
}

const APPOINTMENT_LANGUAGES: readonly AppointmentLanguage[] = ['en', 'ja', 'zh-CN', 'zh-TW', 'vi']

function isAppointmentLanguage(value: string): value is AppointmentLanguage {
  return APPOINTMENT_LANGUAGES.includes(value as AppointmentLanguage)
}

function toDateTimeRequest(value: string): string {
  return value.length === 16 ? `${value}:00` : value
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

  return errors
}

export function validateAppointmentSettings(draft: AppointmentFormDraft): AppointmentFormErrors {
  const errors: AppointmentFormErrors = {}

  if (
    draft.depositAmount === null ||
    !Number.isSafeInteger(draft.depositAmount) ||
    draft.depositAmount < MIN_APPOINTMENT_DEPOSIT ||
    draft.depositAmount > MAX_APPOINTMENT_DEPOSIT
  ) {
    errors.depositAmount = 'appointment.create.validation.depositInvalid'
  }

  if (draft.meetingPlace.trim() === '') {
    errors.meetingPlace = 'appointment.create.validation.meetingPlaceRequired'
  }

  return errors
}

export function validateAppointmentSchedule(draft: AppointmentFormDraft): AppointmentFormErrors {
  const errors: AppointmentFormErrors = {}

  if (draft.activityStartAt === '') {
    errors.activityStartAt = 'appointment.create.validation.startRequired'
  }

  if (draft.activityEndAt === '') {
    errors.activityEndAt = 'appointment.create.validation.endRequired'
  } else if (draft.activityStartAt !== '' && draft.activityEndAt <= draft.activityStartAt) {
    errors.activityEndAt = 'appointment.create.validation.endAfterStart'
  }

  if (draft.joinDeadline === '') {
    errors.joinDeadline = 'appointment.create.validation.deadlineRequired'
  } else if (draft.activityStartAt !== '' && draft.joinDeadline > draft.activityStartAt) {
    errors.joinDeadline = 'appointment.create.validation.deadlineBeforeStart'
  }

  return errors
}

export function validateAppointmentForm(draft: AppointmentFormDraft): AppointmentFormErrors {
  return {
    ...validateAppointmentBasics(draft),
    ...validateAppointmentSettings(draft),
    ...validateAppointmentSchedule(draft),
  }
}

export function toAppointmentCreateRequest(draft: AppointmentFormDraft): AppointmentCreateRequest {
  if (draft.itemId === undefined || draft.itemType === undefined || draft.depositAmount === null) {
    throw new Error('Appointment context and deposit are required before submission')
  }

  return {
    itemId: draft.itemId,
    itemType: draft.itemType,
    languageCode: draft.languageCode,
    appointmentName: draft.appointmentName.trim(),
    maxMembers: draft.maxMembers,
    joinDeadline: toDateTimeRequest(draft.joinDeadline),
    depositAmount: String(draft.depositAmount),
    meetingPlace: draft.meetingPlace.trim(),
    meetingAddress: draft.meetingAddress.trim() || undefined,
    activityStartAt: toDateTimeRequest(draft.activityStartAt),
    activityEndAt: toDateTimeRequest(draft.activityEndAt),
  }
}
