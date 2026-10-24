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

type BeforeSignOutHook = () => Promise<void>

/** 정리가 이보다 오래 걸리면 기다리지 않고 로그아웃한다. */
const BEFORE_SIGN_OUT_TIMEOUT_MS = 3_000

let beforeSignOutHook: BeforeSignOutHook | undefined

/**
 * 로그아웃 요청을 보내기 전에 할 정리를 주입한다.
 *
 * 인증이 살아 있는 동안에만 할 수 있는 일이 대상이다(예: 이 기기의 푸시 구독을 서버에서 지우기).
 * 로그아웃 요청이 나가면 쿠키가 만료되어 그런 호출은 인증 실패로 돌아온다. shared는 feature를
 * import하지 않으므로 어떤 정리인지는 app 계층이 채운다.
 */
export function setBeforeSignOutHook(hook: BeforeSignOutHook | undefined): void {
  beforeSignOutHook = hook
}

/**
 * 정리는 덤이다. 실패하거나 오래 걸려도 로그아웃을 막지 않는다 — 사용자가 로그아웃을 눌렀는데
 * 부가 정리 때문에 못 나가면 안 된다. 못 한 정리는 다음 로그인에서 맞춰진다.
 */
async function runBeforeSignOutHook(): Promise<void> {
  if (beforeSignOutHook === undefined) {
    return
  }

  let timer: ReturnType<typeof setTimeout> | undefined
  try {
    await Promise.race([
      beforeSignOutHook(),
      new Promise<void>((resolve) => {
        timer = setTimeout(resolve, BEFORE_SIGN_OUT_TIMEOUT_MS)
      }),
    ])
  } catch {
    // 위 설명대로 로그아웃은 계속한다.
  } finally {
    clearTimeout(timer)
  }
}

/**
 * 서버가 브라우저 인증 쿠키 만료를 확인한 뒤 앱의 세션 상태를 폐기한다.
 */
export async function requestSignOut(): Promise<void> {
  // 장벽을 세우기 전에 한다. 장벽이 서면 인증 갱신이 막혀, 만료된 토큰으로는 정리가 실패한다.
  await runBeforeSignOutHook()
  activateSignOutBarrier()
  await httpClient.post('/api/v1/auth/logout')
  clearSignOutBarrier()
  signedOutHandler?.()
}
