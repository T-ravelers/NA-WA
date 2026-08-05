import type { NavigationGuard, RouteLocationNormalized } from 'vue-router'

import { ensureAuthSession } from '@/features/auth/model/authQueries'
import { AUTHENTICATED_HOME_PATH, SIGN_IN_PATH } from '@/shared/config/routePaths'

/**
 * 인증 정책은 이 guard 하나로만 처리한다.
 *
 * 화면 컴포넌트에서 개별적으로 인증을 확인하지 않는다. route의 `meta`로 선언한다.
 *
 * - `meta.requiresAuth`: 미인증이면 로그인 화면으로 보낸다.
 * - `meta.guestOnly`: 이미 인증됐다면 서비스 화면으로 보낸다.
 */
declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
  }
}

function resolveReturnPath(to: RouteLocationNormalized): string | undefined {
  return to.fullPath === '/' ? undefined : to.fullPath
}

export const authGuard: NavigationGuard = async (to) => {
  if (to.meta.requiresAuth !== true && to.meta.guestOnly !== true) {
    return true
  }

  const session = await ensureAuthSession()

  if (to.meta.requiresAuth === true && session === null) {
    return { path: SIGN_IN_PATH, query: { returnPath: resolveReturnPath(to) } }
  }

  if (to.meta.guestOnly === true && session !== null) {
    return { path: AUTHENTICATED_HOME_PATH }
  }

  return true
}
