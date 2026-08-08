/**
 * API 주소 설정이 유효한지 확인한다.
 *
 * `VITE_API_BASE_URL`은 `frontend/.env.development` 같은 환경별 파일에서 온다.
 * 저장소에는 `.env.example`만 있고 나머지는 `.gitignore`가 막으므로, 클론한 뒤
 * 복사하지 않으면 값이 비어 있다.
 */

/**
 * API 주소가 비어 있으면 기동을 멈춘다.
 *
 * 값이 없으면 axios가 상대 경로로 요청하고, Vite dev 서버의 SPA fallback이 앱 셸
 * HTML을 `200 OK`로 돌려준다. 네트워크 탭에는 성공만 보이고 `ApiResponse` 봉투를
 * 벗기는 단계에 가서야 깨지므로, 원인을 찾기까지 오래 걸린다. 같은 종류의 조용한
 * 실패를 #108에서 한 번 겪었다.
 *
 * `docs/TECH_STACK.md`가 정한 대로 운영 기본값으로 대체하지 않는다. 대체하지
 * 않기로 했으면 조용히 넘어가서도 안 되므로, 기동 시점에 끊는다.
 */
export function assertApiBaseUrlConfigured(value: string | undefined): void {
  if (typeof value === 'string' && value.trim() !== '') {
    return
  }

  throw new Error(
    'VITE_API_BASE_URL이 비어 있어 앱을 시작할 수 없습니다. ' +
      '`cp frontend/.env.example frontend/.env.development`으로 파일을 만들고 ' +
      '값을 채운 뒤 개발 서버를 다시 시작하세요.',
  )
}
