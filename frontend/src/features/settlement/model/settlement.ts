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
 * 품목 줄만 담는다. 서버는 영수증에 찍힌 합계도 함께 내려주지만 화면은 쓰지 않는다.
 * 인식이 하는 일은 품목 카드를 대신 채워 주는 것 하나이고, 무엇이 맞는 값인지는 사용자가
 * 그 카드에서 정한다.
 */
export interface RecognizedReceiptDraft {
  items: RecognizedReceiptItem[]
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
  /** 정산을 만든 시각. 완료 시각이 없는 예전 정산은 이 값으로 대신 보여준다. */
  createdAt: string
  /** 정산이 끝난 시각. 진행 중이거나 서버가 이 값을 남기기 전에 끝난 정산은 비어 있다. */
  completedAt: string
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
