import type { RouteRecordRaw } from 'vue-router'

import { useExploreFilterMemoryStore } from './model/exploreFilterMemory'
import { useExploreReturnContextStore } from './model/exploreReturnContext'

const EXPLORE_ROUTE_NAMES = new Set(['explore', 'explore-event-detail', 'explore-place-detail'])
const EXPLORE_DETAIL_ROUTE_NAMES = new Set(['explore-event-detail', 'explore-place-detail'])

const routes: RouteRecordRaw[] = [
  {
    path: '/explore',
    name: 'explore',
    component: () => import('./views/ExploreView.vue'),
    meta: { requiresAuth: true },
    beforeEnter: [
      /*
       * 필터가 빠진 주소로 들어오면 그 탭에서 마지막으로 보던 필터를 되돌리고, 필터를 싣고
       * 들어오면 그 주소를 기억한다.
       *
       * 화면이 만들어지기 전에 주소를 고쳐야 `ExploreView`가 평소처럼 URL만 읽고도 필터를
       * 살릴 수 있다. 그래서 기존 필터 동기화 코드는 그대로 두고 여기서 처리한다.
       *
       * 기억을 여기서도 하는 이유는 `ExploreView`의 필터 watcher가 `immediate`가 아니라서다.
       * 진입 주소의 필터는 화면이 다시 쓰지 않으므로, Journey의 날짜 지정 링크·공유 주소·
       * 새로고침으로 들어와 필터를 만지지 않고 상세로 갔다 오면 되돌릴 것이 없어진다.
       *
       * 쪽 번호는 상세를 보고 뒤로 나온 진입에서만 되돌린다. 보던 쪽으로 돌아가는 것과
       * 목록을 처음부터 보는 것은 같은 빈 주소로 들어오므로 어디에서 왔는지로 가른다.
       */
      (to, from) => {
        const filterMemory = useExploreFilterMemoryStore()
        const restored = filterMemory.resolveEntry(to.query, {
          keepPage: EXPLORE_DETAIL_ROUTE_NAMES.has(String(from.name)),
        })
        if (restored !== null) return { name: 'explore', query: restored, replace: true }

        filterMemory.remember(to.query)
        return true
      },
      /*
       * Journey 화면에서 날짜를 지정해 넘어온 맥락을 여기서 받는다.
       *
       * 맥락을 싣지 않은 진입이라도 Discover 안에서 오갈 때는(상세를 보고 뒤로 나오는 등)
       * 아직 흐름이 끝난 것이 아니다. 필터를 한 번 바꾸면 URL에서 journeyId가 지워지므로
       * 주소만으로는 구별할 수 없고, 어디에서 왔는지로 판단한다.
       */
      (to, from) => {
        const context = useExploreReturnContextStore()
        const captured = context.capture({
          journeyId: to.query.journeyId,
          startDate: to.query.startDate,
          endDate: to.query.endDate,
        })

        if (!captured && !EXPLORE_ROUTE_NAMES.has(String(from.name))) context.discardReturn()

        return true
      },
    ],
  },
  {
    path: '/explore/events/:eventId',
    name: 'explore-event-detail',
    component: () => import('./views/EventDetailView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  {
    path: '/explore/places/:placeId',
    name: 'explore-place-detail',
    component: () => import('./views/PlaceDetailView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
]

export default routes
