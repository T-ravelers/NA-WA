/**
 * 지원 로케일.
 *
 * NA-WA는 방한 외국인을 대상으로 하므로 한국어는 서비스 로케일이 아니다.
 * 문구는 `en`을 원본으로 작성하고, 번역되지 않은 key는 `en`으로 폴백한다.
 */
export const SUPPORTED_LOCALES = ['en', 'ja', 'zh-TW', 'vi'] as const

export type AppLocale = (typeof SUPPORTED_LOCALES)[number]

export const DEFAULT_LOCALE: AppLocale = 'en'

export const FALLBACK_LOCALE: AppLocale = 'en'

export function isSupportedLocale(value: string): value is AppLocale {
  return (SUPPORTED_LOCALES as readonly string[]).includes(value)
}

/**
 * 언어 이름은 그 언어 자신의 표기로 적는다.
 *
 * 영어로만 적으면 정작 그 언어 사용자가 자기 언어를 찾지 못한다. `english`는 보조 표기이며
 * 시트처럼 두 줄을 그리는 곳에서만 쓴다.
 *
 * **번역하지 않으므로 i18n 메시지가 아니라 여기 상수다.** 로그인 화면·프로필·언어 시트 세
 * 곳이 같은 표를 따로 들고 있었다.
 */
export const LOCALE_LABEL: Record<AppLocale, { native: string; english: string }> = {
  en: { native: 'English', english: 'English' },
  ja: { native: '日本語', english: 'Japanese' },
  'zh-TW': { native: '繁體中文', english: 'Chinese (Traditional)' },
  vi: { native: 'Tiếng Việt', english: 'Vietnamese' },
}

/** 언어 이름 한 줄만 필요한 곳(설정 행·로그인 화면)의 짧은 형태. */
export function nativeLocaleLabel(locale: AppLocale): string {
  return LOCALE_LABEL[locale].native
}
