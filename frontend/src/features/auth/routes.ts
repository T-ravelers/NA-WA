import type { RouteRecordRaw } from 'vue-router'

import { AUTH_CALLBACK_PATH, SIGN_IN_PATH, WELCOME_PATH } from '@/shared/config/routePaths'

const routes: RouteRecordRaw[] = [
  {
    // 처음 오는 사용자가 만나는 화면. 이미 로그인한 사용자는 guard가 서비스 화면으로 보낸다.
    path: WELCOME_PATH,
    name: 'auth-welcome',
    component: () => import('./views/WelcomeView.vue'),
    meta: { guestOnly: true },
  },
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
