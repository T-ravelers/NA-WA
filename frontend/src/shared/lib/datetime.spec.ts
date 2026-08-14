import { describe, expect, it } from 'vitest'

import {
  formatCalendarDate,
  formatCalendarDateString,
  formatServerDateTime,
  parseCalendarDate,
  parseServerDateTime,
  serializeCalendarDate,
} from './datetime'

describe('parseServerDateTime', () => {
  it('interprets offset-free strings and component arrays as KST', () => {
    expect(parseServerDateTime('2026-07-25T23:30:00')?.toISOString()).toBe(
      '2026-07-25T14:30:00.000Z',
    )
    expect(parseServerDateTime([2026, 7, 25, 23, 30])?.toISOString()).toBe(
      '2026-07-25T14:30:00.000Z',
    )
  })

  it('respects explicit offsets and nanosecond precision up to milliseconds', () => {
    expect(parseServerDateTime('2026-07-25T12:00:00Z')?.toISOString()).toBe(
      '2026-07-25T12:00:00.000Z',
    )
    expect(parseServerDateTime('2026-08-08T18:30:00+09:00')?.toISOString()).toBe(
      '2026-08-08T09:30:00.000Z',
    )
    expect(parseServerDateTime([2026, 7, 25, 12, 0, 0, 123_456_789])?.toISOString()).toBe(
      '2026-07-25T03:00:00.123Z',
    )
  })

  it('accepts only three through seven finite integer components', () => {
    expect(parseServerDateTime([2026, 7, 25])).not.toBeNull()
    expect(parseServerDateTime([2026, 7])).toBeNull()
    expect(parseServerDateTime([2026, 7, 25, 12, 0, 0, 0, 0])).toBeNull()
    const sparse = Array<number>(5)
    sparse[0] = 2026
    sparse[1] = 7
    sparse[2] = 25
    sparse[4] = 0
    expect(parseServerDateTime(sparse)).toBeNull()
    expect(parseServerDateTime([2026, 7, 25.5])).toBeNull()
    expect(parseServerDateTime([2026, 7, Number.NaN])).toBeNull()
  })

  it('rejects invalid calendar and clock values instead of normalizing them', () => {
    expect(parseServerDateTime([2026, 2, 29, 12])).toBeNull()
    expect(parseServerDateTime([2024, 2, 29, 12])).not.toBeNull()
    expect(parseServerDateTime([2026, 13, 1, 12])).toBeNull()
    expect(parseServerDateTime([2026, 1, 1, 24])).toBeNull()
    expect(parseServerDateTime([2026, 1, 1, 12, 60])).toBeNull()
    expect(parseServerDateTime([2026, 1, 1, 12, 0, 60])).toBeNull()
    expect(parseServerDateTime([2026, 1, 1, 12, 0, 0, 1_000_000_000])).toBeNull()
    expect(parseServerDateTime('2026-02-30T12:00:00')).toBeNull()
    expect(parseServerDateTime(null)).toBeNull()
    expect(parseServerDateTime(undefined)).toBeNull()
    expect(parseServerDateTime('')).toBeNull()
  })
})

describe('server instant display', () => {
  it('always displays in KST and returns an empty string for invalid input', () => {
    expect(
      formatServerDateTime('2026-08-07T12:18:02Z', 'en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
      }),
    ).toBe('Aug 7, 2026, 9:18 PM')
    expect(formatServerDateTime('not-a-date', 'en-US', { dateStyle: 'medium' })).toBe('')
    expect(formatServerDateTime(null, 'en-US', { dateStyle: 'medium' })).toBe('')
  })
})

describe('calendar-only helpers', () => {
  it('round-trips local calendar cells without server instant conversion', () => {
    const date = parseCalendarDate('2026-08-09')
    expect(date).not.toBeNull()
    expect(serializeCalendarDate(date)).toBe('2026-08-09')
    expect(formatCalendarDate('2026-08-09', 'en-US', { dateStyle: 'medium' })).toBe('Aug 9, 2026')
    expect(formatCalendarDateString('2026-08-09')).toBe('2026.08.09')
  })

  it('returns empty strings for invalid calendar display values', () => {
    expect(parseCalendarDate('2026-02-30')).toBeNull()
    expect(formatCalendarDate('2026-02-30', 'en-US')).toBe('')
    expect(formatCalendarDateString(null)).toBe('')
    expect(serializeCalendarDate(new Date(Number.NaN))).toBe('')
  })
})
