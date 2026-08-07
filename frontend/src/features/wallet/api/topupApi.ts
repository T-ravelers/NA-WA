import { httpClient } from '@/shared/api/httpClient'

import type {
  StripeIntentCreateRequest,
  StripeIntentResponse,
  StripeTopupStatusResponse,
  TopupMethodsResponse,
  TopupPreviewRequest,
  TopupPreviewResponse,
} from '../model/topup'

export const getTopupMethods = async (): Promise<TopupMethodsResponse> => {
  const { data } = await httpClient.get<TopupMethodsResponse>('/api/v1/topups/methods')

  return data
}

export const previewTopup = async (request: TopupPreviewRequest): Promise<TopupPreviewResponse> => {
  const { data } = await httpClient.post<TopupPreviewResponse>('/api/v1/topups/preview', request)

  return data
}

export const createStripeIntent = async (
  request: StripeIntentCreateRequest,
  idempotencyKey: string,
): Promise<StripeIntentResponse> => {
  const { data } = await httpClient.post<StripeIntentResponse>(
    '/api/v1/topups/stripe/intent',
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )

  return data
}

export const getStripeTopupStatus = async (topupId: number): Promise<StripeTopupStatusResponse> => {
  const { data } = await httpClient.get<StripeTopupStatusResponse>(
    `/api/v1/topups/stripe/${topupId}`,
  )

  return data
}
