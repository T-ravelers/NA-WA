import { describe, expect, it } from 'vitest'

import {
  serverDateTimeSchema,
  serverLocalDateSchema,
  transactionDetailResponseSchema,
  transactionListResponseSchema,
  walletHomeResponseSchema,
} from '../walletResponseSchemas'

const transaction = {
  transferId: 1,
  transferType: 'TOPUP',
  entryType: 'CREDIT',
  amount: 30000,
  balanceAfter: 114500,
  createdAt: [2026, 7, 25, 12, 0],
}

describe('wallet response schemas', () => {
  it('accepts LocalDateTime strings, 3..7 component arrays, and null', () => {
    expect(serverDateTimeSchema.safeParse('2026-07-25T12:00:00').success).toBe(true)
    expect(serverDateTimeSchema.safeParse([2026, 7, 25]).success).toBe(true)
    expect(serverDateTimeSchema.safeParse([2026, 7, 25, 12, 0, 0, 0]).success).toBe(true)
    expect(serverDateTimeSchema.safeParse(null).success).toBe(true)
    expect(serverDateTimeSchema.safeParse([2026, 7]).success).toBe(false)
    expect(serverDateTimeSchema.safeParse([2026, 7, 25, 12, 0, 0, 0, 1]).success).toBe(false)
  })

  it('accepts LocalDate strings or three-part arrays in applied filters', () => {
    expect(serverLocalDateSchema.safeParse('2026-07-25').success).toBe(true)
    expect(serverLocalDateSchema.safeParse([2026, 7, 25]).success).toBe(true)
    expect(serverLocalDateSchema.safeParse(null).success).toBe(true)
    expect(serverLocalDateSchema.safeParse([2026, 7]).success).toBe(false)
  })

  it('validates wallet BigDecimal fields as finite JSON numbers', () => {
    expect(
      walletHomeResponseSchema.safeParse({
        balance: 84500,
        availabilityStatus: 'ACTIVE',
        recentTransactions: [transaction],
      }).success,
    ).toBe(true)

    expect(
      walletHomeResponseSchema.safeParse({
        balance: 84500,
        availabilityStatus: 'ACTIVE',
        recentTransactions: null,
      }).success,
    ).toBe(true)

    expect(
      walletHomeResponseSchema.safeParse({
        balance: '84500',
        availabilityStatus: 'ACTIVE',
        recentTransactions: [transaction],
      }).success,
    ).toBe(false)
  })

  it('accepts future enum strings and rejects a material transaction mismatch', () => {
    expect(
      transactionListResponseSchema.safeParse({
        transactions: [{ ...transaction, transferType: 'FUTURE_TRANSFER' }],
        nextCursor: null,
        appliedFilters: { type: 'FUTURE_TRANSFER', status: null, from: [2026, 7, 1], to: null },
      }).success,
    ).toBe(true)

    expect(
      transactionListResponseSchema.safeParse({
        transactions: [{ ...transaction, entryType: 'UNKNOWN_DIRECTION' }],
        nextCursor: null,
        appliedFilters: { type: null, status: null, from: null, to: null },
      }).success,
    ).toBe(false)

    expect(
      transactionDetailResponseSchema.safeParse({
        amount: '30000',
        occurredAt: null,
        counterparty: null,
        status: 'COMPLETED',
        receipt: null,
        transactionNumber: null,
        fx: null,
      }).success,
    ).toBe(false)
  })
})
