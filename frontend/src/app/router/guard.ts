import type { NavigationGuard, RouteLocationNormalized } from 'vue-router'

import { syncLocaleWithProfile } from '@/features/member/model/localeSync'
import { ensureMemberProfile } from '@/features/member/model/memberQueries'
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
    /** 하단 탭바를 감춘다. 상세·플로우 화면처럼 뒤로 갈 곳이 명확한 화면에서 쓴다. */
    hideBottomNav?: boolean
  }
}

function resolveReturnPath(to: RouteLocationNormalized): string | undefined {
  return to.fullPath === '/' ? undefined : to.fullPath
}

export const authGuard: NavigationGuard = async (to) => {
  if (to.meta.requiresAuth !== true && to.meta.guestOnly !== true) {
    return true
  }

  // members/me는 미인증이면 401이므로 이 호출 하나가 세션 확인을 겸한다.
  const profile = await ensureMemberProfile()

  if (to.meta.requiresAuth === true && profile === null) {
    return { path: SIGN_IN_PATH, query: { returnPath: resolveReturnPath(to) } }
  }

  if (to.meta.guestOnly === true && profile !== null) {
    return { path: AUTHENTICATED_HOME_PATH }
  }

  if (profile !== null) {
    // 로케일 동기화 실패가 화면 진입을 막아서는 안 된다.
    await syncLocaleWithProfile(profile).catch(() => undefined)
  }

  return true
}
