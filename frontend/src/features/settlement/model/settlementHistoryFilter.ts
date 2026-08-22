import { parseCalendarDate, toServerCalendarDate } from '@/shared/lib/datetime'

import type { SettlementSummary } from './settlement'

/**
 * 완료 내역을 거를 기간. 양 끝을 모두 포함하고, 값은 `2026-08-21` 모양의 날짜다.
 *
 * 하루만 고른 것도 기간으로 다룬다(`from`과 `to`가 같은 날). 그래야 화면과 주소가 늘
 * 같은 모양이 되고, "하루만 고른 상태"를 따로 다룰 일이 없다.
 */
export interface SettlementDateRange {
  from: string
  to: string
}

/**
 * 정산을 기간에 넣을 때 기준이 되는 날짜.
 *
 * 완료 시각이 원칙이고, 서버가 그 값을 남기기 전에 끝난 정산만 만든 날짜로 대신한다.
 * 대신하지 않으면 예전 정산은 어떤 기간을 골라도 목록에서 사라진다.
 */
export function settlementCompletedDate(settlement: SettlementSummary): string {
  return toServerCalendarDate(
    settlement.completedAt === '' ? settlement.createdAt : settlement.completedAt,
  )
}

/** 주소에 담긴 값은 무엇이든 올 수 있다. 날짜 하나로 읽히는 것만 받는다. */
function readDate(value: unknown): string {
  const candidate = Array.isArray(value) ? value[0] : value

  return typeof candidate === 'string' && parseCalendarDate(candidate) !== null ? candidate : ''
}

/**
 * 주소에 적힌 기간을 읽는다. 고른 기간이 없으면 `null`이다.
 *
 * 사용자가 주소를 직접 고칠 수 있으므로 한쪽만 있거나 순서가 뒤집힌 값도 온다. 한쪽만
 * 있으면 그 하루로, 뒤집혀 있으면 바로잡아 읽는다 — 여기서 버리면 화면은 아무것도 거르지
 * 않은 채 주소에는 기간이 남아, 새로고침할 때마다 결과가 달라 보인다.
 */
export function resolveDateRange(from: unknown, to: unknown): SettlementDateRange | null {
  const start = readDate(from)
  const end = readDate(to)

  if (start === '' && end === '') return null

  const first = start === '' ? end : start
  const second = end === '' ? start : end

  return first <= second ? { from: first, to: second } : { from: second, to: first }
}

/**
 * 기간 안에 끝난 정산만 남긴다.
 *
 * 날짜를 알 수 없는 정산은 뺀다. 기간으로 좁혀 보는 중이라면 "이 기간의 것"이라고 말할
 * 근거가 없는 줄은 보이지 않는 편이 맞다.
 */
export function filterByCompletedDate(
  settlements: SettlementSummary[],
  range: SettlementDateRange | null,
): SettlementSummary[] {
  if (range === null) return settlements

  return settlements.filter((settlement) => {
    const date = settlementCompletedDate(settlement)

    return date !== '' && date >= range.from && date <= range.to
  })
}
