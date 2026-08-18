/**
 * 정산 백엔드 호출
 *
 * 백엔드 'SettlementController'의 엔드포인트와 1:1로 대응한다
 * 여기서는 경로와 헤더만 다룬다
 * 서버 DTO를 화면 모델로 바꾸는 일은 'settlementMapper'에서 한다
 * 두 계층을 잇는 일은 'settlementGateway'가 맡는다
 * httpClient가 ApiResponse를 처리함으로, 각 함수는 data만 반환한다
 */

import { httpClient } from '@/shared/api/httpClient'

import type { CreateSettlementRequest } from '../model/settlement'
import type {
  SettlementCandidateDto,
  SettlementDetailDto,
  SettlementMutationDto,
  SettlementSummaryDto,
} from './settlementApi.types'

/**
 * 정산 후보 조회
 *
 * 정산 생성 화면에 들어갈 때 쓰는 목록이다
 * 원결제자, 완료여부, 정산 안된 거래를 서버가 골라줌으로
 * 프론트에서 다시 필터링하지 않는다
 */
export async function fetchSettlementCandidates(): Promise<SettlementCandidateDto[]> {
  const { data } = await httpClient.get<SettlementCandidateDto[]>('/api/v1/settlements/candidates')
  return data
}

/**
 * 정산 목록 조회
 *
 * 받은 요청과 보낸 요청을 모두 가져온다
 * 정렬도 서버 몫이라 받은 순서를 그대로 유지한다
 */
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

/**
 * 정산 상세 조회
 *
 * 목록에서 정산 하나 선택해 들어가면 상세, 결제, 결제 완료 화면이 모두 이 엔드포인트를 쓴다
 * 'viewer'는 내 역할과 내 금액과 내가 할 수 있는 동작을 의미한다
 * 'viewrItems'는 항목별 정산에서 내게 배정된 항목이다
 *
 *
 */
export async function fetchSettlementDetail(settlementId: string): Promise<SettlementDetailDto> {
  const { data } = await httpClient.get<SettlementDetailDto>(`/api/v1/settlements/${settlementId}`)
  return data
}

/**
 * 정산 생성
 *
 * 'Idempotency-Key' 헤더로 중복 요청을 방지한다
 * 키 생성과 보관은 'settlementIdempotencyKey'가 맡는다
 */
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

/**
 * 정산 결제
 *
 * 본문 없이 'Idempotency-Key' 헤더만 보낸다
 * 응답에는 결제 결과(결제 성공/실패 여부, 실패 사유 등)가 담긴다
 */
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
