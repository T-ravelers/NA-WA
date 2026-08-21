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

export const topupKeys = {
  all: ['topups'] as const,
  methods: () => [...topupKeys.all, 'methods'] as const,
}

export const getTopupMethodLabel = (method: TopupMethod): string => {
  if (method.type === DEFAULT_TOPUP_METHOD) return 'Stripe'

  return method.displayName
}
