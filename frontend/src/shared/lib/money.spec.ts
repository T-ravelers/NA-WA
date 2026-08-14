import { describe, expect, it } from 'vitest'

import { formatCurrency, formatGroupedDecimal, formatKrw, formatMoney, formatNumber } from './money'

describe('money formatters', () => {
  it('preserves current locale-aware number and currency output', () => {
    expect(formatNumber(1234.5, 'en-US', { maximumFractionDigits: 2 })).toBe('1,234.5')
    expect(formatCurrency(1234, 'en-US', 'KRW', { maximumFractionDigits: 0 })).toBe('₩1,234')
    expect(formatKrw(18_500)).toBe('₩18,500')
  })

  it('returns an empty string for nullable or invalid values', () => {
    expect(formatMoney(null, 'en-US')).toBe('')
    expect(formatMoney(undefined, 'en-US')).toBe('')
    expect(formatMoney('not-a-number', 'en-US')).toBe('')
    expect(formatMoney('not-a-number', 'en-US', { style: 'currency', currency: 'USD' })).toBe('')
    expect(formatKrw(null)).toBe('')
  })

  it('applies signDisplay to exact decimal strings after rounding', () => {
    expect(formatGroupedDecimal('1234.5', 'en-US', { signDisplay: 'exceptZero' })).toBe('+1,234.5')
    expect(formatGroupedDecimal('-1234.5', 'en-US', { signDisplay: 'never' })).toBe('1,234.5')
    expect(
      formatGroupedDecimal('0.004', 'en-US', {
        maximumFractionDigits: 2,
        signDisplay: 'exceptZero',
      }),
    ).toBe('0.00')
    expect(
      formatMoney('1234.5', 'en-US', {
        style: 'currency',
        currency: 'USD',
        signDisplay: 'exceptZero',
      }),
    ).toBe('+$1,234.50')
  })

  it('groups wallet decimal strings without converting through Number', () => {
    const large = '123456789012345678901234567890.123400'
    expect(formatGroupedDecimal(large, 'en-US')).toBe(
      '123,456,789,012,345,678,901,234,567,890.123400',
    )
    expect(formatGroupedDecimal('-10000000000000000000.5000', 'en-US')).toBe(
      '-10,000,000,000,000,000,000.5000',
    )
  })
})
