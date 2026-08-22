import { describe, expect, it } from 'vitest'

import {
  notificationListResponseSchema,
  unreadNotificationCountResponseSchema,
} from '../notificationResponseSchemas'

const notification = {
  id: 1,
  type: 'SETTLEMENT_REQUESTED',
  settlementId: 90,
  actorName: 'Ari',
  gatheringName: 'Dinner',
  amount: '30.0000',
  currencyCode: 'KRW',
  readAt: null,
  createdAt: '2026-08-21T12:00:00',
}

/** 필드 하나를 뺀 응답을 만든다. 구조 분해로 버리면 쓰지 않는 변수가 남는다. */
function omit(source: typeof notification, key: keyof typeof notification) {
  const copy = { ...source }
  delete copy[key]
  return copy
}

/** 목록은 배열이 아니라 쪽으로 온다. nextCursor를 실을 자리가 필요해서다. */
function page(notifications: unknown[], nextCursor: string | null = null) {
  return { notifications, nextCursor }
}

describe('notification response schemas', () => {
  it('accepts amounts as numbers or strings and ids in either form', () => {
    expect(notificationListResponseSchema.safeParse(page([notification])).success).toBe(true)
    expect(
      notificationListResponseSchema.safeParse(
        page([{ ...notification, id: '1', settlementId: '90', amount: 30 }]),
      ).success,
    ).toBe(true)
  })

  it('accepts a missing readAt as well as an explicit null', () => {
    const withoutReadAt = omit(notification, 'readAt')

    expect(notificationListResponseSchema.safeParse(page([withoutReadAt])).success).toBe(true)
    expect(notificationListResponseSchema.safeParse(page([notification])).success).toBe(true)
  })

  /* 마지막 쪽에서는 커서가 없다. 빠진 것과 null을 둘 다 "더 없다"로 읽는다. */
  it('treats a missing nextCursor the same as an explicit null', () => {
    expect(
      notificationListResponseSchema.safeParse({ notifications: [notification] }).success,
    ).toBe(true)
    expect(notificationListResponseSchema.safeParse(page([notification], null)).success).toBe(true)
    expect(notificationListResponseSchema.safeParse(page([notification], '12')).success).toBe(true)
  })

  /*
   * 알림 종류는 문자열 그대로 받는다. 서버가 정산 밖의 새 종류를 먼저 내보내도 목록 전체가
   * 검증에서 막히면, 사용자는 알림이 없는 것인지 화면이 못 읽은 것인지 알 수 없다.
   */
  it('does not reject a notification type the client does not know yet', () => {
    expect(
      notificationListResponseSchema.safeParse(
        page([{ ...notification, type: 'APPOINTMENT_REMINDER' }]),
      ).success,
    ).toBe(true)
  })

  it('keeps parsing when the server adds a field', () => {
    expect(
      notificationListResponseSchema.safeParse(page([{ ...notification, deepLink: '/splits/90' }]))
        .success,
    ).toBe(true)
  })

  it('rejects a notification that is missing what the screen needs', () => {
    const withoutGathering = omit(notification, 'gatheringName')

    expect(notificationListResponseSchema.safeParse(page([withoutGathering])).success).toBe(false)
    expect(
      notificationListResponseSchema.safeParse(page([{ ...notification, amount: null }])).success,
    ).toBe(false)
  })

  /*
   * 배열을 그대로 받으면 화면은 빈 목록으로 그린다. 서버가 옛 모양으로 돌아간 것을
   * 알림이 없는 것과 구분할 수 없게 되므로 검증에서 막는다.
   */
  it('rejects the old bare-array shape', () => {
    expect(notificationListResponseSchema.safeParse([notification]).success).toBe(false)
  })

  it('rejects an unread count that is not a whole non-negative number', () => {
    expect(unreadNotificationCountResponseSchema.safeParse({ count: 3 }).success).toBe(true)
    expect(unreadNotificationCountResponseSchema.safeParse({ count: -1 }).success).toBe(false)
    expect(unreadNotificationCountResponseSchema.safeParse({ count: 1.5 }).success).toBe(false)
    expect(unreadNotificationCountResponseSchema.safeParse({ count: '3' }).success).toBe(false)
  })
})
