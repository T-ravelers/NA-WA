import type { NavigationGuard, RouteLocationNormalized } from 'vue-router'

import { syncLocaleWithProfile } from '@/features/member/model/localeSync'
import type { MemberProfile } from '@/features/member/api/memberApi'
import { ensureMemberProfile } from '@/features/member/model/memberQueries'
import {
  AUTHENTICATED_HOME_PATH,
  MERCHANT_HOME_PATH,
  ONBOARDING_PATH,
  SIGN_IN_PATH,
} from '@/shared/config/routePaths'
import { isSignOutBarrierActive } from '@/shared/api/signOutBarrier'

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

/** `/merchant`와 그 하위만 가맹점 화면이다. `/merchant-x` 같은 이웃 경로를 통과시키지 않는다. */
function isMerchantPath(path: string): boolean {
  return path === MERCHANT_HOME_PATH || path.startsWith(`${MERCHANT_HOME_PATH}/`)
}

/**
 * 가맹점 계정이 손님 화면으로 가려는지 판단한다.
 *
 * 가맹점은 회원가입·QR 생성·매출 조회만 한다. 화면마다 막지 않고 여기 한 곳에서
 * 되돌린다. guard는 화면이 그려지기 전에 돌므로 손님 화면이 스쳐 보이지 않는다.
 *
 * 로그인 직후 가맹점 등록을 마치기 전까지는 계정이 아직 `TRAVELER`라 이 분기가 걸리지
 * 않는다. 그 구간은 `/merchant`로 보내는 복귀 경로가 맡는다.
 */
function isMerchantLockedOut(to: RouteLocationNormalized, profile: MemberProfile | null): boolean {
  return (
    to.meta.requiresAuth === true && profile?.accountType === 'MERCHANT' && !isMerchantPath(to.path)
  )
}

/**
 * 온보딩을 마치지 않은 사용자가 서비스 화면으로 가려는지 판단한다.
 *
 * 온보딩은 표시 이름·국적·언어·통화를 한 번에 확정하는 절차이고 서버가 부분 저장을
 * 허용하지 않는다. 그 전에는 회원을 가리키는 화면들이 빈 프로필을 그리게 되므로 한 곳에서
 * 되돌린다.
 *
 * **가맹점 경로는 예외다.** 가맹점주는 로그인 직후 아직 `TRAVELER`이고 온보딩도 마치지
 * 않은 상태로 `/merchant`에 들어와 상호명을 확정한다(그 등록이 `onboarding_completed_at`을
 * 채운다). 여기서 막으면 가맹점 가입 경로가 통째로 닫힌다.
 */
function isOnboardingLockedOut(
  to: RouteLocationNormalized,
  profile: MemberProfile | null,
): boolean {
  return (
    to.meta.requiresAuth === true &&
    profile?.onboardingRequired === true &&
    to.path !== ONBOARDING_PATH &&
    !isMerchantPath(to.path)
  )
}

export const authGuard: NavigationGuard = async (to) => {
  if (to.meta.requiresAuth !== true && to.meta.guestOnly !== true) {
    return true
  }

  if (isSignOutBarrierActive()) {
    return to.meta.requiresAuth === true ? { path: SIGN_IN_PATH } : true
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

  if (isMerchantLockedOut(to, profile)) {
    return { path: MERCHANT_HOME_PATH }
  }

  if (isOnboardingLockedOut(to, profile)) {
    return { path: ONBOARDING_PATH }
  }

  // 마친 사람이 주소로 다시 들어오면 되돌린다. 빈 폼을 다시 채우게 두지 않는다.
  if (to.path === ONBOARDING_PATH && profile?.onboardingRequired === false) {
    return { path: AUTHENTICATED_HOME_PATH }
  }

  return true
}
