import { httpClient } from '@/shared/api/httpClient'
import type { AppointmentStatus } from '@/shared/lib/appointmentStatus'

export const APPOINTMENT_LIST_PATH = '/api/v1/appointments'

export type AppointmentItemType = 'EVENT' | 'PLACE'
export type AppointmentLanguage = 'en' | 'ja' | 'zh-TW' | 'vi'
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
 * 목록 조회 status 필터가 받는 값. 서버 `LIST_STATUSES`와 1:1이다 — 약속 생성
 * 트랜잭션 안에서만 존재하는 `PAYMENT_PENDING`만 검색 조건이 될 수 없다(서버가
 * 400으로 거절한다). `AWAITING_ATTENDANCE`는 DB에 저장되는 값이 된 뒤로 검색할
 * 수 있다.
 */
export type AppointmentStatusFilter = Exclude<AppointmentStatus, 'PAYMENT_PENDING'>

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
 * `GET /api/v1/appointments/me`가 돌려줄 범위.
 *
 * `ONGOING`은 진행 중인 약속만 다가오는 순으로 준다(지갑 QR 결제가 쓰는 기존 계약).
 * `ALL`은 취소를 뺀 전체를 예정(임박한 순) 먼저, 지난 약속(최근 순) 나중으로 준다 —
 * 프로필의 약속 목록이 쓴다. 정렬은 서버가 하므로 화면에서 다시 세우지 않는다.
 */
export type MyAppointmentScope = 'ONGOING' | 'ALL'

/**
 * `GET /api/v1/appointments/me` 응답 항목. 백엔드 `MyOngoingAppointmentResponse`와 1:1.
 * `activityStartAt`/`activityEndAt`은 `@JsonFormat(shape = STRING)`으로 고정돼 있어
 * 다른 약속 필드와 달리 배열 형태로 오지 않는다.
 *
 * `tripId`·`itemType`은 널이 아니다. 조회가 `am.trip_id IS NOT NULL`로 거르고
 * `explore_items`를 조인해 종류를 가져온다.
 */
export interface MyOngoingAppointment {
  appointmentId: number
  appointmentName: string
  tripId: number
  meetingPlace: string | null
  activityStartAt: string
  activityEndAt: string
  itemId: number
  itemType: AppointmentItemType
  appointmentStatus: AppointmentStatus
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

/**
 * 참여도 여정을 고른다. 방문 날짜는 보내지 않는다 — 약속이 이미 활동 날짜를 갖고
 * 있어 참여자가 고를 여지가 없고, 고르는 것은 "그 날짜를 어느 여정에 넣을지"뿐이다.
 */
export async function joinAppointment(
  appointmentId: number,
  tripId: number,
): Promise<AppointmentMember> {
  const response = await httpClient.post<AppointmentMember>(
    `${APPOINTMENT_LIST_PATH}/${appointmentId}/members`,
    { tripId },
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

export async function fetchMyOngoingAppointments(
  scope: MyAppointmentScope = 'ONGOING',
): Promise<MyOngoingAppointment[]> {
  const response = await httpClient.get<MyOngoingAppointment[]>(`${APPOINTMENT_LIST_PATH}/me`, {
    params: { scope },
  })

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
