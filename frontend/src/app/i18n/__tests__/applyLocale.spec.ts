import { beforeEach, describe, expect, it } from 'vitest'

import { applyLocale } from '../applyLocale'
import { i18n } from '../index'

describe('applyLocale', () => {
  beforeEach(() => {
    localStorage.clear()
    i18n.global.locale.value = 'en'
    document.documentElement.lang = 'en'
  })

  it('switches the active locale', () => {
    applyLocale('ja')

    expect(i18n.global.locale.value).toBe('ja')
  })

  /*
   * "실제로 번역이 바뀌는가"는 아직 단언할 수 없다. ja·zh-CN·zh-TW·vi 메시지 파일이
   * 전부 빈 스텁이라 모든 key가 en으로 폴백한다. 번역이 채워지면 그때 추가한다.
   */

  it('keeps the document language in sync for screen readers', () => {
    applyLocale('vi')

    expect(document.documentElement.lang).toBe('vi')
  })

  // 규약: 감지된 값까지 저장하면 "키의 존재 = 명시 선택"이 깨진다.
  it('does not persist by default', () => {
    applyLocale('ja')

    expect(localStorage.getItem('nawa.locale')).toBeNull()
  })

  it('persists when the caller marks the change as an explicit choice', () => {
    applyLocale('zh-TW', { persist: true })

    expect(localStorage.getItem('nawa.locale')).toBe('zh-TW')
  })
})
