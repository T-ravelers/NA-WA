export type SettlementType = 'EQUAL' | 'ITEMIZED'
export type SettlementStatus = 'REQUESTED' | 'COMPLETED'
export type SettlementRequestStatus = 'NOT_REQUESTED' | 'PENDING' | 'PAID'
export type SettlementViewerRole = 'CREATOR' | 'PARTICIPANT'

export interface Participant {
  /** appointment_member_id — never a member ID. */
  id: string
  name: string
  initials: string
}

export interface SettlementCandidate {
  transferId: string
  appointmentId: string
  payerAppointmentMemberId: string
  journeyName: string
  gatheringName: string
  merchantName: string
  amount: string
  paidAt: string
  payerName: string
  participants: Participant[]
}

export interface ItemizedAllocation {
  appointmentMemberId: string
  quantity: string
}

export interface ItemizedSettlementItem {
  name: string
  unitPrice: string
  quantity: string
  allocations: ItemizedAllocation[]
}

/**
 * 영수증에서 읽어낸 품목 초안 한 줄.
 *
 * 아직 정산 품목이 아니다. 누가 무엇을 먹었는지는 인식이 알려주지 않으므로, 사용자가
 * 배분을 정해야 비로소 정산 품목이 된다. 못 읽은 자리는 빈 문자열로 온다.
 */
export interface RecognizedReceiptItem {
  name: string
  unitPrice: string
  quantity: string
}

/**
 * 영수증 한 장에서 읽어낸 결과.
 *
 * `recognizedTotal`은 영수증에 찍힌 합계다. 할인이나 봉사료가 품목 줄 밖에 붙어서 품목을
 * 다 더한 값과 다를 수 있다. 그래서 정산 금액으로 쓰지 않고 견주어 볼 기준으로만 쓴다.
 */
export interface RecognizedReceiptDraft {
  items: RecognizedReceiptItem[]
  recognizedTotal: string | null
}

export interface CreateSettlementRequest {
  sourceTransferId: string
  type: SettlementType
  participantAppointmentMemberIds: string[]
  items?: ItemizedSettlementItem[]
  /** 미리 올려 둔 영수증 사진의 번호. 사진을 붙이지 않으면 비운다. */
  receiptId?: string
}

export interface SettlementViewer {
  role: SettlementViewerRole
  shareAmount: string
  payableAmount: string
  requestStatus: SettlementRequestStatus
  allowedActions: string[]
}

export interface SettlementSummary {
  id: string
  title: string
  totalAmount: string
  receivableAmount: string
  type: SettlementType
  status: SettlementStatus
  viewer: SettlementViewer
}

/**
 * 납부 현황에 오르는 사람 한 명.
 *
 * 돈을 낼 사람만 온다. 원결제자 본인은 자기에게 보낼 돈이 없어서 여기에 없다.
 */
export interface SettlementCollectionParticipant {
  /** appointment_member_id — never a member ID. */
  id: string
  name: string
  initials: string
  shareAmount: string
  requestStatus: 'PENDING' | 'PAID'
}

export interface SettlementCollection {
  totalCount: number
  paidCount: number
  participants: SettlementCollectionParticipant[]
}

export interface SettlementViewerItem {
  id: string
  name: string
  allocatedQuantity: string
  allocatedAmount: string
}

export interface SettlementDetail {
  id: string
  type: SettlementType
  totalAmount: string
  status: SettlementStatus
  requestedBy: string
  gatheringName: string
  merchantName: string
  viewerItems: SettlementViewerItem[]
  transactionId?: string
  paidBy?: string
  viewer: SettlementViewer
  /** 돈을 받을 원결제자에게만 온다. 낼 사람에게는 비어 있다. */
  collection?: SettlementCollection
}

export interface SettlementPaymentResult {
  settlementId: string
  settlementStatus: SettlementStatus
  transferId: string
  viewer: SettlementViewer
}
