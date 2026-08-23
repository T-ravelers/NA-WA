/**
 * 약속 진행 상태.
 *
 * 백엔드 `AppointmentStatus` enum과 1:1이고, 값 집합과 전이 규칙의 정본은
 * `backend/docs/APPOINTMENT_DEPOSIT_STATE_MACHINE.md`다.
 *
 * appointment(목록·상세·생성)와 journey(타임라인 응답의 `appointment`)가 함께 쓰므로
 * shared에 둔다. feature끼리 직접 import하지 않는다는 규칙(DEVELOPMENT_CONVENTION.md)
 * 아래에서 두 feature가 같은 값 집합을 보려면 여기 말고 둘 곳이 없다 — 한쪽에 두고
 * 다른 쪽이 문자열로 받으면, 없어진 값이 남아도 타입 검사가 잡지 못한다(#387).
 */
export const APPOINTMENT_STATUSES = [
  'PAYMENT_PENDING',
  'RECRUITING',
  /** 정원이 모두 참. 시간으로 도달하는 경로는 없다. */
  'FULL',
  'IN_PROGRESS',
  /**
   * 활동 종료 시각이 지났지만 방장이 아직 출석을 확정하지 않음. 다른 값과 똑같이
   * DB에 저장된다 — 스케줄러가 이 전이를 기록한다.
   */
  'AWAITING_ATTENDANCE',
  'COMPLETED',
  'CANCELLED',
] as const

export type AppointmentStatus = (typeof APPOINTMENT_STATUSES)[number]
