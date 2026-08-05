import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/wallet',
    name: 'wallet',
    component: () => import('./views/WalletView.vue'),
    meta: { requiresAuth: true },
  },
]

export default routes
