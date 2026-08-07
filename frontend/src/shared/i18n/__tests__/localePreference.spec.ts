import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  detectBrowserLocale,
  readStoredLocale,
  resolveInitialLocale,
  storeLocale,
} from '../localePreference'

function setBrowserLanguages(languages: string[]): void {
  vi.spyOn(window.navigator, 'languages', 'get').mockReturnValue(languages)
}

describe('localePreference', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns null when the visitor has never chosen a locale', () => {
    expect(readStoredLocale()).toBeNull()
  })

  it('round-trips an explicit choice', () => {
    storeLocale('ja')

    expect(readStoredLocale()).toBe('ja')
  })

  it('ignores a stored value that is no longer supported', () => {
    localStorage.setItem('nawa.locale', 'ko')

    expect(readStoredLocale()).toBeNull()
  })

  it('matches the browser language exactly when supported', () => {
    setBrowserLanguages(['zh-TW', 'en'])

    expect(detectBrowserLocale()).toBe('zh-TW')
  })

  it('falls back to the base language when the region tag is unknown', () => {
    setBrowserLanguages(['ja-JP'])

    expect(detectBrowserLocale()).toBe('ja')
  })

  it('takes the first supported language rather than the first listed', () => {
    setBrowserLanguages(['ko-KR', 'vi'])

    expect(detectBrowserLocale()).toBe('vi')
  })

  it('falls back to en when no browser language is supported', () => {
    setBrowserLanguages(['ko-KR', 'th'])

    expect(detectBrowserLocale()).toBe('en')
  })

  it('prefers the stored choice over the browser language', () => {
    setBrowserLanguages(['vi'])
    storeLocale('ja')

    expect(resolveInitialLocale()).toBe('ja')
  })

  it('uses the browser language when nothing was stored', () => {
    setBrowserLanguages(['vi'])

    expect(resolveInitialLocale()).toBe('vi')
  })

  // 규약: 키의 존재가 곧 "사용자가 직접 골랐다"는 뜻이다. 감지 결과를 저장하면 깨진다.
  it('does not store the detected locale', () => {
    setBrowserLanguages(['vi'])

    resolveInitialLocale()

    expect(localStorage.getItem('nawa.locale')).toBeNull()
  })
})
