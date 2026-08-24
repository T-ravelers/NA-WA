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
  {
    path: '/appointments/:appointmentId/members/:memberId',
    name: 'appointment-member-profile',
    component: () => import('./views/AppointmentMemberProfileView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  {
    path: '/appointments/:appointmentId/attendance',
    name: 'appointment-attendance',
    component: () => import('./views/AppointmentAttendanceView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  {
    path: '/appointments/:appointmentId',
    name: 'appointment-detail',
    component: () => import('./views/AppointmentDetailView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
]

export default routes
