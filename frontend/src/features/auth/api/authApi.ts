import { httpClient } from '@/shared/api/httpClient'

/** `GET /api/v1/auth/me` 응답. 백엔드 AuthMeResponse와 1:1로 맞춘다. */
export interface AuthMeResponse {
  memberId: number
  displayName: string
  profileImageUrl: string | null
  preferredLanguage: string
  preferredCurrencyCode: string
  onboardingRequired: boolean
}

export async function fetchAuthMe(): Promise<AuthMeResponse> {
  const response = await httpClient.get<AuthMeResponse>('/api/v1/auth/me', {
    /**
     * 401이면 갱신 후 재시도까지는 공통 인터셉터가 처리하지만, 갱신마저 실패했을 때의
     * 화면 이동은 라우터 guard가 결정한다. 여기서 전역 리다이렉트가 끼어들면 guard가
     * 만들던 `returnPath` 리다이렉트를 취소해 복귀 경로가 사라진다.
     */
    suppressSessionExpiredRedirect: true,
  })

  return response.data
}

export async function requestSignOut(): Promise<void> {
  await httpClient.post('/api/v1/auth/logout')
}

/**
 * 소셜 로그인 시작. 백엔드가 302로 provider 인증 페이지에 보낸다.
 *
 * `returnPath`를 넘기지 않는다. 백엔드의 허용 목록은 완전 일치로만 검사하는 보안
 * 장치라 화면 경로를 넘기면 AUTH-008로 거부된다. 복귀 위치는 `model/returnPath.ts`가
 * 브라우저에 보관했다가 콜백에서 복원한다.
 */
export function buildAuthorizationUrl(provider: 'google' | 'line'): string {
  return `${import.meta.env.VITE_API_BASE_URL}/api/v1/auth/oauth2/authorization/${provider}`
}
