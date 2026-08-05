import { queryClient } from '@/app/query/client'

import { fetchAuthMe, type AuthMeResponse } from '../api/authApi'

export const authQueryKeys = {
  all: ['auth'] as const,
  me: () => [...authQueryKeys.all, 'me'] as const,
}

/**
 * 현재 세션을 확인한다.
 *
 * 인증 상태의 소유자는 Vue Query다. Pinia나 localStorage에 복제하지 않는다.
 * 토큰은 HttpOnly 쿠키로만 오간다.
 *
 * 라우터 guard가 화면 진입마다 호출하므로 캐시된 값을 재사용한다.
 * 미인증이면 `null`을 돌려주고 오류로 취급하지 않는다.
 */
export async function ensureAuthSession(): Promise<AuthMeResponse | null> {
  try {
    return await queryClient.ensureQueryData({
      queryKey: authQueryKeys.me(),
      queryFn: fetchAuthMe,
      staleTime: 30_000,
    })
  } catch {
    return null
  }
}

export function clearAuthSession(): void {
  queryClient.removeQueries({ queryKey: authQueryKeys.all })
}
