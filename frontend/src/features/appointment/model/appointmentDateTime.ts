import type { AppointmentDateTimeValue } from '../api/appointmentApi'

const KST_OFFSET = '+09:00'
const HAS_OFFSET = /(?:Z|[+-]\d{2}:?\d{2})$/
const NANOS_PER_MILLISECOND = 1_000_000

function pad(value: number, length: number): string {
  return String(value).padStart(length, '0')
}

function parseNumericDateTime(value: readonly number[]): Date | null {
  if (value.length < 3 || value.length > 7 || value.some((part) => !Number.isInteger(part))) {
    return null
  }

  const [year, month, day, hour = 0, minute = 0, second = 0, nanosecond = 0] = value
  if (
    year === undefined ||
    month === undefined ||
    day === undefined ||
    year < 1 ||
    month < 1 ||
    month > 12 ||
    day < 1 ||
    hour < 0 ||
    hour > 23 ||
    minute < 0 ||
    minute > 59 ||
    second < 0 ||
    second > 59 ||
    nanosecond < 0 ||
    nanosecond >= 1_000_000_000
  ) {
    return null
  }

  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate()
  if (day > daysInMonth) return null

  const millisecond = Math.trunc(nanosecond / NANOS_PER_MILLISECOND)
  const iso = `${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}T${pad(hour, 2)}:${pad(minute, 2)}:${pad(second, 2)}.${pad(millisecond, 3)}${KST_OFFSET}`
  const parsed = new Date(iso)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

/**
 * Appointment API의 LocalDateTime을 KST 기준 Date로 해석한다.
 *
 * 백엔드는 `yyyy-MM-dd'T'HH:mm:ss`처럼 오프셋 없는 문자열을 반환하므로 브라우저의
 * 로컬 타임존에 맡기지 않는다. Jackson 설정에 따라 `[year, month, day, ...]` 숫자 배열이
 * 내려오는 경우도 같은 기준으로 해석한다. 오프셋이 포함된 응답은 서버가 전달한 오프셋을
 * 유지한다.
 */
export function parseAppointmentDateTime(value: AppointmentDateTimeValue): Date | null {
  if (!value) return null
  if (typeof value !== 'string') return parseNumericDateTime(value)

  const parsed = new Date(HAS_OFFSET.test(value) ? value : `${value}${KST_OFFSET}`)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}
