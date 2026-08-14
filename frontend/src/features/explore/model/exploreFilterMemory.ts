import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LocationQuery, LocationQueryRaw } from 'vue-router'

const TAB_KEY = 'tab'

type ExploreTab = 'events' | 'places'

/** Events가 기본 탭이라 URL에 `tab`이 없으면 Events다. */
function tabOf(query: LocationQuery | LocationQueryRaw): ExploreTab {
  return query[TAB_KEY] === 'places' ? 'places' : 'events'
}

/** `tab`은 어느 목록을 볼지일 뿐 필터가 아니다. 그것만 있으면 필터가 없는 주소로 본다. */
function hasFilter(query: LocationQuery): boolean {
  return Object.keys(query).some((key) => key !== TAB_KEY)
}

function isSameQuery(next: LocationQueryRaw, current: LocationQuery): boolean {
  const nextKeys = Object.keys(next)
  if (nextKeys.length !== Object.keys(current).length) return false

  return nextKeys.every((key) => String(next[key]) === String(current[key]))
}

/**
 * Discover에서 마지막으로 보던 필터를 탭별로 기억한다.
 *
 * 필터의 정본은 계속 URL이다. 공유·북마크한 주소는 필터를 담고 있으므로 그대로 쓰고,
 * 여기서 기억한 값은 **필터가 없는 주소로 다시 들어올 때만** 쓴다. 상세 화면에서 뒤로
 * 나오거나 하단 탭으로 들어오면 필터가 빠진 주소로 진입하는데, 그때 되돌리기 위한 것이다.
 *
 * Events와 Places는 쓰는 쿼리 키가 다르므로 슬롯을 나눈다. 한 칸에 담으면 Place 전용 키가
 * Events 진입에 섞인다.
 *
 * 브라우저 저장소를 쓰지 않는다. 새로고침하는 순간에는 필터가 이미 주소에 있어 URL이
 * 복원하고, 앱 안에서 화면을 옮겨다니는 동안에는 이 store가 살아 있다.
 */
export const useExploreFilterMemoryStore = defineStore('explore-filter-memory', () => {
  const lastQuery = ref<Record<ExploreTab, LocationQueryRaw | null>>({
    events: null,
    places: null,
  })

  /** URL에 필터를 쓸 때마다 같은 값을 기억한다. 필터를 모두 지운 상태도 그대로 기억한다. */
  function remember(query: LocationQueryRaw): void {
    lastQuery.value = { ...lastQuery.value, [tabOf(query)]: { ...query } }
  }

  /**
   * 진입 주소에 붙일 query를 정한다. 되돌릴 것이 없으면 `null`이다.
   *
   * 들어온 주소에 필터가 하나라도 있으면 그것이 정본이므로 건드리지 않는다. 어느 탭으로
   * 들어오는지도 들어온 주소가 정하고, 그 탭의 기억만 되돌린다.
   */
  function resolveEntry(incoming: LocationQuery): LocationQueryRaw | null {
    if (hasFilter(incoming)) return null

    const remembered = lastQuery.value[tabOf(incoming)]
    if (remembered === null) return null

    return isSameQuery(remembered, incoming) ? null : { ...remembered }
  }

  return { lastQuery, remember, resolveEntry }
})
