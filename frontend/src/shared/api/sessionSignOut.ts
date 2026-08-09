import { httpClient } from './httpClient'

type SignedOutHandler = () => void

let signedOutHandler: SignedOutHandler | undefined

/**
 * 명시적 로그아웃 뒤의 앱 동작을 주입한다.
 *
 * shared는 router와 feature를 import하지 않으므로 캐시 정리와 화면 이동은 app 계층이
 * 소유한다.
 */
export function setSignedOutHandler(handler: SignedOutHandler): void {
  signedOutHandler = handler
}

/**
 * 서버 세션 종료가 실패해도 브라우저의 세션 상태는 반드시 폐기한다.
 *
 * 로그아웃 API는 멱등이며, 응답 실패 뒤 기존 캐시를 계속 노출하는 쪽이 더 위험하다.
 */
export async function requestSignOut(): Promise<void> {
  try {
    await httpClient.post('/api/v1/auth/logout')
  } finally {
    signedOutHandler?.()
  }
}
