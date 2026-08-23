import type { LocationQuery, RouteLocationRaw } from 'vue-router'

import { useExploreFilterMemoryStore } from './exploreFilterMemory'

/**
 * 필터가 빠진 Discover 진입에 마지막으로 보던 필터를 되돌린다.
 *
 * 되돌릴 것이 있으면 그 주소를 돌려주고, 없으면 들어온 주소를 기억한 뒤 `null`을 돌려준다.
 *
 * 두 곳이 같은 판단을 해야 해서 여기 하나로 둔다. `beforeEnter`는 route record에 **처음
 * 들어올 때만** 돌기 때문에, 이미 Discover에 있는 채로 하단 탭을 다시 눌러 query만 바뀌는
 * 이동은 그것만으로 잡히지 않는다. 그 자리는 `ExploreView`의 `onBeforeRouteUpdate`가 맡는다.
 */
export function resolveExploreFilterEntry(
  query: LocationQuery,
  { keepPage }: { keepPage: boolean },
): RouteLocationRaw | null {
  const filterMemory = useExploreFilterMemoryStore()

  const restored = filterMemory.resolveEntry(query, { keepPage })
  if (restored !== null) return { name: 'explore', query: restored, replace: true }

  filterMemory.remember(query)

  return null
}
