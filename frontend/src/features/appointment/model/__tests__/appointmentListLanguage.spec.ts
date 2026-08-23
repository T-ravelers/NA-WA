import { describe, expect, it } from 'vitest'

import { defaultCreateLanguage, defaultListLanguage } from '../appointmentListLanguage'

describe('appointmentListLanguage', () => {
  // 회원 언어와 약속 언어는 값 집합이 같아서 로케일을 그대로 쓴다.
  it('starts from the member language', () => {
    expect(defaultListLanguage('en')).toBe('en')
    expect(defaultListLanguage('ja')).toBe('ja')
    expect(defaultListLanguage('zh-TW')).toBe('zh-TW')
  })

  // 지원하지 않는 로케일(한국어처럼)에서는 거를 언어가 없다.
  it('falls back to every language for an unsupported locale', () => {
    expect(defaultListLanguage('ko')).toBe('ALL')
  })

  // 생성 기본값이 목록 기본값과 어긋나면, 일본어 회원이 폼을 그대로 두고 만든 약속이
  // en으로 잡혀 ja로 걸린 목록에서 사라진다.
  it('starts a new appointment in the same language the list filters by', () => {
    for (const locale of ['en', 'ja', 'zh-TW', 'vi']) {
      expect(defaultCreateLanguage(locale)).toBe(defaultListLanguage(locale))
    }
  })

  // 약속 언어는 반드시 하나여야 해서 'ALL'로 물러설 자리가 없다.
  it('keeps en as the create default for an unsupported locale', () => {
    expect(defaultCreateLanguage('ko')).toBe('en')
  })
})
