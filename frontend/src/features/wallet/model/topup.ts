import { NormalizedApiError } from '@/shared/api/apiError'

export { formatPoints } from '@/shared/lib/money'

export const TOPUP_CURRENCY = 'KRW'
export const DEFAULT_TOPUP_METHOD = 'STRIPE_CARD'
export const QUICK_TOPUP_AMOUNTS = [10_000, 30_000, 50_000, 100_000] as const

export interface TopupMethod {
  type: string
  displayName: string
  testMode: boolean
  enabled: boolean
}

export interface TopupMethodsResponse {
  methods: TopupMethod[]
  guideMessage: string | null
}

export interface TopupPreviewRequest {
  amount: number
  method: string
  currency: typeof TOPUP_CURRENCY
}

export interface TopupPreviewResponse {
  amount: string | number
  fee: string | number
  currency: string
  sandboxBalance: string | number
  expectedSandboxBalance: string | number
  warning: string | null
}

export interface StripeIntentCreateRequest {
  amount: number
  currency: typeof TOPUP_CURRENCY
}

export interface StripeIntentResponse {
  topupId: number
  clientSecret: string
  providerPaymentId: string
  amount: string | number
  currency: string
  status: string
  paymentMode: string
}

export interface StripeTopupStatusResponse {
  topupId: number
  transactionId: number | null
  status: string
  providerStatus: string
  retryable: boolean
  sandboxBalance: string | number
}

/**
 * 서버가 충전을 꺼 둔 환경(백엔드 `PAYMENT_ENABLED=false`)에서 충전 API가 내는 오류 코드.
 * 충전 수단 목록은 그런 환경에서도 `enabled=false`로 내려오므로 화면은 보통 이 오류를 보기
 * 전에 "준비 중"으로 바뀐다. 이 코드는 화면을 연 뒤에 서버 설정이 바뀐 경우의 안전망이다.
 */
export const PAYMENT_DISABLED_ERROR_CODE = 'WALLET-032'

export const isPaymentDisabledError = (error: unknown): boolean =>
  error instanceof NormalizedApiError && error.code === PAYMENT_DISABLED_ERROR_CODE

export const topupKeys = {
  all: ['topups'] as const,
  methods: () => [...topupKeys.all, 'methods'] as const,
}

export const getTopupMethodLabel = (method: TopupMethod): string => {
  if (method.type === DEFAULT_TOPUP_METHOD) return 'Stripe'

  return method.displayName
}
