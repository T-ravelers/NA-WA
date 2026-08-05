/**
 * 지원 로케일.
 *
 * NA-WA는 방한 외국인을 대상으로 하므로 한국어는 서비스 로케일이 아니다.
 * 문구는 `en`을 원본으로 작성하고, 번역되지 않은 key는 `en`으로 폴백한다.
 */
export const SUPPORTED_LOCALES = ['en', 'ja', 'zh-CN', 'zh-TW', 'vi'] as const

export type AppLocale = (typeof SUPPORTED_LOCALES)[number]

export const DEFAULT_LOCALE: AppLocale = 'en'

export const FALLBACK_LOCALE: AppLocale = 'en'

export function isSupportedLocale(value: string): value is AppLocale {
  return (SUPPORTED_LOCALES as readonly string[]).includes(value)
}
