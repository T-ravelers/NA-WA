const STORAGE_KEY = 'nawa.auth.signOutBarrier'
const ACTIVE_VALUE = 'active'

type SignOutBarrierListener = (active: boolean) => void

const listeners = new Set<SignOutBarrierListener>()

function notify(active: boolean): void {
  listeners.forEach((listener) => listener(active))
}

/** 응답이 불확실한 로그아웃 의도가 이 브라우저에 남아 있는지 확인한다. */
export function isSignOutBarrierActive(): boolean {
  return localStorage.getItem(STORAGE_KEY) === ACTIVE_VALUE
}

/** 로그아웃 요청보다 먼저 장벽을 세워 세션 복구와 보호 화면 재진입을 막는다. */
export function activateSignOutBarrier(): void {
  localStorage.setItem(STORAGE_KEY, ACTIVE_VALUE)
  notify(true)
}

/** 서버 로그아웃 또는 새 로그인 성공이 확정된 뒤 장벽을 해제한다. */
export function clearSignOutBarrier(): void {
  localStorage.removeItem(STORAGE_KEY)
  notify(false)
}

/** 같은 탭의 변경과 다른 탭의 storage 이벤트를 하나의 계약으로 구독한다. */
export function subscribeSignOutBarrier(listener: SignOutBarrierListener): () => void {
  listeners.add(listener)

  return () => listeners.delete(listener)
}

window.addEventListener('storage', (event) => {
  if (event.key !== STORAGE_KEY || event.storageArea !== localStorage) {
    return
  }

  notify(isSignOutBarrierActive())
})
