import { describe, expect, it } from 'vitest'

import { i18n } from '../index'
import { DEFAULT_LOCALE, FALLBACK_LOCALE, SUPPORTED_LOCALES } from '@/shared/i18n/locales'
import { buildMessages } from '../messages'

describe('i18n messages', () => {
  it('uses en as the default and fallback locale', () => {
    expect(DEFAULT_LOCALE).toBe('en')
    expect(FALLBACK_LOCALE).toBe('en')
  })

  it('does not support Korean', () => {
    expect(SUPPORTED_LOCALES).not.toContain('ko')
  })

  it('creates an entry for every supported locale', () => {
    const messages = buildMessages()

    for (const locale of SUPPORTED_LOCALES) {
      expect(messages[locale]).toBeDefined()
    }
  })

  it('collects messages from shared and feature modules', () => {
    const messages = buildMessages()

    // shared/i18n/en.ts
    expect(messages.en.app).toBeDefined()
    // features/auth/i18n/en.ts
    expect(messages.en.auth).toBeDefined()
  })

  it('falls back to en instead of exposing the raw key', () => {
    const { t, locale } = i18n.global

    locale.value = 'ja'

    // ja는 아직 번역이 없으므로 en 문구가 나와야 한다.
    expect(t('app.tagline')).toBe('Plan, travel and settle up together')
    expect(t('app.tagline')).not.toBe('app.tagline')

    locale.value = DEFAULT_LOCALE
  })

  it('maps every backend auth error code to a message', () => {
    const { t } = i18n.global

    for (let code = 1; code <= 18; code += 1) {
      const key = `auth.errorCode.AUTH-${String(code).padStart(3, '0')}`

      expect(t(key)).not.toBe(key)
    }
  })
})
