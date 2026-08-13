import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchWalletHome, getTransactionDetail, getTransactions } from '../walletApi'
import {
  transactionDetailResponseSchema,
  transactionListResponseSchema,
  walletHomeResponseSchema,
} from '../walletResponseSchemas'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/shared/api/httpClient', () => ({
  httpClient: { get },
}))

describe('walletApi', () => {
  beforeEach(() => {
    get.mockReset()
  })

  it('opts wallet home into response validation', async () => {
    const data = { balance: 84500, availabilityStatus: 'ACTIVE', recentTransactions: [] }
    get.mockResolvedValueOnce({ data })

    await expect(fetchWalletHome()).resolves.toEqual(data)
    expect(get).toHaveBeenCalledWith('/api/v1/wallet', {
      responseSchema: walletHomeResponseSchema,
    })
  })

  it('opts transaction list into response validation and preserves query params', async () => {
    const data = {
      transactions: [],
      nextCursor: null,
      appliedFilters: { type: null, status: null, from: null, to: null },
    }
    const params = { status: 'COMPLETED' as const, size: 20 }
    get.mockResolvedValueOnce({ data })

    await expect(getTransactions(params)).resolves.toEqual(data)
    expect(get).toHaveBeenCalledWith('/api/v1/me/transactions', {
      params,
      responseSchema: transactionListResponseSchema,
    })
  })

  it('opts transaction detail into response validation', async () => {
    const data = {
      amount: 30000,
      occurredAt: null,
      counterparty: null,
      status: 'COMPLETED',
      receipt: null,
      transactionNumber: null,
      fx: null,
    }
    get.mockResolvedValueOnce({ data })

    await expect(getTransactionDetail(101)).resolves.toEqual(data)
    expect(get).toHaveBeenCalledWith('/api/v1/me/transactions/101', {
      responseSchema: transactionDetailResponseSchema,
    })
  })
})
