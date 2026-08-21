import { httpClient } from '@/shared/api/httpClient'

export const APPOINTMENT_LIST_PATH = '/api/v1/appointments'

export type AppointmentItemType = 'EVENT' | 'PLACE'
export type AppointmentLanguage = 'en' | 'ja' | 'zh-TW' | 'vi'
export type AppointmentStatus =
  | 'PAYMENT_PENDING'
  | 'RECRUITING'
  | 'CLOSED'
  | 'CONFIRMED'
  | 'IN_PROGRESS'
  /** 활동이 끝났지만 방장이 아직 출석을 확정하지 않음. 서버 조회 응답에만 있는 표시 전용 값. */
  | 'AWAITING_ATTENDANCE'
  | 'COMPLETED'
  | 'CANCELLED'
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

/**
 * 목록 조회 status 필터가 받는 값. 서버 `LIST_STATUSES`와 1:1이다 — DB에
 * 저장되지 않는 표시 전용 `AWAITING_ATTENDANCE`와 트랜잭션 안에서만 존재하는
 * `PAYMENT_PENDING`은 검색 조건이 될 수 없다(서버가 400으로 거절한다).
 */
export type AppointmentStatusFilter = Exclude<
  AppointmentStatus,
  'AWAITING_ATTENDANCE' | 'PAYMENT_PENDING'
>

export interface AppointmentListFilters {
  itemId?: number
  itemType?: AppointmentItemType
  language?: AppointmentLanguage
  keyword?: string
  status?: AppointmentStatusFilter
  page?: number
  size?: number
}

export interface AppointmentCreateRequest {
  itemId: number
  itemType: AppointmentItemType
  /** 이 약속을 확정할 여정. */
  tripId: number
  /** 여정 안에서 활동이 이루어지는 방문 날짜(`yyyy-MM-dd`). */
  visitDate: string
  languageCode: AppointmentLanguage
  appointmentName: string
  maxMembers: number
  joinDeadline: string
  depositAmount: string
  meetingPlace: string
  /** `visitDate` 위에서의 시각만(`HH:mm:ss`). 날짜는 서버가 `visitDate`와 합친다. */
  activityStartTime: string
  activityEndTime: string
}

export interface AppointmentMember {
  appointmentMemberId: number
  memberId: number
  displayName: string
  profileImageUrl: string | null
  preferredLanguage: AppointmentLanguage
  membershipStatus: 'PENDING' | 'ACTIVE' | 'LEFT'
  attendanceStatus: 'PENDING' | 'ATTENDED' | 'NO_SHOW'
  isHost: boolean
}

export type AppointmentAttendanceStatus = AppointmentMember['attendanceStatus']

export interface AppointmentDetail extends AppointmentSummary {
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

export type AppointmentMembershipStatus = AppointmentMember['membershipStatus']

export interface AppointmentParticipation {
  joined: boolean
  appointmentMemberId: number | null
  membershipStatus: AppointmentMembershipStatus | null
  attendanceStatus: AppointmentAttendanceStatus | null
  host: boolean
}

/**
 * `GET /api/v1/appointments/me` 응답 항목. 백엔드 `MyOngoingAppointmentResponse`와 1:1.
 * `activityStartAt`/`activityEndAt`은 `@JsonFormat(shape = STRING)`으로 고정돼 있어
 * 다른 약속 필드와 달리 배열 형태로 오지 않는다.
 */
export interface MyOngoingAppointment {
  appointmentId: number
  appointmentName: string
  tripId: number
  meetingPlace: string | null
  activityStartAt: string
  activityEndAt: string
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

export async function joinAppointment(appointmentId: number): Promise<AppointmentMember> {
  const response = await httpClient.post<AppointmentMember>(
    `${APPOINTMENT_LIST_PATH}/${appointmentId}/members`,
  )

  return response.data
}

export async function fetchMyAppointmentParticipation(
  appointmentId: number,
): Promise<AppointmentParticipation> {
  const response = await httpClient.get<AppointmentParticipation>(
    `${APPOINTMENT_LIST_PATH}/${appointmentId}/members/me`,
  )

  return response.data
}

export async function cancelAppointmentParticipation(appointmentId: number): Promise<void> {
  await httpClient.delete(`${APPOINTMENT_LIST_PATH}/${appointmentId}/members/me`)
}

export async function fetchMyOngoingAppointments(): Promise<MyOngoingAppointment[]> {
  const response = await httpClient.get<MyOngoingAppointment[]>(`${APPOINTMENT_LIST_PATH}/me`)

  return response.data ?? []
}

export interface AppointmentAttendanceRequest {
  members: Array<{ memberId: number; attendanceStatus: 'ATTENDED' | 'NO_SHOW' }>
}

export async function confirmAppointmentAttendance(
  appointmentId: number,
  request: AppointmentAttendanceRequest,
): Promise<void> {
  await httpClient.patch(`${APPOINTMENT_LIST_PATH}/${appointmentId}/attendance`, request)
}

/**
 * 로그인 회원이 이 약속에서 이미 후기를 쓴 대상의 `appointmentMemberId` 목록.
 *
 * 백엔드 `MyReviewStatusResponse`와 1:1이다. 점수·키워드는 담기지 않는다 —
 * 화면은 "누구에게 이미 썼는지"만 알면 되고, 저장된 후기를 고치는 기능이 없다.
 */
export interface MyAppointmentReviewStatus {
  reviewedAppointmentMemberIds: number[]
}

export async function fetchMyAppointmentReviewStatus(
  appointmentId: number,
): Promise<MyAppointmentReviewStatus> {
  const response = await httpClient.get<MyAppointmentReviewStatus>(
    `${APPOINTMENT_LIST_PATH}/${appointmentId}/reviews/me`,
  )

  return response.data ?? { reviewedAppointmentMemberIds: [] }
}

export async function submitAppointmentReview(
  appointmentId: number,
  request: AppointmentReviewRequest,
): Promise<void> {
  await httpClient.post(`${APPOINTMENT_LIST_PATH}/${appointmentId}/reviews`, request)
}
