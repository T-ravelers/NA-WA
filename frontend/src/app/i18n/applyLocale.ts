import { resolveInitialLocale, storeLocale } from '@/shared/i18n/localePreference'
import type { AppLocale } from '@/shared/i18n/locales'

import { i18n } from './index'

interface ApplyLocaleOptions {
  /**
   * 사용자가 직접 고른 결과일 때만 `true`.
   *
   * 브라우저 언어에서 감지한 값을 저장하면 "키의 존재 = 명시 선택" 규약이 깨진다.
   */
  persist?: boolean
}

/**
 * 화면 로케일을 바꾼다.
 *
 * 적용과 저장을 분리한다. 초기 진입에서 감지한 값은 적용만 하고 저장하지 않는다.
 */
export function applyLocale(locale: AppLocale, options: ApplyLocaleOptions = {}): void {
  i18n.global.locale.value = locale

  // 스크린 리더와 브라우저 번역 제안이 이 값을 본다.
  document.documentElement.lang = locale

  if (options.persist === true) {
    storeLocale(locale)
  }
}

/**
 * 앱 시작 시 로케일을 적용한다.
 *
 * `createI18n`은 vue-i18n의 locale만 정하므로, `index.html`에 박혀 있는
 * `<html lang="en">`은 그대로 남는다. 저장된 로케일이 `en`이 아닌 사용자는 새로고침
 * 이후 화면 언어와 `lang`이 어긋나 스크린 리더 발음과 브라우저 번역 제안이 틀어진다.
 *
 * 감지 결과일 수 있으므로 저장하지 않는다.
 */
export function bootstrapLocale(): void {
  applyLocale(resolveInitialLocale())
}
