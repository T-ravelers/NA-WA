function parseJourneyId(value: unknown): number | null {
  if (typeof value !== 'string' && typeof value !== 'number') return null

  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

/**
 * route query에 실려 온 여정 ID를 정규화한다.
 *
 * 대상 여정과 방문 날짜를 화면 사이에 보관하는 일은 explore의 복귀 맥락 store가 맡는다.
 * 여기에는 journey 도메인의 ID 해석 규칙만 남긴다.
 */
export function parseJourneyRouteQuery(value: unknown): number | null {
  return parseJourneyId(Array.isArray(value) ? value[0] : value)
}
