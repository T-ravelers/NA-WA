import { describe, expect, it } from 'vitest'

import { formatEnglishOrdinal, formatMoney, formatPercent, formatSignedPercent } from '../format'

describe('report presentation format', () => {
  it('writes money in points with thousands separators', () => {
    expect(formatMoney(1284500, 'en')).toBe('1,284,500 P')
    expect(formatMoney(0, 'en')).toBe('0 P')
  })

  // 입력은 0–100 스케일이다. 0–1로 착각하면 42%가 4200%가 된다.
  it('takes percentages on a 0–100 scale and rounds to whole numbers', () => {
    expect(formatPercent(42, 'en')).toBe('42%')
    expect(formatPercent(77.85, 'en')).toBe('78%')
  })

  // 코호트 대비 차이(#421). 경계는 눈으로 짐작하기 어려워 값을 고정한다.
  it('signs share differences, and leaves a rounded zero unsigned', () => {
    expect(formatSignedPercent(12, 'en')).toBe('+12%')
    expect(formatSignedPercent(5.4, 'en')).toBe('+5%')
    expect(formatSignedPercent(-16.4, 'en')).toBe('-16%')
    expect(formatSignedPercent(0, 'en')).toBe('0%')
    expect(formatSignedPercent(-0.2, 'en')).toBe('0%')
  })

  it('follows the locale it is given', () => {
    expect(formatMoney(1284500, 'ja')).toBe('1,284,500 P')
    expect(formatSignedPercent(-8, 'vi')).toBe('-8%')
  })

  it('formats English ordinal ranks from 1 through 30', () => {
    const expected = [
      '1st',
      '2nd',
      '3rd',
      '4th',
      '5th',
      '6th',
      '7th',
      '8th',
      '9th',
      '10th',
      '11th',
      '12th',
      '13th',
      '14th',
      '15th',
      '16th',
      '17th',
      '18th',
      '19th',
      '20th',
      '21st',
      '22nd',
      '23rd',
      '24th',
      '25th',
      '26th',
      '27th',
      '28th',
      '29th',
      '30th',
    ]

    expect(expected.map((_, index) => formatEnglishOrdinal(index + 1))).toEqual(expected)
  })

  it('keeps 111, 112, and 113 in the th ordinal category', () => {
    expect([111, 112, 113].map(formatEnglishOrdinal)).toEqual(['111th', '112th', '113th'])
  })
})
