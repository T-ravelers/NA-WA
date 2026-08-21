import type { AppointmentStatus } from '../api/appointmentApi'

/**
 * 약속 상태 → 배지 tone.
 *
 * 목록 카드와 상세 헤더가 같은 표를 쓴다. 따로 두면 한쪽만 고쳐져 같은 약속이 두 화면에서
 * 다른 색으로 보인다(#365 이전이 그랬다 — 모집 중만 초록이고 나머지 여섯 상태가 전부 회색).
 *
 * 실제 흐름 `RECRUITING → CLOSED → IN_PROGRESS → AWAITING_ATTENDANCE → COMPLETED`를 단계별로
 * 가른다. 특히 `AWAITING_ATTENDANCE`는 방장이 출석을 확정해야 끝나는 **행동 필요** 상태라
 * 「완료」·「취소」와 같은 회색이면 안 된다(앰버 `pending`, 시안 요청서 B-1).
 *
 * 색만으로 말하지 않는다 — 배지는 항상 `appointment.status.*` 라벨을 함께 보여준다.
 */
export type AppointmentStatusTone =
  'ongoing' | 'scheduled' | 'info' | 'pending' | 'completed' | 'neutral'

const STATUS_TONE: Record<AppointmentStatus, AppointmentStatusTone> = {
  /** 모집 중 — 참여할 수 있다. */
  RECRUITING: 'ongoing',
  /** 모집이 끝났고 활동은 아직이다. 예정 상태. */
  CLOSED: 'scheduled',
  /** 도달 경로가 없는 값. CLOSED와 같은 예정 단계로 둔다. */
  CONFIRMED: 'scheduled',
  /** 활동 중. */
  IN_PROGRESS: 'info',
  /** 활동은 끝났고 방장의 출석 확정이 필요하다. 표시 전용 값(#350). */
  AWAITING_ATTENDANCE: 'pending',
  COMPLETED: 'completed',
  CANCELLED: 'neutral',
  /** 트랜잭션 한정 값. 화면에 오면 중립으로. */
  PAYMENT_PENDING: 'neutral',
}

/** 모르는 값이 와도 깨지지 않게 중립으로 떨어뜨린다. */
export function appointmentStatusTone(status: string | undefined): AppointmentStatusTone {
  return status !== undefined && status in STATUS_TONE
    ? STATUS_TONE[status as AppointmentStatus]
    : 'neutral'
}
