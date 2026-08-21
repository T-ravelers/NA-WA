import { describe, expect, it } from 'vitest'

import { appointmentStatusTone } from '../appointmentStatus'

describe('appointmentStatusTone', () => {
  it('gives each real stage its own tone', () => {
    expect(appointmentStatusTone('RECRUITING')).toBe('ongoing')
    expect(appointmentStatusTone('CLOSED')).toBe('scheduled')
    expect(appointmentStatusTone('IN_PROGRESS')).toBe('info')
    expect(appointmentStatusTone('AWAITING_ATTENDANCE')).toBe('pending')
    expect(appointmentStatusTone('COMPLETED')).toBe('completed')
  })

  it('keeps in-progress and awaiting-attendance apart from completed and cancelled', () => {
    const done = [appointmentStatusTone('COMPLETED'), appointmentStatusTone('CANCELLED')]

    expect(done).not.toContain(appointmentStatusTone('IN_PROGRESS'))
    expect(done).not.toContain(appointmentStatusTone('AWAITING_ATTENDANCE'))
  })

  it('falls back to neutral for unreachable, transaction-only, or unknown values', () => {
    expect(appointmentStatusTone('CANCELLED')).toBe('neutral')
    expect(appointmentStatusTone('PAYMENT_PENDING')).toBe('neutral')
    expect(appointmentStatusTone('SOMETHING_NEW')).toBe('neutral')
    expect(appointmentStatusTone(undefined)).toBe('neutral')
  })
})
