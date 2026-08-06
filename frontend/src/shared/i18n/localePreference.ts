import { DEFAULT_LOCALE, isSupportedLocale, type AppLocale } from './locales'

/**
 * 사용자가 직접 고른 로케일을 담는 키.
 *
 * **키의 존재 자체가 "명시 선택"을 뜻한다.** 브라우저 언어에서 감지한 값은 저장하지
 * 않는다. 저장하면 감지 결과와 사용자 선택을 구분할 수 없게 되고, 로그인 시 서버 값을
 * 채택해야 할 상황에서 잘못 덮어쓴다.
 *
 * 서버의 `members.preferred_language`가 `NOT NULL DEFAULT 'en'`이라 "사용자가 en을
 * 골랐다"와 "아무도 고른 적 없다"를 서버에서는 구분할 수 없다. 그 구분을 여기서 한다.
 */
const STORAGE_KEY = 'nawa.locale'

/** 사용자가 직접 고른 로케일. 고른 적이 없거나 값이 더 이상 지원되지 않으면 `null`. */
export function readStoredLocale(): AppLocale | null {
  const stored = localStorage.getItem(STORAGE_KEY)

  if (stored === null || !isSupportedLocale(stored)) {
    return null
  }

  return stored
}

/** 사용자가 직접 골랐을 때만 호출한다. */
export function storeLocale(locale: AppLocale): void {
  localStorage.setItem(STORAGE_KEY, locale)
}

/**
 * 브라우저 언어에서 지원 로케일을 고른다.
 *
 * `ja-JP`처럼 지역 태그가 붙은 값은 기본 언어(`ja`)로 낮춰 다시 맞춰본다. 다만
 * `zh-CN`·`zh-TW`는 그 자체가 지원 로케일이므로 완전 일치를 먼저 시도한다.
 */
export function detectBrowserLocale(): AppLocale {
  for (const language of navigator.languages) {
    if (isSupportedLocale(language)) {
      return language
    }

    const base = language.split('-')[0]

    if (isSupportedLocale(base)) {
      return base
    }
  }

  return DEFAULT_LOCALE
}

/** 앱 시작 시 적용할 로케일. 저장하지 않는다. */
export function resolveInitialLocale(): AppLocale {
  return readStoredLocale() ?? detectBrowserLocale()
}
