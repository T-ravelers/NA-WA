import { describe, expect, it } from 'vitest'

import { parseAppointmentDateTime } from '../appointmentDateTime'

describe('parseAppointmentDateTime', () => {
  it('interprets an offset-free appointment time as Asia/Seoul', () => {
    expect(parseAppointmentDateTime('2026-08-08T18:30:00')?.toISOString()).toBe(
      '2026-08-08T09:30:00.000Z',
    )
  })

  it('preserves an explicit server offset', () => {
    expect(parseAppointmentDateTime('2026-08-08T18:30:00+09:00')?.toISOString()).toBe(
      '2026-08-08T09:30:00.000Z',
    )
  })

  it('returns null for missing or invalid values', () => {
    expect(parseAppointmentDateTime(null)).toBeNull()
    expect(parseAppointmentDateTime('')).toBeNull()
    expect(parseAppointmentDateTime('not-a-date')).toBeNull()
  })
})
