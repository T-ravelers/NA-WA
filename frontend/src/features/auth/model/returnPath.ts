const STORAGE_KEY = 'nawa.auth.returnPath'

/**
 * 로그인 후 돌아갈 위치를 브라우저에 잠시 보관한다.
 *
 * 백엔드 `AUTH_FRONTEND_ALLOWED_RETURN_PATHS`는 오픈 리다이렉트를 막는 보안 장치이며
 * 완전 일치로만 검사한다(OAuthReturnPathPolicy). 따라서 화면 경로를 백엔드에 넘기면
 * 화면이 늘 때마다 백엔드 설정을 고쳐야 하고, 쿼리 파라미터가 붙은 경로는 아예 쓸 수
 * 없다.
 *
 * 그래서 백엔드에는 returnPath를 보내지 않고 기본값 `/`로 복귀시킨 뒤, 실제 위치는
 * 여기에 보관했다가 콜백 화면에서 복원한다. 백엔드 허용 목록은 좁은 상태로 둔다.
 *
 * OAuth는 전체 페이지 이동이라 메모리 상태가 사라지므로 `sessionStorage`를 쓴다.
 * 탭 단위로만 유지되고 탭을 닫으면 사라진다. 경로 외의 정보는 저장하지 않는다.
 */

/** 같은 앱 안의 경로만 허용한다. `//host`나 절대 URL은 외부로 나갈 수 있어 막는다. */
function isInAppPath(value: string): boolean {
  return value.startsWith('/') && !value.startsWith('//')
}

export function storeReturnPath(value: unknown): void {
  if (typeof value !== 'string' || !isInAppPath(value)) {
    sessionStorage.removeItem(STORAGE_KEY)

    return
  }

  sessionStorage.setItem(STORAGE_KEY, value)
}

/** 저장된 경로를 한 번만 돌려주고 지운다. 없거나 유효하지 않으면 `null`이다. */
export function consumeReturnPath(): string | null {
  const stored = sessionStorage.getItem(STORAGE_KEY)

  sessionStorage.removeItem(STORAGE_KEY)

  return stored !== null && isInAppPath(stored) ? stored : null
}
