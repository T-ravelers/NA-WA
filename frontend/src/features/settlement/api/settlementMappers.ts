import type {
  SettlementCandidate,
  SettlementDetail,
  SettlementPaymentResult,
  SettlementSummary,
  SettlementViewer,
} from '../model/settlement'
import type {
  ApiAmount,
  SettlementCandidateDto,
  SettlementDetailDto,
  SettlementMutationDto,
  SettlementSummaryDto,
  SettlementViewerDto,
} from './settlementApi.types'

function amount(value: ApiAmount): string {
  return String(value)
}

function viewer(dto: SettlementViewerDto): SettlementViewer {
  return {
    role: dto.role,
    shareAmount: amount(dto.shareAmount),
    payableAmount: amount(dto.payableAmount),
    requestStatus: dto.requestStatus,
    allowedActions: dto.allowedActions,
  }
}

function formatPaidAt(value: string): string {
  const hasOffset = /(?:Z|[+-]\d{2}:?\d{2})$/.test(value)
  const parsed = new Date(hasOffset ? value : `${value}+09:00`)
  if (Number.isNaN(parsed.getTime())) return value

  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    timeZone: 'Asia/Seoul',
  }).format(parsed)
}

export function mapSettlementCandidate(dto: SettlementCandidateDto): SettlementCandidate {
  return {
    transferId: String(dto.transferId),
    appointmentId: String(dto.appointmentId),
    payerAppointmentMemberId: String(dto.payerAppointmentMemberId),
    journeyName: dto.journeyName,
    gatheringName: dto.gatheringName,
    merchantName: dto.merchantName,
    amount: amount(dto.amount),
    paidAt: formatPaidAt(dto.paidAt),
    payerName: dto.payerName,
    participants: dto.participants.map((participant) => ({
      ...participant,
      id: String(participant.id),
    })),
  }
}

export function mapSettlementSummary(dto: SettlementSummaryDto): SettlementSummary {
  return {
    id: String(dto.id),
    title: dto.title,
    totalAmount: amount(dto.totalAmount),
    receivableAmount: amount(dto.receivableAmount),
    type: dto.type,
    status: dto.status,
    viewer: viewer(dto.viewer),
  }
}

export function mapSettlementDetail(dto: SettlementDetailDto): SettlementDetail {
  return {
    id: String(dto.id),
    type: dto.type,
    totalAmount: amount(dto.totalAmount),
    status: dto.status,
    requestedBy: dto.requestedBy,
    gatheringName: dto.gatheringName,
    merchantName: dto.merchantName,
    viewerItems: dto.viewerItems.map((item) => ({
      id: String(item.settlementItemId),
      name: item.name,
      allocatedQuantity: amount(item.allocatedQuantity),
      allocatedAmount: amount(item.allocatedAmount),
    })),
    transactionId: dto.transactionId ?? undefined,
    paidBy: dto.paidBy ?? undefined,
    viewer: viewer(dto.viewer),
  }
}

export function mapSettlementPayment(dto: SettlementMutationDto): SettlementPaymentResult {
  return {
    settlementId: String(dto.settlementId),
    settlementStatus: dto.settlementStatus,
    transferId: String(dto.transferId),
    viewer: viewer(dto.viewer),
  }
}
