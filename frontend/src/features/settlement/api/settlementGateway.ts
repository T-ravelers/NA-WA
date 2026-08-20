import {
  createSettlement,
  fetchSettlementCandidates,
  fetchSettlementDetail,
  fetchSettlementReceipt,
  fetchSettlements,
  paySettlement,
  recognizeSettlementReceipt,
  uploadSettlementReceipt,
} from './settlementApi'
import type {
  CreateSettlementRequest,
  RecognizedReceiptDraft,
  SettlementCandidate,
  SettlementDetail,
  SettlementPaymentResult,
  SettlementSummary,
} from '../model/settlement'
import {
  mapRecognizedReceipt,
  mapSettlementCandidate,
  mapSettlementDetail,
  mapSettlementPayment,
  mapSettlementSummary,
} from './settlementMappers'

export interface SettlementGateway {
  getCandidates(): Promise<SettlementCandidate[]>
  getSettlements(): Promise<{ received: SettlementSummary[]; sent: SettlementSummary[] }>
  getDetail(settlementId: string): Promise<SettlementDetail>
  create(
    appointmentId: string,
    idempotencyKey: string,
    request: CreateSettlementRequest,
  ): Promise<{ id: string }>
  pay(settlementId: string, idempotencyKey: string): Promise<SettlementPaymentResult>
  uploadReceipt(file: File): Promise<{ receiptId: string }>
  recognizeReceipt(receiptId: string): Promise<RecognizedReceiptDraft>
  getReceipt(settlementId: string): Promise<Blob>
}

export const apiSettlementGateway: SettlementGateway = {
  getCandidates: async () => (await fetchSettlementCandidates()).map(mapSettlementCandidate),
  getSettlements: async () => {
    const response = await fetchSettlements()
    return {
      received: response.received.map(mapSettlementSummary),
      sent: response.sent.map(mapSettlementSummary),
    }
  },
  getDetail: async (settlementId) => mapSettlementDetail(await fetchSettlementDetail(settlementId)),
  create: createSettlement,
  pay: async (settlementId, idempotencyKey) =>
    mapSettlementPayment(await paySettlement(settlementId, idempotencyKey)),
  uploadReceipt: uploadSettlementReceipt,
  recognizeReceipt: async (receiptId) =>
    mapRecognizedReceipt(await recognizeSettlementReceipt(receiptId)),
  getReceipt: fetchSettlementReceipt,
}

export const settlementGateway: SettlementGateway = apiSettlementGateway
