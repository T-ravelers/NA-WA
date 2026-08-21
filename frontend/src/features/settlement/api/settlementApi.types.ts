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
 * 영수증에서 읽어낸 품목 한 줄
 *
 * 값이 전부 null일 수 있다. 영수증이 접혔거나 흐리면 이름만 읽히고 금액은 안 읽히기도
 * 한다. 그런 줄을 버리지 않고 그대로 받아야 사용자가 빈 칸만 채울 수 있다.
 */
export interface SettlementReceiptOcrItemDto {
  name: string | null
  unitPrice: ApiAmount | null
  quantity: ApiAmount | null
}

/**
 * 영수증 글자 인식 결과
 *
 * 'recognizedTotal'은 영수증에 찍힌 합계다. 서버가 내려주므로 계약에는 남겨 두지만
 * **화면은 쓰지 않는다.** 사진을 반듯하게 찍지 않으면 합계부터 틀리게 읽히는데, 그 값으로
 * "결제 금액과 다릅니다"라고 알리면 사용자가 고칠 수도 없는 숫자를 근거로 겁을 주게 된다.
 */
export interface SettlementReceiptOcrDto {
  items: SettlementReceiptOcrItemDto[]
  recognizedTotal: ApiAmount | null
}

/**
 * 납부 현황의 참여자 한 줄
 *
 * 'id'는 회원 번호가 아니라 약속 참가 행 번호다. 정산은 이 값으로 사람을 가린다.
 */
export interface SettlementCollectionParticipantDto {
  id: string | number
  name: string
  initials: string
  shareAmount: ApiAmount
  requestStatus: 'PENDING' | 'PAID'
}

/**
 * 누가 냈는지의 현황
 *
 * 'totalCount'에 원결제자 본인은 들어 있지 않다. 자기 자신에게 보낼 돈이 없어서 세면
 * 전원이 다 내도 숫자가 끝까지 차지 않는다
 */
export interface SettlementCollectionDto {
  totalCount: number
  paidCount: number
  participants: SettlementCollectionParticipantDto[]
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
  /** 돈을 받을 원결제자에게만 온다. 그 밖에는 null이다. */
  collection: SettlementCollectionDto | null
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
