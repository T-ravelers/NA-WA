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

type SignOutCleanup = () => Promise<void>

/** 정리가 이보다 오래 걸리면 기다리지 않고 다음으로 넘어간다. */
const BEFORE_SIGN_OUT_TIMEOUT_MS = 3_000

let beforeSignOutHook: SignOutCleanup | undefined
let beforeSignOutFallback: SignOutCleanup | undefined
let afterSignOutRequest: (() => void) | undefined

/**
 * 로그아웃 요청을 보내기 전에 할 정리를 주입한다.
 *
 * 인증이 살아 있는 동안에만 할 수 있는 일이 대상이다(예: 이 기기의 푸시 구독을 서버에서 지우기).
 * 로그아웃 요청이 나가면 쿠키가 만료되어 그런 호출은 인증 실패로 돌아온다. shared는 feature를
 * import하지 않으므로 어떤 정리인지는 app 계층이 채운다.
 *
 * `fallback`은 정리가 실패하거나 시간 안에 끝나지 않았을 때만 대신 실행한다. 인증 없이도 되는
 * 더 작은 정리여야 한다(예: 서버를 거치지 않고 브라우저의 푸시 구독만 끊기).
 *
 * `after`는 로그아웃 요청이 끝나면(성공·실패 모두) 부른다. 정리가 로그아웃 요청이 끝날 때까지 붙잡아
 * 둔 것을 놓는 자리다(예: 정리 뒤에 다른 탭이 이전 계정으로 다시 저장하지 못하게 막아 둔 것). 응답이
 * 오기 전까지는 쿠키가 살아 있어서, 정리를 마친 직후 풀어 주면 그 사이의 요청이 정리를 되돌린다.
 */
export function setBeforeSignOutHook(
  hook: SignOutCleanup | undefined,
  fallback?: SignOutCleanup,
  after?: () => void,
): void {
  beforeSignOutHook = hook
  beforeSignOutFallback = fallback
  afterSignOutRequest = after
}

/** 제한 시간 안에 끝나면 true. 실패하거나 시간이 지나면 false이고, 던지지 않는다. */
async function finishesInTime(task: SignOutCleanup): Promise<boolean> {
  let timer: ReturnType<typeof setTimeout> | undefined
  try {
    return await Promise.race([
      task().then(() => true),
      new Promise<boolean>((resolve) => {
        timer = setTimeout(() => resolve(false), BEFORE_SIGN_OUT_TIMEOUT_MS)
      }),
    ])
  } catch {
    return false
  } finally {
    clearTimeout(timer)
  }
}

/**
 * 정리는 덤이다. 실패하거나 오래 걸려도 로그아웃을 막지 않는다 — 사용자가 로그아웃을 눌렀는데
 * 부가 정리 때문에 못 나가면 안 된다. 다만 정리를 끝내지 못했다고 그대로 두면 안 되는 일이 있어
 * (로그아웃한 계정의 푸시가 이 기기에 계속 온다), 그때는 대체 정리를 한 번 더 시도한다.
 */
async function runBeforeSignOutHook(): Promise<void> {
  if (beforeSignOutHook === undefined) {
    return
  }

  if (!(await finishesInTime(beforeSignOutHook)) && beforeSignOutFallback !== undefined) {
    await finishesInTime(beforeSignOutFallback)
  }
}

/** 정리는 덤이라, 붙잡아 둔 것을 놓다가 실패해도 로그아웃의 결과를 바꾸지 않는다. */
function runAfterSignOutRequest(): void {
  try {
    afterSignOutRequest?.()
  } catch {
    // 위 설명대로 무시한다.
  }
}

/**
 * 서버가 브라우저 인증 쿠키 만료를 확인한 뒤 앱의 세션 상태를 폐기한다.
 */
export async function requestSignOut(): Promise<void> {
  // 장벽을 세우기 전에 한다. 장벽이 서면 인증 갱신이 막혀, 만료된 토큰으로는 정리가 실패한다.
  await runBeforeSignOutHook()
  activateSignOutBarrier()
  try {
    await httpClient.post('/api/v1/auth/logout')
  } finally {
    runAfterSignOutRequest()
  }
  clearSignOutBarrier()
  signedOutHandler?.()
}
