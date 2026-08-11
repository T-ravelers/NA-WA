const KST_OFFSET = '+09:00'
const HAS_OFFSET = /(?:Z|[+-]\d{2}:?\d{2})$/

/**
 * Appointment API의 LocalDateTime을 KST 기준 Date로 해석한다.
 *
 * 백엔드는 `yyyy-MM-dd'T'HH:mm:ss`처럼 오프셋 없는 문자열을 반환하므로 브라우저의
 * 로컬 타임존에 맡기지 않는다. 오프셋이 포함된 응답은 서버가 전달한 오프셋을 유지한다.
 */
export function parseAppointmentDateTime(value: string | null): Date | null {
  if (!value) return null

  const parsed = new Date(HAS_OFFSET.test(value) ? value : `${value}${KST_OFFSET}`)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}
