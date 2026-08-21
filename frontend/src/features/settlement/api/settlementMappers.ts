import { formatServerDateTime } from '@/shared/lib/datetime'

import type {
  RecognizedReceiptDraft,
  SettlementCandidate,
  SettlementCollection,
  SettlementDetail,
  SettlementPaymentResult,
  SettlementSummary,
  SettlementViewer,
} from '../model/settlement'
import type {
  ApiAmount,
  SettlementCandidateDto,
  SettlementCollectionDto,
  SettlementDetailDto,
  SettlementMutationDto,
  SettlementReceiptOcrDto,
  SettlementSummaryDto,
  SettlementViewerDto,
} from './settlementApi.types'

function amount(value: ApiAmount): string {
  return String(value)
}

/**
 * 못 읽은 자리인지 본다.
 *
 * 서버는 지금 빈 값을 null로 보내지만 자리를 아예 빼고 보내도 뜻은 같다. 둘을 같게 다뤄야
 * 서버가 표현 방식을 바꿔도 인식 기능이 통째로 멈추지 않는다.
 */
function unread(value: ApiAmount | null | undefined): value is null | undefined {
  return value === null || value === undefined
}

/**
 * 못 읽은 자리를 빈 문자열로 바꾼다.
 *
 * 이 값은 곧바로 품목 입력란에 들어가는데, 입력란은 문자열만 다룬다. 그대로 넘기면 화면에
 * "null"이라고 찍힌다.
 */
function optionalAmount(value: ApiAmount | null | undefined): string {
  return unread(value) ? '' : amount(value)
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

/** 서버 시각 파싱은 `shared/lib/datetime.ts`의 공용 파서만 쓴다. */
function formatPaidAt(value: string): string {
  const formatted = formatServerDateTime(value, 'en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })

  return formatted === '' ? value : formatted
}

/**
 * 누가 냈는지를 볼 수 있는 사람인지 가린다.
 *
 * 서버는 볼 수 없는 사람에게 null을 보내고, 자리를 아예 빼고 보내도 뜻은 같다. 이것을 빈
 * 목록으로 바꾸면 "볼 수 없다"와 "받을 사람이 없다"가 한 모양이 되어, 화면이 아무도 없는
 * 카드를 그리게 된다.
 */
function collection(
  dto: SettlementCollectionDto | null | undefined,
): SettlementCollection | undefined {
  if (dto === null || dto === undefined) {
    return undefined
  }

  return {
    totalCount: dto.totalCount,
    paidCount: dto.paidCount,
    participants: dto.participants.map((participant) => ({
      id: String(participant.id),
      name: participant.name,
      initials: participant.initials,
      shareAmount: amount(participant.shareAmount),
      requestStatus: participant.requestStatus,
    })),
  }
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
    collection: collection(dto.collection),
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

export function mapRecognizedReceipt(dto: SettlementReceiptOcrDto): RecognizedReceiptDraft {
  return {
    items: dto.items.map((item) => ({
      name: item.name ?? '',
      unitPrice: optionalAmount(item.unitPrice),
      quantity: optionalAmount(item.quantity),
    })),
  }
}
