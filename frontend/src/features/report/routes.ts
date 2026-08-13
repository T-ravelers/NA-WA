import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/reports',
    name: 'report-list',
    component: () => import('./views/ReportsView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/reports/:reportId',
    name: 'report-detail',
    component: () => import('./views/ReportDetailView.vue'),
    meta: { requiresAuth: true },
  },
]

export default routes
