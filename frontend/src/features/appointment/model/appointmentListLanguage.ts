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
