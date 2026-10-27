import { FALLBACK_LOCALE, isSupportedLocale, type AppLocale } from '@/shared/i18n/locales'
import { formatCurrency } from '@/shared/lib/money'

import en from '../i18n/en'
import ja from '../i18n/ja'
import vi from '../i18n/vi'
import zhTW from '../i18n/zh-TW'
import {
  settlementReturnQuery,
  settlementSideFor,
  toNotificationKind,
  type NotificationKind,
} from './notification'

/**
 * 웹 푸시 알림을 서비스 워커가 화면에 띄우기 위한 재료와 규칙.
 *
 * 서비스 워커 번들(`src/sw.ts`)에 들어가므로 Vue·DOM에 기대는 것을 import하지 않는다.
 */

const APP_NAME = 'NA-WA'

/** 서비스 워커가 열린 창에 보내는 메시지. 창은 이것을 받으면 알림·정산 쿼리를 다시 받아 온다. */
export const PUSH_RECEIVED_MESSAGE_TYPE = 'na-wa:push-received'

export function isPushReceivedMessage(data: unknown): boolean {
  return (
    typeof data === 'object' &&
    data !== null &&
    (data as { type?: unknown }).type === PUSH_RECEIVED_MESSAGE_TYPE
  )
}

/**
 * 서버가 보내는 푸시 본문. `PushSenderImpl.Payload`와 같은 모양이다.
 *
 * 문장이 아니라 값을 받는다. 백엔드에는 알림 문구를 만드는 i18n이 없고, 문구는 알림 목록과 같은
 * 번역 파일로 여기서 만든다. 어느 언어로 만들지는 구독을 등록한 화면의 `language`가 정한다.
 */
export interface PushPayload {
  type: string
  settlementId: number
  actorName: string
  gatheringName: string
  amount: number
  currencyCode: string
  language: string
}

/** 읽을 수 없는 본문이면 null. 호출한 쪽은 그래도 알림은 띄워야 한다. */
export function parsePushPayload(raw: string | null): PushPayload | null {
  if (raw === null || raw === '') return null

  let value: unknown
  try {
    value = JSON.parse(raw)
  } catch {
    return null
  }
  if (typeof value !== 'object' || value === null) return null

  const payload = value as Record<string, unknown>
  if (
    typeof payload.type !== 'string' ||
    typeof payload.actorName !== 'string' ||
    typeof payload.gatheringName !== 'string' ||
    typeof payload.currencyCode !== 'string' ||
    typeof payload.language !== 'string' ||
    typeof payload.settlementId !== 'number' ||
    !Number.isInteger(payload.settlementId) ||
    typeof payload.amount !== 'number' ||
    !Number.isFinite(payload.amount)
  ) {
    return null
  }

  return {
    type: payload.type,
    settlementId: payload.settlementId,
    actorName: payload.actorName,
    gatheringName: payload.gatheringName,
    amount: payload.amount,
    currencyCode: payload.currencyCode,
    language: payload.language,
  }
}

interface PushMessages {
  notification: { title: string; item: Record<NotificationKind, string> }
}

const MESSAGES: Record<AppLocale, PushMessages> = { en, ja, vi, 'zh-TW': zhTW }

export interface PushNotificationContent {
  title: string
  body: string
  lang: AppLocale
  /** 알림을 눌렀을 때 열 주소. 같은 출처의 경로다. */
  url: string
}

/**
 * 푸시 하나를 기기 알림으로 바꾼다.
 *
 * 본문을 못 읽어도 알림은 띄운다. iOS는 푸시마다 눈에 보이는 알림이 있어야 하고, 그러지 않은
 * 구독은 몇 번 뒤에 취소한다. 이때는 내용을 알 수 없으므로 알림 목록을 열어 준다.
 */
export function pushNotificationContent(payload: PushPayload | null): PushNotificationContent {
  if (payload === null) {
    return {
      title: APP_NAME,
      body: MESSAGES[FALLBACK_LOCALE].notification.title,
      lang: FALLBACK_LOCALE,
      url: '/notifications',
    }
  }

  const lang = isSupportedLocale(payload.language) ? payload.language : FALLBACK_LOCALE
  const kind = toNotificationKind(payload.type)

  const body = fill(MESSAGES[lang].notification.item[kind], {
    actor: payload.actorName,
    gathering: payload.gatheringName,
    amount: formatAmount(payload.amount, lang, payload.currencyCode),
  })

  /*
   * 알림 목록에서 항목을 눌렀을 때와 같은 주소다. 어디서 왔는지(`origin`)와 어느 탭인지(`side`)를
   * 남겨야 정산 상세에서 뒤로 갔을 때 알림 목록으로 돌아온다.
   */
  const query = new URLSearchParams(settlementReturnQuery(settlementSideFor(kind))).toString()

  return {
    title: APP_NAME,
    body,
    lang,
    url: `/settlements/${payload.settlementId}?${query}`,
  }
}

/** 문구의 `{이름}` 자리를 채운다. 값이 없는 자리는 그대로 둔다. */
function fill(template: string, values: Record<string, string>): string {
  return template.replace(/\{(\w+)\}/g, (placeholder, name: string) => values[name] ?? placeholder)
}

/** 통화 코드가 잘못돼도 금액 표기 하나 때문에 알림이 사라지지 않게 숫자만이라도 보인다. */
function formatAmount(amount: number, locale: AppLocale, currencyCode: string): string {
  try {
    return formatCurrency(amount, locale, currencyCode)
  } catch {
    return String(amount)
  }
}
