import { describe, expect, it } from 'vitest'

import { validateItemizedItems } from '../settlementRules'

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
