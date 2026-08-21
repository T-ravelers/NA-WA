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
    path: '/journeys/:tripId/settings',
    name: 'journey-settings',
    component: () => import('./views/JourneySettingsView.vue'),
    meta: { requiresAuth: true },
  },
]

export default routes
