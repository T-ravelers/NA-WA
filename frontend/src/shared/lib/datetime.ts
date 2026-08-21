/** 서버 시각을 표시할 때 사용하는 고정 타임존. */
export const SERVER_TIME_ZONE = 'Asia/Seoul'

const KST_OFFSET_MINUTES = 9 * 60
const NANOS_PER_MILLISECOND = 1_000_000
const SERVER_DATE_TIME_PATTERN =
  /^(\d{4,6})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?(Z|[+-]\d{2}:?\d{2})?$/
const CALENDAR_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/

/** Jackson LocalDateTime와 ISO 시각 응답을 함께 받기 위한 공통 입력 타입. */
export type ServerDateTimeValue = string | readonly number[] | null | undefined
export type ServerDateTime = ServerDateTimeValue

export type DateTimeFormatOptions = Omit<Intl.DateTimeFormatOptions, 'timeZone'>

function isLeapYear(year: number): boolean {
  return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
}

function getDaysInMonth(year: number, month: number): number {
  if (month === 2) return isLeapYear(year) ? 29 : 28
  return [4, 6, 9, 11].includes(month) ? 30 : 31
}

function isValidDateParts(year: number, month: number, day: number): boolean {
  return (
    Number.isInteger(year) &&
    year >= 1 &&
    Number.isInteger(month) &&
    month >= 1 &&
    month <= 12 &&
    Number.isInteger(day) &&
    day >= 1 &&
    day <= getDaysInMonth(year, month)
  )
}

function isValidTimeParts(
  hour: number,
  minute: number,
  second: number,
  nanosecond: number,
): boolean {
  return (
    Number.isInteger(hour) &&
    hour >= 0 &&
    hour <= 23 &&
    Number.isInteger(minute) &&
    minute >= 0 &&
    minute <= 59 &&
    Number.isInteger(second) &&
    second >= 0 &&
    second <= 59 &&
    Number.isInteger(nanosecond) &&
    nanosecond >= 0 &&
    nanosecond < 1_000_000_000
  )
}

/** 오프셋을 적용한 Date를 만든다. Date.UTC의 0~99년 특수 처리를 피한다. */
function createDateFromParts(
  year: number,
  month: number,
  day: number,
  hour: number,
  minute: number,
  second: number,
  millisecond: number,
  offsetMinutes: number,
): Date | null {
  const localWallClock = new Date(Date.UTC(0, month - 1, day, hour, minute, second, millisecond))
  localWallClock.setUTCFullYear(year)

  const timestamp = localWallClock.getTime() - offsetMinutes * 60_000
  const parsed = new Date(timestamp)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

function parseNumericDateTime(value: readonly number[]): Date | null {
  if (value.length < 3 || value.length > 7) return null
  for (let index = 0; index < value.length; index += 1) {
    if (!Number.isInteger(value[index])) return null
  }

  const [year, month, day, hour = 0, minute = 0, second = 0, nanosecond = 0] = value
  if (
    year === undefined ||
    month === undefined ||
    day === undefined ||
    !isValidDateParts(year, month, day) ||
    !isValidTimeParts(hour, minute, second, nanosecond)
  ) {
    return null
  }

  return createDateFromParts(
    year,
    month,
    day,
    hour,
    minute,
    second,
    Math.trunc(nanosecond / NANOS_PER_MILLISECOND),
    KST_OFFSET_MINUTES,
  )
}

function parseOffset(value: string | undefined): number | null {
  if (value === undefined || value === 'Z') return value === 'Z' ? 0 : KST_OFFSET_MINUTES

  const sign = value.startsWith('+') ? 1 : -1
  const digits = value.slice(1).replace(':', '')
  const hours = Number(digits.slice(0, 2))
  const minutes = Number(digits.slice(2, 4))

  return hours <= 23 && minutes <= 59 ? sign * (hours * 60 + minutes) : null
}

function parseServerDateTimeString(value: string): Date | null {
  const match = SERVER_DATE_TIME_PATTERN.exec(value.trim())
  if (!match) return null

  const [
    ,
    yearText,
    monthText,
    dayText,
    hourText,
    minuteText,
    secondText,
    fractionText,
    offsetText,
  ] = match
  const year = Number(yearText)
  const month = Number(monthText)
  const day = Number(dayText)
  const hour = Number(hourText)
  const minute = Number(minuteText)
  const second = secondText === undefined ? 0 : Number(secondText)
  const nanosecond = fractionText === undefined ? 0 : Number(fractionText.padEnd(9, '0'))

  if (!isValidDateParts(year, month, day) || !isValidTimeParts(hour, minute, second, nanosecond)) {
    return null
  }

  const offsetMinutes = parseOffset(offsetText)
  if (offsetMinutes === null) return null

  return createDateFromParts(
    year,
    month,
    day,
    hour,
    minute,
    second,
    Math.trunc(nanosecond / NANOS_PER_MILLISECOND),
    offsetMinutes,
  )
}

/**
 * 서버 LocalDateTime을 Date로 해석한다.
 *
 * 숫자 배열과 오프셋 없는 문자열은 KST 벽시계로 읽고, 문자열에 오프셋이 있으면 그
 * 오프셋을 유지한다. 잘못된 값은 호출부가 분기할 수 있도록 null로 돌려준다.
 */
export function parseServerDateTime(value: ServerDateTimeValue): Date | null {
  if (Array.isArray(value)) return parseNumericDateTime(value)
  if (typeof value !== 'string' || value.trim() === '') return null

  return parseServerDateTimeString(value)
}

function toValidDate(value: Date | ServerDateTimeValue): Date | null {
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  return parseServerDateTime(value)
}

/** 서버 시각 표시용 옵션. 호출부에서 타임존을 바꿀 수 없도록 타입에서도 제외한다. */
export function formatServerDateTime(
  value: Date | ServerDateTimeValue,
  locale: string,
  options: DateTimeFormatOptions = {},
): string {
  const date = toValidDate(value)
  if (date === null) return ''

  return new Intl.DateTimeFormat(locale, {
    ...options,
    timeZone: SERVER_TIME_ZONE,
  }).format(date)
}

/**
 * 서버 시각에서 날짜만 남긴다(예: `2026-08-21`).
 *
 * 날짜로 거르거나 묶을 때 쓴다. 시각을 그대로 비교하면 브라우저가 어느 지역에 있느냐에
 * 따라 같은 저녁에 끝난 일이 하루 앞뒤로 갈린다. 서버와 같은 서울 기준으로 날짜를
 * 정해야 사용자가 고른 기간과 목록이 어긋나지 않는다. 읽을 수 없는 값은 빈 문자열이다.
 */
export function toServerCalendarDate(value: Date | ServerDateTimeValue): string {
  const date = toValidDate(value)
  if (date === null) return ''

  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: SERVER_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date)

  // 지역 표기를 문자열로 받아 자르지 않고 조각으로 읽는다. 자리 순서와 구분자는
  // 브라우저마다 다를 수 있지만, 조각의 이름은 다르지 않다.
  const find = (type: Intl.DateTimeFormatPartTypes): string =>
    parts.find((part) => part.type === type)?.value ?? ''

  const year = find('year')
  const month = find('month')
  const day = find('day')
  if (year === '' || month === '' || day === '') return ''

  return `${year}-${month}-${day}`
}

function toValidCalendarDate(value: string | Date | null | undefined): Date | null {
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  return parseCalendarDate(value)
}

/** 날짜 전용 API 값(YYYY-MM-DD)을 브라우저의 로컬 달력 날짜로 만든다. */
export function parseCalendarDate(value: string | null | undefined): Date | null {
  if (typeof value !== 'string') return null

  const match = CALENDAR_DATE_PATTERN.exec(value)
  if (!match) return null

  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  if (!isValidDateParts(year, month, day)) return null

  const date = new Date(0)
  date.setHours(0, 0, 0, 0)
  date.setFullYear(year, month - 1, day)
  return date
}

/** date-picker 셀의 Date를 날짜 전용 API 직렬화 값으로 바꾼다. */
export function serializeCalendarDate(value: Date | null | undefined): string {
  if (!(value instanceof Date) || Number.isNaN(value.getTime())) return ''

  const year = String(value.getFullYear()).padStart(4, '0')
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** 날짜 전용 값을 로케일 표기로 표시한다. 타임존을 지정하지 않아 달력 날짜를 보존한다. */
export function formatCalendarDate(
  value: string | Date | null | undefined,
  locale: string,
  options: DateTimeFormatOptions = { dateStyle: 'medium' },
): string {
  const date = toValidCalendarDate(value)
  if (date === null) return ''

  return new Intl.DateTimeFormat(locale, options).format(date)
}

/** 날짜 전용 API 값을 화면에서 쓰는 구분자로 표시한다(예: 2026.08.01). */
export function formatCalendarDateString(
  value: string | null | undefined,
  separator = '.',
): string {
  const date = parseCalendarDate(value)
  if (date === null) return ''

  return serializeCalendarDate(date).replace(/-/g, separator)
}

/** 달력 한 칸. `date`는 날짜 전용 API 값(YYYY-MM-DD)이다. */
export interface CalendarCell {
  date: string
  day: number
  /** 이번 달 날짜인지. 앞뒤 달에서 채워 넣은 칸은 `false`다. */
  inMonth: boolean
}

/** 달력 한 판의 줄 수. 6주 × 7일로 고정해 달마다 높이가 흔들리지 않게 한다. */
const CALENDAR_CELL_COUNT = 42

/**
 * 한 달을 6주짜리 달력 판으로 만든다.
 *
 * 첫 주의 빈 앞자리와 마지막 주의 빈 뒷자리는 앞뒤 달 날짜로 채우고 `inMonth: false`로
 * 표시한다. 호출부는 그 칸을 흐리게 그리거나 누르지 못하게 한다.
 *
 * 주는 일요일에 시작한다.
 */
export function buildCalendarMonth(monthCursor: Date): CalendarCell[] {
  const year = monthCursor.getFullYear()
  const month = monthCursor.getMonth()
  const startOffset = new Date(year, month, 1).getDay()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const previousMonthDays = new Date(year, month, 0).getDate()
  const cells: CalendarCell[] = []

  for (let index = 0; index < CALENDAR_CELL_COUNT; index += 1) {
    const rawDay = index - startOffset + 1
    const inMonth = rawDay >= 1 && rawDay <= daysInMonth
    const day = inMonth ? rawDay : rawDay < 1 ? previousMonthDays + rawDay : rawDay - daysInMonth

    cells.push({ date: serializeCalendarDate(new Date(year, month, rawDay)), day, inMonth })
  }

  return cells
}

/** 달력에서 달을 옮긴다. 항상 1일을 가리켜 말일이 있는 달에서 날짜가 튀지 않는다. */
export function shiftCalendarMonth(monthCursor: Date, offset: number): Date {
  return new Date(monthCursor.getFullYear(), monthCursor.getMonth() + offset, 1)
}

// 의미를 드러내는 별칭을 제공해 날짜 전용 값이 서버 시각 파서로 흘러가지 않게 한다.
export const formatDateOnly = formatCalendarDate
export const formatDateOnlyString = formatCalendarDateString
