import type { RouteRecordRaw } from 'vue-router'

import { ONBOARDING_PATH } from '@/shared/config/routePaths'

const routes: RouteRecordRaw[] = [
  {
    path: '/profile',
    name: 'profile',
    component: () => import('./views/ProfileView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/profile/edit',
    name: 'profile-edit',
    component: () => import('./views/ProfileEditView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  {
    /*
     * 온보딩. guard가 `onboardingRequired`인 사용자를 여기로 보낸다.
     *
     * 건너뛸 수 없는 화면이라 하단 탭을 감춘다 — 탭이 보이면 못 가는 곳을 가리키게 된다.
     */
    path: ONBOARDING_PATH,
    name: 'onboarding',
    component: () => import('./views/OnboardingView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  {
    // 설치형 PWA에 남은 예전 바로가기가 404로 떨어지지 않게 둔다.
    path: '/settings',
    redirect: '/profile',
  },
]

export default routes
