import { deletePushSubscription, savePushSubscription } from '../api/pushSubscriptionApi'
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
 * 서버에 구독을 등록하는 작업 가운데 아직 끝나지 않은 것.
 *
 * 로그인·언어 변경의 동기화는 기다리지 않고 시작한다. 이미 인증을 통과해 처리 중인 요청은 로그아웃으로
 * 취소되지 않아서, 그 PUT이 로그아웃의 DELETE보다 늦게 저장되면 지운 구독이 이전 계정 몫으로 되살아난다.
 * 그래서 등록 작업은 끝날 때까지 여기에 담아 두고, 로그아웃의 삭제가 이것이 끝나기를 기다린다.
 */
const registrationsInFlight = new Set<Promise<unknown>>()

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
 */
export function enablePush(publicKey: string, language: string): Promise<EnablePushResult> {
  // 안쪽 함수를 곧바로 불러, 권한 요청이 클릭과 같은 호출 안에서 시작되게 한다.
  return trackRegistration(requestAndRegister(publicKey, language))
}

async function requestAndRegister(publicKey: string, language: string): Promise<EnablePushResult> {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    return { permission, enabled: false }
  }

  const registration = await navigator.serviceWorker.ready
  const existing = await registration.pushManager.getSubscription()
  const subscription =
    existing ??
    (await registration.pushManager.subscribe({
      // 알림을 띄우지 않는 푸시는 받지 않겠다는 약속이다. iOS는 이 값을 반드시 요구한다.
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey) as BufferSource,
    }))

  try {
    await savePushSubscription(toSubscribeBody(subscription, language))
  } catch (error) {
    // 이번에 새로 만든 구독만 되돌린다. 서버에 등록되지 않은 채 브라우저에 남으면 카드를 다시 열 때
    // 구독이 있다는 이유로 "켜짐"으로 보이고, 켜기 버튼도 사라져 다시 시도할 수 없다.
    if (existing === null) {
      await subscription.unsubscribe().catch(() => undefined)
    }
    throw error
  }

  return { permission, enabled: true }
}

/**
 * 서버와 브라우저에서 이 기기의 구독을 지운다.
 *
 * 서버 쪽이 실패해도 브라우저 구독은 끊는다. 서버에 남은 행은 푸시 서비스가 410으로 끝난
 * 구독이라고 알려 줄 때 서버가 스스로 치운다.
 */
export async function disablePush(): Promise<void> {
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
 * 푸시 본문의 언어가 바뀐다. 구독이 없으면 아무것도 하지 않고(권한도 묻지 않는다), 실패해도
 * 던지지 않는다 — 다음에 앱을 열 때 다시 맞춘다.
 */
export function syncPushSubscription(language: string): Promise<void> {
  return trackRegistration(registerExistingSubscription(language))
}

async function registerExistingSubscription(language: string): Promise<void> {
  try {
    if (
      detectPushSupport(browserPushEnvironment()) !== 'supported' ||
      Notification.permission !== 'granted'
    ) {
      return
    }

    const subscription = await readPushSubscription()
    if (subscription !== null) {
      await savePushSubscription(toSubscribeBody(subscription, language))
    }
  } catch {
    // 위 설명대로 다음에 다시 맞춘다.
  }
}

/**
 * 로그아웃하기 전에 이 기기의 구독을 서버에서 지운다.
 *
 * 인증이 살아 있는 동안에만 할 수 있어서 로그아웃 요청보다 먼저 불러야 한다. 지우지 않으면
 * 로그아웃한 계정의 알림이 이 기기에 계속 도착한다. 브라우저 구독은 그대로 두어, 같은 기기로
 * 다시 로그인하면 `syncPushSubscription`이 새 계정으로 등록한다.
 *
 * 진행 중인 등록(로그인·언어 변경의 동기화, 켜기)이 있으면 그것이 끝난 뒤에 지운다. 먼저 지우면
 * 늦게 끝난 등록이 삭제 뒤에 저장되어 이전 계정의 구독이 되살아난다.
 *
 * 실패는 던진다. 로그아웃을 막지 않는 것은 부르는 쪽(`requestSignOut`)의 몫이고, 이 삭제가
 * 끝나지 못했을 때(기다리는 시간 포함)의 대체 정리는 `dropBrowserPushSubscription`이다.
 */
export async function removePushSubscriptionFromServer(): Promise<void> {
  if (detectPushSupport(browserPushEnvironment()) !== 'supported') {
    return
  }

  await registrationsSettled()
  const subscription = await readPushSubscription()
  if (subscription !== null) {
    await deletePushSubscription(subscription.endpoint)
  }
}

/**
 * 서버를 거치지 않고 이 기기의 브라우저 구독만 끊는다.
 *
 * 로그아웃 전 서버 삭제(`removePushSubscriptionFromServer`)가 실패하거나 시간 안에 끝나지 못하면
 * 서버 행과 브라우저 구독이 모두 남아, 로그아웃한 계정의 알림이 다음 로그인까지 이 기기에 뜬다.
 * 인증이 없어도 되는 일이라 그때 대신 한다. 서버에 남은 행은 푸시 서비스가 410으로 끝난 구독이라고
 * 알려 줄 때 서버가 스스로 치운다. 이 경로에서는 같은 기기로 다시 로그인해도 푸시가 자동으로
 * 이어지지 않으므로 알림 화면의 카드에서 다시 켜야 한다.
 */
export async function dropBrowserPushSubscription(): Promise<void> {
  if (detectPushSupport(browserPushEnvironment()) !== 'supported') {
    return
  }

  await (await readPushSubscription())?.unsubscribe()
}
