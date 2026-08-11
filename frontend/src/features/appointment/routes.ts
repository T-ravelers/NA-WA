import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/appointments',
    name: 'appointment-list',
    component: () => import('./views/AppointmentListView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  {
    path: '/appointments/new',
    name: 'appointment-create',
    component: () => import('./views/AppointmentCreateView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
]

export default routes
