import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LocationQuery, LocationQueryRaw } from 'vue-router'

const TAB_KEY = 'tab'

/**
 * 목록을 거르지 않는 키. 진입 맥락일 뿐이라 기억해서 되돌리면 안 된다.
 *
 * `journeyId`는 "어느 여정에 담는가"이고 그 처리는 복귀 맥락 store(#192)가 맡는다.
 * `ExploreView`도 필터를 한 번 바꾸면 URL에서 이 키를 지우므로, 기억이 화면보다 오래
 * 들고 있으면 일회성이어야 할 맥락이 되살아난다.
 */
const CONTEXT_KEYS = new Set(['journeyId'])

/**
 * 목록의 몇 번째 쪽인가. 필터이긴 하지만 되돌리는 조건이 다르다.
 *
 * 보던 항목의 상세를 열었다 돌아오면 있던 쪽으로 돌아가는 것이 맞다. 반대로 이미 Discover를
 * 보면서 하단 탭을 다시 누른 사람에게 3쪽을 보여주면 목록을 처음부터 보려는 의도와 어긋난다.
 */
const PAGE_KEYS = new Set(['eventPage', 'placePage'])

/** 기억할 필터만 남긴다. */
function filtersOf(query: LocationQuery | LocationQueryRaw): LocationQueryRaw {
  return Object.fromEntries(Object.entries(query).filter(([key]) => !CONTEXT_KEYS.has(key)))
}

function withoutPage(query: LocationQueryRaw): LocationQueryRaw {
  return Object.fromEntries(Object.entries(query).filter(([key]) => !PAGE_KEYS.has(key)))
}

type ExploreTab = 'events' | 'places'

/** Events가 기본 탭이라 URL에 `tab`이 없으면 Events다. */
function tabOf(query: LocationQuery | LocationQueryRaw): ExploreTab {
  return query[TAB_KEY] === 'places' ? 'places' : 'events'
}

/**
 * 키가 하나도 없는 주소. 하단 탭이 보내는 `/explore`가 이것이다.
 *
 * `tab`조차 없어서 `tabOf`로는 Events로만 읽힌다. Events는 원래 `tab`을 안 붙이므로 이 둘을
 * 주소만으로 구별할 수 없고, 그래서 "보던 탭"을 따로 기억해 둔다.
 */
function isBareEntry(query: LocationQuery): boolean {
  return Object.keys(query).length === 0
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
 * 여기서 기억한 값은 **필터가 없는 주소로 다시 들어올 때만** 쓴다. 항목 상세에서 뒤로
 * 나오거나 하단 탭을 다시 누르면 필터가 빠진 주소로 오는데, 그때 되돌리기 위한 것이다.
 *
 * **기억은 Discover 안에 머무는 동안만 산다.** 다른 화면에 갔다가 돌아오는 것은 목록을
 * 처음부터 보려는 뜻이므로, 그 진입에서 `routes.ts`가 `clear()`로 통째로 버린다.
 *
 * Events와 Places는 쓰는 쿼리 키가 다르므로 슬롯을 나눈다. 한 칸에 담으면 Place 전용 키가
 * Events 진입에 섞인다. 대신 두 칸이 따로 놀지 않도록 버릴 때는 반드시 함께 버린다.
 *
 * 브라우저 저장소를 쓰지 않는다. 새로고침하는 순간에는 필터가 이미 주소에 있어 URL이
 * 복원하고, 앱 안에서 화면을 옮겨다니는 동안에는 이 store가 살아 있다.
 */
export const useExploreFilterMemoryStore = defineStore('explore-filter-memory', () => {
  const lastQuery = ref<Record<ExploreTab, LocationQueryRaw | null>>({
    events: null,
    places: null,
  })

  /**
   * 마지막으로 본 탭.
   *
   * 하단 탭이 보내는 `/explore`에는 `tab`이 없어서, 이것이 없으면 Places를 보던 사람도
   * Events로 떨어진다. 탭에 따라 되돌리는 결과가 달라지면 안 되므로 함께 들고 있는다.
   */
  const lastTab = ref<ExploreTab>('events')

  /**
   * URL에 필터를 쓸 때마다 같은 값을 기억한다. 필터를 모두 지운 상태도 그대로 기억한다.
   *
   * 화면이 필터를 바꿀 때만이 아니라 진입 주소도 이리로 들어온다. 그래야 Journey에서
   * 날짜를 지정해 들어온 뒤 필터를 만지지 않고 바로 상세로 갔다 돌아와도 그 날짜가 남는다.
   */
  function remember(query: LocationQuery | LocationQueryRaw): void {
    lastTab.value = tabOf(query)
    lastQuery.value = { ...lastQuery.value, [lastTab.value]: filtersOf(query) }
  }

  /**
   * 진입 주소에 붙일 query를 정한다. 되돌릴 것이 없으면 `null`이다.
   *
   * 들어온 주소에 필터가 하나라도 있으면 그것이 정본이므로 건드리지 않는다. 어느 탭으로
   * 들어오는지도 들어온 주소가 정하고, 그 탭의 기억만 되돌린다.
   *
   * `keepPage`는 항목 상세를 보고 뒤로 나온 진입에서만 참이다. 보던 쪽으로 돌아가야 하는
   * 경우와 목록을 처음부터 보려는 경우를 주소만으로는 구별할 수 없어 호출부가 알려준다.
   */
  function resolveEntry(
    incoming: LocationQuery,
    { keepPage = false }: { keepPage?: boolean } = {},
  ): LocationQueryRaw | null {
    if (hasFilter(incoming)) return null

    const remembered = lastQuery.value[isBareEntry(incoming) ? lastTab.value : tabOf(incoming)]
    if (remembered === null) return null

    const restored = keepPage ? { ...remembered } : withoutPage(remembered)
    return isSameQuery(restored, incoming) ? null : restored
  }

  /**
   * 기억을 통째로 버린다. 두 탭을 함께 버리는 것이 핵심이다.
   *
   * 한 탭만 비우면 나머지 탭의 지난 필터가 살아남아, 그 탭을 누르는 순간 화면에 없던 조건이
   * 걸린다. 사용자에게는 Events와 Places가 따로 노는 것으로 보인다.
   *
   * Discover 밖에서 들어올 때와 계정이 바뀔 때 부른다. 뒤쪽은 `eventSavedOnly`처럼 그 계정
   * 에서만 뜻이 있는 필터가 다음 사람에게 넘어가는 것을 막는다.
   */
  function clear(): void {
    lastQuery.value = { events: null, places: null }
    lastTab.value = 'events'
  }

  return { lastQuery, lastTab, remember, resolveEntry, clear }
})
