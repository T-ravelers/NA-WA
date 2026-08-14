import { httpClient } from '@/shared/api/httpClient'

import type { CreateSettlementRequest } from '../model/settlement'
import type {
  SettlementCandidateDto,
  SettlementDetailDto,
  SettlementMutationDto,
  SettlementSummaryDto,
} from './settlementApi.types'

export async function fetchSettlementCandidates(): Promise<SettlementCandidateDto[]> {
  const { data } = await httpClient.get<SettlementCandidateDto[]>('/api/v1/settlements/candidates')
  return data
}

export async function fetchSettlements(): Promise<{
  received: SettlementSummaryDto[]
  sent: SettlementSummaryDto[]
}> {
  const { data } = await httpClient.get<{
    received: SettlementSummaryDto[]
    sent: SettlementSummaryDto[]
  }>('/api/v1/settlements')
  return data
}

export async function fetchSettlementDetail(settlementId: string): Promise<SettlementDetailDto> {
  const { data } = await httpClient.get<SettlementDetailDto>(`/api/v1/settlements/${settlementId}`)
  return data
}

export async function createSettlement(
  appointmentId: string,
  idempotencyKey: string,
  request: CreateSettlementRequest,
): Promise<{ id: string }> {
  const { data } = await httpClient.post<{ id: string | number }>(
    `/api/v1/appointments/${appointmentId}/settlements`,
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return { id: String(data.id) }
}

export async function paySettlement(
  settlementId: string,
  idempotencyKey: string,
): Promise<SettlementMutationDto> {
  const { data } = await httpClient.post<SettlementMutationDto>(
    `/api/v1/settlements/${settlementId}/members/me/pay`,
    undefined,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return data
}
