export type ApiAmount = string | number

export interface SettlementParticipantDto {
  id: string | number
  name: string
  initials: string
}

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

export interface SettlementViewerDto {
  role: 'CREATOR' | 'PARTICIPANT'
  shareAmount: ApiAmount
  payableAmount: ApiAmount
  requestStatus: 'NOT_REQUESTED' | 'PENDING' | 'PAID'
  allowedActions: string[]
}

export interface SettlementSummaryDto {
  id: string | number
  title: string
  totalAmount: ApiAmount
  receivableAmount: ApiAmount
  type: 'EQUAL' | 'ITEMIZED'
  status: 'REQUESTED' | 'COMPLETED'
  viewer: SettlementViewerDto
}

export interface SettlementViewerItemDto {
  settlementItemId: string | number
  name: string
  allocatedQuantity: ApiAmount
  allocatedAmount: ApiAmount
}

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

export interface SettlementMutationDto {
  settlementId: string | number
  settlementStatus: 'REQUESTED' | 'COMPLETED'
  transferId: string | number
  viewer: SettlementViewerDto
}
