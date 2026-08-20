import { describe, expect, it } from 'vitest'

import {
  mapRecognizedReceipt,
  mapSettlementCandidate,
  mapSettlementDetail,
  mapSettlementSummary,
} from '../settlementMappers'

describe('settlement response mappers', () => {
  it('keeps appointment member identifiers and the authoritative payer identifier', () => {
    const candidate = mapSettlementCandidate({
      transferId: 7,
      appointmentId: 9,
      payerAppointmentMemberId: 12,
      journeyName: 'Seoul',
      gatheringName: 'Dinner',
      merchantName: 'Cafe',
      amount: '25.00',
      paidAt: '2026-08-12T10:00:00',
      payerName: 'Alex',
      participants: [{ id: 12, name: 'Alex', initials: 'AL' }],
    })

    expect(candidate.payerAppointmentMemberId).toBe('12')
    expect(candidate.participants[0]?.id).toBe('12')
  })

  it('reads an offset-less payment time as server time instead of the browser time zone', () => {
    const candidate = mapSettlementCandidate({
      transferId: 7,
      appointmentId: 9,
      payerAppointmentMemberId: 12,
      journeyName: 'Seoul',
      gatheringName: 'Dinner',
      merchantName: 'Cafe',
      amount: '25.00',
      paidAt: '2026-08-12T10:00:00',
      payerName: 'Alex',
      participants: [{ id: 12, name: 'Alex', initials: 'AL' }],
    })

    expect(candidate.paidAt).toBe('Aug 12, 2026, 10:00 AM')
  })

  it('falls back to the raw payment time when the server value cannot be parsed', () => {
    const candidate = mapSettlementCandidate({
      transferId: 7,
      appointmentId: 9,
      payerAppointmentMemberId: 12,
      journeyName: 'Seoul',
      gatheringName: 'Dinner',
      merchantName: 'Cafe',
      amount: '25.00',
      paidAt: 'not a timestamp',
      payerName: 'Alex',
      participants: [],
    })

    expect(candidate.paidAt).toBe('not a timestamp')
  })

  it('maps nested viewer fields without deriving a payment action', () => {
    const detail = mapSettlementDetail({
      id: 42,
      type: 'ITEMIZED',
      totalAmount: '25.00',
      status: 'REQUESTED',
      requestedBy: 'Alex',
      gatheringName: 'Dinner',
      merchantName: 'Cafe',
      viewerItems: [
        { settlementItemId: 1, name: 'Dinner', allocatedQuantity: '1', allocatedAmount: '12.50' },
      ],
      transactionId: null,
      paidBy: 'Alex',
      viewer: {
        role: 'PARTICIPANT',
        shareAmount: '12.50',
        payableAmount: '12.50',
        requestStatus: 'PENDING',
        allowedActions: ['PAY'],
      },
    })

    expect(detail.viewer).toEqual({
      role: 'PARTICIPANT',
      shareAmount: '12.50',
      payableAmount: '12.50',
      requestStatus: 'PENDING',
      allowedActions: ['PAY'],
    })
    expect(detail.viewerItems).toEqual([
      { id: '1', name: 'Dinner', allocatedQuantity: '1', allocatedAmount: '12.50' },
    ])
  })

  it('maps list viewer state from the nested response', () => {
    expect(
      mapSettlementSummary({
        id: 42,
        title: 'Dinner',
        totalAmount: '25.00',
        receivableAmount: '12.50',
        type: 'EQUAL',
        status: 'REQUESTED',
        viewer: {
          role: 'PARTICIPANT',
          shareAmount: '12.50',
          payableAmount: '12.50',
          requestStatus: 'PENDING',
          allowedActions: ['PAY'],
        },
      }),
    ).toMatchObject({ totalAmount: '25.00', viewer: { allowedActions: ['PAY'] } })
  })

  /*
   * 읽지 못한 자리는 빈 칸이 돼야 한다. null이나 undefined가 그대로 흘러가면 입력란에
   * "null"이라고 찍히고, 사용자는 그것을 지우고 다시 적어야 한다.
   */
  it('turns unread receipt values into empty inputs', () => {
    expect(
      mapRecognizedReceipt({
        items: [{ name: null, unitPrice: 4500, quantity: null }, { name: 'Wine' } as never],
        recognizedTotal: null,
      }),
    ).toEqual({
      items: [
        { name: '', unitPrice: '4500', quantity: '' },
        { name: 'Wine', unitPrice: '', quantity: '' },
      ],
      recognizedTotal: null,
    })
  })

  /** 서버가 금액을 숫자로 보내도 품목 입력란은 문자열만 다룬다. */
  it('normalizes recognized amounts to strings', () => {
    expect(
      mapRecognizedReceipt({
        items: [{ name: 'Pasta', unitPrice: 10500, quantity: 2 }],
        recognizedTotal: 21000,
      }),
    ).toEqual({
      items: [{ name: 'Pasta', unitPrice: '10500', quantity: '2' }],
      recognizedTotal: '21000',
    })
  })
})
