import { queryClient } from '@/app/query/client'
import { router } from '@/app/router'
import { clearReturnPath } from '@/features/auth/model/returnPath'
import { clearMemberProfile } from '@/features/member/model/memberQueries'
import { clearCsrfToken } from '@/shared/api/csrf'
import { AUTH_CALLBACK_PATH, SIGN_IN_PATH } from '@/shared/config/routePaths'

/** 이미 인증 화면에 있으면 다시 보내지 않는다. */
const AUTH_FLOW_PATHS: string[] = [SIGN_IN_PATH, AUTH_CALLBACK_PATH]

function clearSessionData(): void {
  // 프로필 전용 정리 계약을 먼저 지키고, 다른 계정 데이터까지 전체 캐시에서 제거한다.
  clearMemberProfile()
  queryClient.clear()
  clearCsrfToken()
}

/** 세션 만료는 사용자가 보던 보호 경로를 로그인 뒤 복원한다. */
export function handleSessionExpired(): void {
  clearSessionData()

  const current = router.currentRoute.value

  // 로그인 화면에서 다시 로그인 화면으로 보내면 returnPath가 `/sign-in`이 된다.
  if (AUTH_FLOW_PATHS.includes(current.path)) {
    return
  }

  void router.replace({
    path: SIGN_IN_PATH,
    query: { returnPath: current.fullPath },
  })
}

/** 명시적 로그아웃은 이전 복귀 경로를 폐기하고 새 로그인 흐름을 시작한다. */
export function handleSignedOut(): void {
  clearSessionData()
  clearReturnPath()

  void router.replace({ path: SIGN_IN_PATH })
}
