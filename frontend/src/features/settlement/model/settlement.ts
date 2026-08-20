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
}

export interface SettlementPaymentResult {
  settlementId: string
  settlementStatus: SettlementStatus
  transferId: string
  viewer: SettlementViewer
}
