export type SpendingScope = 'personal' | 'shared'

export const QR_PAYMENT_PREVIEW = {
  recipient: 'Seoul Night Tour',
  amount: 18_500,
  currentBalance: 128_500,
  balanceAfter: 110_000,
} as const

export const ACTIVE_APPOINTMENTS = [
  {
    id: 'seoul-night-tour',
    name: 'Seoul Night Tour',
    period: 'Aug 10–12',
  },
  {
    id: 'seoul-foodie-week',
    name: 'Seoul Foodie Week',
    period: 'Aug 14–18',
  },
] as const

export const formatKrw = (amount: number): string =>
  `₩${amount.toLocaleString('en-US', { maximumFractionDigits: 0 })}`
