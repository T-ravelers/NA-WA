import type { RouteRecordRaw } from 'vue-router'

import { useExploreReturnContextStore } from './model/exploreReturnContext'

const EXPLORE_ROUTE_NAMES = new Set(['explore', 'explore-event-detail', 'explore-place-detail'])

const routes: RouteRecordRaw[] = [
  {
    path: '/explore',
    name: 'explore',
    component: () => import('./views/ExploreView.vue'),
    meta: { requiresAuth: true },
    /*
     * Journey 화면에서 날짜를 지정해 넘어온 맥락을 여기서 받는다.
     *
     * 맥락을 싣지 않은 진입이라도 Discover 안에서 오갈 때는(상세를 보고 뒤로 나오는 등)
     * 아직 흐름이 끝난 것이 아니다. 필터를 한 번 바꾸면 URL에서 journeyId가 지워지므로
     * 주소만으로는 구별할 수 없고, 어디에서 왔는지로 판단한다.
     */
    beforeEnter: (to, from) => {
      const context = useExploreReturnContextStore()
      const captured = context.capture({
        journeyId: to.query.journeyId,
        startDate: to.query.startDate,
        endDate: to.query.endDate,
      })

      if (!captured && !EXPLORE_ROUTE_NAMES.has(String(from.name))) context.discardReturn()

      return true
    },
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
