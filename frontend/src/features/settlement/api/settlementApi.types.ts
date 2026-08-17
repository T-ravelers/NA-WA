/**
 * 정산 서버 DTO 타입 정의
 */

/**
 * 서버가 보내는 금액과 수량
 */
export type ApiAmount = string | number

/**
 * 정산에 넣을 수 있는 사람
 *
 * id는 약속 참여자 id(appointmentMemberId)이다.
 */
export interface SettlementParticipantDto {
  id: string | number
  name: string
  initials: string
}

/**
 * 정산 요청서에 반영할 결제 후보
 */
export interface SettlementCandidateDto {
  transferId: string | number
  appointmentId: string | number
  payerAppointmentMemberId: string | number
  journeyName: string
  gatheringName: string
  merchantName: string
  amount: ApiAmount
  paidAt: string
  payerName: string
  participants: SettlementParticipantDto[]
}

/**
 * 정산 요청서에 대한 뷰어 정보
 */
export interface SettlementViewerDto {
  role: 'CREATOR' | 'PARTICIPANT'
  shareAmount: ApiAmount
  payableAmount: ApiAmount
  requestStatus: 'NOT_REQUESTED' | 'PENDING' | 'PAID'
  allowedActions: string[]
}

/**
 * 정산 목록의 한 줄
 *
 * 정산 전체 금액, 상태에 대해 그 줄에서의 내 몫(viewer)이 함께 온다
 */
export interface SettlementSummaryDto {
  id: string | number
  title: string
  totalAmount: ApiAmount
  receivableAmount: ApiAmount
  type: 'EQUAL' | 'ITEMIZED'
  status: 'REQUESTED' | 'COMPLETED'
  viewer: SettlementViewerDto
}

/**
 * 품목별 정산에서 배정된 항목 한 줄
 */
export interface SettlementViewerItemDto {
  settlementItemId: string | number
  name: string
  allocatedQuantity: ApiAmount
  allocatedAmount: ApiAmount
}

/**
 * 정산 상세
 */
export interface SettlementDetailDto {
  id: string | number
  type: 'EQUAL' | 'ITEMIZED'
  totalAmount: ApiAmount
  status: 'REQUESTED' | 'COMPLETED'
  requestedBy: string
  gatheringName: string
  merchantName: string
  viewerItems: SettlementViewerItemDto[]
  transactionId: string | null
  paidBy: string | null
  viewer: SettlementViewerDto
}

/**
 * 결제 요청 결과
 *
 * 결제완료 후 서버가 보내는 결제 결과
 */
export interface SettlementMutationDto {
  settlementId: string | number
  settlementStatus: 'REQUESTED' | 'COMPLETED'
  transferId: string | number
  viewer: SettlementViewerDto
}
