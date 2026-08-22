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

    // 번역 파일이 모든 key를 채우면 "빠진 key"가 없어진다. 폴백을 보려면 en에만 있는 key를 만든다.
    i18n.global.mergeLocaleMessage('en', { fallbackProbe: 'Probe message' })
    locale.value = 'ja'

    expect(t('fallbackProbe')).toBe('Probe message')
    expect(t('fallbackProbe')).not.toBe('fallbackProbe')

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
