import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'nawa.explore.returnContext'
const VISIT_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

/** 담기를 마친 뒤 돌아갈 화면. route name과 params만 보관해 직렬화 가능한 형태로 둔다. */
export interface ExploreReturnLocation {
  name: string
  params: Record<string, string>
}

interface StoredContext {
  journeyId: number | null
  visitDate: string | null
  returnTo: ExploreReturnLocation | null
}

const EMPTY_CONTEXT: StoredContext = { journeyId: null, visitDate: null, returnTo: null }

/** route query는 같은 키가 여러 번 오면 배열이 된다. 첫 값만 본다. */
function firstQueryValue(value: unknown): unknown {
  return Array.isArray(value) ? value[0] : value
}

function parseJourneyId(value: unknown): number | null {
  if (typeof value !== 'string' && typeof value !== 'number') return null

  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

function parseVisitDate(value: unknown): string | null {
  return typeof value === 'string' && VISIT_DATE_PATTERN.test(value) ? value : null
}

function parseReturnLocation(value: unknown): ExploreReturnLocation | null {
  if (typeof value !== 'object' || value === null) return null

  const { name, params } = value as { name?: unknown; params?: unknown }
  if (typeof name !== 'string' || name === '') return null
  if (typeof params !== 'object' || params === null) return null

  const entries = Object.entries(params as Record<string, unknown>)
  if (entries.some(([, param]) => typeof param !== 'string')) return null

  return { name, params: Object.fromEntries(entries) as Record<string, string> }
}

function readStoredContext(): StoredContext {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (raw === null) return EMPTY_CONTEXT

  try {
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null) return EMPTY_CONTEXT

    const { journeyId, visitDate, returnTo } = parsed as Record<string, unknown>

    return {
      journeyId: parseJourneyId(journeyId),
      visitDate: parseVisitDate(visitDate),
      returnTo: parseReturnLocation(returnTo),
    }
  } catch {
    return EMPTY_CONTEXT
  }
}

/**
 * Journey 화면에서 날짜를 지정해 Discover로 넘어온 맥락을 화면 사이에 나른다.
 *
 * 이 값은 route query로 나를 수 없다. ExploreView의 필터 동기화가 화이트리스트 방식이라
 * 필터가 한 번 바뀌면 목록에 없는 쿼리 키가 URL에서 사라지고, 목록에서 상세로 넘어갈 때도
 * 쿼리가 전파되지 않는다. 반대로 필터 값 자체는 공유·북마크되어야 하므로 URL에 남긴다.
 *
 * 새로고침을 견뎌야 하므로 sessionStorage에 함께 보관한다. 탭 단위로만 유지된다.
 */
export const useExploreReturnContextStore = defineStore('explore-return-context', () => {
  const stored = readStoredContext()

  const journeyId = ref<number | null>(stored.journeyId)
  const visitDate = ref<string | null>(stored.visitDate)
  const returnTo = ref<ExploreReturnLocation | null>(stored.returnTo)

  function persist(): void {
    if (journeyId.value === null && visitDate.value === null && returnTo.value === null) {
      sessionStorage.removeItem(STORAGE_KEY)

      return
    }

    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        journeyId: journeyId.value,
        visitDate: visitDate.value,
        returnTo: returnTo.value,
      }),
    )
  }

  /**
   * Discover 진입 시 맥락을 기록한다.
   *
   * 새 쿼리 키를 만들지 않기 위해 Journey 화면은 기존 키만 보낸다. 하루를 지정해 넘어오면
   * `startDate`와 `endDate`가 같은 날이고, 그 하루가 곧 담을 날짜다.
   *
   * `journeyId`까지 있을 때만 갱신한다. 그 키는 Journey에서 넘어온 링크에만 붙으므로,
   * 하단 탭으로 들어와 하루짜리 기간 필터를 직접 걸어도 맥락으로 오해하지 않는다.
   */
  function capture(input: { journeyId: unknown; startDate: unknown; endDate: unknown }): boolean {
    const nextJourneyId = parseJourneyId(firstQueryValue(input.journeyId))
    const nextVisitDate = parseVisitDate(firstQueryValue(input.startDate))
    if (nextJourneyId === null || nextVisitDate === null) return false
    if (nextVisitDate !== parseVisitDate(firstQueryValue(input.endDate))) return false

    journeyId.value = nextJourneyId
    visitDate.value = nextVisitDate
    returnTo.value = { name: 'journey-detail', params: { tripId: String(nextJourneyId) } }
    persist()

    return true
  }

  /**
   * 담기 시트에서 고른 대상 여정을 기억한다.
   *
   * 맥락에 담긴 여정과 다른 여정을 고르면 날짜와 복귀 위치는 그 여정의 것이 아니므로 버린다.
   */
  function setJourneyId(value: unknown): void {
    const parsed = parseJourneyId(value)
    if (parsed === null || parsed === journeyId.value) return

    journeyId.value = parsed
    visitDate.value = null
    returnTo.value = null
    persist()
  }

  function clear(): void {
    journeyId.value = null
    visitDate.value = null
    returnTo.value = null
    persist()
  }

  return { journeyId, visitDate, returnTo, capture, setJourneyId, clear }
})
