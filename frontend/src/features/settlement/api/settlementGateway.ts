import {
  createSettlement,
  fetchSettlementCandidates,
  fetchSettlementDetail,
  fetchSettlements,
  paySettlement,
} from './settlementApi'
import type {
  CreateSettlementRequest,
  SettlementCandidate,
  SettlementDetail,
  SettlementPaymentResult,
  SettlementSummary,
} from '../model/settlement'
import {
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
}

export const settlementGateway: SettlementGateway = apiSettlementGateway
