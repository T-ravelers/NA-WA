import { afterEach, describe, expect, it } from 'vitest'

import {
  clearSettlementCreateIdempotencyKey,
  clearSettlementPaymentIdempotencyKey,
  resolveSettlementCreateIdempotencyKey,
  resolveSettlementPaymentIdempotencyKey,
} from '../settlementIdempotency'

const request = {
  sourceTransferId: '7',
  type: 'ITEMIZED' as const,
  participantAppointmentMemberIds: ['12', '19'],
  items: [
    {
      name: 'Dinner',
      unitPrice: '12.50',
      quantity: '2',
      allocations: [
        { appointmentMemberId: '12', quantity: '1' },
        { appointmentMemberId: '19', quantity: '1' },
      ],
    },
  ],
}

describe('settlement idempotency', () => {
  afterEach(() => sessionStorage.clear())

  it('reuses a create key only for an equivalent full itemized request', () => {
    expect(resolveSettlementCreateIdempotencyKey('9', request, () => 'create-1')).toBe('create-1')
    expect(
      resolveSettlementCreateIdempotencyKey(
        '9',
        {
          ...request,
          items: [
            {
              ...request.items[0]!,
              allocations: [
                { appointmentMemberId: '19', quantity: '1' },
                { appointmentMemberId: '12', quantity: '1' },
              ],
            },
          ],
        },
        () => 'create-2',
      ),
    ).toBe('create-1')
    expect(
      resolveSettlementCreateIdempotencyKey(
        '9',
        { ...request, items: [{ ...request.items[0]!, unitPrice: '13.00' }] },
        () => 'create-3',
      ),
    ).toBe('create-3')
  })

  it('keeps payment idempotency independent from creation and clears each scope separately', () => {
    expect(resolveSettlementPaymentIdempotencyKey('42', () => 'pay-1')).toBe('pay-1')
    expect(resolveSettlementCreateIdempotencyKey('9', request, () => 'create-1')).toBe('create-1')
    clearSettlementPaymentIdempotencyKey('42')
    clearSettlementCreateIdempotencyKey('7')

    expect(resolveSettlementPaymentIdempotencyKey('42', () => 'pay-2')).toBe('pay-2')
    expect(resolveSettlementCreateIdempotencyKey('9', request, () => 'create-2')).toBe('create-2')
  })
})
