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
