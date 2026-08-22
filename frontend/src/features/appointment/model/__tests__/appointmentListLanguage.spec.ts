import { describe, expect, it } from 'vitest'

import { defaultListLanguage } from '../appointmentListLanguage'

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
})
