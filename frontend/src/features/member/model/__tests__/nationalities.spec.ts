import { describe, expect, it } from 'vitest'

import {
  NATIONALITY_CODES,
  isNationalityCode,
  nationalityName,
  nationalityOptions,
} from '../nationalities'

describe('nationalities', () => {
  /*
   * 백엔드 `Locale.getISOCountries()`(Java 17)가 249개다. 프론트가 더 넓으면 고른 값이
   * 저장 단계에서 MEMBER-005로 튕기고, 좁으면 실제 국적을 고를 수 없다.
   */
  it('carries the same 249 codes the server validates against', () => {
    expect(NATIONALITY_CODES).toHaveLength(249)
    expect(new Set(NATIONALITY_CODES).size).toBe(249)
  })

  it('every code is two upper-case letters', () => {
    expect(NATIONALITY_CODES.filter((code) => !/^[A-Z]{2}$/.test(code))).toEqual([])
  })

  /* `Intl`이 아는 region은 이보다 넓다. 폐지·예약 코드가 새어 들어오면 안 된다. */
  it('leaves out withdrawn and reserved codes that Intl still knows', () => {
    for (const code of ['AN', 'SU', 'YU', 'EU', 'UK', 'XK', 'ZZ']) {
      expect(isNationalityCode(code)).toBe(false)
    }
  })

  it('names a country in the asked language', () => {
    expect(nationalityName('JP', 'en')).toBe('Japan')
    expect(nationalityName('JP', 'ja')).toBe('日本')
  })

  it('has no name for an empty, unknown, or malformed code', () => {
    expect(nationalityName(null, 'en')).toBeNull()
    expect(nationalityName('', 'en')).toBeNull()
    expect(nationalityName('not-a-code', 'en')).toBeNull()
  })

  /*
   * `Intl`은 예약 코드에 코드와 다른 이름을 준다. "이름이 코드와 같으면 모르는 것"이라는
   * 판정만 두었더니 프로필에 `From Unknown Region`이 찍혔다.
   */
  it('does not name a reserved code that Intl still describes', () => {
    expect(new Intl.DisplayNames(['en'], { type: 'region' }).of('ZZ')).toBe('Unknown Region')
    expect(nationalityName('ZZ', 'en')).toBeNull()
  })

  it('sorts the options by the name shown in that language', () => {
    const english = nationalityOptions('en').map((option) => option.name)

    expect(english).toHaveLength(249)
    const collator = new Intl.Collator('en')

    expect([...english].sort((a, b) => collator.compare(a, b))).toEqual(english)
  })

  it('reorders when the language changes', () => {
    const english = nationalityOptions('en').map((option) => option.code)
    const japanese = nationalityOptions('ja').map((option) => option.code)

    expect(japanese).not.toEqual(english)
    expect([...japanese].sort()).toEqual([...english].sort())
  })
})
