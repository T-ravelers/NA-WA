import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { appRoutes } from './appRoutes'
import { authGuard } from './guard'

/**
 * feature route 수집.
 *
 * `features/<domain>/routes.ts`가 `RouteRecordRaw[]`를 default export하면 자동으로
 * 등록된다. **새 화면을 추가할 때 이 파일을 수정하지 않는다.** 담당자가 늘어나도
 * 라우터 파일에서 충돌이 생기지 않게 하려는 구조다.
 */
const featureRouteModules = import.meta.glob<{ default: RouteRecordRaw[] }>(
  '../../features/*/routes.ts',
  { eager: true },
)

function collectFeatureRoutes(): RouteRecordRaw[] {
  return Object.values(featureRouteModules).flatMap((module) => module.default)
}

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  // 404 catch-all이 마지막에 오도록 appRoutes를 뒤에 둔다.
  routes: [...collectFeatureRoutes(), ...appRoutes],
})

router.beforeEach(authGuard)
