import { describe, expect, it } from 'vitest'

import walletMessages from '../../i18n/en'
import type { WalletHome, WalletTransaction } from '../../api/walletApi'
import {
  formatTransactionDateTime,
  TRANSFER_TYPES,
  parseServerDateTime,
  toWalletHomeData,
} from '../walletHome'

function transaction(overrides: Partial<WalletTransaction> = {}): WalletTransaction {
  return {
    transferId: 1,
    transferType: 'QR_PAYMENT',
    entryType: 'DEBIT',
    amount: 18000,
    balanceAfter: 84500,
    createdAt: [2026, 7, 25, 12, 0],
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

  it('돈이 나간 거래와 들어온 거래를 방향으로 갈라 둔다', () => {
    const data = toWalletHomeData(
      response({
        recentTransactions: [
          transaction({ transferId: 1, entryType: 'DEBIT' }),
          transaction({ transferId: 2, entryType: 'CREDIT' }),
        ],
      }),
    )

    expect(data.activities[0]?.outgoing).toBe(true)
    expect(data.activities[1]?.outgoing).toBe(false)
  })

  it('알 수 없는 지갑 상태는 UNKNOWN으로 떨어뜨린다', () => {
    expect(toWalletHomeData(response({ availabilityStatus: 'FROZEN' })).status).toBe('UNKNOWN')
    expect(toWalletHomeData(response({ availabilityStatus: 'ACTIVE' })).status).toBe('ACTIVE')
  })

  it('최근 거래가 null이면 활동 목록을 빈 배열로 정규화한다', () => {
    expect(toWalletHomeData(response({ recentTransactions: null })).activities).toEqual([])
  })

  // 회귀: 지갑 응답 필드가 누락된 사례가 실제로 관측됐다. 계약상 필수 필드지만 방어적으로 다룬다.
  it('지갑 응답 필드가 누락돼도 화면을 중단시키지 않고 기본값을 쓴다', () => {
    const data = toWalletHomeData({} as WalletHome)

    expect(data.balance).toBe(0)
    expect(data.status).toBe('UNKNOWN')
    expect(data.activities).toEqual([])
  })
})

describe('parseServerDateTime', () => {
  // 회귀: 백엔드가 실제로 내려주는 형식이다. 문자열로 가정하면 전 거래의 시각이 사라진다.
  it('숫자 배열 시각을 KST로 읽는다', () => {
    expect(parseServerDateTime([2026, 7, 25, 12, 0])?.toISOString()).toBe(
      '2026-07-25T03:00:00.000Z',
    )
  })

  it('배열의 초·나노초가 생략돼도 읽는다', () => {
    // 뒤쪽 0은 직렬화에서 빠진다. 날짜만 있는 3칸도 자정으로 읽는다.
    expect(parseServerDateTime([2026, 7, 25])?.toISOString()).toBe('2026-07-24T15:00:00.000Z')
    expect(parseServerDateTime([2026, 7, 25, 12, 30, 45])?.toISOString()).toBe(
      '2026-07-25T03:30:45.000Z',
    )
    expect(parseServerDateTime([2026, 7, 25, 12, 0, 0, 500_000_000])?.toISOString()).toBe(
      '2026-07-25T03:00:00.500Z',
    )
  })

  it('KST 자정 이전 시각도 전날 UTC로 정규화한다', () => {
    // 시에서 9를 빼면 음수가 된다. Date.UTC가 전날로 넘겨야 한다.
    expect(parseServerDateTime([2026, 7, 25, 3, 0])?.toISOString()).toBe('2026-07-24T18:00:00.000Z')
  })

  it('배열이 짧거나 숫자가 아니면 null이다', () => {
    expect(parseServerDateTime([2026, 7])).toBeNull()
    expect(parseServerDateTime([])).toBeNull()
    expect(parseServerDateTime([2026, 7, Number.NaN])).toBeNull()
  })

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

describe('formatTransactionDateTime', () => {
  // 거래 내역·거래 상세 화면(#99)에서 쓰는 표시 문자열이다. 배열 응답을 못 읽으면
  // 화면에 'Unknown date'가 그대로 노출된다.
  it('LocalDateTime 배열 응답을 사람이 읽을 수 있는 문자열로 만든다', () => {
    expect(formatTransactionDateTime([2026, 8, 7, 12, 18, 2])).not.toBe('Unknown date')
  })

  // 회귀: 서비스는 한국에서만 쓰이므로 거래 시각은 항상 KST로 보여야 한다. 표시
  // 타임존을 기기에 맡기면 해외 타임존 기기에서 날짜가 전날로 밀린다.
  it('기기 타임존과 무관하게 KST로 표시한다', () => {
    expect(formatTransactionDateTime([2026, 8, 7, 12, 18, 2])).toBe('Aug 7, 2026, 12:18 PM')
  })
})
