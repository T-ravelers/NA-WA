import { serializeCalendarDate } from '@/shared/lib/datetime'

/**
 * 날짜 프리셋이 뜻하는 달력 범위입니다.
 *
 * 프리셋은 서버로 보내는 필터 값이 아니라 달력의 선택 가능 범위를 정하는
 * 단축키입니다(#275). 실제 필터는 항상 이 범위에서 나온 startDate/endDate로
 * 나갑니다. 필터 시트(범위 계산)와 ExploreView(개명 전 URL의 프리셋 복원)가
 * 같은 계산을 써야 해서 공용 모듈로 둡니다.
 */
export interface PresetDateRange {
  min: string
  // Opening soon은 상한이 없다 — max가 없으면 그 날짜 이후 전체가 선택 가능하다.
  max?: string
}

export function presetDateRange(preset: string): PresetDateRange | null {
  const today = todayDate()
  if (preset === 'ONGOING') {
    const value = serializeCalendarDate(today)
    return { min: value, max: value }
  }
  if (preset === 'OPENING_SOON') {
    return { min: serializeCalendarDate(addDays(today, 1)) }
  }
  if (preset === 'THIS_WEEKEND') {
    // 이번 주 토·일. 주말이 이미 시작됐으면 오늘부터 일요일까지다.
    const dayOfWeek = today.getDay()
    const saturday = addDays(today, dayOfWeek === 0 ? -1 : 6 - dayOfWeek)
    const sunday = addDays(saturday, 1)
    return {
      min: serializeCalendarDate(saturday < today ? today : saturday),
      max: serializeCalendarDate(sunday),
    }
  }
  if (preset === 'THIS_MONTH') {
    return {
      min: serializeCalendarDate(today),
      max: serializeCalendarDate(new Date(today.getFullYear(), today.getMonth() + 1, 0)),
    }
  }
  return null
}

export function todayDate(): Date {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}

export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)
}
