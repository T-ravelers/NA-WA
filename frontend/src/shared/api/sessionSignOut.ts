import { httpClient } from './httpClient'
import { activateSignOutBarrier, clearSignOutBarrier } from './signOutBarrier'

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
 * 서버가 브라우저 인증 쿠키 만료를 확인한 뒤 앱의 세션 상태를 폐기한다.
 */
export async function requestSignOut(): Promise<void> {
  activateSignOutBarrier()
  await httpClient.post('/api/v1/auth/logout')
  clearSignOutBarrier()
  signedOutHandler?.()
}
