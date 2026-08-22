import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/journeys',
    name: 'journey-list',
    component: () => import('./views/JourneyListView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/journeys/new',
    name: 'journey-create',
    component: () => import('./views/JourneyCreateView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/journeys/:tripId',
    name: 'journey-detail',
    component: () => import('./views/JourneyDetailView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/journeys/:tripId/invite',
    name: 'journey-invite',
    component: () => import('./views/JourneyInviteView.vue'),
    /* 여정 상세에서 들어오는 집중 화면이라 하단 탭을 감춘다. 시안에도 탭이 없다. */
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  {
    path: '/journeys/:tripId/settings',
    name: 'journey-settings',
    component: () => import('./views/JourneySettingsView.vue'),
    meta: { requiresAuth: true },
  },
]

export default routes
