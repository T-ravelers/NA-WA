import { httpClient } from '@/shared/api/httpClient'

export const APPOINTMENT_LIST_PATH = '/api/v1/appointments'

export type AppointmentItemType = 'EVENT' | 'PLACE'
export type AppointmentLanguage = 'en' | 'ja' | 'zh-CN' | 'zh-TW' | 'vi'
export type AppointmentStatus =
  'RECRUITING' | 'CLOSED' | 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
/** Jackson LocalDateTime may be serialized as an ISO string or numeric components. */
export type AppointmentDateTimeValue = string | readonly number[] | null

export interface AppointmentSummary {
  appointmentId: number
  itemId: number
  itemType: AppointmentItemType
  appointmentName: string
  languageCode: AppointmentLanguage
  maxMembers: number
  currentMemberCount: number
  depositAmount: string
  appointmentStatus: AppointmentStatus
  meetingPlace: string | null
  activityStartAt: AppointmentDateTimeValue
  activityEndAt: AppointmentDateTimeValue
  joinDeadline: AppointmentDateTimeValue
  hostDisplayName: string | null
}

export interface AppointmentListResponse {
  content: AppointmentSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export interface AppointmentListFilters {
  itemId?: number
  itemType?: AppointmentItemType
  language?: AppointmentLanguage
  keyword?: string
  status?: AppointmentStatus
  page?: number
  size?: number
}

export interface AppointmentCreateRequest {
  itemId: number
  itemType: AppointmentItemType
  languageCode: AppointmentLanguage
  appointmentName: string
  maxMembers: number
  joinDeadline: string
  depositAmount: string
  meetingPlace: string
  meetingAddress?: string
  activityStartAt: string
  activityEndAt: string
}

export interface AppointmentMember {
  appointmentMemberId: number
  memberId: number
  displayName: string
  profileImageUrl: string | null
  preferredLanguage: AppointmentLanguage
  membershipStatus: 'ACTIVE' | 'LEFT'
  attendanceStatus: 'PENDING' | 'ATTENDED' | 'NO_SHOW'
  isHost: boolean
}

export type AppointmentAttendanceStatus = AppointmentMember['attendanceStatus']

export interface AppointmentDetail extends AppointmentSummary {
  meetingAddress: string | null
  description: string | null
  members: AppointmentMember[]
}

export type ReviewCategory = 'PUNCTUALITY' | 'MANNERS' | 'COMMUNICATION'
export type ReviewKeywordCode =
  'FRIENDLY' | 'ON_TIME' | 'CONSIDERATE' | 'GOOD_COMMUNICATOR' | 'WOULD_JOIN_AGAIN'

export interface AppointmentReviewRequest {
  reviewedAppointmentMemberId: number
  scores: Record<ReviewCategory, number>
  keywordCodes: ReviewKeywordCode[]
}

function normalizePageResponse(response: AppointmentListResponse): AppointmentListResponse {
  return {
    ...response,
    content: response.content ?? [],
  }
}

export async function fetchAppointments(
  filters: AppointmentListFilters = {},
): Promise<AppointmentListResponse> {
  const response = await httpClient.get<AppointmentListResponse>(APPOINTMENT_LIST_PATH, {
    params: filters,
  })

  return normalizePageResponse(response.data)
}

export async function fetchAppointment(appointmentId: number): Promise<AppointmentDetail> {
  const response = await httpClient.get<AppointmentDetail>(
    `${APPOINTMENT_LIST_PATH}/${appointmentId}`,
  )

  return {
    ...response.data,
    members: response.data.members ?? [],
  }
}

export async function createAppointment(
  request: AppointmentCreateRequest,
): Promise<AppointmentDetail> {
  const response = await httpClient.post<AppointmentDetail>(APPOINTMENT_LIST_PATH, request)

  return response.data
}

export async function fetchAppointmentMembers(appointmentId: number): Promise<AppointmentMember[]> {
  const response = await httpClient.get<AppointmentMember[]>(
    `${APPOINTMENT_LIST_PATH}/${appointmentId}/members`,
  )

  return response.data ?? []
}

export async function submitAppointmentReview(
  appointmentId: number,
  request: AppointmentReviewRequest,
): Promise<void> {
  await httpClient.post(`${APPOINTMENT_LIST_PATH}/${appointmentId}/reviews`, request)
}
