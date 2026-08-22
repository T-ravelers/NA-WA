import { describe, expect, it } from 'vitest'

import { settlementReturnQuery, toAppNotification, toNotificationKind } from '../notification'

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

/*
 * 정산 상세는 이 `origin` 값을 보고 뒤로 갈 곳을 정한다. 값이 어긋나면 알림에서 연 정산이
 * 정산 홈으로 떨어지고, 벨을 눌러 들어온 사용자는 지갑에서 두 화면이나 떨어진 곳에 선다.
 *
 * 두 feature는 서로를 import할 수 없어(`no-cross-feature-imports`) 값을 양쪽에 따로 적는다.
 * 그래서 양쪽 테스트가 각자 이 글자를 붙잡는다 — 짝이 되는 것은 정산 쪽
 * `settlementReturn.spec.ts`의 "opened from a notification" 테스트다.
 */
describe('settlementReturnQuery', () => {
  it('marks that the settlement was opened from the notification list', () => {
    expect(settlementReturnQuery('received')).toEqual({
      origin: 'notifications',
      side: 'received',
    })
  })

  /* 완료 알림은 어느 쪽인지 알 수 없다. 그때도 온 곳 표시는 남아야 한다. */
  it('still marks where it came from when the side is unknown', () => {
    expect(settlementReturnQuery(undefined)).toEqual({ origin: 'notifications' })
  })
})
