import { createI18n } from 'vue-i18n'

import { ko } from './locales/ko'

export const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  fallbackLocale: 'ko',
  messages: {
    ko,
  },
})
