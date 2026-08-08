const STORAGE_KEY = 'nawa.activeJourneyId'

function parseJourneyId(value: unknown): number | null {
  if (typeof value !== 'string' && typeof value !== 'number') return null

  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

/** 현재 사용자가 탐색 항목을 추가할 대상으로 선택한 여정 ID를 읽습니다. */
export function readActiveJourneyId(): number | null {
  return parseJourneyId(sessionStorage.getItem(STORAGE_KEY))
}

/** 여정 화면에서 현재 선택한 여정을 저장할 때 사용합니다. */
export function storeActiveJourneyId(journeyId: number): void {
  const parsed = parseJourneyId(journeyId)
  if (parsed === null) return

  sessionStorage.setItem(STORAGE_KEY, String(parsed))
}

export function clearActiveJourneyId(): void {
  sessionStorage.removeItem(STORAGE_KEY)
}

export function parseJourneyRouteQuery(value: unknown): number | null {
  return parseJourneyId(Array.isArray(value) ? value[0] : value)
}
