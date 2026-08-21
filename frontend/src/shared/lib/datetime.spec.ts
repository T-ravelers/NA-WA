import { describe, expect, it } from 'vitest'

import {
  buildCalendarMonth,
  formatCalendarDate,
  formatCalendarDateString,
  formatServerDateTime,
  parseCalendarDate,
  parseServerDateTime,
  serializeCalendarDate,
  shiftCalendarMonth,
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

describe('buildCalendarMonth', () => {
  it('always fills six weeks so the grid height does not jump', () => {
    expect(buildCalendarMonth(new Date(2026, 7, 1))).toHaveLength(42)
    expect(buildCalendarMonth(new Date(2026, 1, 1))).toHaveLength(42)
  })

  /** 2026-08-01은 토요일이라 앞자리 여섯 칸을 7월에서 채운다. */
  it('pads the first week with the previous month', () => {
    const cells = buildCalendarMonth(new Date(2026, 7, 1))

    expect(cells[0]).toEqual({ date: '2026-07-26', day: 26, inMonth: false })
    expect(cells[5]).toEqual({ date: '2026-07-31', day: 31, inMonth: false })
    expect(cells[6]).toEqual({ date: '2026-08-01', day: 1, inMonth: true })
  })

  it('pads the last week with the next month', () => {
    const cells = buildCalendarMonth(new Date(2026, 7, 1))
    const last = cells[41]

    expect(last?.inMonth).toBe(false)
    expect(last?.date.startsWith('2026-09')).toBe(true)
  })

  it('keeps every day of the month', () => {
    const august = buildCalendarMonth(new Date(2026, 7, 1)).filter((cell) => cell.inMonth)

    expect(august).toHaveLength(31)
    expect(august[0]?.date).toBe('2026-08-01')
    expect(august[30]?.date).toBe('2026-08-31')
  })

  it('handles a leap February', () => {
    const february = buildCalendarMonth(new Date(2028, 1, 1)).filter((cell) => cell.inMonth)

    expect(february).toHaveLength(29)
    expect(february[28]?.date).toBe('2028-02-29')
  })
})

describe('shiftCalendarMonth', () => {
  it('moves by whole months and lands on the first', () => {
    expect(serializeCalendarDate(shiftCalendarMonth(new Date(2026, 7, 15), 1))).toBe('2026-09-01')
    expect(serializeCalendarDate(shiftCalendarMonth(new Date(2026, 7, 15), -1))).toBe('2026-07-01')
  })

  /** 31일에서 2월로 옮겨도 3월로 튀지 않아야 한다. */
  it('does not overflow from a long month into the next one', () => {
    expect(serializeCalendarDate(shiftCalendarMonth(new Date(2026, 0, 31), 1))).toBe('2026-02-01')
  })

  it('crosses the year boundary', () => {
    expect(serializeCalendarDate(shiftCalendarMonth(new Date(2026, 11, 1), 1))).toBe('2027-01-01')
  })
})
