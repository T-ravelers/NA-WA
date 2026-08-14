import type { RouteRecordRaw } from 'vue-router'

import { useExploreFilterMemoryStore } from './model/exploreFilterMemory'

const routes: RouteRecordRaw[] = [
  {
    path: '/explore',
    name: 'explore',
    component: () => import('./views/ExploreView.vue'),
    meta: { requiresAuth: true },
    /*
     * 필터가 빠진 주소로 들어오면 마지막으로 보던 필터를 되돌린다.
     *
     * 화면이 만들어지기 전에 주소를 고쳐야 `ExploreView`가 평소처럼 URL만 읽고도 필터를
     * 살릴 수 있다. 그래서 필터 동기화 코드는 그대로 두고 여기서 처리한다.
     */
    beforeEnter: (to) => {
      const restored = useExploreFilterMemoryStore().resolveEntry(to.query)
      if (restored === null) return true

      return { name: 'explore', query: restored, replace: true }
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
