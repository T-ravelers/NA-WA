import { httpClient } from '@/shared/api/httpClient'

import type {
  QrPaymentCreateRequest,
  QrPaymentCreateResponse,
  QrPaymentExecuteRequest,
  QrPaymentExecuteResponse,
  QrPaymentPreviewRequest,
  QrPaymentPreviewResponse,
  QrPaymentResolveResponse,
  QrPaymentStatusResponse,
} from '../model/qrPayment'

export async function createPaymentQr(
  request: QrPaymentCreateRequest,
): Promise<QrPaymentCreateResponse> {
  const { data } = await httpClient.post<QrPaymentCreateResponse>(
    '/api/v1/wallet/qr/create',
    request,
  )

  return data
}

export async function listActiveQrPayments(): Promise<QrPaymentCreateResponse[]> {
  const { data } = await httpClient.get<QrPaymentCreateResponse[]>('/api/v1/wallet/qr/active')

  return data
}

export async function resolvePaymentQr(qrToken: string): Promise<QrPaymentResolveResponse> {
  const { data } = await httpClient.post<QrPaymentResolveResponse>('/api/v1/wallet/qr/resolve', {
    qrToken,
  })

  return data
}

export async function previewQrPayment(
  request: QrPaymentPreviewRequest,
): Promise<QrPaymentPreviewResponse> {
  const { data } = await httpClient.post<QrPaymentPreviewResponse>(
    '/api/v1/wallet/qr/payment/preview',
    request,
  )

  return data
}

export async function executeQrPayment(
  request: QrPaymentExecuteRequest,
  idempotencyKey: string,
): Promise<QrPaymentExecuteResponse> {
  const { data } = await httpClient.post<QrPaymentExecuteResponse>(
    '/api/v1/wallet/qr/payment/execute',
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )

  return data
}

export async function getQrPaymentStatus(transferId: number): Promise<QrPaymentStatusResponse> {
  const { data } = await httpClient.get<QrPaymentStatusResponse>(
    `/api/v1/wallet/qr/payment/${transferId}`,
  )

  return data
}
