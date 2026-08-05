import type { RouteRecordRaw } from 'vue-router'

import { AUTHENTICATED_HOME_PATH } from '@/shared/config/routePaths'

/**
 * 특정 도메인에 속하지 않는 앱 수준 route.
 *
 * 도메인 화면은 여기가 아니라 `features/<domain>/routes.ts`에 추가한다.
 */
export const appRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'root',
    /**
     * redirect는 guard보다 먼저 해석된다. 따라서 인증 여부 판단을 여기서 하지 않고
     * 서비스 화면으로 넘긴 뒤 `meta.requiresAuth`를 authGuard가 처리하게 둔다.
     * 미인증이면 authGuard가 로그인 화면으로 보낸다.
     */
    redirect: AUTHENTICATED_HOME_PATH,
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/app/views/NotFoundView.vue'),
  },
]
