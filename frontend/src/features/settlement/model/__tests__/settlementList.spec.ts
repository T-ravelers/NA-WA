import { describe, expect, it } from 'vitest'

import type { SettlementStatus, SettlementSummary } from '../settlement'
import { canPay, hasViewerPaid, primaryAmount, splitIntoSections } from '../settlementList'

function summary(
  id: string,
  status: SettlementStatus,
  viewer: Partial<SettlementSummary['viewer']> = {},
): SettlementSummary {
  return {
    id,
    title: `Dinner ${id}`,
    totalAmount: '25.00',
    receivableAmount: '12.50',
    type: 'EQUAL',
    status,
    createdAt: '2026-08-01T19:00:00',
    completedAt: status === 'COMPLETED' ? '2026-08-02T19:00:00' : '',
    viewer: {
      role: 'PARTICIPANT',
      shareAmount: '12.50',
      payableAmount: '12.50',
      requestStatus: 'PENDING',
      allowedActions: ['PAY'],
      ...viewer,
    },
  }
}

describe('settlementList', () => {
  it('splits requested and completed settlements while keeping the server order', () => {
    const sections = splitIntoSections([
      summary('1', 'REQUESTED'),
      summary('2', 'COMPLETED'),
      summary('3', 'REQUESTED'),
    ])

    expect(sections.ongoing.map((entry) => entry.id)).toEqual(['1', '3'])
    expect(sections.completed.map((entry) => entry.id)).toEqual(['2'])
  })

  it('marks a settlement the viewer already paid while the settlement is still open', () => {
    const paid = summary('1', 'REQUESTED', { requestStatus: 'PAID', allowedActions: [] })

    expect(splitIntoSections([paid]).ongoing).toHaveLength(1)
    expect(hasViewerPaid(paid)).toBe(true)
    expect(canPay(paid)).toBe(false)
  })

  it('reads the payable action from the server instead of inferring it from the amount', () => {
    expect(canPay(summary('1', 'REQUESTED', { allowedActions: [] }))).toBe(false)
    expect(canPay(summary('1', 'REQUESTED', { allowedActions: ['PAY'] }))).toBe(true)
  })

  it('highlights the viewer share when paying and the receivable when collecting', () => {
    const settlement = summary('1', 'REQUESTED')

    expect(primaryAmount(settlement, 'received')).toBe('12.50')
    expect(primaryAmount(settlement, 'sent')).toBe(settlement.receivableAmount)
  })
})
