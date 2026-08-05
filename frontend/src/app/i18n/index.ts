import { createI18n } from 'vue-i18n'

import { DEFAULT_LOCALE, FALLBACK_LOCALE } from './locales'
import { buildMessages } from './messages'

export const i18n = createI18n({
  legacy: false,
  locale: DEFAULT_LOCALE,
  fallbackLocale: FALLBACK_LOCALE,
  messages: buildMessages(),
})

export { DEFAULT_LOCALE, FALLBACK_LOCALE, SUPPORTED_LOCALES, isSupportedLocale } from './locales'
export type { AppLocale } from './locales'
