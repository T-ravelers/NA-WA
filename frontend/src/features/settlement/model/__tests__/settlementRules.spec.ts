import { describe, expect, it } from 'vitest'

import type { ItemizedSettlementItem } from '../settlement'
import {
  compareItemizedTotal,
  summarizeItemizedShares,
  validateItemizedItems,
} from '../settlementRules'

function item(unitPrice: string, quantity: string): ItemizedSettlementItem {
  return { name: 'x', unitPrice, quantity, allocations: [] }
}

describe('ITEMIZED request rules', () => {
  it('accepts a quantity allocation only when every item is fully assigned', () => {
    expect(
      validateItemizedItems([
        {
          name: 'Dinner',
          unitPrice: '12.50',
          quantity: '2',
          allocations: [
            { appointmentMemberId: '12', quantity: '1.250' },
            { appointmentMemberId: '19', quantity: '0.750' },
          ],
        },
      ]),
    ).toEqual({ valid: true, invalidItemIndexes: [] })
  })

  it('rejects partial, over-assigned, malformed, or unselected-member allocations', () => {
    expect(
      validateItemizedItems(
        [
          {
            name: 'Dinner',
            unitPrice: '12.50',
            quantity: '2',
            allocations: [{ appointmentMemberId: '12', quantity: '1' }],
          },
          {
            name: 'Coffee',
            unitPrice: '3',
            quantity: '1',
            allocations: [{ appointmentMemberId: '99', quantity: '1.001' }],
          },
        ],
        new Set(['12', '19']),
      ),
    ).toEqual({ valid: false, invalidItemIndexes: [0, 1] })
  })
})

describe('compareItemizedTotal', () => {
  it('matches when the item totals add up to the payment', () => {
    expect(compareItemizedTotal([item('10.00', '2'), item('5.00', '1')], '25.00')).toEqual({
      matches: true,
      total: '25',
    })
  })

  it('catches totals that overshoot the payment', () => {
    // 서버가 거절하는 조건이라 화면에서 먼저 막아야 한다.
    const result = compareItemizedTotal([item('30.00', '1')], '25.00')

    expect(result?.matches).toBe(false)
    expect(result?.total).toBe('30')
  })

  it('counts fractional quantities without floating point drift', () => {
    // 0.1 + 0.2 를 부동소수점으로 더하면 0.3이 되지 않는다.
    expect(compareItemizedTotal([item('1', '0.1'), item('1', '0.2')], '0.3')?.matches).toBe(true)
    expect(compareItemizedTotal([item('0.0001', '0.001')], '0.0000001')?.matches).toBe(true)
  })

  it('stays out of the way when a value cannot be read', () => {
    // 형식 오류는 품목별 검증이 잡는다. 여기서 겹쳐 막으면 원인이 헷갈린다.
    expect(compareItemizedTotal([item('abc', '1')], '25.00')).toBeNull()
    expect(compareItemizedTotal([item('10', '1')], 'not-a-number')).toBeNull()
    expect(compareItemizedTotal([], '25.00')).toBeNull()
  })
})

describe('summarizeItemizedShares', () => {
  const dinner: ItemizedSettlementItem = {
    name: 'Dinner',
    unitPrice: '10.00',
    quantity: '3',
    allocations: [
      { appointmentMemberId: '12', quantity: '1' },
      { appointmentMemberId: '19', quantity: '2' },
    ],
  }

  it('adds up what each person owes', () => {
    const summary = summarizeItemizedShares([dinner], '12')

    expect(summary?.shares).toEqual([
      { appointmentMemberId: '12', amount: '10' },
      { appointmentMemberId: '19', amount: '20' },
    ])
    expect(summary?.total).toBe('30')
  })

  it('leaves the payer share out of the requested amount', () => {
    // 원결제자가 자기 자신에게 청구하지는 않는다. 30 중 자기 몫 10을 뺀 20만 요청한다.
    expect(summarizeItemizedShares([dinner], '12')?.requested).toBe('20')
    expect(summarizeItemizedShares([dinner], '19')?.requested).toBe('10')
  })

  it('counts fractional allocations exactly', () => {
    const summary = summarizeItemizedShares(
      [
        {
          name: 'Wine',
          unitPrice: '0.1',
          quantity: '3',
          allocations: [
            { appointmentMemberId: '12', quantity: '1' },
            { appointmentMemberId: '19', quantity: '2' },
          ],
        },
      ],
      '12',
    )

    expect(summary?.shares.map((share) => share.amount)).toEqual(['0.1', '0.2'])
    expect(summary?.total).toBe('0.3')
  })

  it('gives nothing back when a value cannot be read', () => {
    expect(summarizeItemizedShares([], '12')).toBeNull()
    expect(
      summarizeItemizedShares(
        [{ name: 'x', unitPrice: 'abc', quantity: '1', allocations: [] }],
        '12',
      ),
    ).toBeNull()
  })
})
