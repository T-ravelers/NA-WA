import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/profile',
    name: 'profile',
    component: () => import('./views/ProfileView.vue'),
    meta: { requiresAuth: true },
  },
  {
    // 설치형 PWA에 남은 예전 바로가기가 404로 떨어지지 않게 둔다.
    path: '/settings',
    redirect: '/profile',
  },
]

export default routes
