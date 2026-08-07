import type { RouteRecordRaw } from 'vue-router'

/**
 * 특정 도메인에 속하지 않는 앱 수준 route.
 *
 * 도메인 화면은 여기가 아니라 `features/<domain>/routes.ts`에 추가한다.
 *
 * `/`는 인증 도메인이 랜딩 화면으로 소유한다. redirect가 guard보다 먼저 해석되므로,
 * 여기서 서비스 화면으로 넘기면 처음 온 사용자가 랜딩을 보지 못한다.
 */
export const appRoutes: RouteRecordRaw[] = [
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/app/views/NotFoundView.vue'),
  },
]
