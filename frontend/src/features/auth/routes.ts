import type { RouteRecordRaw } from 'vue-router'

import { AUTH_CALLBACK_PATH, SIGN_IN_PATH } from '@/shared/config/routePaths'

const routes: RouteRecordRaw[] = [
  {
    path: SIGN_IN_PATH,
    name: 'auth-sign-in',
    component: () => import('./views/SignInView.vue'),
    meta: { guestOnly: true },
  },
  {
    // 백엔드가 리다이렉트하는 경로다. 백엔드 설정과 함께 바꾸지 않으면 로그인이 끊긴다.
    path: AUTH_CALLBACK_PATH,
    name: 'auth-callback',
    component: () => import('./views/AuthCallbackView.vue'),
  },
]

export default routes
