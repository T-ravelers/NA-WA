import { describe, expect, it } from 'vitest'

import walletMessages from '../../i18n/en'
import type { WalletHome, WalletTransaction } from '../../api/walletApi'
import { TRANSFER_TYPES, parseServerDateTime, toWalletHomeData } from '../walletHome'

function transaction(overrides: Partial<WalletTransaction> = {}): WalletTransaction {
  return {
    transferId: 1,
    transferType: 'QR_PAYMENT',
    entryType: 'DEBIT',
    amount: 18000,
    balanceAfter: 84500,
    createdAt: '2026-07-25T12:00:00',
    ...overrides,
  }
}

function response(overrides: Partial<WalletHome> = {}): WalletHome {
  return {
    balance: 84500,
    availabilityStatus: 'ACTIVE',
    recentTransactions: [transaction()],
    ...overrides,
  }
}

describe('toWalletHomeData', () => {
  it('백엔드 TransferType 전체에 표시 문구가 있다', () => {
    const activity = walletMessages.wallet.home.activity

    for (const type of TRANSFER_TYPES) {
      expect(activity, `${type} 문구가 없다`).toHaveProperty(type)
    }

    expect(activity).toHaveProperty('UNKNOWN')
  })

  it('i18n에만 있고 백엔드에 없는 거래 종류를 두지 않는다', () => {
    const known = new Set<string>([...TRANSFER_TYPES, 'UNKNOWN'])

    for (const key of Object.keys(walletMessages.wallet.home.activity)) {
      expect(known, `${key}는 백엔드 TransferType에 없다`).toContain(key)
    }
  })

  it('알 수 없는 거래 종류는 UNKNOWN으로 떨어뜨리고 항목은 남긴다', () => {
    const data = toWalletHomeData(
      response({ recentTransactions: [transaction({ transferType: 'TRANSFER' })] }),
    )

    expect(data.activities).toHaveLength(1)
    expect(data.activities[0]?.kind).toBe('UNKNOWN')
  })

  it('DEBIT은 차감, CREDIT은 증가로 부호를 붙인다', () => {
    const data = toWalletHomeData(
      response({
        recentTransactions: [
          transaction({ transferId: 1, entryType: 'DEBIT', amount: 18000 }),
          transaction({ transferId: 2, entryType: 'CREDIT', amount: 50000 }),
        ],
      }),
    )

    expect(data.activities[0]?.signedAmount).toBe(-18000)
    expect(data.activities[1]?.signedAmount).toBe(50000)
  })

  it('원장 금액이 이미 음수여도 부호를 두 번 붙이지 않는다', () => {
    const data = toWalletHomeData(
      response({ recentTransactions: [transaction({ entryType: 'DEBIT', amount: -18000 })] }),
    )

    expect(data.activities[0]?.signedAmount).toBe(-18000)
  })

  it('SETTLEMENT만 정산 완료로 본다', () => {
    const data = toWalletHomeData(
      response({
        recentTransactions: [
          transaction({ transferId: 1, transferType: 'SETTLEMENT' }),
          transaction({ transferId: 2, transferType: 'TOPUP' }),
        ],
      }),
    )

    expect(data.activities[0]?.settled).toBe(true)
    expect(data.activities[1]?.settled).toBe(false)
  })

  it('알 수 없는 지갑 상태는 UNKNOWN으로 떨어뜨린다', () => {
    expect(toWalletHomeData(response({ availabilityStatus: 'FROZEN' })).status).toBe('UNKNOWN')
    expect(toWalletHomeData(response({ availabilityStatus: 'ACTIVE' })).status).toBe('ACTIVE')
  })
})

describe('parseServerDateTime', () => {
  it('오프셋이 없는 서버 시각을 KST로 읽는다', () => {
    // KST 23:30은 같은 날 UTC 14:30이다. 로컬 타임존으로 읽으면 날짜가 밀린다.
    expect(parseServerDateTime('2026-07-25T23:30:00')?.toISOString()).toBe(
      '2026-07-25T14:30:00.000Z',
    )
  })

  it('오프셋이 이미 있으면 그대로 존중한다', () => {
    expect(parseServerDateTime('2026-07-25T12:00:00Z')?.toISOString()).toBe(
      '2026-07-25T12:00:00.000Z',
    )
  })

  it('값이 없거나 해석할 수 없으면 null이다', () => {
    expect(parseServerDateTime(null)).toBeNull()
    expect(parseServerDateTime('')).toBeNull()
    expect(parseServerDateTime('nope')).toBeNull()
  })
})
