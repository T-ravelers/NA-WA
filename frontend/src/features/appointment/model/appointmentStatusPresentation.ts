import type { AppointmentStatus } from '@/shared/lib/appointmentStatus'

export type AppointmentStatusTone =
  'ongoing' | 'scheduled' | 'pending' | 'info' | 'danger' | 'neutral'

const STATUS_TONE: Record<AppointmentStatus, AppointmentStatusTone> = {
  PAYMENT_PENDING: 'pending',
  RECRUITING: 'ongoing',
  FULL: 'scheduled',
  IN_PROGRESS: 'info',
  AWAITING_ATTENDANCE: 'pending',
  COMPLETED: 'neutral',
  CANCELLED: 'danger',
}

/**
 * 목록과 상세에서 같은 Appointment 상태를 같은 tone으로 표시한다.
 * 상태 문구가 의미의 정본이고 tone은 빠른 구분을 돕는 보조 표현이다.
 */
export function appointmentStatusTone(
  status: AppointmentStatus | undefined,
): AppointmentStatusTone {
  return status === undefined ? 'neutral' : STATUS_TONE[status]
}
