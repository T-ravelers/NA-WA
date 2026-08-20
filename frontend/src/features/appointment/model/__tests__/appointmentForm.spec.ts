import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  toAppointmentCreateRequest,
  validateAppointmentSchedule,
  type AppointmentFormDraft,
} from '../appointmentForm'

function baseDraft(overrides: Partial<AppointmentFormDraft> = {}): AppointmentFormDraft {
  return {
    itemId: 42,
    itemType: 'EVENT',
    tripId: 7,
    visitDate: '2026-08-20',
    appointmentName: 'Seongsu K-Beauty Tour',
    maxMembers: 4,
    languageCode: 'en',
    depositAmount: 10_000,
    meetingPlaceMode: 'ITEM' as const,
    meetingPlace: 'Seongsu Beauty Lab',
    activityStartTime: '18:30',
    activityEndTime: '22:00',
    joinDeadline: '2026-08-19T17:30',
    ...overrides,
  }
}

describe('validateAppointmentSchedule', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-19T12:00:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('rejects a start time earlier than now on the same visit date', () => {
    const errors = validateAppointmentSchedule(
      baseDraft({ visitDate: '2026-08-19', activityStartTime: '11:00' }),
    )

    expect(errors.activityStartTime).toBe('appointment.create.validation.startInPast')
  })

  it('allows a start time later than now on the same visit date', () => {
    const errors = validateAppointmentSchedule(
      baseDraft({ visitDate: '2026-08-19', activityStartTime: '13:00' }),
    )

    expect(errors.activityStartTime).toBeUndefined()
  })

  it('does not apply the past-time check to a future visit date', () => {
    const errors = validateAppointmentSchedule(
      baseDraft({ visitDate: '2026-08-20', activityStartTime: '00:01' }),
    )

    expect(errors.activityStartTime).toBeUndefined()
  })

  it('rejects an end time that is not after the start time', () => {
    const errors = validateAppointmentSchedule(
      baseDraft({ activityStartTime: '18:30', activityEndTime: '18:30' }),
    )

    expect(errors.activityEndTime).toBe('appointment.create.validation.endAfterStart')
  })

  it('rejects a join deadline on or after the assembled activity start', () => {
    const errors = validateAppointmentSchedule(
      baseDraft({
        visitDate: '2026-08-20',
        activityStartTime: '18:30',
        joinDeadline: '2026-08-20T19:00',
      }),
    )

    expect(errors.joinDeadline).toBe('appointment.create.validation.deadlineBeforeStart')
  })

  it('accepts a join deadline before the assembled activity start', () => {
    const errors = validateAppointmentSchedule(
      baseDraft({
        visitDate: '2026-08-20',
        activityStartTime: '18:30',
        joinDeadline: '2026-08-20T18:00',
      }),
    )

    expect(errors.joinDeadline).toBeUndefined()
  })
})

describe('toAppointmentCreateRequest', () => {
  it('assembles the journey and time-only fields into the request shape', () => {
    const request = toAppointmentCreateRequest(baseDraft())

    expect(request).toEqual({
      itemId: 42,
      itemType: 'EVENT',
      tripId: 7,
      visitDate: '2026-08-20',
      languageCode: 'en',
      appointmentName: 'Seongsu K-Beauty Tour',
      maxMembers: 4,
      joinDeadline: '2026-08-19T17:30:00',
      depositAmount: '10000',
      meetingPlace: 'Seongsu Beauty Lab',
      activityStartTime: '18:30:00',
      activityEndTime: '22:00:00',
    })
  })

  it('throws when the journey has not been selected yet', () => {
    expect(() => toAppointmentCreateRequest(baseDraft({ tripId: undefined }))).toThrow()
  })
})
