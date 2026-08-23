import { describe, expect, it } from 'vitest'

import type { MyOngoingAppointment } from '../../api/appointmentApi'
import { filterAppointmentsForServerDate } from '../useMyOngoingAppointmentsQuery'

function appointment(
  appointmentId: number,
  activityStartAt: string,
  appointmentStatus: MyOngoingAppointment['appointmentStatus'],
): MyOngoingAppointment {
  return {
    appointmentId,
    appointmentName: `Appointment ${appointmentId}`,
    tripId: 1,
    meetingPlace: null,
    activityStartAt,
    activityEndAt: activityStartAt,
    itemId: 1,
    itemType: 'EVENT',
    appointmentStatus,
  }
}

describe('filterAppointmentsForServerDate', () => {
  it('서울 날짜가 오늘인 약속을 상태와 무관하게 남긴다', () => {
    const appointments = [
      appointment(1, '2026-08-23T09:00:00', 'RECRUITING'),
      appointment(2, '2026-08-23T18:00:00', 'IN_PROGRESS'),
      appointment(3, '2026-08-24T00:00:00', 'FULL'),
    ]

    expect(
      filterAppointmentsForServerDate(appointments, new Date('2026-08-23T14:59:59Z'))?.map(
        ({ appointmentId }) => appointmentId,
      ),
    ).toEqual([1, 2])
  })
})
