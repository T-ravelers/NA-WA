import { describe, expect, it } from 'vitest'

import { formatCurrency } from '@/shared/lib/money'

import {
  isPushReceivedMessage,
  parsePushPayload,
  PUSH_RECEIVED_MESSAGE_TYPE,
  pushNotificationContent,
  type PushPayload,
} from '../pushNotification'

function payload(overrides: Partial<PushPayload> = {}): PushPayload {
  return {
    type: 'SETTLEMENT_REQUESTED',
    settlementId: 90,
    actorName: 'Ari',
    gatheringName: 'Dinner',
    amount: 30000,
    currencyCode: 'KRW',
    language: 'en',
    ...overrides,
  }
}

describe('parsePushPayload', () => {
  it('reads the body the server sends', () => {
    expect(parsePushPayload(JSON.stringify(payload()))).toEqual(payload())
  })

  it('accepts the decimal amount the server serializes from DECIMAL(19,4)', () => {
    expect(
      parsePushPayload(
        '{"type":"SETTLEMENT_PAID","settlementId":7,"actorName":"Chan",' +
          '"gatheringName":"Trip","amount":20000.0000,"currencyCode":"KRW","language":"ja"}',
      ),
    ).toMatchObject({ settlementId: 7, amount: 20000, language: 'ja' })
  })

  it.each([
    ['nothing', null],
    ['an empty body', ''],
    ['text that is not JSON', 'hello'],
    ['JSON that is not an object', '"hello"'],
    ['null', 'null'],
  ])('returns null for %s', (_label, raw) => {
    expect(parsePushPayload(raw)).toBeNull()
  })

  it.each([
    ['a missing field', { actorName: undefined }],
    ['a string settlement id', { settlementId: '90' }],
    ['a fractional settlement id', { settlementId: 90.5 }],
    ['a string amount', { amount: '30000' }],
    ['a non-finite amount', { amount: null }],
    ['a numeric language', { language: 1 }],
  ])('returns null for %s', (_label, overrides) => {
    expect(parsePushPayload(JSON.stringify({ ...payload(), ...overrides }))).toBeNull()
  })
})

describe('pushNotificationContent', () => {
  const amount = (locale: string) => formatCurrency(30000, locale, 'KRW')

  it('writes the same sentence as the notification list for the payload language', () => {
    expect(pushNotificationContent(payload()).body).toBe(
      `Ari asked you for ${amount('en')} for Dinner`,
    )
    expect(pushNotificationContent(payload({ language: 'ja' })).body).toBe(
      `Ariさんが「Dinner」の ${amount('ja')} を請求しました`,
    )
    expect(pushNotificationContent(payload({ language: 'zh-TW' })).body).toBe(
      `Ari 向你請款「Dinner」的 ${amount('zh-TW')}`,
    )
    expect(pushNotificationContent(payload({ language: 'vi' })).body).toBe(
      `Ari đã yêu cầu bạn ${amount('vi')} cho Dinner`,
    )
  })

  it('formats the amount with the currency, not as a bare number', () => {
    expect(pushNotificationContent(payload()).body).toContain('₩30,000')
  })

  it('writes one sentence per notification kind', () => {
    expect(
      pushNotificationContent(payload({ type: 'SETTLEMENT_PAID', actorName: 'Chan' })).body,
    ).toBe(`Chan paid you ${amount('en')} for Dinner`)
    expect(pushNotificationContent(payload({ type: 'SETTLEMENT_COMPLETED' })).body).toBe(
      'Everyone has paid for Dinner',
    )
  })

  it('falls back to the generic sentence for a kind the app does not know', () => {
    expect(pushNotificationContent(payload({ type: 'SETTLEMENT_REFUNDED' })).body).toBe(
      'Something happened with Dinner',
    )
  })

  it('falls back to English for a language the app does not support', () => {
    const content = pushNotificationContent(payload({ language: 'ko' }))

    expect(content.lang).toBe('en')
    expect(content.body).toBe(`Ari asked you for ${amount('en')} for Dinner`)
  })

  it('keeps the notification when the currency code is invalid', () => {
    const content = pushNotificationContent(payload({ currencyCode: 'NOPE!' }))

    expect(content.body).toBe('Ari asked you for 30000 for Dinner')
  })

  it('keeps the sentence in the payload language and titles it with the app name', () => {
    const content = pushNotificationContent(payload({ language: 'vi' }))

    expect(content.title).toBe('NA-WA')
    expect(content.lang).toBe('vi')
  })

  /**
   * 알림 목록에서 항목을 눌렀을 때와 같은 주소여야 한다. `origin`이 없으면 정산 상세에서 뒤로
   * 갈 때 정산 홈으로 떨어지고, `side`가 틀리면 그 정산이 없는 탭이 열린다.
   */
  it('opens the settlement the way the notification list does', () => {
    expect(pushNotificationContent(payload()).url).toBe(
      '/settlements/90?origin=notifications&side=received',
    )
    expect(pushNotificationContent(payload({ type: 'SETTLEMENT_PAID' })).url).toBe(
      '/settlements/90?origin=notifications&side=sent',
    )
    // 완료 알림은 받은 사람이 어느 쪽이었는지 알 수 없어 side를 정하지 않는다.
    expect(pushNotificationContent(payload({ type: 'SETTLEMENT_COMPLETED' })).url).toBe(
      '/settlements/90?origin=notifications',
    )
  })

  /** iOS는 푸시마다 눈에 보이는 알림이 있어야 구독을 유지한다. 본문을 못 읽어도 띄운다. */
  it('still shows a notification, opening the list, when the body could not be read', () => {
    expect(pushNotificationContent(null)).toEqual({
      title: 'NA-WA',
      body: 'Notifications',
      lang: 'en',
      url: '/notifications',
    })
  })
})

describe('isPushReceivedMessage', () => {
  it('recognizes only the message the service worker sends', () => {
    expect(isPushReceivedMessage({ type: PUSH_RECEIVED_MESSAGE_TYPE })).toBe(true)
    expect(isPushReceivedMessage({ type: 'something-else' })).toBe(false)
    expect(isPushReceivedMessage('na-wa:push-received')).toBe(false)
    expect(isPushReceivedMessage(null)).toBe(false)
    expect(isPushReceivedMessage(undefined)).toBe(false)
  })
})
