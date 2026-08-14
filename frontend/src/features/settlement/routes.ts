import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/settlements',
    name: 'settlements',
    component: () => import('./views/SettlementListView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/settlements/new',
    name: 'settlement-new',
    component: () => import('./views/SettlementRequestView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/settlements/:settlementId',
    name: 'settlement-detail',
    component: () => import('./views/SettlementDetailView.vue'),
    meta: { requiresAuth: true },
  },
]

export default routes
