import { createI18n } from 'vue-i18n'

import { FALLBACK_LOCALE } from '@/shared/i18n/locales'
import { resolveInitialLocale } from '@/shared/i18n/localePreference'

import { buildMessages } from './messages'

export const i18n = createI18n({
  legacy: false,
  // 저장된 명시 선택 → 브라우저 언어 → en. 감지 결과는 저장하지 않는다.
  locale: resolveInitialLocale(),
  fallbackLocale: FALLBACK_LOCALE,
  messages: buildMessages(),
})

export {
  DEFAULT_LOCALE,
  FALLBACK_LOCALE,
  SUPPORTED_LOCALES,
  isSupportedLocale,
} from '@/shared/i18n/locales'
export type { AppLocale } from '@/shared/i18n/locales'
