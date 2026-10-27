import {
  deletePushSubscription,
  fetchPushPublicKey,
  savePushSubscription,
} from '../api/pushSubscriptionApi'
import type { PushSubscribeBody } from '../api/pushSubscriptionApi.types'

/**
 * 이 기기의 웹 푸시 구독을 다루는 브라우저 쪽 동작.
 *
 * 푸시는 목록을 대신하지 않는다. 여기 있는 어떤 것도 실패하거나 지원되지 않아도 알림 목록과
 * 15초 폴링은 그대로 동작해야 하므로, 화면이 기다리는 쪽(`enablePush`)을 빼면 실패를 밖으로
 * 던지지 않는다.
 */

/**
 * - `supported`: 이 브라우저로 구독할 수 있다.
 * - `needs-install`: iOS 사파리 탭이다. 홈 화면에 추가한 앱(iOS 16.4 이상)에서만 웹 푸시가
 *   열리므로, 설치부터 안내해야 한다.
 * - `unsupported`: 구독할 방법이 없다.
 */
export type PushSupport = 'supported' | 'needs-install' | 'unsupported'

export interface PushEnvironment {
  hasServiceWorker: boolean
  hasPushManager: boolean
  hasNotification: boolean
  userAgent: string
  platform: string
  maxTouchPoints: number
  /** 홈 화면에 추가한 앱으로 열렸는가. */
  standalone: boolean
}

export function browserPushEnvironment(): PushEnvironment {
  return {
    hasServiceWorker: 'serviceWorker' in navigator,
    hasPushManager: 'PushManager' in window,
    hasNotification: 'Notification' in window,
    userAgent: navigator.userAgent,
    platform: navigator.platform,
    maxTouchPoints: navigator.maxTouchPoints,
    standalone:
      (navigator as Navigator & { standalone?: boolean }).standalone === true ||
      (typeof window.matchMedia === 'function' &&
        window.matchMedia('(display-mode: standalone)').matches),
  }
}

/** iPadOS 13 이상은 사용자 에이전트가 Mac으로 나와서, 터치 지원으로 따로 가려낸다. */
function isIos(environment: PushEnvironment): boolean {
  return (
    /iPad|iPhone|iPod/.test(environment.userAgent) ||
    (environment.platform === 'MacIntel' && environment.maxTouchPoints > 1)
  )
}

export function detectPushSupport(environment: PushEnvironment): PushSupport {
  if (environment.hasServiceWorker && environment.hasPushManager && environment.hasNotification) {
    return 'supported'
  }

  return isIos(environment) && !environment.standalone ? 'needs-install' : 'unsupported'
}

/** VAPID 공개키(base64url)를 `subscribe`가 받는 바이트로 바꾼다. */
export function urlBase64ToUint8Array(base64Url: string): Uint8Array {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4)
  const base64 = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/')

  return Uint8Array.from(atob(base64), (char) => char.charCodeAt(0))
}

/** 브라우저의 구독을 서버가 받는 모양으로 옮긴다. 키가 하나라도 빠지면 보낼 수 없다. */
export function toSubscribeBody(
  subscription: PushSubscription,
  language: string,
): PushSubscribeBody {
  const { endpoint, keys } = subscription.toJSON()
  const p256dh = keys?.p256dh
  const auth = keys?.auth

  if (!endpoint || !p256dh || !auth) {
    throw new Error('The push subscription is missing its endpoint or keys.')
  }

  return { endpoint, p256dh, auth, language }
}

/**
 * 이 구독을 만들 때 쓴 VAPID 공개키가 지금 서버의 키인가.
 *
 * 구독은 만든 키에 묶여 있어서, 서버가 키를 바꾸면 푸시 서비스가 예전 구독으로 가는 발송을
 * 거절한다. 브라우저가 키를 알려 주지 않으면(null) 같다고 본다 — 확인할 수 없는 구독을 지우지 않는다.
 */
function isSubscribedWithKey(subscription: PushSubscription, publicKey: string): boolean {
  const used = subscription.options.applicationServerKey
  if (used === null) {
    return true
  }

  const expected = urlBase64ToUint8Array(publicKey)
  const actual = new Uint8Array(used)

  return (
    actual.length === expected.length && actual.every((byte, index) => byte === expected[index])
  )
}

/**
 * 진행 중인 등록 작업에 "더는 구독을 만들지 말라"고 알리는 신호의 표식.
 *
 * 등록 작업은 취소되지 않고 끝까지 돈다. 그래서 로그아웃이 등록을 기다리다 포기하고 브라우저 구독을
 * 끊은 뒤(`dropBrowserPushSubscription`)나 사용자가 푸시를 끈 뒤(`disablePush`)에, 놓인 작업이 늦게
 * 구독을 새로 만들어 저장하면 방금 끊은 자리가 되살아난다. 구독을 만드는 작업은 시작할 때 이 표식을
 * 기억해 두고, 달라졌으면 만들지 않는다. 시작 때의 표식과 비교하므로 신호를 보낸 뒤 새로 시작하는
 * 작업(로그아웃이 실패해 남은 페이지에서 다시 켜기 등)은 막지 않는다.
 *
 * 브라우저 구독과 로그인은 같은 출처의 모든 탭이 나눠 쓰므로 표식도 탭 사이에서 나눈다. 저장소를 읽거나
 * 쓸 수 없으면 이 탭 안에서만 취소된다.
 */
const CANCEL_STORAGE_KEY = 'nawa.push.registrationCancel'
let localCancels = 0

function cancelMark(): string {
  try {
    return `${localCancels}:${localStorage.getItem(CANCEL_STORAGE_KEY) ?? ''}`
  } catch {
    return `${localCancels}:`
  }
}

/** 지금까지 시작한 등록 작업이 더는 구독을 만들지 못하게 한다. 다른 탭의 작업도 포함한다. */
function cancelPendingRegistrations(): void {
  localCancels += 1
  try {
    localStorage.setItem(CANCEL_STORAGE_KEY, `${Date.now()}.${Math.random()}`)
  } catch {
    // 위 설명대로 이 탭의 작업만 취소된다.
  }
}

/** 등록 작업을 시작할 때 부른다. 돌려받은 함수는 그 뒤에 취소 신호가 있었는지 알려 준다. */
function watchCancellation(): () => boolean {
  const startedAt = cancelMark()

  return () => cancelMark() !== startedAt
}

/**
 * 공개키로 새 구독을 만든다. 이 앱에서 구독을 만드는 곳은 여기뿐이다.
 *
 * 작업이 취소됐으면 만들지 않고, 만드는 도중에 취소됐으면 만든 구독을 도로 끊는다. 두 경우 모두
 * null이고, 부르는 쪽은 서버에 저장하지 않고 끝낸다.
 */
async function subscribeWithKey(
  registration: ServiceWorkerRegistration,
  publicKey: string,
  cancelled: () => boolean,
): Promise<PushSubscription | null> {
  if (cancelled()) {
    return null
  }

  const subscription = await registration.pushManager.subscribe({
    // 알림을 띄우지 않는 푸시는 받지 않겠다는 약속이다. iOS는 이 값을 반드시 요구한다.
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(publicKey) as BufferSource,
  })
  if (!cancelled()) {
    return subscription
  }

  await subscription.unsubscribe().catch(() => undefined)

  return null
}

/**
 * 예전 키로 만든 구독을 서버와 브라우저에서 치운다.
 *
 * 브라우저는 키가 다른 구독이 남아 있으면 새 키로 구독하지 못한다. 서버에 남은 행은 푸시 서비스가
 * "끝난 구독"(404·410)이 아니라 인증 거절로 답할 수 있어 스스로 지워진다는 보장이 없으므로 직접
 * 지운다. 서버 삭제가 실패해도 브라우저 구독은 끊는다.
 */
async function discardStaleSubscription(stale: PushSubscription): Promise<void> {
  await deletePushSubscription(stale.endpoint).catch(() => undefined)
  await stale.unsubscribe()
}

/**
 * 구독을 서버에 등록한다. `created`는 이번 시도가 새로 만든 구독이라는 뜻이다.
 *
 * 새로 만든 구독만 등록에 실패했을 때 되돌린다. 서버에 등록되지 않은 채 브라우저에 남으면 카드를
 * 다시 열 때 구독이 있다는 이유로 "켜짐"으로 보이고, 켜기 버튼도 사라져 다시 시도할 수 없다.
 */
async function register(
  subscription: PushSubscription,
  language: string,
  created: boolean,
): Promise<void> {
  try {
    await savePushSubscription(toSubscribeBody(subscription, language))
  } catch (error) {
    if (created) {
      await subscription.unsubscribe().catch(() => undefined)
    }
    throw error
  }
}

/**
 * 이 기기에 이미 있는 구독.
 *
 * `serviceWorker.ready`가 아니라 `getRegistration`을 쓴다. 서비스 워커가 등록되지 않은 환경
 * (개발 서버 등)에서는 `ready`가 영영 끝나지 않아 화면 상태를 읽는 일이 멈춘다.
 */
export async function readPushSubscription(): Promise<PushSubscription | null> {
  const registration = await navigator.serviceWorker.getRegistration()

  return (await registration?.pushManager.getSubscription()) ?? null
}

/**
 * 서버에 구독을 등록하는 작업 가운데 이 탭에서 아직 끝나지 않은 것.
 *
 * 로그인·언어 변경의 동기화는 기다리지 않고 시작한다. 이미 인증을 통과해 처리 중인 요청은 로그아웃으로
 * 취소되지 않아서, 그 PUT이 로그아웃의 DELETE보다 늦게 저장되면 지운 구독이 이전 계정 몫으로 되살아난다.
 * 그래서 등록 작업은 끝날 때까지 여기에 담아 두고, 로그아웃의 삭제가 이것이 끝나기를 기다린다.
 * 다른 탭의 등록은 이 목록에 보이지 않으므로 `withRegistrationLock`이 따로 맡는다.
 */
const registrationsInFlight = new Set<Promise<unknown>>()

/**
 * 등록과 로그아웃의 삭제가 탭 사이에서도 순서를 지키게 하는 락(Web Locks)의 이름.
 *
 * 등록은 이 락을 공유로 잡아 서로 막지 않고, 삭제는 배타로 잡아 어느 탭에서든 진행 중인 등록이 모두
 * 끝난 뒤에 실행한다. 배타 요청이 줄을 서 있으면 그 뒤에 온 등록은 삭제가 끝나기를 기다린다. 탭을
 * 닫으면 그 탭이 쥔 락은 풀린다.
 */
const REGISTRATION_LOCK = 'nawa.push.registration'

/**
 * 락을 잡고 작업을 실행한다.
 *
 * 락 API가 없는 브라우저(Chrome 69·Firefox 96·Safari 15.4 이전)나 락을 잡지 못하는 환경(불투명한
 * 출처 등)에서는 락 없이 실행해, 이 탭의 진행 중 목록만으로 순서를 지킨다. 푸시는 최선 노력이라
 * 순서 보장을 못 한다고 등록 자체를 막지 않는다. 작업이 던진 오류는 그대로 던진다.
 */
async function withRegistrationLock<T>(
  mode: 'shared' | 'exclusive',
  task: () => Promise<T>,
): Promise<T> {
  if (!('locks' in navigator)) {
    return task()
  }

  let started = false
  try {
    return await navigator.locks.request(REGISTRATION_LOCK, { mode }, () => {
      started = true
      return task()
    })
  } catch (error) {
    if (started) {
      throw error
    }

    return task()
  }
}

function trackRegistration<T>(task: Promise<T>): Promise<T> {
  registrationsInFlight.add(task)
  const forget = () => registrationsInFlight.delete(task)
  task.then(forget, forget)

  return task
}

/** 진행 중인 등록이 성공하든 실패하든 모두 끝날 때까지 기다린다. 기다리는 동안 시작된 것도 포함한다. */
async function registrationsSettled(): Promise<void> {
  while (registrationsInFlight.size > 0) {
    await Promise.allSettled([...registrationsInFlight])
  }
}

export interface EnablePushResult {
  permission: NotificationPermission
  enabled: boolean
}

/**
 * 권한을 묻고, 구독하고, 서버에 등록한다.
 *
 * **사용자의 클릭에서 곧바로 이어서 불러야 한다.** 권한 요청을 비동기 작업 뒤로 미루면 Safari가
 * 사용자 동작이 아니라고 보고 거절한다. 그래서 공개키는 미리 받아 두고 인자로 넘긴다.
 *
 * 기기에 이미 있는 구독이 다른 공개키로 만든 것이면 지우고 새 키로 다시 구독한다.
 *
 * 구독을 만들기 전에 이 작업이 취소됐다면(로그아웃이 기다림을 놓았거나 `disablePush`가 불렸다면)
 * 구독하지 않고 `enabled: false`로 끝난다.
 */
export function enablePush(publicKey: string, language: string): Promise<EnablePushResult> {
  // 안쪽 함수를 곧바로 불러, 권한 요청이 클릭과 같은 호출 안에서 시작되게 한다.
  return trackRegistration(requestAndRegister(publicKey, language))
}

async function requestAndRegister(publicKey: string, language: string): Promise<EnablePushResult> {
  const cancelled = watchCancellation()
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    return { permission, enabled: false }
  }

  const registration = await navigator.serviceWorker.ready

  // 사람이 권한에 답하기를 기다리는 동안은 락을 쥐지 않는다. 로그아웃이 그만큼 붙잡혀서는 안 된다.
  return withRegistrationLock('shared', async () => {
    let existing = await registration.pushManager.getSubscription()
    if (existing !== null && !isSubscribedWithKey(existing, publicKey)) {
      // 서버가 키를 바꾸기 전에 만든 구독이다. 그대로 등록하면 푸시 서비스가 발송을 계속 거절한다.
      await discardStaleSubscription(existing)
      existing = null
    }
    const subscription = existing ?? (await subscribeWithKey(registration, publicKey, cancelled))
    if (subscription === null) {
      return { permission, enabled: false }
    }
    await register(subscription, language, existing === null)

    return { permission, enabled: true }
  })
}

/**
 * 서버와 브라우저에서 이 기기의 구독을 지운다.
 *
 * 서버 쪽이 실패해도 브라우저 구독은 끊는다. 서버에 남은 행은 푸시 서비스가 410으로 끝난
 * 구독이라고 알려 줄 때 서버가 스스로 치운다.
 *
 * 끄기는 사용자의 마지막 의사라서, 진행 중인 등록(예: 예전 서버 행의 삭제를 기다리는 키 갱신)이 이
 * 뒤에 구독을 새로 만들어 되살리지 못하게 먼저 취소한다. 다른 탭의 등록도 포함한다.
 */
export async function disablePush(): Promise<void> {
  cancelPendingRegistrations()
  const subscription = await readPushSubscription()
  if (subscription === null) {
    return
  }

  try {
    await deletePushSubscription(subscription.endpoint)
  } catch {
    // 위 설명대로 서버가 나중에 정리한다.
  }
  await subscription.unsubscribe()
}

/**
 * 이 기기에 이미 있는 구독을 로그인한 계정과 현재 언어로 서버에 다시 맞춘다.
 *
 * 같은 기기에 다른 계정이 로그인하면 서버가 소유자를 옮기고, 화면 언어를 바꾸면 서버가 보내는
 * 푸시 본문의 언어가 바뀐다. 서버가 VAPID 키를 바꿨다면 예전 키로 만든 구독은 푸시 서비스가
 * 거절하므로 이때 새 키로 다시 구독한다. 구독이 없으면 아무것도 하지 않고(권한도 묻지 않는다),
 * 실패해도 던지지 않는다 — 다음에 앱을 열 때 다시 맞춘다.
 *
 * 다시 구독하기 전에 이 작업이 취소됐다면(로그아웃이 기다림을 놓았거나 `disablePush`가 불렸다면)
 * 구독을 만들지 않고 끝난다.
 *
 * `onPublicKey`는 서버의 지금 공개키를 확인했을 때 그 키를 받는다. 화면이 따로 들고 있는 키를 같은
 * 것으로 맞추는 데 쓴다 — 갱신은 새 키로 복구했는데 화면의 키가 예전 그대로면, 그 뒤 끄고 켤 때
 * 예전 키로 다시 구독한다.
 */
export function syncPushSubscription(
  language: string,
  onPublicKey?: (publicKey: string) => void,
): Promise<void> {
  // 락을 기다리는 동안 온 취소 신호도 놓치지 않도록, 기다리기 전에 표식을 잡는다.
  const cancelled = watchCancellation()

  return trackRegistration(
    withRegistrationLock('shared', () =>
      registerExistingSubscription(language, cancelled, onPublicKey),
    ),
  )
}

async function registerExistingSubscription(
  language: string,
  cancelled: () => boolean,
  onPublicKey: ((publicKey: string) => void) | undefined,
): Promise<void> {
  try {
    if (
      detectPushSupport(browserPushEnvironment()) !== 'supported' ||
      Notification.permission !== 'granted'
    ) {
      return
    }

    const subscription = await readPushSubscription()
    if (subscription !== null) {
      const current = await renewIfStale(subscription, cancelled, onPublicKey)
      if (current !== null) {
        await register(current.subscription, language, current.created)
      }
    }
  } catch {
    // 위 설명대로 다음에 다시 맞춘다.
  }
}

/**
 * 구독이 예전 키로 만든 것이면 지금 키로 다시 구독해 돌려준다.
 *
 * 서버의 키를 알 수 없으면(푸시가 꺼졌거나 조회에 실패) 확인할 수 없으니 그대로 둔다. 다시 구독이
 * 브라우저에서 막혀 실패하면(사용자 동작을 요구하는 환경 등) 구독이 없는 상태로 끝난다. 어차피
 * 쓸 수 없던 구독이라 잃는 것은 없고, 카드가 "켜기"로 돌아가 사용자가 눌러 다시 켤 수 있다.
 * 작업이 취소돼 구독을 만들지 못한 경우도 같은 상태로 끝나며, null을 돌려준다.
 */
async function renewIfStale(
  subscription: PushSubscription,
  cancelled: () => boolean,
  onPublicKey: ((publicKey: string) => void) | undefined,
): Promise<{ subscription: PushSubscription; created: boolean } | null> {
  const publicKey = await fetchPushPublicKey().catch(() => null)
  if (publicKey === null) {
    return { subscription, created: false }
  }

  onPublicKey?.(publicKey)
  if (isSubscribedWithKey(subscription, publicKey)) {
    return { subscription, created: false }
  }

  await discardStaleSubscription(subscription)
  const registration = await navigator.serviceWorker.ready
  const renewed = await subscribeWithKey(registration, publicKey, cancelled)

  return renewed === null ? null : { subscription: renewed, created: true }
}

/**
 * 로그아웃하기 전에 이 기기의 구독을 서버에서 지운다.
 *
 * 인증이 살아 있는 동안에만 할 수 있어서 로그아웃 요청보다 먼저 불러야 한다. 지우지 않으면
 * 로그아웃한 계정의 알림이 이 기기에 계속 도착한다. 브라우저 구독은 그대로 두어, 같은 기기로
 * 다시 로그인하면 `syncPushSubscription`이 새 계정으로 등록한다.
 *
 * 진행 중인 등록(로그인·언어 변경의 동기화, 켜기)이 있으면 그것이 끝난 뒤에 지운다. 먼저 지우면
 * 늦게 끝난 등록이 삭제 뒤에 저장되어 이전 계정의 구독이 되살아난다. 다른 탭에서 진행 중인 등록도
 * 마찬가지라서, 이 탭의 목록을 기다린 뒤 배타 락을 잡고 지운다(`withRegistrationLock`).
 *
 * 실패는 던진다. 로그아웃을 막지 않는 것은 부르는 쪽(`requestSignOut`)의 몫이고, 이 삭제가
 * 끝나지 못했을 때(기다리는 시간 포함)의 대체 정리는 `dropBrowserPushSubscription`이다.
 */
export async function removePushSubscriptionFromServer(): Promise<void> {
  if (detectPushSupport(browserPushEnvironment()) !== 'supported') {
    return
  }

  await registrationsSettled()
  await withRegistrationLock('exclusive', async () => {
    const subscription = await readPushSubscription()
    if (subscription !== null) {
      await deletePushSubscription(subscription.endpoint)
    }
  })
}

/**
 * 서버를 거치지 않고 이 기기의 브라우저 구독만 끊는다.
 *
 * 로그아웃 전 서버 삭제(`removePushSubscriptionFromServer`)가 실패하거나 시간 안에 끝나지 못하면
 * 서버 행과 브라우저 구독이 모두 남아, 로그아웃한 계정의 알림이 다음 로그인까지 이 기기에 뜬다.
 * 인증이 없어도 되는 일이라 그때 대신 한다. 서버에 남은 행은 푸시 서비스가 410으로 끝난 구독이라고
 * 알려 줄 때 서버가 스스로 치운다. 이 경로에서는 같은 기기로 다시 로그인해도 푸시가 자동으로
 * 이어지지 않으므로 알림 화면의 카드에서 다시 켜야 한다.
 *
 * 이 함수가 실행되면 로그아웃이 아직 끝나지 않은 등록 작업을 기다리다 포기한 것이다. 그 작업은 계속
 * 돌지만, 이 뒤에 구독을 새로 만들지는 못한다(`subscribeWithKey`). 그러지 않으면 방금 끊은 자리에
 * 새 구독이 생겨 로그아웃한 계정의 알림이 다시 이 기기로 온다. 다른 탭에서 도는 작업도 같다.
 */
export async function dropBrowserPushSubscription(): Promise<void> {
  cancelPendingRegistrations()
  if (detectPushSupport(browserPushEnvironment()) !== 'supported') {
    return
  }

  await (await readPushSubscription())?.unsubscribe()
}
