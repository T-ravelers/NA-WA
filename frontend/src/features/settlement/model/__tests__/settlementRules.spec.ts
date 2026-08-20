import { describe, expect, it } from 'vitest'

import type { ItemizedSettlementItem } from '../settlement'
import {
  compareItemizedTotal,
  compareRecognizedTotal,
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

  it('accepts a giveaway item priced at zero but still refuses a blank price', () => {
    expect(
      validateItemizedItems(
        [
          {
            name: 'Zero cola (review event)',
            unitPrice: '0',
            quantity: '1',
            allocations: [{ appointmentMemberId: '12', quantity: '1' }],
          },
          {
            name: 'Dinner',
            unitPrice: '0.0000',
            quantity: '2',
            allocations: [{ appointmentMemberId: '19', quantity: '2' }],
          },
        ],
        new Set(['12', '19']),
      ),
    ).toEqual({ valid: true, invalidItemIndexes: [] })

    expect(
      validateItemizedItems(
        [
          {
            name: 'Zero cola (review event)',
            unitPrice: '',
            quantity: '1',
            allocations: [{ appointmentMemberId: '12', quantity: '1' }],
          },
        ],
        new Set(['12']),
      ),
    ).toEqual({ valid: false, invalidItemIndexes: [0] })
  })
})

describe('compareItemizedTotal', () => {
  it('matches when the item totals add up to the payment', () => {
    expect(compareItemizedTotal([item('10.00', '2'), item('5.00', '1')], '25.00')).toEqual({
      matches: true,
      total: '25',
      difference: '0',
      exceedsPayment: false,
    })
  })

  it('catches totals that overshoot the payment', () => {
    // 서버가 거절하는 조건이라 화면에서 먼저 막아야 한다.
    const result = compareItemizedTotal([item('30.00', '1')], '25.00')

    expect(result?.matches).toBe(false)
    expect(result?.total).toBe('30')
    // 두 값을 나란히 보여주는 것만으로는 사용자가 차액을 암산해야 한다.
    expect(result?.difference).toBe('5')
    expect(result?.exceedsPayment).toBe(true)
  })

  /** 모자란 쪽도 얼마나 모자란지 알아야 어느 단가를 올릴지 정할 수 있다. */
  it('reports how far the items fall short', () => {
    const result = compareItemizedTotal([item('20.00', '1')], '25.00')

    expect(result?.difference).toBe('5')
    expect(result?.exceedsPayment).toBe(false)
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

describe('receipt total comparison', () => {
  it('reports a match without minding how the decimals are written', () => {
    expect(compareRecognizedTotal('25', '25.00')).toEqual({ matches: true })
    expect(compareRecognizedTotal('25.0000', '25')).toEqual({ matches: true })
  })

  /*
   * 어긋나도 결과만 알릴 뿐 막지 않는다. 여러 명이 나눠 결제했거나 할인·봉사료가 붙으면
   * 정상적으로도 달라지고, 인식 값 자체가 틀렸을 수도 있다.
   */
  it('reports a difference so the screen can warn without blocking', () => {
    expect(compareRecognizedTotal('30.00', '25.00')).toEqual({ matches: false })
  })

  /** 읽지 못했거나 읽을 수 없는 값이면 견줄 것이 없다. 견주지 못한 것을 어긋난 것으로 보면 안 된다. */
  it('stays silent when there is nothing to compare', () => {
    expect(compareRecognizedTotal(null, '25.00')).toBeNull()
    expect(compareRecognizedTotal('', '25.00')).toBeNull()
    expect(compareRecognizedTotal('twenty', '25.00')).toBeNull()
    expect(compareRecognizedTotal('25.00', '')).toBeNull()
  })
})
