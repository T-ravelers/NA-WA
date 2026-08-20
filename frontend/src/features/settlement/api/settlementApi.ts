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
  SettlementReceiptOcrDto,
  SettlementSummaryDto,
} from './settlementApi.types'
import { settlementReceiptOcrResponseSchema } from './settlementResponseSchemas'

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

/**
 * 영수증 사진 업로드
 *
 * 정산을 만들기 전에 먼저 올린다. 돌려받은 'receiptId'를 정산 생성 요청에 실어 보내면
 * 서버가 그 사진을 정산에 연결한다.
 */
export async function uploadSettlementReceipt(file: File): Promise<{ receiptId: string }> {
  const form = new FormData()
  form.append('file', file)

  const { data } = await httpClient.post<{ receiptId: string | number }>(
    '/api/v1/settlement-receipts',
    form,
  )
  return { receiptId: String(data.receiptId) }
}

/**
 * 영수증 글자 인식
 *
 * 올려 둔 사진에서 품목 초안을 읽어 온다. 결과는 서버에 저장되지 않으며, 사용자가 확인하고
 * 고친 값만 정산 생성 요청으로 올라간다.
 *
 * 읽기만 하는데 POST인 이유는 부를 때마다 바깥 서비스에 요금이 나가기 때문이다. 브라우저나
 * 중간 서버가 임의로 다시 부르거나 응답을 캐시에 담아 두면 안 된다.
 */
export async function recognizeSettlementReceipt(
  receiptId: string,
): Promise<SettlementReceiptOcrDto> {
  const { data } = await httpClient.post<SettlementReceiptOcrDto>(
    `/api/v1/settlement-receipts/${receiptId}/ocr`,
    undefined,
    { responseSchema: settlementReceiptOcrResponseSchema },
  )
  return data
}

/**
 * 영수증 사진 조회
 *
 * 이미지 바이트를 그대로 받는다. img 태그에 주소를 그대로 박으면 안 된다. 인증이 쿠키로
 * 오가는데 프론트와 API가 서로 다른 사이트라, 브라우저가 그런 이미지 요청에는 쿠키를
 * 싣지 않아 거절당한다. 그래서 여기서 직접 받아 화면에 넘긴다.
 */
export async function fetchSettlementReceipt(settlementId: string): Promise<Blob> {
  const { data } = await httpClient.get<Blob>(`/api/v1/settlements/${settlementId}/receipt`, {
    responseType: 'blob',
  })
  return data
}
