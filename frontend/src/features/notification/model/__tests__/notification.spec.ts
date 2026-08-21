import { describe, expect, it } from 'vitest'

import { toAppNotification, toNotificationKind } from '../notification'

const dto = {
  id: 1,
  type: 'SETTLEMENT_PAID',
  settlementId: 90,
  actorName: 'Chan',
  gatheringName: 'Dinner',
  amount: '20.0000',
  currencyCode: 'KRW',
  readAt: null,
  createdAt: '2026-08-21T12:00:00',
}

describe('toNotificationKind', () => {
  it('keeps the three settlement kinds the screen knows', () => {
    expect(toNotificationKind('SETTLEMENT_REQUESTED')).toBe('SETTLEMENT_REQUESTED')
    expect(toNotificationKind('SETTLEMENT_PAID')).toBe('SETTLEMENT_PAID')
    expect(toNotificationKind('SETTLEMENT_COMPLETED')).toBe('SETTLEMENT_COMPLETED')
  })

  it('falls back to UNKNOWN instead of dropping a kind it has not seen', () => {
    expect(toNotificationKind('APPOINTMENT_REMINDER')).toBe('UNKNOWN')
  })
})

describe('toAppNotification', () => {
  it('turns ids into strings so the router and cache keys agree', () => {
    const notification = toAppNotification(dto)

    expect(notification.id).toBe('1')
    expect(notification.settlementId).toBe('90')
    expect(notification.amount).toBe(20)
  })

  it('reads the read state from readAt alone', () => {
    expect(toAppNotification(dto).isRead).toBe(false)
    expect(toAppNotification({ ...dto, readAt: undefined }).isRead).toBe(false)
    expect(toAppNotification({ ...dto, readAt: '2026-08-21T13:00:00' }).isRead).toBe(true)
  })
})
