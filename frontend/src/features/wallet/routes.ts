import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/wallet',
    name: 'wallet',
    component: () => import('./views/WalletHomeView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/wallet/top-up',
    name: 'wallet-top-up',
    component: () => import('./views/TopupView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  {
    path: '/wallet/qr',
    name: 'wallet-qr',
    component: () => import('./views/WalletQrView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  {
    path: '/wallet/transactions',
    name: 'wallet-transactions',
    component: () => import('./views/TransactionsView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  {
    path: '/wallet/transactions/:transactionId',
    name: 'wallet-transaction-detail',
    component: () => import('./views/TransactionDetailView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
]

export default routes
