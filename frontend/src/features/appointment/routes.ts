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
    path: '/appointments/:appointmentId/members',
    name: 'appointment-members',
    component: () => import('./views/AppointmentMembersView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  {
    path: '/appointments/:appointmentId/attendance',
    name: 'appointment-attendance',
    component: () => import('./views/AppointmentAttendanceView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  {
    path: '/appointments/:appointmentId/reviews',
    name: 'appointment-reviews',
    component: () => import('./views/AppointmentReviewView.vue'),
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
