import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/explore',
    name: 'explore',
    component: () => import('./views/ExploreView.vue'),
    meta: { requiresAuth: true },
  },
]

export default routes
