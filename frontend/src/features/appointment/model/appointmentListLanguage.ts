import { isSupportedLocale } from '@/shared/i18n/locales'

import type { AppointmentLanguage } from '../api/appointmentApi'

export type AppointmentLanguageFilter = 'ALL' | AppointmentLanguage

/**
 * 회원이 고른 언어를 약속 목록의 기본 언어 칩으로 바꾼다.
 *
 * 약속 언어(`en`·`ja`·`zh-TW`·`vi`)와 지원 로케일의 값 집합이 같아서 앱 로케일을 그대로
 * 쓴다. 회원 언어는 로그인 시 `members.preferred_language`와 맞춰지므로(localeSync)
 * 목록이 회원 조회를 따로 하지 않아도 같은 값을 본다.
 *
 * 고른 칩을 저장하지는 않는다. 목록에 들어올 때는 언제나 회원 언어에서 시작하고,
 * 다른 언어를 보는 것은 그 화면에 머무는 동안의 일이다.
 */
export function defaultListLanguage(locale: string): AppointmentLanguageFilter {
  return isSupportedLocale(locale) ? locale : 'ALL'
}

/**
 * 회원이 고른 언어를 약속 생성 폼의 기본 약속 언어로 바꾼다.
 *
 * 목록과 같은 근거를 쓴다. 두 기본값이 어긋나면 일본어 회원이 폼을 그대로 두고 만든
 * 약속이 `en`으로 잡히는데, 돌아온 목록은 `ja`로 걸러 **자기가 방금 만든 약속이
 * 목록에 없다.** 빈 결과일 때 전체로 되돌리는 구제도 다른 `ja` 약속이 하나라도 있으면
 * 걸리지 않는다.
 *
 * 목록과 달리 'ALL'로 물러설 자리가 없다 — 약속 언어는 반드시 하나여야 한다. 지원하지
 * 않는 로케일에서는 기존 기본값인 `en`을 유지한다.
 */
export function defaultCreateLanguage(locale: string): AppointmentLanguage {
  return isSupportedLocale(locale) ? locale : 'en'
}
