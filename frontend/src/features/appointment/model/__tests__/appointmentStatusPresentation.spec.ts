import { describe, expect, it } from 'vitest'

import type { AppointmentStatus } from '@/shared/lib/appointmentStatus'
import { appointmentStatusTone } from '../appointmentStatusPresentation'

describe('appointmentStatusTone', () => {
  it.each([
    ['RECRUITING', 'ongoing'],
    ['FULL', 'scheduled'],
    ['IN_PROGRESS', 'info'],
    ['AWAITING_ATTENDANCE', 'pending'],
    ['COMPLETED', 'neutral'],
  ] as const)('maps the active lifecycle state %s to %s', (status, tone) => {
    expect(appointmentStatusTone(status)).toBe(tone)
  })

  it.each([
    ['PAYMENT_PENDING', 'pending'],
    ['CANCELLED', 'danger'],
  ] as const)('keeps the fallback state %s renderable as %s', (status, tone) => {
    expect(appointmentStatusTone(status as AppointmentStatus)).toBe(tone)
  })

  it('uses neutral when status is unavailable', () => {
    expect(appointmentStatusTone(undefined)).toBe('neutral')
  })
})
