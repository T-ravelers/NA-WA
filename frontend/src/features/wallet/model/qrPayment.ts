import type { SpendingCategory } from '@/shared/lib/spendingCategory'

export { formatPoints } from '@/shared/lib/money'

export type SpendingScope = 'personal' | 'shared'

/** 백엔드 `SpendingScope` enum과 1:1. 화면은 소문자 `SpendingScope`로 다루고 API 호출 직전에만 바꾼다. */
export type QrPaymentSpendingScope = 'PERSONAL' | 'SHARED'

export const toQrPaymentSpendingScope = (scope: SpendingScope): QrPaymentSpendingScope =>
  scope === 'shared' ? 'SHARED' : 'PERSONAL'

/** `qr_payment_codes.amount`는 DECIMAL(19, 4)라 정수부 최대 자릿수가 15자리다. */
export const MAX_QR_PAYMENT_AMOUNT = 999_999_999_999_999

export const isValidQrPaymentAmount = (value: number | null): value is number =>
  value !== null &&
  Number.isFinite(value) &&
  Number.isSafeInteger(value) &&
  value > 0 &&
  value <= MAX_QR_PAYMENT_AMOUNT

export interface QrPaymentCreateRequest {
  amount: number | null
  memo: string | null
}

/** `POST /api/v1/wallet/qr/create` 응답. 백엔드 `QrPaymentCreateResponse`와 1:1. */
export interface QrPaymentCreateResponse {
  qrPaymentCodeId: number
  qrToken: string
  amount: number | null
  memo: string | null
  status: string
  currencyCode: string
  expiresAt: string
}

/** `POST /api/v1/wallet/qr/resolve` 응답. 백엔드 `QrPaymentResolveResponse`와 1:1. */
export interface QrPaymentResolveResponse {
  qrPaymentId: number
  payeeName: string
  amount: number | null
  amountInputRequired: boolean
  memo: string | null
  status: string
  currencyCode: string
  expiresAt: string
}

export interface QrPaymentPreviewRequest {
  qrToken: string
  amount: number
  spendingScope: QrPaymentSpendingScope
  appointmentId: number | null
}

/** `POST /api/v1/wallet/qr/payment/preview` 응답. 백엔드 `QrPaymentPreviewResponse`와 1:1. */
export interface QrPaymentPreviewResponse {
  qrPaymentId: number
  payeeName: string
  amount: number
  currentBalance: number
  balanceAfter: number
  currencyCode: string
  spendingScope: QrPaymentSpendingScope
  trip: { tripId: number; title: string } | null
  appointment: { appointmentId: number; appointmentName: string } | null
  canPay: boolean
  expiresAt: string
}

export interface QrPaymentExecuteRequest {
  qrToken: string
  amount: number
  spendingScope: QrPaymentSpendingScope
  appointmentId: number | null
  /**
   * 결제자가 고른 소비 카테고리.
   *
   * 백엔드는 `null`도 받아 `OTHER`로 접지만, 화면은 기본 선택을 두고 항상 값을 보낸다.
   * 미리보기 요청에는 없다 — 카테고리는 결제 금액과 잔액을 바꾸지 않는다.
   */
  spendingCategory: SpendingCategory
}

/** `POST /api/v1/wallet/qr/payment/execute` 응답. 백엔드 `QrPaymentExecuteResponse`와 1:1. */
export interface QrPaymentExecuteResponse {
  transferId: number
  qrPaymentId: number
  status: string
  amount: number
  balanceAfter: number
  currencyCode: string
  completedAt: string
}

/** `GET /api/v1/wallet/qr/payment/{transferId}` 응답. 백엔드 `QrPaymentStatusResponse`와 1:1. */
export interface QrPaymentStatusResponse {
  transferId: number
  status: string
  amount: number
  balanceAfter: number
  currencyCode: string
  completedAt: string
}

export const qrPaymentKeys = {
  all: ['wallet', 'qr'] as const,
  active: () => [...qrPaymentKeys.all, 'active'] as const,
  preview: (
    qrToken: string,
    amount: number,
    spendingScope: QrPaymentSpendingScope,
    appointmentId: number | null,
  ) => [...qrPaymentKeys.all, 'preview', qrToken, amount, spendingScope, appointmentId] as const,
  status: (transferId: number) => [...qrPaymentKeys.all, 'status', transferId] as const,
}
